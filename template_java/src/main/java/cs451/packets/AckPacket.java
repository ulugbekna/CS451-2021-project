package cs451.packets;

public class AckPacket extends Packet {
    public AckPacket(int senderId, int id) {super(senderId, id);}

    @Override
    public String toString() {
        return "AckPacket{senderId=" + senderId + "; messageId=" + messageId + "}";
    }
}
