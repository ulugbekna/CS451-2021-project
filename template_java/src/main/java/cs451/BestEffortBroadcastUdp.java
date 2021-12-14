package cs451;

import cs451.packets.MessagePacket;
import cs451.packets.PacketCodec;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;

import static cs451.Constants.INITIAL_RESEND_TIMEOUT;
import static cs451.Constants.SEND_RECV_BUF_SZ;
import static cs451.Log.error;

public class BestEffortBroadcastUdp {
    private final PerfectLinkUdp plink;
    private final LinkedBlockingQueue<MessagePacket> deliveredQ;

    public BestEffortBroadcastUdp(DatagramSocket socket,
                                  ScheduledExecutorService exec,
                                  LinkedBlockingQueue<MessagePacket> deliveredQ) {
        this.deliveredQ = deliveredQ;

        plink = new PerfectLinkUdp(socket, exec, this::deliver);
    }

    public void blockingListen() {
        plink.listenToAndHandleIncomingPackets(); // beware: blocks the thread
    }

    private void deliver(MessagePacket m) {
        try {
            deliveredQ.put(m);
        } catch (InterruptedException e) {
            error("BEB deliver: put failure", e);
        }
    }

    /**
     * Broadcast a message
     */
    void broadcast(MessagePacket msg, HashMap<Integer, Node> peers) {
        deliver(msg);

        var outBuf = new byte[SEND_RECV_BUF_SZ];
        var nBytesWritten = PacketCodec.serializeMessagePacket(outBuf, msg);

        peers.forEach((_procId, peerNode) -> {
            var outPacket = new DatagramPacket(outBuf, 0, nBytesWritten, peerNode.addr, peerNode.port);
            plink.sendMsg(msg.messageId, outPacket, INITIAL_RESEND_TIMEOUT);
        });
    }
}
