package arq;

/**
 * Interface that all ARQ (Automatic Repeat reQuest) protocols must implement.
 *
 * Implementations:
 * - StopAndWait   (Priyanshi + Tanishqa, Week 3-4)
 * - GoBackN       (Tanishqa, Week 3-4)
 * - SelectiveRepeat (Aryan, Week 5-6)
 *
 * This interface is defined by Raunak and consumed by all team members.
 * DO NOT change this interface without notifying the entire team.
 */
public interface ARQProtocol {

    /**
     * Reliably send an entire byte array (e.g., file contents) over the channel.
     * The implementation is responsible for:
     * - Fragmenting data into packets of MAX_PAYLOAD_SIZE
     * - Sending packets with sequence numbers
     * - Receiving and processing ACKs/NAKs
     * - Retransmitting lost/corrupt packets
     * - Marking the last fragment
     *
     * @param fileData the complete data to send
     * @throws Exception if the transfer fails irrecoverably
     */
    void sendData(byte[] fileData) throws Exception;

    /**
     * Reliably receive and reassemble a complete byte array.
     * The implementation is responsible for:
     * - Receiving packets and verifying checksums
     * - Sending ACKs/NAKs
     * - Buffering out-of-order packets (Selective Repeat)
     * - Reassembling the final byte array in sequence order
     * - Detecting the last fragment
     *
     * @return the reassembled data
     * @throws Exception if the transfer fails irrecoverably
     */
    byte[] receiveData() throws Exception;

    /**
     * Get protocol performance statistics for benchmarking/plotting.
     *
     * @return a snapshot of the current statistics
     */
    ProtocolStats getStats();

    /**
     * Get the human-readable name of this protocol.
     *
     * @return e.g., "Stop-and-Wait", "Go-Back-N", "Selective Repeat"
     */
    String getProtocolName();
}
