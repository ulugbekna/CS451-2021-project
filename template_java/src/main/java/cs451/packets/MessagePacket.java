package cs451.packets;

import java.util.Objects;

public class MessagePacket extends Packet {
    /**
     * message content
     */
    public final int authorId;
    public final String message;

    public MessagePacket(int senderId, int msgId, int authorId, String msg) {
        super(senderId, msgId);
        this.message = msg;
        this.authorId = authorId;
    }

    public MessagePacket copyWithSenderId(int newSenderId) {
        return new MessagePacket(newSenderId, messageId, authorId, message);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        MessagePacket that = (MessagePacket) o;
        return authorId == that.authorId && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), authorId, message);
    }

    @Override
    public String toString() {
        return "MessagePacket{" +
                "authorId=" + authorId +
                ", senderId=" + senderId +
                ", messageId=" + messageId +
                ", message='" + message + '\'' +
                '}';
    }
}
