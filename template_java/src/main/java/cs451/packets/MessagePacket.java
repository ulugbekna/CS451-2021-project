package cs451.packets;

import java.util.Objects;

public class MessagePacket extends Packet {
    public final String message;

    public MessagePacket(int senderId, int id, String message) {
        super(senderId, id);
        this.message = message;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        MessagePacket that = (MessagePacket) o;
        return Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), message);
    }

    @Override
    public String toString() {
        return "MessagePacket{" +
                "messageId=" + messageId +
                ", message=" + message +
                ", senderId=" + senderId +
                '}';
    }
}
