package cs451;

import cs451.packets.MessagePacket;

import java.net.DatagramSocket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

import static cs451.Log.error;

public class UniformReliableBroadcastUdp {
    private final int myProcId;
    private final int nNodes;
    private final HashMap<Integer, Node> peers;

    private final ScheduledExecutorService exec;

    private final Consumer<MessagePacket> onDeliverCallback;
    private final ConcurrentHashMap<MessagePacket, Boolean> delivered;
    private final ConcurrentHashMap<MessagePacket, Boolean> pending;
    private final ConcurrentHashMap<MessagePacket, HashSet<Integer /* id */>> ack;

    private final BestEffortBroadcastUdp beb;

    private final LinkedBlockingQueue<MessagePacket> bebDeliveredQ;

    public UniformReliableBroadcastUdp(int myProcId, HashMap<Integer, Node> peers,
                                       DatagramSocket socket, ScheduledExecutorService exec,
                                       Consumer<MessagePacket> onDeliverCallback) {
        this.myProcId = myProcId;
        this.nNodes = peers.size() + 1; // + 1 because "peers" doesn't contain myself
        this.peers = peers;
        this.onDeliverCallback = onDeliverCallback;
        this.exec = exec;

        bebDeliveredQ = new LinkedBlockingQueue<>();
        beb = new BestEffortBroadcastUdp(socket, exec, bebDeliveredQ);

        /* Internal State: */
        delivered = new ConcurrentHashMap<>();
        pending = new ConcurrentHashMap<>(1024);
        ack = new ConcurrentHashMap<>();
    }

    private void onBebDeliver(MessagePacket bebDeliveredMsg) {
        // TODO: useless allocation? ugly piece of code
        var originalMsg = bebDeliveredMsg.copyWithSenderId(bebDeliveredMsg.authorId);

        ack.compute(originalMsg, (MessagePacket _m, HashSet<Integer> currentSetOrNull) -> {
            var set = currentSetOrNull != null ? currentSetOrNull : new HashSet<Integer>();
            set.add(bebDeliveredMsg.senderId);

            // here we deliver as soon as we can
            // note that we _have_ to check that we haven't delivered because ack can be incremented
            // even after the ack count > nNodes/2, so we don't want to deliver every time
            if (set.size() > (nNodes / 2) && delivered.put(originalMsg, true) == null)
                onDeliverCallback.accept(originalMsg);

            return set;
        });

        if (pending.put(originalMsg, true) == null) {
            // relay or broadcast?
            // with relay node 1 delivers, which means node 2 isn't getting its own ack
            var msgFromMe = bebDeliveredMsg.copyWithSenderId(myProcId);
            exec.submit(() -> beb.broadcast(msgFromMe, peers));
        }
    }

    /*
     * Blocks!
     *
     * Takes up 2 threads: one for itself and the other for BEB
     * */
    public void blockingListen() {
        exec.submit(beb::blockingListen); // Important: blocks a whole thread in the thread pool
        while (true) {
            try {
                onBebDeliver(bebDeliveredQ.take());
            } catch (InterruptedException e) {
                error("URB: blocking listen: ", e);
            }
        }
    }

    public void broadcast(MessagePacket msg) {
        pending.put(msg, true);
        beb.broadcast(msg, peers);
    }
}
