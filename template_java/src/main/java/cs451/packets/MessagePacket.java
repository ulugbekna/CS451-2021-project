package cs451.packets;

public class MessagePacket extends Packet {
    public final String message;

    public MessagePacket(int id, String message) {
        super(id);
        this.message = message;
    }
}
