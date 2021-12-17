package cs451;

import cs451.packets.AckPacket;
import cs451.packets.MessagePacket;
import cs451.packets.Packet;
import cs451.packets.PacketCodec;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static cs451.Constants.*;

/*
 * TODO:
 *  The way we allocated buffers now for sending packets is bad. We allocate a lot.
 *      Option 1. Write a pool of byte buffers that can be reused when they're no more useful, ie
 *          their sending is cancelled
 *      Option 2. Write a single large buffer in which we can reuse slots - this should be even better
 * e
 * TODO: write tests
 * TODO: profile
 * TODO: reuse byte[] when can when sending packets?
 * */
public class PerfectLinkUdp {
    /*
     * CONSTANTS
     * */
    private static final int TIMEOUT_MULTIPLICATION_COEFF = 2;
    private static final int SEEN_MSGS_TBL_INIT_SZ = 2048;

    /*
     * DATA
     * */

    /* Provided as arguments */
    private final DatagramSocket socket;
    private final ScheduledExecutorService exec;
    private final Consumer<MessagePacket> onDeliverCallback;

    /* Internal state */
    private final AckSyncTbl ackSyncTbl;
    private final ConcurrentHashMap<MessagePacket, Boolean> seenMsgs;

    /* Internal state for statistics collection */
    private final PerfectLinkStats stats;


    /*
     * FUNCTIONALITY
     * */
    PerfectLinkUdp(DatagramSocket socket, ScheduledExecutorService exec, Consumer<MessagePacket> onDeliverCallback) {
        this.socket = socket;
        this.exec = exec;
        this.onDeliverCallback = onDeliverCallback;

        /* Internal State: */

        ackSyncTbl = new AckSyncTbl();
        seenMsgs = new ConcurrentHashMap<>(SEEN_MSGS_TBL_INIT_SZ);

        stats = new PerfectLinkStats();
    }

    private void sendPacketOrFailSilently(DatagramSocket socket, DatagramPacket outPacket) {
        try {
            stats.msgPackSent();
            socket.send(outPacket);
        } catch (Throwable e) {
            // any failure in sending a packet should be logged,
            Log.error("couldn't send a message packet from perfect links config", e);
        }
    }

    /**
     * Sends a packet, schedules its resend and puts the future corresponding to the resend to the `ackSyncTbl`.
     * <p>
     * Invariant: do not raise
     */
    private void sendPacket(int messageId, DatagramPacket outPacket, int timeoutMs) {
        try {
            System.out.println(
                    "sending msg (id: " + messageId + ") to : " + outPacket.getPort() + " with timeout: " + timeoutMs);

            sendPacketOrFailSilently(socket, outPacket); // fail silently as we anyway resend until an ack is recvd

            var infResend = exec.schedule(
                    () -> sendPacket(messageId, outPacket, timeoutMs * TIMEOUT_MULTIPLICATION_COEFF),
                    timeoutMs, TimeUnit.MILLISECONDS);

            ackSyncTbl.set(outPacket.getPort(), messageId, infResend);
        } catch (Throwable e) {
            Log.error("couldn't send a message packet from perfect links config", e);
        }
    }

    public void sendMsgs(MessagePacket m, Collection<Node> toWhom) {
        var outBuf = new byte[SEND_RECV_BUF_SZ];
        var nBytesWritten = PacketCodec.serializeMessagePacket(outBuf, m);

        for (var node : toWhom) {
            var outPacket = new DatagramPacket(outBuf, 0, nBytesWritten, node.addr, node.port);
            sendPacket(m.messageId, outPacket, INITIAL_RESEND_TIMEOUT);
        }
    }

    /*
     * Cancels a scheduled resend in `ackSyncTbl`.
     * */
    private void processIncomingAckPacket(AckPacket ackPacket, int fromPort) {
        // stop infinite resending of the packet, since received an ack for it
        var infResend = ackSyncTbl.retrieve(fromPort, ackPacket.messageId);
        if (infResend != null) {
            infResend.cancel(false);
        }
    }

    /**
     * "Deliver" if the message packet hasn't been seen yet.
     * <p>
     * Best-effort send an `ack` packet for the received message.
     */
    private void processIncomingMessagePacket(MessagePacket packet, InetAddress fromIP, int fromPort) {
        seenMsgs.computeIfAbsent(packet, (p) -> {
            onDeliverCallback.accept(packet); // TODO: do this outside of compute atomic operation
            return true; // we use `seenMsgs` as a Set, so we don't really care about this returned value
        });

        // try sending an ack once,
        // if not successful - give up (because the sender will keep sending the message packet until it gets an ack
        byte[] packetBytes = new byte[ACK_PACK_SZ]; // TODO: optimize by predeclaring ?
        var nBytesWritten = PacketCodec.serializeAckPacket(packetBytes, packet.senderId, packet.messageId);
        try {
            socket.send(new DatagramPacket(packetBytes, nBytesWritten, fromIP, fromPort));
            stats.ackSent();
            Log.trace("processIncomingMessagePacket",
                    "send: Ack { senderId = " + packet.senderId + "; Id = " + packet.messageId + " }");
        } catch (IOException e) {
            Log.warn("couldn't send ack to " +
                    fromIP.toString() + ":" + fromPort + " " + ", but not resending", e);
        }
    }

    /*
     * INVARIANT: Must NOT throw -- runs in executor
     *
     * We handle two types of packets, ie all kinds of packets that exist
     * 1. AckPacket
     *   A peer is sending an ack for the packet that we sent,
     *   so we should stop resending it
     * 2. MessagePacket
     *   A peer sent us a message packet, so we try sending an ack for it to that peer
     * */
    private void processIncomingPacket(Packet packet, InetAddress fromIP, int fromPort) {
        try {
            Log.trace("processIncomingPacket", "received " + packet);
            if (packet instanceof AckPacket) {
                stats.ackRecvd();
                processIncomingAckPacket((AckPacket) packet, fromPort);
            } else if (packet instanceof MessagePacket) {
                stats.msgPackRecvd();
                processIncomingMessagePacket((MessagePacket) packet, fromIP, fromPort);
            } else {
                Log.error("incorrect packet: it's neither a msg nor an ack packet");
            }
        } catch (Exception e) {
            Log.error("processing incoming packet", e);
        }
    }


    /**
     * Listen to a socket and submit tasks to scheduler to handle incoming packets
     * - AckPacket: stop resending
     * - MessagePacket: acknowledge
     * <p>
     * Assumptions:
     * - assumes that an `IOException` thrown from `socket.receive()` means that the socket was closed
     * <p>
     * Notes:
     * - Important: this fn is blocking
     * - Important: Doesn't throw exceptions.
     */
    public void listenToAndHandleIncomingPackets() {
        try {
            byte[] recvBuf = new byte[SEND_RECV_BUF_SZ];
            DatagramPacket inputPacket = new DatagramPacket(recvBuf, recvBuf.length);
            Log.info("Starting to wait for a client to connect...");

            while (true) { // TODO: add support for interruption
                try {
                    socket.receive(inputPacket);

                    var packet = PacketCodec.deserialize(recvBuf, inputPacket.getLength());

                    exec.submit(() -> processIncomingPacket(packet, inputPacket.getAddress(), inputPacket.getPort()));
                } catch (SocketException e) {
                    var msg = e.getMessage();
                    if (msg.equals("Socket closed")) {
                        stats.logInfo();
                        break;
                    }
                } catch (IOException e) {
                    Log.error("on receive", e);
                }
            }
        } catch (Exception e) {
            Log.error("receiver", e);
        }
    }
}
