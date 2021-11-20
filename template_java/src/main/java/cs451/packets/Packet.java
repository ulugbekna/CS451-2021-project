package cs451.packets;

import java.util.Objects;

public abstract class Packet {
    /**
     * original message author's process ID: 1 - n, where `n` is the total number of procs
     */
    public final int senderId;

    /**
     * message sequence ID
     */
    public final int messageId;

    protected Packet(int senderId, int msgId) {
        this.senderId = senderId;
        this.messageId = msgId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Packet packet = (Packet) o;
        return senderId == packet.senderId && messageId == packet.messageId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(senderId, messageId);
    }

    @Override
    public String toString() {
        return "Packet{" +
                "senderId=" + senderId +
                ", messageId=" + messageId +
                '}';
    }
}

