package cs451;

import cs451.packets.AckPacket;
import cs451.packets.MessagePacket;
import cs451.packets.PacketCodec;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static cs451.Log.*;

/*
 * TODO: write tests
 * TODO: profile
 * TODO: reuse byte[] when can when sending packets?
 * */
public class PerfectLinkUdp {
    /*
     * CONSTANTS
     * */
    private static final int BUF_SZ = 128;
    private static final int TIMEOUT_MULTIPLICATION_COEFF = 2;

    /*
     * DATA
     * */
    private final DatagramSocket socket;
    private final ScheduledExecutorService exec;
    private final AckSyncTbl ackSyncTbl;
    private final ConcurrentHashMap<MessagePacket, Boolean> seenMsgs;

    /* Statistical data for debugging */
    private final AtomicLong nAcksSent = new AtomicLong(0);
    private final AtomicLong nMsgPacksSent = new AtomicLong(0);
    private final AtomicLong nMsgPacksRecvd = new AtomicLong(0);
    private final AtomicLong nAckPacksRecvd = new AtomicLong(0);

    /* HOOKS */
    private Consumer<MessagePacket> onDeliverCallback = (MessagePacket packet) -> {};


    /*
     * FUNCTIONALITY
     * */
    PerfectLinkUdp(DatagramSocket socket, ScheduledExecutorService exec) {
        this.socket = socket;
        this.exec = exec;
        ackSyncTbl = new AckSyncTbl();
        seenMsgs = new ConcurrentHashMap<>(2048);
    }

    private void sendPacketOrFailSilently(DatagramSocket socket, DatagramPacket outPacket) {
        try {
            nMsgPacksSent.incrementAndGet();
            socket.send(outPacket);
        } catch (Throwable e) {
            // any failure in sending a packet should be logged,
            error("couldn't send a message packet from perfect links config", e);
        }
    }

    /*
     * Sends a packet, schedules its resend and puts the future corresponding to the resend to the `ackSyncTbl`.
     *
     * Invariant: do not raise
     * */
    public void sendMsg(int messageId, DatagramPacket outPacket, int timeoutMs) {
        try {
            trace("sendMsg",
                    "sending message (id: " + messageId + ") to :" +
                            outPacket.getPort() + " with timeout: " + timeoutMs);

            sendPacketOrFailSilently(socket, outPacket); // fail silently as we anyway resend until an ack is recvd

            var infResend = exec.schedule(
                    () -> sendMsg(messageId, outPacket, timeoutMs * TIMEOUT_MULTIPLICATION_COEFF),
                    timeoutMs, TimeUnit.MILLISECONDS);

            ackSyncTbl.set(outPacket.getPort(), messageId, infResend);
        } catch (Throwable e) {
            error("couldn't send a message packet from perfect links config", e);
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

    /*
     * "Deliver" if the message packet hasn't been seen yet.
     *
     * Best-effort send an `ack` packet for the received message.
     * */
    private void processIncomingMessagePacket(MessagePacket packet, InetAddress fromIP, int fromPort) {
        seenMsgs.computeIfAbsent(packet, (p) -> {
            onDeliverCallback.accept(packet);
            return true;
        });

        // try sending an ack once,
        // if not successful - give up (because the sender will keep sending the message packet until it gets an ack
        // TODO: can I optimize byte[] use ?
        byte[] packetBytes = new byte[9];
        var nBytesWritten = PacketCodec.serializeAckPacket(packetBytes, packet.senderId, packet.messageId);
        try {
            socket.send(new DatagramPacket(packetBytes, nBytesWritten, fromIP, fromPort));
            nAcksSent.incrementAndGet();
            trace("processIncomingMessagePacket",
                    "send: Ack { senderId = " + packet.senderId + "; Id = " + packet.messageId);
        } catch (IOException e) {
            warn("couldn't send ack to " +
                    fromIP.toString() + ":" + fromPort + " " + ", but not resending", e);
        }
    }

    /*
     * Invariant: Must not throw since runs in the executor
     *
     * We handle two types of packets, ie all kinds of packets that exist
     * 1. AckPacket
     *   A peer is sending an ack for the packet that we sent,
     *   so we should stop resending it
     * 2. MessagePacket
     *   A peer sent us a message packet, so we try sending an ack for it to that peer
     *
     * TODO: handle "delivery"
     * */
    private void processIncomingPacket(Object packet, InetAddress fromIP, int fromPort) {
        try {
            trace("processIncomingPacket", "received " + packet);
            if (packet instanceof AckPacket) {
                nAckPacksRecvd.incrementAndGet();
                processIncomingAckPacket((AckPacket) packet, fromPort);
            } else if (packet instanceof MessagePacket) {
                nMsgPacksRecvd.incrementAndGet();
                processIncomingMessagePacket((MessagePacket) packet, fromIP, fromPort);
            } else {
                error("incorrect packet: it's neither a msg nor an ack packet");
            }
        } catch (Exception e) {
            error("processing incoming packet", e);
        }
    }


    /*
     * Listen to a socket and submit tasks to schedule incoming packets
     *
     * Assumptions:
     * - assumes that an `IOException` thrown from `socket.receive()` means that the socket was closed
     * Notes:
     * - this fn is blocking
     * */
    public void listenToAndHandleIncomingPackets() {
        try {
            byte[] recvBuf = new byte[BUF_SZ];
            DatagramPacket inputPacket = new DatagramPacket(recvBuf, recvBuf.length);
            info("Starting to wait for a client to connect...");

            while (true) { // TODO: add support for interruption
                try {
                    socket.receive(inputPacket);

                    var packet = PacketCodec.deserialize(recvBuf, inputPacket.getLength());

                    exec.submit(() -> processIncomingPacket(packet, inputPacket.getAddress(), inputPacket.getPort()));
                } catch (SocketException e) {
                    var msg = e.getMessage();
                    if (msg.equals("Socket closed")) {
                        info(
                                "sent packs\n" +
                                        "  msg: " + nMsgPacksSent + "\n" +
                                        "  ack: " + nAcksSent + "\n" +
                                        "received packs\n" +
                                        "  msg: " + nMsgPacksRecvd + "\n" +
                                        "  ack: " + nAckPacksRecvd + "\n"
                        );
                        break;
                    }
                } catch (IOException e) {
                    error("on receive: sending an ack", e);
                }
            }
        } catch (Exception e) {
            error("receiver", e);
        }
    }

    public void registerOnDeliverCallback(Consumer<MessagePacket> cb) {
        onDeliverCallback = cb;
    }
}
