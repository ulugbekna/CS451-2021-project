package cs451.packets;

import java.io.Serializable;

public class MessagePacket extends Packet implements Serializable {
    public final String message;

    public MessagePacket(int id, String message) {
        super(id);
        this.message = message;
    }

    @Override
    public String toString() {
        return "MessagePacket{" +
                "message='" + message + '\'' +
                ", id=" + id +
                '}';
    }
}
