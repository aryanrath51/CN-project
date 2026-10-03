package packet;

import checksum.ChecksumUtil;

/**
 * Represents a single protocol packet with a fixed 20-byte header and variable payload.
 *
 * Header Layout (20 bytes, big-endian / network byte order):
 * ┌────────────────────────────────────────────────────────────────┐
 * │ Byte 0     : PacketType code (8 bits)                         │
 * │ Byte 1     : Flags (8 bits)  — bit 0 = isLastFragment         │
 * │ Bytes 2-5  : Sequence Number (32 bits, unsigned via int)       │
 * │ Bytes 6-9  : Acknowledgment Number (32 bits)                   │
 * │ Bytes 10-11: Payload Length (16 bits, unsigned via short)       │
 * │ Bytes 12-13: Checksum (16 bits, RFC 1071 Internet checksum)    │
 * │ Bytes 14-21: Timestamp (64 bits, System.nanoTime() at send)    │
 * └────────────────────────────────────────────────────────────────┘
 * Followed by payload (0 to MAX_PAYLOAD_SIZE bytes).
 *
 * Total max packet size: 20 + 1400 = 1420 bytes (fits in Ethernet MTU).
 */
public class Packet {

    // ── Constants ──────────────────────────────────────────────────────

    /** Fixed header size in bytes: type(1) + flags(1) + seq(4) + ack(4) + payloadLen(2) + checksum(2) + timestamp(8) = 22. */
    public static final int HEADER_SIZE = 22;

    /** Maximum payload size, chosen to keep total packet under typical MTU. */
    public static final int MAX_PAYLOAD_SIZE = 1400;

    // ── Flags bit masks ───────────────────────────────────────────────

    /** Flag: this is the last fragment of the file. */
    public static final byte FLAG_LAST_FRAGMENT = 0x01;

    // ── Header fields ─────────────────────────────────────────────────

    private PacketType type;
    private byte flags;
    private int sequenceNumber;
    private int ackNumber;
    private short payloadLength;
    private short checksum;
    private long timestamp;

    // ── Payload ───────────────────────────────────────────────────────

    private byte[] payload;

    // ── Constructors ──────────────────────────────────────────────────

    /** Default constructor for manual field-by-field construction. */
    public Packet() {
        this.payload = new byte[0];
        this.payloadLength = 0;
    }

    // ── Factory methods ───────────────────────────────────────────────

    /**
     * Create a DATA packet.
     *
     * @param seqNum   sequence number (0-indexed fragment number)
     * @param data     payload bytes (must be ≤ MAX_PAYLOAD_SIZE)
     * @param isLast   true if this is the final fragment
     * @return the constructed DATA packet
     * @throws IllegalArgumentException if payload exceeds MAX_PAYLOAD_SIZE
     */
    public static Packet createData(int seqNum, byte[] data, boolean isLast) {
        if (data != null && data.length > MAX_PAYLOAD_SIZE) {
            throw new IllegalArgumentException(
                    "Payload size " + data.length + " exceeds max " + MAX_PAYLOAD_SIZE);
        }
        Packet p = new Packet();
        p.type = PacketType.DATA;
        p.sequenceNumber = seqNum;
        p.flags = isLast ? FLAG_LAST_FRAGMENT : 0;
        p.payload = (data != null) ? data : new byte[0];
        p.payloadLength = (short) p.payload.length;
        p.timestamp = System.nanoTime();
        return p;
    }

    /**
     * Create an ACK packet.
     *
     * @param ackNum the sequence number being acknowledged
     * @return the constructed ACK packet
     */
    public static Packet createAck(int ackNum) {
        Packet p = new Packet();
        p.type = PacketType.ACK;
        p.ackNumber = ackNum;
        p.timestamp = System.nanoTime();
        return p;
    }

    /**
     * Create a NAK (negative acknowledgment) packet for Selective Repeat.
     *
     * @param seqNum the sequence number of the missing/corrupt packet
     * @return the constructed NAK packet
     */
    public static Packet createNak(int seqNum) {
        Packet p = new Packet();
        p.type = PacketType.NAK;
        p.sequenceNumber = seqNum;
        p.timestamp = System.nanoTime();
        return p;
    }

    /**
     * Create a SYN packet to initiate a connection/transfer.
     *
     * @return the constructed SYN packet
     */
    public static Packet createSyn() {
        Packet p = new Packet();
        p.type = PacketType.SYN;
        p.timestamp = System.nanoTime();
        return p;
    }

    /**
     * Create a FIN packet to signal end of transfer.
     *
     * @return the constructed FIN packet
     */
    public static Packet createFin() {
        Packet p = new Packet();
        p.type = PacketType.FIN;
        p.timestamp = System.nanoTime();
        return p;
    }

    // ── Flag helpers ──────────────────────────────────────────────────

    /** @return true if this packet has the LAST_FRAGMENT flag set */
    public boolean isLastFragment() {
        return (flags & FLAG_LAST_FRAGMENT) != 0;
    }

    /** Set or clear the LAST_FRAGMENT flag. */
    public void setLastFragment(boolean last) {
        if (last) {
            flags |= FLAG_LAST_FRAGMENT;
        } else {
            flags &= ~FLAG_LAST_FRAGMENT;
        }
    }

    // ── Getters & Setters ─────────────────────────────────────────────

    public PacketType getType() {
        return type;
    }

    public void setType(PacketType type) {
        this.type = type;
    }

    public byte getFlags() {
        return flags;
    }

    public void setFlags(byte flags) {
        this.flags = flags;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public int getAckNumber() {
        return ackNumber;
    }

    public void setAckNumber(int ackNumber) {
        this.ackNumber = ackNumber;
    }

    public short getPayloadLength() {
        return payloadLength;
    }

    public void setPayloadLength(short payloadLength) {
        this.payloadLength = payloadLength;
    }

    public short getChecksum() {
        return checksum;
    }

    public void setChecksum(short checksum) {
        this.checksum = checksum;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public byte[] getPayload() {
        return payload;
    }

    public void setPayload(byte[] payload) {
        this.payload = (payload != null) ? payload : new byte[0];
        this.payloadLength = (short) this.payload.length;
    }

    // ── Debug ─────────────────────────────────────────────────────────

    @Override
    public String toString() {
        return String.format("Packet{type=%s, seq=%d, ack=%d, flags=0x%02X, payloadLen=%d, checksum=0x%04X}",
                type, sequenceNumber, ackNumber, flags, payloadLength, checksum & 0xFFFF);
    }
}
