package cs451.packets;

import java.io.Serializable;
import java.util.Objects;

public abstract class Packet implements Serializable {
    public final int senderId;
    public final int id;

    protected Packet(int senderId, int id) {
        this.senderId = senderId;
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Packet packet = (Packet) o;
        return senderId == packet.senderId && id == packet.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(senderId, id);
    }

    @Override
    public String toString() {
        return "Packet{" +
                "senderId=" + senderId +
                ", id=" + id +
                '}';
    }
}

