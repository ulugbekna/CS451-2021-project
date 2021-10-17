package cs451.packets;

import java.io.Serializable;

public class AckPacket extends Packet implements Serializable {
    public AckPacket(int senderId, int id) {super(senderId, id);}

    @Override
    public String toString() {
        return "AckPacket{senderId=" + senderId + "; id=" + id + "}";
    }
}
