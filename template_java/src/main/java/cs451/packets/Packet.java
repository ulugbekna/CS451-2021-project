package cs451.packets;

import java.io.Serializable;

public abstract class Packet implements Serializable {
    public final int id;

    protected Packet(int id) {this.id = id;}

    @Override
    public String toString() {
        return "Packet{" +
                "id=" + id +
                '}';
    }
}

