package cs451;

import cs451.packets.MessagePacket;

import java.net.DatagramSocket;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;

import static cs451.Log.error;

/**
 * Properties
 * <p>
 * BEB1. Validity: If pi and pj are correct, then every message broadcast by pi is eventually delivered by pj
 * <p>
 * BEB2. No duplication: No message is delivered more than once
 * <p>
 * BEB3. No creation: No message is delivered unless it was broadcast
 */
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

        plink.sendMsgs(msg, peers.values()); // NOTE: we're guaranteed that `peers.values()` doesn't change
    }
}
