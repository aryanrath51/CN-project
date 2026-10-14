package arq;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import packet.CorruptPacketException;
import packet.Packet;
import packet.PacketSerializer;
import packet.PacketType;
import transport.UDPReceiver;
import transport.UDPSender;

public class GoBackN implements ARQProtocol {

    public enum SenderState {
        READY,
        SENDING,
        WAITING_FOR_ACK,
        TIMEOUT,
        RETRANSMITTING,
        COMPLETED,
        FAILED
    }

    public enum ReceiverState {
        WAITING_FOR_DATA,
        ACCEPTING_DATA,
        ACKNOWLEDGING,
        LINGERING,
        COMPLETED,
        FAILED
    }

    private volatile SenderState senderState = SenderState.READY;
    private volatile ReceiverState receiverState =
            ReceiverState.WAITING_FOR_DATA;

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
    private int lastAcceptedSequenceNumber = -1;

    // Packets created from the current file transfer.
    private final List<Packet> packetBuffer = new ArrayList<>();

    // Holds payloads accepted by the receiver, in sequence order.
    private final List<byte[]> receivedChunks = new ArrayList<>();

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

    public SenderState getSenderState() {
        return senderState;
    }

    public ReceiverState getReceiverState() {
        return receiverState;
    }

    @Override
    public void sendData(byte[] fileData) throws Exception {
        if (fileData == null) {
            throw new IllegalArgumentException("fileData cannot be null");
        }

        packetBuffer.clear();
        base = 0;
        nextSequenceNumber = 0;
        packetBuffer.addAll(fragmentData(fileData));

        senderState = SenderState.SENDING;
        stats.markStart();

        try {
            sendWindow();

            while (base < packetBuffer.size()) {
                senderState = SenderState.WAITING_FOR_ACK;
                boolean timedOut =
                        receiveAndProcessAck(retransmissionTimeoutMs);

                if (timedOut && base < nextSequenceNumber) {
                    senderState = SenderState.TIMEOUT;
                    senderState = SenderState.RETRANSMITTING;
                    retransmitOutstandingPackets();
                    senderState = SenderState.SENDING;
                } else if (base < packetBuffer.size()) {
                    senderState = SenderState.SENDING;
                }
            }

            senderState = SenderState.COMPLETED;
        } catch (Exception e) {
            senderState = SenderState.FAILED;
            throw e;
        } finally {
            stats.markEnd();
        }
    }

    /**
     * Split the complete file into packets whose payload does not exceed
     * Packet.MAX_PAYLOAD_SIZE.
     */
    private List<Packet> fragmentData(byte[] fileData) {
        List<Packet> packets = new ArrayList<>();

        // A zero-length file still needs one final DATA packet.
        if (fileData.length == 0) {
            packets.add(Packet.createData(0, new byte[0], true));
            return packets;
        }

        int sequenceNumber = 0;

        for (int offset = 0;
             offset < fileData.length;
             offset += Packet.MAX_PAYLOAD_SIZE) {

            int remaining = fileData.length - offset;
            int payloadSize = Math.min(Packet.MAX_PAYLOAD_SIZE, remaining);

            byte[] payload = Arrays.copyOfRange(
                    fileData, offset, offset + payloadSize);

            boolean isLastFragment = offset + payloadSize >= fileData.length;

            Packet packet = Packet.createData(
                    sequenceNumber, payload, isLastFragment);

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
        return Math.min(base + windowSize, packetBuffer.size());
    }

    /**
     * Send all packets currently inside the Go-Back-N sender window.
     */
    private void sendWindow() throws Exception {
        int windowEnd = getWindowEnd();

        while (nextSequenceNumber < windowEnd) {
            Packet packet = packetBuffer.get(nextSequenceNumber);
            sender.sendPacket(packet);

            stats.totalPacketsSent++;
            stats.totalBytesSent += Packet.HEADER_SIZE + packet.getPayloadLength();
            nextSequenceNumber++;
        }
    }

    private void retransmitOutstandingPackets() throws Exception {
        for (int seq = base; seq < nextSequenceNumber; seq++) {
            Packet packet = packetBuffer.get(seq);
            sender.sendPacket(packet);

            // totalPacketsSent counts first transmissions only.
            stats.totalRetransmissions++;
            stats.totalBytesSent += Packet.HEADER_SIZE + packet.getPayloadLength();
        }
    }

    /**
     * Process a cumulative ACK received from the receiver.
     * An ACK for sequence number N confirms packet N and all earlier packets.
     */
    private void processAck(Packet ackPacket) throws Exception {
        if (ackPacket == null || ackPacket.getType() != PacketType.ACK) {
            return;
        }

        stats.totalAcksReceived++;
        int ackNumber = ackPacket.getAckNumber();

        // Ignore ACKs for packets that have not been sent yet.
        if (ackNumber >= nextSequenceNumber) {
            return;
        }

        // Ignore duplicate/old ACKs.
        if (ackNumber < base) {
            return;
        }

        // Cumulative ACK: everything through ackNumber is acknowledged.
        base = ackNumber + 1;

        // The window has moved forward, so new packets may now be sent.
        senderState = SenderState.SENDING;
        sendWindow();
    }

    private boolean receiveAndProcessAck(long timeoutMs) throws Exception {
        byte[] rawData = receiver.receive(timeoutMs);

        if (rawData == null) {
            return true; // Receive timeout
        }

        try {
            Packet packet = PacketSerializer.deserialize(rawData);
            processAck(packet);
        } catch (CorruptPacketException e) {
            stats.totalCorruptDrops++;
        }

        return false; // A datagram arrived; no timeout
    }

    @Override
    public byte[] receiveData() throws Exception {
        receivedChunks.clear();
        expectedSequenceNumber = 0;
        lastAcceptedSequenceNumber = -1;
        receiverState = ReceiverState.WAITING_FOR_DATA;

        try {
            while (true) {
                byte[] rawData = receiver.receive(retransmissionTimeoutMs);

                if (rawData == null) {
                    throw new SocketTimeoutException(
                            "Timed out while waiting for DATA");
                }

                Packet packet;

                try {
                    packet = PacketSerializer.deserialize(rawData);
                } catch (CorruptPacketException e) {
                    stats.totalCorruptDrops++;

                    if (expectedSequenceNumber > 0) {
                        receiverState = ReceiverState.ACKNOWLEDGING;
                        sender.sendPacket(
                                Packet.createAck(expectedSequenceNumber - 1));
                        receiverState = ReceiverState.WAITING_FOR_DATA;
                    }
                    continue;
                }

                if (packet.getType() != PacketType.DATA) {
                    continue;
                }

                stats.totalPacketsReceived++;
                int sequenceNumber = packet.getSequenceNumber();

                if (sequenceNumber == expectedSequenceNumber) {
                    receiverState = ReceiverState.ACCEPTING_DATA;
                    receivedChunks.add(packet.getPayload());
                    lastAcceptedSequenceNumber = sequenceNumber;
                    stats.totalBytesDelivered += packet.getPayloadLength();
                    expectedSequenceNumber++;

                    // ACK the most recently accepted in-order packet.
                    receiverState = ReceiverState.ACKNOWLEDGING;
                    sender.sendPacket(Packet.createAck(sequenceNumber));

                    if (packet.isLastFragment()) {
                        int totalLength = receivedChunks.stream()
                                .mapToInt(chunk -> chunk.length)
                                .sum();

                        byte[] result = new byte[totalLength];
                        int offset = 0;

                        for (byte[] chunk : receivedChunks) {
                            System.arraycopy(
                                    chunk, 0, result, offset, chunk.length);
                            offset += chunk.length;
                        }

                        // Linger so a retransmitted final packet can be
                        // acknowledged again if its first ACK was lost.
                        receiverState = ReceiverState.LINGERING;
                        long lingerMs =
                                Math.max(retransmissionTimeoutMs * 2, 1000);
                        long deadline =
                                System.nanoTime() + lingerMs * 1_000_000L;

                        while (System.nanoTime() < deadline) {
                            long remainingMs = Math.max(
                                    1, (deadline - System.nanoTime())
                                            / 1_000_000L);

                            byte[] duplicateRaw = receiver.receive(remainingMs);
                            if (duplicateRaw == null) {
                                continue;
                            }

                            try {
                                Packet duplicate =
                                        PacketSerializer.deserialize(duplicateRaw);

                                if (duplicate.getType() == PacketType.DATA
                                        && duplicate.getSequenceNumber()
                                                == lastAcceptedSequenceNumber) {
                                    stats.totalDuplicatesReceived++;
                                    receiverState = ReceiverState.ACKNOWLEDGING;
                                    sender.sendPacket(Packet.createAck(
                                            lastAcceptedSequenceNumber));
                                    receiverState = ReceiverState.LINGERING;
                                }
                            } catch (CorruptPacketException e) {
                                stats.totalCorruptDrops++;
                            }
                        }

                        receiverState = ReceiverState.COMPLETED;
                        return result;
                    }

                    receiverState = ReceiverState.WAITING_FOR_DATA;
                } else {
                    // Go-Back-N discards out-of-order and duplicate packets.
                    stats.totalDuplicatesReceived++;

                    // Repeat the ACK for the last correctly received packet.
                    if (expectedSequenceNumber > 0) {
                        receiverState = ReceiverState.ACKNOWLEDGING;
                        sender.sendPacket(
                                Packet.createAck(expectedSequenceNumber - 1));
                    }

                    receiverState = ReceiverState.WAITING_FOR_DATA;
                }
            }
        } catch (Exception e) {
            receiverState = ReceiverState.FAILED;
            throw e;
        }
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
