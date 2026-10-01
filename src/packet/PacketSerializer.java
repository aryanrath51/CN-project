package packet;

import checksum.ChecksumUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Handles binary serialization and deserialization of {@link Packet} objects.
 *
 * Serialization format: fixed 20-byte header (big-endian) followed by variable payload.
 * The checksum is computed over the entire byte array with the checksum field zeroed,
 * then embedded at bytes 12-13 before transmission.
 *
 * Thread-safe: all methods are stateless and static.
 */
public class PacketSerializer {

    /**
     * Serialize a {@link Packet} into a byte array ready for UDP transmission.
     *
     * Steps:
     * 1. Allocate buffer of HEADER_SIZE + payloadLength
     * 2. Write all header fields in network byte order (BIG_ENDIAN)
     * 3. Write payload bytes
     * 4. Compute RFC 1071 checksum over entire buffer (checksum field zeroed)
     * 5. Embed computed checksum at bytes 12-13
     *
     * @param packet the packet to serialize
     * @return the serialized byte array
     */
    public static byte[] serialize(Packet packet) {
        int totalSize = Packet.HEADER_SIZE + packet.getPayloadLength();
        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        buffer.order(ByteOrder.BIG_ENDIAN); // Network byte order

        // ── Write header (20 bytes) ──────────────────────────────
        buffer.put(packet.getType().getCode());       // byte  0     : type
        buffer.put(packet.getFlags());                 // byte  1     : flags
        buffer.putInt(packet.getSequenceNumber());     // bytes 2-5   : seq number
        buffer.putInt(packet.getAckNumber());          // bytes 6-9   : ack number
        buffer.putShort(packet.getPayloadLength());    // bytes 10-11 : payload length
        buffer.putShort((short) 0);                    // bytes 12-13 : checksum (zeroed for computation)
        buffer.putLong(packet.getTimestamp());          // bytes 14-21 : timestamp

        // ── Write payload ────────────────────────────────────────
        if (packet.getPayload() != null && packet.getPayloadLength() > 0) {
            buffer.put(packet.getPayload(), 0, packet.getPayloadLength());
        }

        byte[] data = buffer.array();

        // ── Compute and embed checksum ───────────────────────────
        short checksum = ChecksumUtil.compute(data);
        data[ChecksumUtil.CHECKSUM_OFFSET]     = (byte) ((checksum >> 8) & 0xFF);
        data[ChecksumUtil.CHECKSUM_OFFSET + 1] = (byte) (checksum & 0xFF);
        packet.setChecksum(checksum);

        return data;
    }

    /**
     * Deserialize a raw byte array received from UDP into a {@link Packet}.
     *
     * Steps:
     * 1. Validate minimum size (HEADER_SIZE)
     * 2. Verify RFC 1071 checksum (reject corrupt packets)
     * 3. Parse header fields from ByteBuffer
     * 4. Extract payload bytes
     *
     * @param data the raw bytes received from the network
     * @return the reconstructed Packet
     * @throws CorruptPacketException if the packet is too small or fails checksum
     */
    public static Packet deserialize(byte[] data) throws CorruptPacketException {
        // ── Validate minimum size ────────────────────────────────
        if (data == null || data.length < Packet.HEADER_SIZE) {
            throw new CorruptPacketException(
                    "Packet too small: " + (data == null ? "null" : data.length) +
                    " bytes (minimum " + Packet.HEADER_SIZE + ")");
        }

        // ── Verify checksum ──────────────────────────────────────
        if (!ChecksumUtil.verify(data)) {
            throw new CorruptPacketException("Checksum verification failed");
        }

        // ── Parse header ─────────────────────────────────────────
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.BIG_ENDIAN);

        byte typeCode = buffer.get();                     // byte  0
        byte flags = buffer.get();                         // byte  1
        int seqNum = buffer.getInt();                      // bytes 2-5
        int ackNum = buffer.getInt();                      // bytes 6-9
        short payloadLen = buffer.getShort();               // bytes 10-11
        short checksum = buffer.getShort();                 // bytes 12-13
        long timestamp = buffer.getLong();                  // bytes 14-21

        // ── Validate payload length ──────────────────────────────
        if (payloadLen < 0 || payloadLen > Packet.MAX_PAYLOAD_SIZE) {
            throw new CorruptPacketException(
                    "Invalid payload length: " + payloadLen);
        }

        int expectedSize = Packet.HEADER_SIZE + payloadLen;
        if (data.length < expectedSize) {
            throw new CorruptPacketException(
                    "Packet truncated: expected " + expectedSize +
                    " bytes but got " + data.length);
        }

        // ── Resolve packet type ──────────────────────────────────
        PacketType type;
        try {
            type = PacketType.fromCode(typeCode);
        } catch (IllegalArgumentException e) {
            throw new CorruptPacketException("Invalid packet type: 0x" +
                    String.format("%02X", typeCode), e);
        }

        // ── Extract payload ──────────────────────────────────────
        byte[] payload = new byte[payloadLen];
        if (payloadLen > 0) {
            buffer.get(payload);
        }

        // ── Assemble Packet ──────────────────────────────────────
        Packet packet = new Packet();
        packet.setType(type);
        packet.setFlags(flags);
        packet.setSequenceNumber(seqNum);
        packet.setAckNumber(ackNum);
        packet.setPayloadLength(payloadLen);
        packet.setChecksum(checksum);
        packet.setTimestamp(timestamp);
        packet.setPayload(payload);

        return packet;
    }
}
