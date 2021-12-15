package cs451;

import cs451.packets.MessagePacket;
import cs451.packets.PacketCodec;

import java.net.DatagramSocket;
import java.util.HashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

public class LocalizedCausalBroadcast {
    /* Passed from client */
    private final int myProcId;

    /* Internal State */
    private final int nProcs;

    private final VectorClock vc;

    public LocalizedCausalBroadcast(int myProcId, HashMap<Integer, Node> peers,
                                    DatagramSocket socket, ScheduledExecutorService exec,
                                    Consumer<MessagePacket> onDeliverCallback) {

        this.myProcId = myProcId;


        /* initialize internal state */
        nProcs = peers.size() + 1;

        vc = new VectorClock(peers.size() + 1);

    }

    public void blockingList() {

    }

    public void broadcast(MessagePacket m) {
        // FIXME: deliver to myself

        var mWithVC = messagePacketWithVCEmbedded(m);
        vc.increment(myProcId);
    }

    private MessagePacket messagePacketWithVCEmbedded(MessagePacket msg) {
        var oldPayload = msg.payload;
        var newPayload = new byte[nProcs * 4 /* int size */ + oldPayload.length];

        int i;

        // serialize VC
        for (i = 0; i < nProcs; ++i) {
            PacketCodec.putIntToBytes(newPayload, i * 4, vc.getById(i + 1));
        }

        // copy oldPayload to newPayload
        System.arraycopy(oldPayload, 0, newPayload, i * 4, oldPayload.length);

        return msg.copyWithDifferentPayload(newPayload);
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
    }
}
