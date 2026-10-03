package packet;

/**
 * Thrown when a received packet fails integrity verification.
 * This can happen due to:
 * - Checksum mismatch (bit corruption in transit)
 * - Packet too small (truncated)
 * - Invalid header fields
 */
public class CorruptPacketException extends Exception {

    public CorruptPacketException(String message) {
        super(message);
    }

    public CorruptPacketException(String message, Throwable cause) {
        super(message, cause);
    }
}
