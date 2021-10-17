package cs451;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

class AckSyncTbl {
    /*
    Invariants to uphold:
        - since in `get` we get a value for a certain key in a conditional and then proceed to operate on it,
        we need to make sure that the value isn't deleted - easiest solution to this is to _not_ remove kv pairs at
        all
     */
    private final ConcurrentHashMap<
            Integer /* port */,
            ConcurrentHashMap<Integer /* msgID */, ScheduledFuture<?>>> tbl =
            new ConcurrentHashMap<>();

    public void set(int port, int msgId, ScheduledFuture<?> fut) {
        tbl.computeIfAbsent(port, (_port) -> new ConcurrentHashMap<>());

        var tblById = tbl.get(port);
        assert tblById != null;

        tblById.put(msgId, fut);
    }

    /*
     * Get the future by `port` and `msgId`. Returns `null` if not present.
     * */
    public ScheduledFuture<?> get(int port, int msgId) {
        var tblById = tbl.get(port);
        if (tblById != null) {return tblById.get(msgId);}
        return null;
    }
}