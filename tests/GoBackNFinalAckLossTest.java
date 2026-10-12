import arq.GoBackN;
import arq.ProtocolStats;
import packet.Packet;
import packet.PacketSerializer;
import packet.PacketType;
import transport.UDPReceiver;
import transport.UDPSender;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class GoBackNFinalAckLossTest {

    private static final int TIMEOUT_MS = 400;
    private static final int WINDOW_SIZE = 4;
    private static final int TRANSFER_TIMEOUT_MS = 12000;

    public static void main(String[] args) throws Exception {
        DatagramSocket senderSocket = new DatagramSocket(0);
        DatagramSocket receiverSocket = new DatagramSocket(0);
        DatagramSocket proxySocket = new DatagramSocket(0);

        UDPReceiver senderReceiver = null;
        UDPReceiver receiverUdp = null;

        try {
            // Only the proxy needs a timeout so its thread can check shutdown.
            proxySocket.setSoTimeout(250);

            int senderPort = senderSocket.getLocalPort();
            int receiverPort = receiverSocket.getLocalPort();

            byte[] original = new byte[5000];
            for (int i = 0; i < original.length; i++) {
                original[i] = (byte) (i % 251);
            }

            int finalSequence =
                    (original.length - 1) / Packet.MAX_PAYLOAD_SIZE;

            AtomicInteger finalAcksSeen = new AtomicInteger();
            AtomicInteger finalAcksDropped = new AtomicInteger();
            AtomicInteger finalAcksForwarded = new AtomicInteger();

            AtomicReference<byte[]> receivedData = new AtomicReference<>();
            AtomicReference<ProtocolStats> receiverStats = new AtomicReference<>();
            AtomicReference<Throwable> senderError = new AtomicReference<>();
            AtomicReference<Throwable> receiverError = new AtomicReference<>();
            AtomicReference<Throwable> proxyError = new AtomicReference<>();

            senderReceiver = new UDPReceiver(senderSocket, 1024);
            receiverUdp = new UDPReceiver(receiverSocket, 1024);

            Thread senderUdpThread =
                    new Thread(senderReceiver, "sender-udp-receiver");
            Thread receiverUdpThread =
                    new Thread(receiverUdp, "receiver-udp-receiver");

            senderUdpThread.setDaemon(true);
            receiverUdpThread.setDaemon(true);
            senderUdpThread.start();
            receiverUdpThread.start();

            UDPSender senderTransport = new UDPSender(
                    senderSocket, "127.0.0.1", proxySocket.getLocalPort());
            UDPSender receiverTransport = new UDPSender(
                    receiverSocket, "127.0.0.1", proxySocket.getLocalPort());

            GoBackN senderProtocol = new GoBackN(
                    senderTransport, senderReceiver, WINDOW_SIZE, TIMEOUT_MS);
            GoBackN receiverProtocol = new GoBackN(
                    receiverTransport, receiverUdp, WINDOW_SIZE, TIMEOUT_MS);

            Thread proxyThread = new Thread(() -> {
                byte[] buffer = new byte[65507];

                try {
                    while (!proxySocket.isClosed()) {
                        DatagramPacket incoming =
                                new DatagramPacket(buffer, buffer.length);

                        try {
                            proxySocket.receive(incoming);
                        } catch (SocketTimeoutException timeout) {
                            continue;
                        }

                        byte[] raw = Arrays.copyOfRange(
                                incoming.getData(),
                                incoming.getOffset(),
                                incoming.getOffset() + incoming.getLength());

                        int sourcePort = incoming.getPort();
                        System.out.println(
                                "PROXY: packet received from port " + sourcePort
                                + ", sender port=" + senderPort
                                + ", receiver port=" + receiverPort);

                        int destinationPort;

                        // Receiver -> sender: ACK path.
                        if (sourcePort == receiverPort) {
                            destinationPort = senderPort;

                            try {
                                Packet packet = PacketSerializer.deserialize(raw);
                                System.out.println(
                                        "PROXY: packet type=" + packet.getType()
                                        + ", seq=" + packet.getSequenceNumber()
                                        + ", ack=" + packet.getAckNumber()
                                        + ", sourcePort=" + sourcePort);

                                if (packet.getType() == PacketType.ACK
                                        && packet.getAckNumber() == finalSequence) {
                                    int count = finalAcksSeen.incrementAndGet();

                                    if (count == 1) {
                                        finalAcksDropped.incrementAndGet();
                                        System.out.println(
                                                "INTENTIONAL LOSS: dropped final ACK "
                                                + finalSequence);
                                        continue;
                                    }

                                    finalAcksForwarded.incrementAndGet();
                                    System.out.println(
                                            "PROXY: forwarding final ACK "
                                            + packet.getAckNumber() + " to sender");
                                }
                            } catch (Exception ignored) {
                                // Forward non-decodable packets unchanged.
                            }
                        } else if (sourcePort == senderPort) {
                            // Sender -> receiver: DATA path.
                            destinationPort = receiverPort;

                            try {
                                Packet packet = PacketSerializer.deserialize(raw);
                                System.out.println(
                                        "PROXY: forwarding sender packet type="
                                        + packet.getType()
                                        + ", seq=" + packet.getSequenceNumber()
                                        + " to receiver");
                            } catch (Exception ignored) {
                                // Forward packets unchanged if they cannot be decoded.
                            }
                        } else {
                            continue;
                        }

                        DatagramPacket outgoing = new DatagramPacket(
                                raw,
                                raw.length,
                                InetAddress.getLoopbackAddress(),
                                destinationPort);
                        proxySocket.send(outgoing);
                    }
                } catch (SocketException e) {
                    if (!proxySocket.isClosed()) {
                        proxyError.set(e);
                    }
                } catch (Exception e) {
                    proxyError.set(e);
                }
            }, "final-ack-loss-proxy");

            proxyThread.setDaemon(true);
            proxyThread.start();

            Thread receiverThread = new Thread(() -> {
                try {
                    receivedData.set(receiverProtocol.receiveData());
                    receiverStats.set(receiverProtocol.getStats());
                } catch (Throwable t) {
                    receiverError.set(t);
                }
            }, "gbn-receiver");

            Thread senderThread = new Thread(() -> {
                try {
                    senderProtocol.sendData(original);
                } catch (Throwable t) {
                    senderError.set(t);
                }
            }, "gbn-sender");

            receiverThread.start();
            Thread.sleep(150);
            senderThread.start();

            senderThread.join(TRANSFER_TIMEOUT_MS);
            receiverThread.join(TRANSFER_TIMEOUT_MS);

            if (senderThread.isAlive() || receiverThread.isAlive()) {
                throw new AssertionError(
                        "Transfer did not finish in time. "
                        + "Dropped final ACKs: " + finalAcksDropped.get()
                        + ", forwarded final ACKs: " + finalAcksForwarded.get()
                        + ", final ACKs seen: " + finalAcksSeen.get());
            }

            if (senderError.get() != null) {
                throw new AssertionError("Sender failed", senderError.get());
            }
            if (receiverError.get() != null) {
                throw new AssertionError("Receiver failed", receiverError.get());
            }
            if (proxyError.get() != null) {
                throw new AssertionError("Proxy failed", proxyError.get());
            }
            if (finalAcksDropped.get() != 1) {
                throw new AssertionError(
                        "Expected exactly one dropped final ACK; got "
                        + finalAcksDropped.get());
            }
            if (finalAcksForwarded.get() < 1) {
                throw new AssertionError("No retransmitted final ACK reached the sender");
            }

            ProtocolStats senderStats = senderProtocol.getStats();
            ProtocolStats receiverProtocolStats = receiverStats.get();

            if (senderStats.totalRetransmissions < 1) {
                throw new AssertionError("Sender did not retransmit after ACK loss");
            }
            if (receiverProtocolStats == null) {
                throw new AssertionError("Receiver statistics unavailable");
            }
            if (receiverProtocolStats.totalDuplicatesReceived < 1) {
                throw new AssertionError("Receiver did not record the retransmitted packet");
            }
            if (receivedData.get() == null
                    || !Arrays.equals(original, receivedData.get())) {
                throw new AssertionError("Received payload does not match");
            }
            if (receiverProtocolStats.totalBytesDelivered != original.length) {
                throw new AssertionError(
                        "Unexpected delivered byte count: "
                        + receiverProtocolStats.totalBytesDelivered);
            }

            System.out.println("PASS: final ACK loss recovery succeeded");
            System.out.println("Final ACKs dropped: " + finalAcksDropped.get());
            System.out.println("Final ACKs forwarded: " + finalAcksForwarded.get());
            System.out.println("Sender retransmissions: "
                    + senderStats.totalRetransmissions);
            System.out.println("Receiver duplicates: "
                    + receiverProtocolStats.totalDuplicatesReceived);
            System.out.println("Bytes delivered: "
                    + receiverProtocolStats.totalBytesDelivered);

        } finally {
            if (senderReceiver != null) {
                senderReceiver.stop();
            }
            if (receiverUdp != null) {
                receiverUdp.stop();
            }
            proxySocket.close();
            senderSocket.close();
            receiverSocket.close();
        }
    }
}
