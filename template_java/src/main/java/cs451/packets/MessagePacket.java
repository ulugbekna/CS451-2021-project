package cs451.packets;

import java.io.Serializable;

public class MessagePacket extends Packet implements Serializable {
    public final String message;

    public MessagePacket(int senderId, int id, String message) {
        super(senderId, id);
        this.message = message;
    }

    @Override
    public String toString() {
        return "MessagePacket{" +
                "message='" + message + '\'' +
                ", senderId=" + senderId +
                ", id=" + id +
                '}';
    }
}
