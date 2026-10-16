package tests;

import arq.SelectiveRepeat;
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

public class SelectiveRepeatFinalAckLossTest {

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
            proxySocket.setSoTimeout(250);

            int senderPort = senderSocket.getLocalPort();
            int receiverPort = receiverSocket.getLocalPort();

            byte[] original = new byte[5000];
            for (int i = 0; i < original.length; i++) {
                original[i] = (byte) (i % 251);
            }

            int finalSequence = (original.length - 1) / Packet.MAX_PAYLOAD_SIZE;

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

            Thread senderUdpThread = new Thread(senderReceiver, "sr-sender-udp-receiver");
            Thread receiverUdpThread = new Thread(receiverUdp, "sr-receiver-udp-receiver");

            senderUdpThread.setDaemon(true);
            receiverUdpThread.setDaemon(true);
            senderUdpThread.start();
            receiverUdpThread.start();

            UDPSender senderTransport = new UDPSender(
                    senderSocket, "127.0.0.1", proxySocket.getLocalPort());
            UDPSender receiverTransport = new UDPSender(
                    receiverSocket, "127.0.0.1", proxySocket.getLocalPort());

            // Using your SelectiveRepeat implementation here!
            SelectiveRepeat senderProtocol = new SelectiveRepeat(
                    senderTransport, senderReceiver, WINDOW_SIZE, TIMEOUT_MS);
            SelectiveRepeat receiverProtocol = new SelectiveRepeat(
                    receiverTransport, receiverUdp, WINDOW_SIZE, TIMEOUT_MS);

            Thread proxyThread = new Thread(() -> {
                byte[] buffer = new byte[65507];

                try {
                    while (!proxySocket.isClosed()) {
                        DatagramPacket incoming = new DatagramPacket(buffer, buffer.length);

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
                        int destinationPort;

                        if (sourcePort == receiverPort) {
                            destinationPort = senderPort;

                            try {
                                Packet packet = PacketSerializer.deserialize(raw);

                                if (packet.getType() == PacketType.ACK
                                        && packet.getAckNumber() == finalSequence) {
                                    int count = finalAcksSeen.incrementAndGet();

                                    if (count == 1) {
                                        finalAcksDropped.incrementAndGet();
                                        System.out.println("INTENTIONAL LOSS: dropped final ACK " + finalSequence);
                                        continue;
                                    }

                                    finalAcksForwarded.incrementAndGet();
                                }
                            } catch (Exception ignored) {
                            }
                        } else if (sourcePort == senderPort) {
                            destinationPort = receiverPort;
                        } else {
                            continue;
                        }

                        DatagramPacket outgoing = new DatagramPacket(
                                raw, raw.length, InetAddress.getLoopbackAddress(), destinationPort);
                        proxySocket.send(outgoing);
                    }
                } catch (SocketException e) {
                    if (!proxySocket.isClosed()) {
                        proxyError.set(e);
                    }
                } catch (Exception e) {
                    proxyError.set(e);
                }
            }, "sr-final-ack-loss-proxy");

            proxyThread.setDaemon(true);
            proxyThread.start();

            Thread receiverThread = new Thread(() -> {
                try {
                    receivedData.set(receiverProtocol.receiveData());
                    receiverStats.set(receiverProtocol.getStats());
                } catch (Throwable t) {
                    receiverError.set(t);
                }
            }, "sr-receiver");

            Thread senderThread = new Thread(() -> {
                try {
                    senderProtocol.sendData(original);
                } catch (Throwable t) {
                    senderError.set(t);
                }
            }, "sr-sender");

            receiverThread.start();
            Thread.sleep(150);
            senderThread.start();

            senderThread.join(TRANSFER_TIMEOUT_MS);
            receiverThread.join(TRANSFER_TIMEOUT_MS);

            if (senderThread.isAlive() || receiverThread.isAlive()) {
                throw new AssertionError("Selective Repeat transfer did not finish in time.");
            }

            if (senderError.get() != null) {
                throw new AssertionError("Sender failed", senderError.get());
            }
            if (receiverError.get() != null) {
                throw new AssertionError("Receiver failed", receiverError.get());
            }
            if (receivedData.get() == null || !Arrays.equals(original, receivedData.get())) {
                throw new AssertionError("Received payload does not match original data.");
            }

            System.out.println("PASS: Selective Repeat final ACK loss recovery succeeded!");

        } finally {
            if (senderReceiver != null) senderReceiver.stop();
            if (receiverUdp != null) receiverUdp.stop();
            proxySocket.close();
            senderSocket.close();
            receiverSocket.close();
        }
    }
}