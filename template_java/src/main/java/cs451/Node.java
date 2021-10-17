package cs451;

import java.net.InetAddress;

public class Node {
    public final int id;
    public final InetAddress addr;
    public final int port;

    public Node(int id, InetAddress addr, int port) {
        this.id = id;
        this.addr = addr;
        this.port = port;
    }

    @Override
    public String toString() {
        return "Node{" + "id=" + id + ", addr=" + addr + ", port=" + port + '}';
    }
}
