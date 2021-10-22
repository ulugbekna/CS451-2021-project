package cs451;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class MyNode {
    public final Node me;
    public final HashMap<Integer, Node> peers;
    public AtomicInteger msgUid = new AtomicInteger(1);

    public MyNode(Node me, HashMap<Integer, Node> peers) {
        this.me = me;
        this.peers = peers;
    }

    @Override
    public String toString() {
        return "MyNode{" + "me=" + me + ", peers=" + peers + ", msgUid=" + msgUid + '}';
    }
}
