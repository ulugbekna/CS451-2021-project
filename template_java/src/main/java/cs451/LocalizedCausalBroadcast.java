package cs451;

import cs451.packets.MessagePacket;
import cs451.packets.PacketCodec;

import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

import static cs451.Log.error;

public class LocalizedCausalBroadcast {
    /* Passed from client */
    private final int myProcId;
    private final Consumer<MessagePacket> onDeliver;
    private final ProcArray<int[]> causalProcs;

    /* Internal State */
    private final int nProcs;
    private final BlockingQueue<MessagePacket> urbDeliveredQ;
    private final UniformReliableBroadcastUdp urb;
    private final VectorClock vc;
    private final ScheduledExecutorService exec;

    private final HashMap<LCBMessagePacket, Boolean> pending; // NOTE: NOT concurrency-safe

    public LocalizedCausalBroadcast(int myProcId, HashMap<Integer, Node> peers, ProcArray<int[]> causalProcs,
                                    DatagramSocket socket, ScheduledExecutorService exec,
                                    Consumer<MessagePacket> onDeliver) {

        this.myProcId = myProcId;
        this.exec = exec;
        this.onDeliver = onDeliver;
        this.causalProcs = causalProcs;

        /* initialize internal state */
        nProcs = peers.size() + 1;

        urbDeliveredQ = new LinkedBlockingQueue<>();
        urb = new UniformReliableBroadcastUdp(myProcId, peers, socket, exec,
                (m) -> putInQ(urbDeliveredQ, m));

        vc = new VectorClock(peers.size() + 1); // is not concurrency-safe; must be using in `synchronized` block

        pending = new HashMap<>();
    }

    /**
     * `putInQ(q, m)` just puts `m` in queue `q` and swallows the exception if there's one
     */
    private void putInQ(BlockingQueue<MessagePacket> q, MessagePacket m) {
        try {
            q.put(m);
        } catch (Exception e) {
            error("putInQ", e);
        }
    }

    /*
     * Important: can NOT be run concurrently; access to `pending` in this function assumes mutexed access to pending
     * */
    private void onUrbDeliver(MessagePacket m) {
        pending.put(new LCBMessagePacket(m), true);
        final var delivered = new ArrayList<LCBMessagePacket>();
        synchronized (vc) {
            pending.forEach((lcb, _b) -> { // TODO: can parallelize? beware synchronized block
                var origM = lcb.origM;
                if (vc.getById(origM.authorId) == origM.messageId - 1) { // enforce FIFO
                    final var cProcs = causalProcs.getById(origM.authorId);
                    final var vcm = lcb.vcm;
                    var canDeliver = true;
                    for (var cProcId : cProcs) {
                        if (vc.getById(cProcId) < vcm.getById(cProcId))
                            canDeliver = false;
                    }
                    if (canDeliver) {
                        onDeliver.accept(origM);
                        delivered.add(lcb);
                        vc.increment(origM.authorId);
                    }
                }
            });
        }
        for (var d : delivered) {
            pending.remove(d);
        }
    }

    public void blockingListen() {
        exec.submit(urb::blockingListen);
        while (true) {
            try {
                onUrbDeliver(urbDeliveredQ.take());
            } catch (InterruptedException e) {
                error("URB: blocking listen: ", e);
            }
        }
    }

    public void broadcast(int msgId, String message) {
        // Note: delivering to myself is done in BEB

        var mWithVC = msgPackWithVC(msgId, message);
        urb.broadcast(mWithVC);
        synchronized (vc) {vc.increment(myProcId);}
    }

    private MessagePacket msgPackWithVC(int msgId, String message) {
        var userPayload = message.getBytes(StandardCharsets.US_ASCII);
        var payload = new byte[nProcs * 4 /* int size */ + userPayload.length];

        int i;

        // serialize VC
        synchronized (vc) {
            for (i = 0; i < nProcs; ++i) {
                PacketCodec.putIntToBytes(payload, i * 4, vc.getById(i + 1));
            }
        }

        // copy oldPayload to newPayload
        System.arraycopy(userPayload, 0, payload, i * 4, userPayload.length);

        return new MessagePacket(myProcId, msgId, myProcId, payload);
    }

    private class LCBMessagePacket {
        public final MessagePacket origM;
        public final VectorClock vcm;

        public LCBMessagePacket(MessagePacket mWithVCEmbedded) {

            VectorClock vcm = new VectorClock(nProcs);

            int i;

            //deserialize VC
            for (i = 0; i < nProcs; ++i) {
                int v = PacketCodec.getIntFromBytes(mWithVCEmbedded.payload, i * 4);
                vcm.setById(i + 1, v);
            }

            // restore original payload
            var originalPayloadLen = mWithVCEmbedded.payload.length - i * 4;
            var originalPayload = new byte[originalPayloadLen];
            System.arraycopy(mWithVCEmbedded.payload, i * 4, originalPayload, 0, originalPayloadLen);
            origM = mWithVCEmbedded.copyWithDifferentPayload(originalPayload);

            this.vcm = vcm;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            LCBMessagePacket that = (LCBMessagePacket) o;

            return origM.equals(that.origM);
        }

        @Override
        public int hashCode() {
            return origM.hashCode();
        }
    }
}
