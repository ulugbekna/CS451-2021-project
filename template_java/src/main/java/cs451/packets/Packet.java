package cs451.packets;

import java.io.Serializable;

public abstract class Packet implements Serializable {
    public final int senderId;
    public final int id;

    protected Packet(int senderId, int id) {
        this.senderId = senderId;
        this.id = id;
    }

    @Override
    public String toString() {
        return "Packet{" +
                "senderId=" + senderId +
                ", id=" + id +
                '}';
    }
}

