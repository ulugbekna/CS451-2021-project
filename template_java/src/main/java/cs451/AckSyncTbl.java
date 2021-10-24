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
            new ConcurrentHashMap<>(128);

    public ScheduledFuture<?> set(int port, int msgId, ScheduledFuture<?> v) {
        tbl.computeIfAbsent(port,
                (_port) -> /* could be cool to have a weak pointer */ new ConcurrentHashMap<>(256));

        var tblById = tbl.get(port);
        assert tblById != null;

        return tblById.put(msgId, v);
    }

    public ScheduledFuture<?> retrieve(int port, int msgId) {
        var tblById = tbl.get(port);
        return tblById != null ? tblById.remove(msgId) : null;
    }
}