
package arq;

import java.io.ByteArrayOutputStream;
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

public class StopAndWait implements ARQProtocol {

    private static final int MAX_RETRIES = 20;

    private final UDPSender sender;
    private final UDPReceiver receiver;
    private final ProtocolStats stats = new ProtocolStats();
    private final long retransmissionTimeoutMs;

    private int expectedSequenceNumber = 0;
    private int lastAcceptedSequenceNumber = -1;

    public StopAndWait(
            UDPSender sender,
            UDPReceiver receiver,
            long retransmissionTimeoutMs) {

        if (sender == null || receiver == null) {
            throw new IllegalArgumentException(
                    "Sender and receiver cannot be null");
        }
        if (retransmissionTimeoutMs <= 0) {
            throw new IllegalArgumentException(
                    "Timeout must be positive");
        }

        this.sender = sender;
        this.receiver = receiver;
        this.retransmissionTimeoutMs = retransmissionTimeoutMs;
    }

    @Override
    public void sendData(byte[] fileData) throws Exception {
        if (fileData == null) {
            throw new IllegalArgumentException(
                    "fileData cannot be null");
        }

        List<Packet> packets = fragmentData(fileData);
        receiver.clear();
        stats.markStart();

        try {
            for (Packet packet : packets) {
                boolean acknowledged = false;
                int retries = 0;

                while (!acknowledged) {
                    sender.sendPacket(packet);
                    stats.totalBytesSent += Packet.HEADER_SIZE
                            + packet.getPayloadLength();

                    if (retries == 0) {
                        stats.totalPacketsSent++;
                    } else {
                        stats.totalRetransmissions++;
                    }

                    byte[] raw = receiver.receive(
                            retransmissionTimeoutMs);

                    if (raw == null) {
                        retries++;
                        if (retries >= MAX_RETRIES) {
                            throw new SocketTimeoutException(
                                    "No ACK for sequence "
                                            + packet.getSequenceNumber()
                                            + " after "
                                            + MAX_RETRIES + " attempts");
                        }
                        continue;
                    }

                    try {
                        Packet response =
                                PacketSerializer.deserialize(raw);

                        if (response.getType() != PacketType.ACK) {
                            continue;
                        }

                        stats.totalAcksReceived++;

                        if (response.getAckNumber()
                                == packet.getSequenceNumber()) {
                            acknowledged = true;
                        }
                    } catch (CorruptPacketException e) {
                        stats.totalCorruptDrops++;
                    }
                }
            }
        } finally {
            stats.markEnd();
        }
    }

    private List<Packet> fragmentData(byte[] data) {
        List<Packet> packets = new ArrayList<>();

        if (data.length == 0) {
            packets.add(Packet.createData(0, new byte[0], true));
            return packets;
        }

        int sequence = 0;

        for (int offset = 0;
             offset < data.length;
             offset += Packet.MAX_PAYLOAD_SIZE) {

            int length = Math.min(
                    Packet.MAX_PAYLOAD_SIZE, data.length - offset);

            byte[] payload = Arrays.copyOfRange(
                    data, offset, offset + length);

            boolean last = offset + length == data.length;

            packets.add(Packet.createData(sequence++, payload, last));
        }

        return packets;
    }

    @Override
    public byte[] receiveData() throws Exception {
        receiver.clear();
        expectedSequenceNumber = 0;
        lastAcceptedSequenceNumber = -1;

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        stats.markStart();

        try {
            while (true) {
                byte[] raw = receiver.receive(retransmissionTimeoutMs);

                if (raw == null) {
                    throw new SocketTimeoutException(
                            "Timed out waiting for DATA");
                }

                Packet packet;

                try {
                    packet = PacketSerializer.deserialize(raw);
                } catch (CorruptPacketException e) {
                    stats.totalCorruptDrops++;
                    sendPreviousAck();
                    continue;
                }

                if (packet.getType() != PacketType.DATA) {
                    continue;
                }

                stats.totalPacketsReceived++;

                int sequence = packet.getSequenceNumber();

                if (sequence != expectedSequenceNumber) {
                    stats.totalDuplicatesReceived++;
                    sendPreviousAck();
                    continue;
                }

                byte[] payload = packet.getPayload();
                output.write(payload, 0, payload.length);

                stats.totalBytesDelivered += payload.length;
                lastAcceptedSequenceNumber = sequence;
                expectedSequenceNumber++;

                sender.sendPacket(Packet.createAck(sequence));

                if (packet.isLastFragment()) {
                    // Keep listening briefly so a lost final ACK can
                    // be recovered when the sender retransmits final DATA.
                    long lingerMs = Math.max(
                            retransmissionTimeoutMs * 2, 1000);
                    long deadline = System.nanoTime()
                            + lingerMs * 1_000_000L;

                    while (System.nanoTime() < deadline) {
                        long remaining = Math.max(
                                1, (deadline - System.nanoTime())
                                        / 1_000_000L);

                        byte[] duplicateRaw = receiver.receive(remaining);

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
                                sender.sendPacket(Packet.createAck(
                                        lastAcceptedSequenceNumber));
                            }
                        } catch (CorruptPacketException e) {
                            stats.totalCorruptDrops++;
                        }
                    }

                    return output.toByteArray();
                }
            }
        } finally {
            stats.markEnd();
        }
    }

    private void sendPreviousAck() throws Exception {
        if (lastAcceptedSequenceNumber >= 0) {
            sender.sendPacket(
                    Packet.createAck(lastAcceptedSequenceNumber));
        }
    }

    @Override
    public ProtocolStats getStats() {
        return stats;
    }

    @Override
    public String getProtocolName() {
        return "Stop-and-Wait";
    }
}
