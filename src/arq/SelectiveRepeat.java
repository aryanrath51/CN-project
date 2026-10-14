package arq;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import packet.CorruptPacketException;
import packet.Packet;
import packet.PacketSerializer;
import packet.PacketType;
import transport.UDPReceiver;
import transport.UDPSender;

public class SelectiveRepeat implements ARQProtocol {

    private final UDPSender sender;
    private final UDPReceiver receiver;
    private final ProtocolStats stats = new ProtocolStats();
    private final int windowSize;
    private final long retransmissionTimeoutMs;

    public SelectiveRepeat(
            UDPSender sender,
            UDPReceiver receiver,
            int windowSize,
            long retransmissionTimeoutMs) {
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

        List<Packet> packetBuffer = fragmentData(fileData);
        int totalPackets = packetBuffer.size();
        
        int base = 0;
        int nextSequenceNumber = 0;
        
        // Selective Repeat: track sent time and individual timers for each unacked packet
        Map<Integer, Long> packetSendTimes = new HashMap<>();
        boolean[] acknowledged = new boolean[totalPackets];

        stats.markStart();

        try {
            while (base < totalPackets) {
                // Send packets within the window
                while (nextSequenceNumber < base + windowSize && nextSequenceNumber < totalPackets) {
                    Packet packet = packetBuffer.get(nextSequenceNumber);
                    sender.sendPacket(packet);
                    
                    stats.totalPacketsSent++;
                    stats.totalBytesSent += Packet.HEADER_SIZE + packet.getPayloadLength();
                    
                    packetSendTimes.put(nextSequenceNumber, System.currentTimeMillis());
                    nextSequenceNumber++;
                }

                // Wait briefly for ACKs
                byte[] rawAck = receiver.receive(20); // 20ms check
                if (rawAck != null) {
                    try {
                        Packet ackPacket = PacketSerializer.deserialize(rawAck);
                        if (ackPacket.getType() == PacketType.ACK) {
                            stats.totalAcksReceived++;
                            int ackNum = ackPacket.getAckNumber();
                            
                            if (ackNum >= 0 && ackNum < totalPackets) {
                                if (!acknowledged[ackNum]) {
                                    acknowledged[ackNum] = true;
                                    packetSendTimes.remove(ackNum);
                                }
                                // Slide window base forward if base is acknowledged
                                while (base < totalPackets && acknowledged[base]) {
                                    base++;
                                }
                            }
                        }
                    } catch (CorruptPacketException e) {
                        stats.totalCorruptDrops++;
                    }
                }

                // Check for individual packet timeouts and selectively retransmit
                long currentTime = System.currentTimeMillis();
                for (Map.Entry<Integer, Long> entry : packetSendTimes.entrySet()) {
                    int seq = entry.getKey();
                    if (!acknowledged[seq] && (currentTime - entry.getValue() > retransmissionTimeoutMs)) {
                        Packet packet = packetBuffer.get(seq);
                        sender.sendPacket(packet);
                        stats.totalRetransmissions++;
                        stats.totalBytesSent += Packet.HEADER_SIZE + packet.getPayloadLength();
                        
                        // Reset timer for this specific packet
                        packetSendTimes.put(seq, currentTime);
                    }
                }
            }
        } finally {
            stats.markEnd();
        }
    }

    private List<Packet> fragmentData(byte[] fileData) {
        List<Packet> packets = new ArrayList<>();
        if (fileData.length == 0) {
            packets.add(Packet.createData(0, new byte[0], true));
            return packets;
        }

        int sequenceNumber = 0;
        for (int offset = 0; offset < fileData.length; offset += Packet.MAX_PAYLOAD_SIZE) {
            int remaining = fileData.length - offset;
            int payloadSize = Math.min(Packet.MAX_PAYLOAD_SIZE, remaining);
            byte[] payload = Arrays.copyOfRange(fileData, offset, offset + payloadSize);
            boolean isLastFragment = (offset + payloadSize >= fileData.length);

            packets.add(Packet.createData(sequenceNumber, payload, isLastFragment));
            sequenceNumber++;
        }
        return packets;
    }

    @Override
    public byte[] receiveData() throws Exception {
        Map<Integer, byte[]> receiveBuffer = new HashMap<>();
        Map<Integer, Boolean> isLastPacketMap = new HashMap<>();
        int expectedSequenceNumber = 0;
        int highestSequenceReceived = -1;
        boolean lastPacketReceived = false;
        int totalExpectedPackets = -1;

        try {
            while (true) {
                byte[] rawData = receiver.receive(retransmissionTimeoutMs);
                if (rawData == null) {
                    throw new SocketTimeoutException("Timed out while waiting for DATA");
                }

                Packet packet;
                try {
                    packet = PacketSerializer.deserialize(rawData);
                } catch (CorruptPacketException e) {
                    stats.totalCorruptDrops++;
                    continue;
                }

                if (packet.getType() != PacketType.DATA) {
                    continue;
                }

                stats.totalPacketsReceived++;
                int seqNum = packet.getSequenceNumber();

                // Send individual ACK for the received packet immediately
                sender.sendPacket(Packet.createAck(seqNum));

                if (packet.isLastFragment()) {
                    lastPacketReceived = true;
                    totalExpectedPackets = seqNum + 1;
                }

                // Selective Repeat Receiver Buffer Logic
                if (seqNum >= expectedSequenceNumber && seqNum < expectedSequenceNumber + windowSize) {
                    if (!receiveBuffer.containsKey(seqNum)) {
                        receiveBuffer.put(seqNum, packet.getPayload());
                        stats.totalBytesDelivered += packet.getPayloadLength();
                    }
                } else if (seqNum < expectedSequenceNumber) {
                    // Duplicate packet, already accepted
                    stats.totalDuplicatesReceived++;
                }

                // Check if we can deliver contiguous chunks starting from expectedSequenceNumber
                while (receiveBuffer.containsKey(expectedSequenceNumber)) {
                    expectedSequenceNumber++;
                }

                // Completion check
                if (lastPacketReceived && expectedSequenceNumber == totalExpectedPackets) {
                    int totalLength = 0;
                    for (int i = 0; i < totalExpectedPackets; i++) {
                        totalLength += receiveBuffer.get(i).length;
                    }

                    byte[] result = new byte[totalLength];
                    int offset = 0;
                    for (int i = 0; i < totalExpectedPackets; i++) {
                        byte[] chunk = receiveBuffer.get(i);
                        System.arraycopy(chunk, 0, result, offset, chunk.length);
                        offset += chunk.length;
                    }
                    return result;
                }
            }
        } catch (Exception e) {
            throw e;
        }
    }

    @Override
    public ProtocolStats getStats() {
        return stats;
    }

    @Override
    public String getProtocolName() {
        return "Selective-Repeat";
    }
}