package cs451.packets;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class PacketCodec {

    /*
     *  [ack/msg pack - 1 byte][sender ID int serialized - 4 bytes][message ID int serialized - 4 bytes]{optional message if it's a msg pck}
     * */
    private static final byte ACK_PACKET_HEADER_BYTE = 0;
    private static final byte MESSAGE_PACKET_HEADER_BYTE = 1;
    private static final int PACKET_HEADER_BYTE_IDX = 0;
    private static final int PACKET_HEADER_SENDER_ID_IDX = 1;
    private static final int PACKET_HEADER_MESSAGE_ID_IDX = 5;
    private static final int PACKET_MESSAGE_IDX = 9;

    /* deserializes an integer from buf[offset:offset+4] right side of range exclusive */
    private static int getIntFromBytes(byte[] buf, int offset) {
        return ((buf[offset] & 0xFF) << 24) |
                ((buf[offset + 1] & 0xFF) << 16) |
                ((buf[offset + 2] & 0xFF) << 8) |
                ((buf[offset + 3] & 0xFF));
    }

    private static void putIntToBytes(byte[] buf, int offset, int v) {
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
        var msgId = getIntFromBytes(buf, PACKET_HEADER_MESSAGE_ID_IDX);
        var senderId = getIntFromBytes(buf, PACKET_HEADER_SENDER_ID_IDX);
        switch (buf[PACKET_HEADER_BYTE_IDX]) {
            case ACK_PACKET_HEADER_BYTE:
                return new AckPacket(senderId, msgId);
            case MESSAGE_PACKET_HEADER_BYTE:
                return new MessagePacket(senderId, msgId, new String(
                        Arrays.copyOfRange(buf, PACKET_MESSAGE_IDX, len)));
            default:
                throw new RuntimeException("Incorrect serialized format received");
        }
    }

    public static int serializeAckPacket(byte[] buf, int senderId, int messageId) {
        buf[PACKET_HEADER_BYTE_IDX] = ACK_PACKET_HEADER_BYTE;
        putIntToBytes(buf, PACKET_HEADER_SENDER_ID_IDX, senderId);
        putIntToBytes(buf, PACKET_HEADER_MESSAGE_ID_IDX, messageId);
        return 9;
    }

    public static int serializeMessagePacket(byte[] buf, int senderId, int messageId, String message) {
        buf[PACKET_HEADER_BYTE_IDX] = MESSAGE_PACKET_HEADER_BYTE;
        putIntToBytes(buf, PACKET_HEADER_SENDER_ID_IDX, senderId);
        putIntToBytes(buf, PACKET_HEADER_MESSAGE_ID_IDX, messageId);
        var messageBytes = message.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(messageBytes, 0, buf, PACKET_MESSAGE_IDX, messageBytes.length);
        return 9 + messageBytes.length;
    }
}
