package cs451;

import cs451.packets.MessagePacket;

import java.net.DatagramSocket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class UniformReliableBroadcastUdp {
    private final int myProcId;
    private final int nNodes;
    private final HashMap<Integer, Node> peers;

    private final ScheduledExecutorService exec;

    private final Consumer<MessagePacket> onDeliverCallback;
    private final ConcurrentHashMap<MessagePacket, Boolean> delivered;
    //    private final TwoLayerTbl<
//            /* authorId */ Integer, /* messageId */ Integer, /* used as set, so doesn't matter */ Boolean> pending;
    private final ConcurrentHashMap<MessagePacket, Boolean> pending;
    private final ConcurrentHashMap<MessagePacket, HashSet<Integer /* id */>> ack;

    private final BestEffortBroadcastUdp beb;

    public UniformReliableBroadcastUdp(int myProcId, HashMap<Integer, Node> peers,
                                       DatagramSocket socket, ScheduledExecutorService exec,
                                       Consumer<MessagePacket> onDeliverCallback) {
        this.myProcId = myProcId;
        this.nNodes = peers.size() + 1; // + 1 because "peers" doesn't contain myself
        this.peers = peers;
        this.onDeliverCallback = onDeliverCallback;
        this.exec = exec;
        delivered = new ConcurrentHashMap<>();
//        pending = new TwoLayerTbl<>(256, 256);
        pending = new ConcurrentHashMap<>(1024);
        ack = new ConcurrentHashMap<>();
        beb = new BestEffortBroadcastUdp(myProcId, socket, exec, this::onBebDeliver);

        exec.scheduleAtFixedRate(this::tryDelivering, 20, 20, TimeUnit.MILLISECONDS);
    }

    /**
     * Used for periodically checking whether any of the bebDelivered messages got necessary number of relays
     */
    void tryDelivering() {
        pending.forEach((MessagePacket msg, Boolean v) -> {
            if (canDeliver(msg) && delivered.put(msg, true) == null) {
                onDeliverCallback.accept(msg);
            }
        });
    }

    boolean canDeliver(MessagePacket m) {
        var set = ack.get(m);
        return set != null && set.size() > (nNodes / 2);
    }

    void onBebDeliver(MessagePacket bebDeliveredMsg) {
        // TODO: useless allocation? ugly piece of code
        var originalMsg =
                new MessagePacket.Builder(bebDeliveredMsg).withSenderId(bebDeliveredMsg.authorId).build();

        ack.compute(originalMsg, (MessagePacket _m, HashSet<Integer> currentSetOrNull) -> {
            var set = currentSetOrNull != null ? currentSetOrNull : new HashSet<Integer>();
            set.add(bebDeliveredMsg.senderId);
            return set;
        });

        if (pending.put(originalMsg, true) == null) {
            // relay or broadcast?
            // with relay node 1 delivers, which means node 2 isn't getting its own ack
            var msgFromMe = new MessagePacket.Builder(bebDeliveredMsg).withSenderId(myProcId).build();
            exec.submit(() -> beb.broadcast(msgFromMe, peers));
        }
    }

    /*
     * Blocks!
     * */
    void blockingListen() {
        beb.blockingListen();
    }

    void broadcast(MessagePacket msg, HashMap<Integer, Node> peers) {
        pending.put(msg, true);
        beb.broadcast(msg, peers);
    }
}
