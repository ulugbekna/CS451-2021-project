package cs451.packets;

import cs451.Log;

import java.util.Arrays;

public class PacketCodec {

    /*
     *  [ack/msg pack - 1 byte][sender ID int serialized - 4 bytes][message ID int serialized - 4 bytes]{optional message if it's a msg pck}
     * */
    private static final byte PACKET_KIND_ACK = 0;
    private static final byte PACKET_KIND_MSG = 1;

    private static final int PACKET_HEADER_KIND_IDX = 0;
    private static final int PACKET_HEADER_SENDER_ID_IDX = 1;
    private static final int PACKET_HEADER_PAYLOAD_ID_IDX = PACKET_HEADER_SENDER_ID_IDX + Integer.BYTES;

    // MessagePacket-only

    private static final int PACKET_HEADER_AUTHOR_ID_IDX = PACKET_HEADER_PAYLOAD_ID_IDX + Integer.BYTES;
    private static final int PACKET_PAYLOAD_IDX = PACKET_HEADER_AUTHOR_ID_IDX + Integer.BYTES;

    /* deserializes an integer from buf[offset:offset+4] right side of range exclusive */
    public static int getIntFromBytes(byte[] buf, int offset) {
        return ((buf[offset] & 0xFF) << 24) |
                ((buf[offset + 1] & 0xFF) << 16) |
                ((buf[offset + 2] & 0xFF) << 8) |
                ((buf[offset + 3] & 0xFF));
    }

    public static void putIntToBytes(byte[] buf, int offset, int v) {
        buf[offset] = (byte) (v >> 24);
        buf[offset + 1] = (byte) (v >> 16);
        buf[offset + 2] = (byte) (v >> 8);
        buf[offset + 3] = (byte) v;
    }

    /*
     * uses only `len` bytes of `buf` starting at index 0
     * */
    public static Packet deserialize(byte[] buf, int len) {
        assert len > 8;
        var msgId = getIntFromBytes(buf, PACKET_HEADER_PAYLOAD_ID_IDX);
        var senderId = getIntFromBytes(buf, PACKET_HEADER_SENDER_ID_IDX);
        switch (buf[PACKET_HEADER_KIND_IDX]) {
            case PACKET_KIND_ACK:
                return new AckPacket(senderId, msgId);
            case PACKET_KIND_MSG:
                var authorId = getIntFromBytes(buf, PACKET_HEADER_AUTHOR_ID_IDX);
                return new MessagePacket(senderId, msgId, authorId,
                        Arrays.copyOfRange(buf, PACKET_PAYLOAD_IDX, len));
            default:
                Log.error("error in deserialization");
                throw new RuntimeException("Incorrect serialized format received");
        }
    }

    public static int serializeAckPacket(byte[] buf, int senderId, int messageId) {
        buf[PACKET_HEADER_KIND_IDX] = PACKET_KIND_ACK;
        putIntToBytes(buf, PACKET_HEADER_SENDER_ID_IDX, senderId);
        putIntToBytes(buf, PACKET_HEADER_PAYLOAD_ID_IDX, messageId);
        return 9;
    }

    public static int serializeMessagePacket(byte[] buf, int senderId, int messageId, int authorId, byte[] payload) {
        buf[PACKET_HEADER_KIND_IDX] = PACKET_KIND_MSG;
        putIntToBytes(buf, PACKET_HEADER_SENDER_ID_IDX, senderId);
        putIntToBytes(buf, PACKET_HEADER_PAYLOAD_ID_IDX, messageId);
        putIntToBytes(buf, PACKET_HEADER_AUTHOR_ID_IDX, authorId);
        System.arraycopy(payload, 0, buf, PACKET_PAYLOAD_IDX, payload.length);
        return PACKET_HEADER_AUTHOR_ID_IDX + Integer.BYTES + payload.length;
    }

    public static int serializeMessagePacket(byte[] buf, MessagePacket m) {
        return serializeMessagePacket(buf, m.senderId, m.messageId, m.authorId, m.payload);
    }
}
