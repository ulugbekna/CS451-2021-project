package cs451.packets;

import java.util.Objects;

public class MessagePacket extends Packet {
    /**
     * message content
     */
    public final int authorId;
    public final byte[] payload;

    public MessagePacket(int senderId, int msgId, int authorId, byte[] payload) {
        super(senderId, msgId);
        this.payload = payload;
        this.authorId = authorId;
    }

    public MessagePacket copyWithSenderId(int newSenderId) {
        return new MessagePacket(newSenderId, messageId, authorId, payload);
    }

    public MessagePacket copyWithDifferentPayload(byte[] newPayload) {
        return new MessagePacket(senderId, messageId, authorId, newPayload);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        MessagePacket that = (MessagePacket) o;
        return authorId == that.authorId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), authorId);
    }

    @Override
    public String toString() {
        return "MessagePacket{" +
                "authorId=" + authorId +
                ", senderId=" + senderId +
                ", messageId=" + messageId +
                '}';
    }
}
