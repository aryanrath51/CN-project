package arq;

import transport.UDPReceiver;
import transport.UDPSender;

public class GoBackN implements ARQProtocol {

    private final UDPSender sender;
    private final UDPReceiver receiver;
    private final ProtocolStats stats = new ProtocolStats();

    private final int windowSize;
    private final long retransmissionTimeoutMs;

    // Sender state
    private int base = 0;
    private int nextSequenceNumber = 0;

    // Receiver state
    private int expectedSequenceNumber = 0;

    public GoBackN(
            UDPSender sender,
            UDPReceiver receiver,
            int windowSize,
            long retransmissionTimeoutMs) {

        if (sender == null) {
            throw new IllegalArgumentException("sender cannot be null");
        }

        if (receiver == null) {
            throw new IllegalArgumentException("receiver cannot be null");
        }

        if (windowSize <= 0) {
            throw new IllegalArgumentException("windowSize must be positive");
        }

        if (retransmissionTimeoutMs <= 0) {
            throw new IllegalArgumentException(
                    "retransmissionTimeoutMs must be positive");
        }

        this.sender = sender;
        this.receiver = receiver;
        this.windowSize = windowSize;
        this.retransmissionTimeoutMs = retransmissionTimeoutMs;
    }

    @Override
    public void sendData(byte[] fileData) throws Exception {
        // Go-Back-N sender implementation will be added incrementally.
    }

    @Override
    public byte[] receiveData() throws Exception {
        // Go-Back-N receiver implementation will be added incrementally.
        return new byte[0];
    }

    @Override
    public ProtocolStats getStats() {
        return stats;
    }

    @Override
    public String getProtocolName() {
        return "Go-Back-N";
    }
}