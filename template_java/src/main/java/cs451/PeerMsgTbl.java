package cs451;

import java.util.concurrent.ConcurrentHashMap;

class PeerMsgTbl<T> {
    /*
    Invariants to uphold:
        - since in `get` we get a value for a certain key in a conditional and then proceed to operate on it,
        we need to make sure that the value isn't deleted - easiest solution to this is to _not_ remove kv pairs at
        all
     */
    private final ConcurrentHashMap<
            Integer /* port */,
            ConcurrentHashMap<Integer /* msgID */, T>> tbl =
            new ConcurrentHashMap<>();

    public void set(int port, int msgId, T v) {
        tbl.computeIfAbsent(port, (_port) -> /* could be cool to have a weak pointer */ new ConcurrentHashMap<>());

        var tblById = tbl.get(port);
        assert tblById != null;

        tblById.put(msgId, v);
    }

    /*
     * Get the future by `port` and `msgId`. Returns `null` if not present.
     * */
    public T get(int port, int msgId) {
        var tblById = tbl.get(port);
        return tblById != null ? tblById.get(msgId) : null;
    }
}