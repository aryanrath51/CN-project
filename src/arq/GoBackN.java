package arq;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import packet.Packet;
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

    // Packets created from the current file transfer.
    private final List<Packet> packetBuffer = new ArrayList<>();

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
    if (fileData == null) {
        throw new IllegalArgumentException("fileData cannot be null");
    }

    // Reset sender state for a new transfer.
    packetBuffer.clear();
    base = 0;
    nextSequenceNumber = 0;

    // Split the file into DATA packets.
    packetBuffer.addAll(fragmentData(fileData));

    // Send the packets currently allowed by the GBN window.
    sendWindow();
}

    /**
     * Split the complete file into packets whose payload does not exceed
     * Packet.MAX_PAYLOAD_SIZE.
     */
    private List<Packet> fragmentData(byte[] fileData) {
        List<Packet> packets = new ArrayList<>();

        // A zero-length file still needs one final DATA packet.
        if (fileData.length == 0) {
            packets.add(Packet.createData(
                    0,
                    new byte[0],
                    true
            ));
            return packets;
        }

        int sequenceNumber = 0;

        for (int offset = 0;
             offset < fileData.length;
             offset += Packet.MAX_PAYLOAD_SIZE) {

            int remaining = fileData.length - offset;

            int payloadSize = Math.min(
                    Packet.MAX_PAYLOAD_SIZE,
                    remaining
            );

            byte[] payload = Arrays.copyOfRange(
                    fileData,
                    offset,
                    offset + payloadSize
            );

            boolean isLastFragment =
                    offset + payloadSize >= fileData.length;

            Packet packet = Packet.createData(
                    sequenceNumber,
                    payload,
                    isLastFragment
            );

            packets.add(packet);
            sequenceNumber++;
        }

        return packets;
    }

    /**
     * Return the index immediately after the packets currently
     * allowed by the Go-Back-N sender window.
     */
    private int getWindowEnd() {
        return Math.min(
                base + windowSize,
                packetBuffer.size()
        );
    }
    /**
 * Send all packets currently inside the Go-Back-N sender window.
 *
 * The window contains packets from base up to, but not including,
 * getWindowEnd().
 */
private void sendWindow() throws Exception {
    int windowEnd = getWindowEnd();

    while (nextSequenceNumber < windowEnd) {
        Packet packet = packetBuffer.get(nextSequenceNumber);

        sender.sendPacket(packet);

        stats.totalPacketsSent++;
        stats.totalBytesSent += Packet.HEADER_SIZE
                + packet.getPayloadLength();

        nextSequenceNumber++;
    }
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