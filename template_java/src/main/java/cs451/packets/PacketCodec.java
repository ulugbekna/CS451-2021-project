package cs451.packets;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class PacketCodec {
    private static final byte ACK_BYTE = 0;
    private static final byte MESSAGE_BYTE = 1;

    // each packet's 0th byte is a "packet type" byte
    private static final int PACKET_TYPE_IDX = 0;

    public static int encodeAckPacket(AckPacket p, byte[] buf) {
        // assumptions: `buf` is at least as large as (1 + String.valueOf(MAX_INT).length) bytes
        buf[PACKET_TYPE_IDX] = ACK_BYTE;
        var id = String.valueOf(p.id).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(id, 0, buf, PACKET_TYPE_IDX + 1, id.length);
        return 1 + id.length;
    }

    public static int encodeMessagePacket(MessagePacket p, byte[] buf) {
        // [PACKET_TYPE][id][\n][message]
        buf[PACKET_TYPE_IDX] = MESSAGE_BYTE;
        var id = String.valueOf(p.id).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(id, 0, buf, PACKET_TYPE_IDX + 1, id.length);
        buf[PACKET_TYPE_IDX + id.length] = '\n';
        var msg = p.message.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(msg, 0, buf, PACKET_TYPE_IDX + id.length + 1, msg.length);
        return 1 + id.length + msg.length;
    }

    public static Packet decode(byte[] b, int bufLen) {
        switch (b[0]) {
            case ACK_BYTE: {
                var id = Integer.parseInt(new String(Arrays.copyOfRange(b, 1, bufLen)));
                return new AckPacket(id);
            }
            case MESSAGE_BYTE:
                for (int i = 1; i < bufLen; ++i) {
                    if (b[i] == '\n') {
                        var id = Integer.parseInt(new String(Arrays.copyOfRange(b, 1, i)));
                        var message = new String(Arrays.copyOfRange(b, i, bufLen));
                        return new MessagePacket(id, message);
                    }
                }
                // break statement is missing knowingly
            default:
                throw new IllegalArgumentException();
        }
    }
}
