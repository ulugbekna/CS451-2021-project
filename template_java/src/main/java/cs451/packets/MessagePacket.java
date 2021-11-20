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

    public static class Builder {
        private int senderId = -1;
        private int messageId = -1;
        private int authorId = -1;
        private String message = "";

        public Builder() {}

        public Builder(MessagePacket m) {
            senderId = m.senderId;
            messageId = m.messageId;
            message = m.message;
            authorId = m.authorId;
        }

        public Builder withSenderId(int senderId) {
            this.senderId = senderId;
            return this;
        }

        public Builder withMessageId(int messageId) {
            this.messageId = messageId;
            return this;
        }

        public Builder withAuthorId(int authorId) {
            this.authorId = authorId;
            return this;
        }

        public Builder withMessage(String message) {
            this.message = message;
            return this;
        }

        public MessagePacket build() {
            assert (senderId != -1 && messageId != -1 && authorId != -1 && !message.equals(""));
            return new MessagePacket(senderId, messageId, authorId, message);
        }
    }
}
