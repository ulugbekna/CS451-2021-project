package cs451;

import cs451.packets.AckPacket;
import cs451.packets.MessagePacket;
import cs451.packets.PacketCodec;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.concurrent.*;

import static cs451.Log.*;

public class PerfectLinkUdp {
    private static final int BUF_SZ = 2048;
    final BlockingQueue<MessagePacket> deliveredMessages;
    private final DatagramSocket socket;
    private final ScheduledExecutorService exec;
    private final PeerMsgTbl<ScheduledFuture<?>> ackSyncTbl;
    private final PeerMsgTbl<Boolean> seenMsgs;


    PerfectLinkUdp(DatagramSocket socket, ScheduledExecutorService exec) {
        this.socket = socket;
        this.exec = exec;
        ackSyncTbl = new PeerMsgTbl<>();
        seenMsgs = new PeerMsgTbl<>();
        deliveredMessages = new LinkedBlockingQueue<>();
    }

    private void sendPacketOrFailSilently(DatagramSocket socket, DatagramPacket outPacket) {
        try {
            socket.send(outPacket);
        } catch (Throwable e) {
            // any failure in sending a packet should be logged,
            error("couldn't send a message packet from perfect links config", e);
        }
    }

    /*
     * Invariant: do not raise
     * */
    public void sendPacketAndScheduleResend(MessagePacket msgPacket,
                                            DatagramPacket outPacket,
                                            int timeoutMs) {
        try {
            trace("sendPacketUntilAck", "sending " + msgPacket + " to :" + outPacket.getPort()
                    + " t/o : " + timeoutMs);

            sendPacketOrFailSilently(socket, outPacket); // fail silently because we anyway resend until an ack is recvd

            var infResend = exec.schedule(
                    () -> sendPacketAndScheduleResend(msgPacket, outPacket, timeoutMs * 2),
                    timeoutMs, TimeUnit.MILLISECONDS);

            ackSyncTbl.set(outPacket.getPort(), msgPacket.id, infResend);
        } catch (Throwable e) {
            error("couldn't send a message packet from perfect links config", e);
        }
    }

    private void processIncomingAckPacket(AckPacket ackPacket, int fromPort) {
        // stop infinite resending of the packet, since received an ack for it
        var infResend = ackSyncTbl.get(fromPort, ackPacket.id);
        if (infResend != null) {
            infResend.cancel(false);
        }
    }

    private void processIncomingMessagePacket(MessagePacket packet, InetAddress fromIP, int fromPort) throws IOException {
        if (seenMsgs.set(fromPort, packet.id, true) != null) { // TODO: this block should happen atomically perhaps?
            deliveredMessages.add(packet);
        }

        // try sending an ack once,
        // if not successful - give up (because the sender will keep sending the message packet until it gets an ack
        // TODO: can I optimize byte[] use ?
        var ackPacket = new AckPacket(packet.senderId, packet.id);
        var b = PacketCodec.convertToBytes(ackPacket);
        try {
            socket.send(new DatagramPacket(b, b.length, fromIP, fromPort));
            trace("processIncomingRequests", "sending an ack: " + ackPacket);
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
                processIncomingAckPacket((AckPacket) packet, fromPort);
            } else if (packet instanceof MessagePacket) {
                processIncomingMessagePacket((MessagePacket) packet, fromIP, fromPort);
            } else {
                assert false; /* a packet MUST be either AckPacket or MessagePacket} */
            }
        } catch (Exception e) {
            error("processing incoming packet", e);
        }
    }


    /*
     * Listen to a socket and submit tasks to schedule incoming packets
     *
     * Note: this fn is blocking
     *
     * Invariant: note that we reuse `recvBuf`, so this function should NOT be called concurrently
     * */
    public void listenToAndHandleIncomingPackets() {
        byte[] recvBuf = new byte[BUF_SZ];
        DatagramPacket inputPacket = new DatagramPacket(recvBuf, recvBuf.length);
        info("Starting to wait for a client to connect...");

        while (true) { // TODO: add support for interruption
            try {
                socket.receive(inputPacket);

                var packet = PacketCodec.convertFromBytes(
                        Arrays.copyOfRange(recvBuf, 0, inputPacket.getLength()));

                exec.submit(() -> processIncomingPacket(packet, inputPacket.getAddress(), inputPacket.getPort()));
            } catch (ClassNotFoundException e) {
                error("on receive: deserializing ", e);
            } catch (IOException e) {
                error("on receive: sending an ack", e);
            }
        }
    }

    public void close() {

    }
}
