package cs451.packets;

import java.io.Serializable;

public class AckPacket extends Packet implements Serializable {
    public AckPacket(int id) {super(id);}

    @Override
    public String toString() {
        return "AckPacket{id=" + id + "}";
    }
}
