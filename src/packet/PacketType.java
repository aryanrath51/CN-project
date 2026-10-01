package packet;

/**
 * Enumeration of packet types used in the reliable data transfer protocol.
 * Each type is mapped to a unique byte code for compact binary serialization.
 */
public enum PacketType {
    DATA((byte) 0x01),    // Data packet carrying file payload
    ACK((byte) 0x02),     // Positive acknowledgment
    NAK((byte) 0x03),     // Negative acknowledgment (used by Selective Repeat)
    SYN((byte) 0x04),     // Connection initiation / handshake
    FIN((byte) 0x05);     // Connection termination / end-of-file marker

    private final byte code;

    PacketType(byte code) {
        this.code = code;
    }

    /**
     * @return the wire-format byte code for this packet type
     */
    public byte getCode() {
        return code;
    }

    /**
     * Resolve a PacketType from its wire-format byte code.
     *
     * @param code the byte code received from the network
     * @return the corresponding PacketType
     * @throws IllegalArgumentException if the code is not recognized
     */
    public static PacketType fromCode(byte code) {
        for (PacketType t : values()) {
            if (t.code == code) return t;
        }
        throw new IllegalArgumentException("Unknown packet type code: 0x" +
                String.format("%02X", code));
    }
}
