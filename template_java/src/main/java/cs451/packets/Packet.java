package cs451.packets;

import java.util.Objects;

public abstract class Packet {
    public final int senderId;
    public final int messageId;

    protected Packet(int senderId, int id) {
        this.senderId = senderId;
        this.messageId = id;
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

