package cs451;

import cs451.packets.MessagePacket;

import java.net.DatagramSocket;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class FifoBroadcast {
    /*

    for each node `n` \in `P`
        expectedMsgIds[n] = 1

    onUrbDeliver(MessagePacket urbDeliveredMsg) {
        // there is a concurrency issue here? we get a value but this value may be increased by the time we get
        var atomicExpectedMsgId = expectedMsgIds.get(urbDeliveredMsg.authorId) // can't be null if we really initialize the table to 1's
        var msgId = urbDeliveredMsg.messageId

        if msgId == atomicExpectedMsgId.get():
            deliver(urbDeliveredMsg) // ie call onDeliverCallback
            atomicExpectedMsgId.increment() // NOTE: we shouldn't increment before we deliver; ow, other thread can deliver before we do
        else:
            pending.put(urbDeliveredMsg.messageId, urbDeliveredMsg)
            pending.computeIfPresent(urbDeliveredMsg.authorId, (authorId, msgsSet) ->
                msgsSet.put(urbDeliveredMsg.messageId, urbDeliveredMsg)) // I'm assuming we initialize all sets beforehand
    }

    tryDeliverPeriodically() {
        for every authorId:
            if pending contains a message that can be delivered
                deliver that message
                increment expectedMsgId

    }
    */

    final Consumer<MessagePacket> onDeliverCallback;

    final UniformReliableBroadcastUdp urb;
    final ScheduledExecutorService exec;

    final HashMap<Integer, Node> peers;

    final ConcurrentHashMap</* authorID */ Integer, AtomicInteger> expectedMsgIds;
    final ConcurrentHashMap</* authorID */ Integer, HashMap</* Msg ID */ Integer, MessagePacket>> pending;

    public FifoBroadcast(int myProcId, HashMap<Integer, Node> peers,
                         DatagramSocket socket, ScheduledExecutorService exec,
                         Consumer<MessagePacket> onDeliverCallback) {
        this.onDeliverCallback = onDeliverCallback;
        this.exec = exec;
        this.peers = peers;

        /*
         * Internal State Initialization
         * */
        urb = new UniformReliableBroadcastUdp(myProcId, peers, socket, exec, this::onUrbDeliver);

        // initialize expectedMsgIds
        expectedMsgIds = new ConcurrentHashMap<>(16);
        expectedMsgIds.put(myProcId, new AtomicInteger(1));
        for (var peer : peers.keySet())
            expectedMsgIds.put(peer, new AtomicInteger(1));

        // initialize pending
        pending = new ConcurrentHashMap<>(16);
        pending.put(myProcId, new HashMap<>());
        for (var peer : peers.keySet())
            pending.put(peer, new HashMap<>());

        /*
         * Functionality
         * */

        // run periodically
        exec.scheduleAtFixedRate(this::checkPendingToDeliver, 20, 20, TimeUnit.MILLISECONDS);
    }

    void onUrbDeliver(MessagePacket msg) {
        var atomicExpectedMsgId = expectedMsgIds.get(msg.authorId);
        var msgId = msg.messageId;

        /*
         * Concurrency of this block explained:
         *   We can get a value from the atomic, but it can get changed (only incremented!) concurrently,
         *   so we'd be dealing with an out-of-date value in the later code -- BUT that's ok
         *
         *   1. Note that all `msg`s are unique, so if expectedMsgId == msgId, then no one else can increment the atomic
         *       because their any other msg ID that can run this code must be `> msgId`
         *       (because all `< msgId` must've been acknowledged and must have incremented the atomic
         *   2. If the message has `msgId > expectedMsgId` but after we get `expectedMsgId` value, it is incremented and
         *       now `msgId == expectedMsgId`, that's okay, we'll simply add the current message to `pending` and
         *       at some point later it will be delivered by our periodically running function
         * */

        if (msgId == atomicExpectedMsgId.get()) {
            onDeliverCallback.accept(msg); // ie call onDeliverCallback
            // Note! we shouldn't increment the atomic before we deliver;
            // ow, some other thread can deliver before we do
            atomicExpectedMsgId.incrementAndGet();
        } else {
            pending.computeIfPresent(msg.authorId, (authorId, msgs) -> {
                msgs.put(msg.messageId, msg);
                return msgs;
            });
        }
    }

    void checkPendingToDeliver() {
        pending.forEach((authorId, msgsPending) -> {
            var atomicExpectedMsgId = expectedMsgIds.get(authorId);
            var expectedMsgId = atomicExpectedMsgId.get();
            var msgOrNull = msgsPending.get(expectedMsgId);
            if (msgOrNull != null) {
                atomicExpectedMsgId.incrementAndGet();
                exec.submit(() -> onDeliverCallback.accept(msgOrNull));
            }
        });
    }

    void broadcast(MessagePacket msg) {
        urb.broadcast(msg, peers);
    }

    void blockingListen() {
        /*
         * Start listening to packets in a blocking manner
         * */
        urb.blockingListen();
    }
}
