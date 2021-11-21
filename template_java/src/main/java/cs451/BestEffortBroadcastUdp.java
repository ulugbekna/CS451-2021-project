package cs451;

import cs451.packets.MessagePacket;
import cs451.packets.PacketCodec;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.HashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

import static cs451.Constants.INITIAL_RESEND_TIMEOUT;
import static cs451.Constants.SEND_RECV_BUF_SZ;

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
     * Broadcast a message
     */
    void broadcast(MessagePacket msg, HashMap<Integer, Node> peers) {
        onDeliverCallback.accept(msg);

        var outBuf = new byte[SEND_RECV_BUF_SZ];
        var nBytesWritten = PacketCodec.serializeMessagePacket(outBuf, msg);

        peers.forEach((_procId, peerNode) -> {
            var outPacket = new DatagramPacket(outBuf, 0, nBytesWritten, peerNode.addr, peerNode.port);
            plink.sendMsg(msg.messageId, outPacket, INITIAL_RESEND_TIMEOUT);
        });
    }
}
