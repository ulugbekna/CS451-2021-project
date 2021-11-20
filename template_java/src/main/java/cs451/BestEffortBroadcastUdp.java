package cs451;

import cs451.packets.MessagePacket;
import cs451.packets.PacketCodec;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.HashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

import static cs451.Constants.INITIAL_RESEND_TIMEOUT;
import static cs451.Constants.SEND_BUF_SZ;

public class BestEffortBroadcastUdp {
    private final PerfectLinkUdp plink;
    private final int myProcId;
    private final Consumer<MessagePacket> onDeliverCallback;

    public BestEffortBroadcastUdp(int myProcId, DatagramSocket socket, ScheduledExecutorService exec,
                                  Consumer<MessagePacket> onDeliverCallback) {
        this.myProcId = myProcId;
        this.onDeliverCallback = onDeliverCallback;
        plink = new PerfectLinkUdp(socket, exec);
        plink.registerOnDeliverCallback(onDeliverCallback);
    }

    /*
     * Blocks!
     * */
    void blockingListen() {
        plink.listenToAndHandleIncomingPackets(); // beware: blocks the thread
    }

    /**
     * Sends the given message `m` with the `senderId = myId` and `authorId` intact.
     * <p>
     * Differs from `broadcast` in that
     * - it doesn't set `authorId = myId` but keeps intact the one set in `m`
     * - doesn't deliver to itself
     */
    void relay(MessagePacket m, HashMap<Integer, Node> peers) {
        var msgId = m.messageId;
        var outBuf = new byte[SEND_BUF_SZ];
        var nBytesWritten =
                PacketCodec.serializeMessagePacket(outBuf,
                        myProcId,
                        msgId,
                        m.authorId,
                        String.valueOf(msgId));
        var outPacket = new DatagramPacket(outBuf, 0, nBytesWritten);

        peers.forEach((Integer procId, Node peerNode) -> {
            if (procId != m.authorId) {
                outPacket.setAddress(peerNode.addr);
                outPacket.setPort(peerNode.port);
                plink.sendMsg(msgId, outPacket, INITIAL_RESEND_TIMEOUT);
            }
        });
    }

    /*
     * Broadcast a message with `senderId = myId` and `authorId = myId`.
     * */
    void broadcast(int msgId, HashMap<Integer, Node> peers) {
        onDeliverCallback.accept(new MessagePacket(myProcId, msgId, myProcId, String.valueOf(msgId)));

        var outBuf = new byte[SEND_BUF_SZ];
        var nBytesWritten =
                PacketCodec.serializeMessagePacket(outBuf, myProcId, msgId, myProcId, String.valueOf(msgId));
        var outPacket = new DatagramPacket(outBuf, 0, nBytesWritten);

        peers.forEach((Integer procId, Node peerNode) -> {
            outPacket.setAddress(peerNode.addr);
            outPacket.setPort(peerNode.port);
            plink.sendMsg(msgId, outPacket, INITIAL_RESEND_TIMEOUT);
        });
    }
}
