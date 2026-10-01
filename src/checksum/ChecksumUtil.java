package checksum;

/**
 * 16-bit Internet checksum (RFC 1071).
 *
 * Algorithm:
 * 1. Treat the data as a sequence of 16-bit words (big-endian).
 * 2. Sum all words using 32-bit arithmetic.
 * 3. Fold the carry bits back into the lower 16 bits.
 * 4. Take the one's complement of the result.
 *
 * Verification: computing the checksum over the entire packet (including the
 * embedded checksum field) should yield 0xFFFF if the data is intact.
 */
public class ChecksumUtil {

    /** Byte offset of the checksum field within the packet header. */
    public static final int CHECKSUM_OFFSET = 12;

    /** Size of the checksum field in bytes. */
    public static final int CHECKSUM_SIZE = 2;

    /**
     * Compute the 16-bit one's complement checksum over the given data.
     * The checksum field (bytes 12-13) in the data MUST be zeroed before calling.
     *
     * @param data the raw packet bytes with checksum field zeroed
     * @return the computed checksum as a short
     */
    public static short compute(byte[] data) {
        long sum = 0;
        int length = data.length;

        // Sum consecutive 16-bit words
        int i = 0;
        while (i < length - 1) {
            int word = ((data[i] & 0xFF) << 8) | (data[i + 1] & 0xFF);
            sum += word;
            i += 2;
        }

        // If odd number of bytes, pad the last byte with a zero byte
        if (length % 2 != 0) {
            sum += (data[length - 1] & 0xFF) << 8;
        }

        // Fold 32-bit sum to 16 bits: add carry bits back
        while ((sum >> 16) != 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }

        // One's complement
        return (short) (~sum & 0xFFFF);
    }

    /**
     * Verify the checksum of a received packet.
     * Computes the checksum over the entire packet (including the checksum field).
     * If the packet is intact, the result will be 0xFFFF.
     *
     * @param data the raw packet bytes with embedded checksum
     * @return true if the checksum is valid (packet not corrupted)
     */
    public static boolean verify(byte[] data) {
        long sum = 0;
        int length = data.length;

        int i = 0;
        while (i < length - 1) {
            int word = ((data[i] & 0xFF) << 8) | (data[i + 1] & 0xFF);
            sum += word;
            i += 2;
        }

        if (length % 2 != 0) {
            sum += (data[length - 1] & 0xFF) << 8;
        }

        while ((sum >> 16) != 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }

        return (short) (sum & 0xFFFF) == (short) 0xFFFF;
    }
}
