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

    void broadcast(int msgId, HashMap<Integer, Node> peers) {
        onDeliverCallback.accept(new MessagePacket(myProcId, msgId, String.valueOf(msgId)));
        peers.forEach((Integer procId, Node peerNode) -> {
            var outBuf = new byte[SEND_BUF_SZ];
            var nBytesWritten = PacketCodec.serializeMessagePacket(outBuf, myProcId, msgId,
                    String.valueOf(msgId));
            var outPacket = new DatagramPacket(outBuf, 0, nBytesWritten, peerNode.addr, peerNode.port);

            plink.sendMsg(msgId, outPacket, INITIAL_RESEND_TIMEOUT);
        });
    }
}
