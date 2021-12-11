package cs451;

import java.util.concurrent.atomic.AtomicLong;

import static cs451.Log.info;

/* Statistical data for debugging */
public class PerfectLinkStats {
    private final AtomicLong nAcksSent = new AtomicLong(0);
    private final AtomicLong nMsgPacksSent = new AtomicLong(0);
    private final AtomicLong nMsgPacksRecvd = new AtomicLong(0);
    private final AtomicLong nAckPacksRecvd = new AtomicLong(0);

    void msgPackSent() {nMsgPacksSent.incrementAndGet();}

    void msgPackRecvd() {nMsgPacksRecvd.incrementAndGet();}

    void ackSent() {nAcksSent.incrementAndGet();}

    void ackRecvd() {nAckPacksRecvd.incrementAndGet();}

    void logInfo() {
        info("sent packs\n" +
                "  msg: " + nMsgPacksSent + "\n" +
                "  ack: " + nAcksSent + "\n" +
                "received packs\n" +
                "  msg: " + nMsgPacksRecvd + "\n" +
                "  ack: " + nAckPacksRecvd + "\n"
        );
    }
}
