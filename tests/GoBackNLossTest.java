import arq.GoBackN;
import channel.ChannelConfig;
import channel.ChannelEmulator;
import transport.UDPSender;
import transport.UDPReceiver;

import java.net.DatagramSocket;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

public class GoBackNLossTest {
    public static void main(String[] args) throws Exception {
        DatagramSocket senderSocket = new DatagramSocket(0);
        DatagramSocket receiverSocket = new DatagramSocket(0);

        ChannelEmulator emulator = null;
        UDPReceiver senderReceiver = null;
        UDPReceiver receiverReceiver = null;

        try {
            int senderPort = senderSocket.getLocalPort();
            int receiverPort = receiverSocket.getLocalPort();

            // Use a fixed seed so the impairment pattern is reproducible.
            ChannelConfig config = ChannelConfig.lossyChannel(0.25, 42L);
            int emulatorPort;

            try (DatagramSocket temporarySocket = new DatagramSocket(0)) {
                emulatorPort = temporarySocket.getLocalPort();
            }

            emulator = new ChannelEmulator(
                    config, emulatorPort, "localhost", receiverPort);

            senderReceiver = new UDPReceiver(senderSocket, 200);
            receiverReceiver = new UDPReceiver(receiverSocket, 200);

            UDPSender sender = new UDPSender(
                    senderSocket, "127.0.0.1", emulatorPort);
            UDPSender receiverSender = new UDPSender(
                    receiverSocket, "127.0.0.1", emulatorPort);

            GoBackN senderProtocol =
                    new GoBackN(sender, senderReceiver, 4, 500);
            GoBackN receiverProtocol =
                    new GoBackN(receiverSender, receiverReceiver, 4, 25000);

            Thread emulatorThread = new Thread(emulator, "gbn-loss-emulator");
            Thread senderReader = new Thread(senderReceiver, "gbn-loss-sender-reader");
            Thread receiverReader = new Thread(receiverReceiver, "gbn-loss-receiver-reader");

            emulatorThread.setDaemon(true);
            senderReader.setDaemon(true);
            receiverReader.setDaemon(true);

            emulatorThread.start();
            senderReader.start();
            receiverReader.start();

            byte[] original = new byte[20000];
            for (int i = 0; i < original.length; i++) {
                original[i] = (byte) (i % 251);
            }

            AtomicReference<byte[]> received = new AtomicReference<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();

            Thread receiving = new Thread(() -> {
                try {
                    received.set(receiverProtocol.receiveData());
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                }
            }, "gbn-loss-receiving");

            Thread sending = new Thread(() -> {
                try {
                    senderProtocol.sendData(original);
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                }
            }, "gbn-loss-sending");

            receiving.start();
            sending.start();

            sending.join(60000);
            receiving.join(60000);

            System.out.println(emulator.getStatistics());

            if (sending.isAlive() || receiving.isAlive()) {
                throw new AssertionError(
                        "Transfer timed out; a worker is still running");
            }

            if (failure.get() != null) {
                throw new AssertionError("Transfer failed", failure.get());
            }

            if (!Arrays.equals(original, received.get())) {
                throw new AssertionError(
                        "Received bytes differ from original file");
            }

            String[] channelStats = emulator.getStatsCsv().split(",");
            long droppedPackets = Long.parseLong(channelStats[2]);

            if (droppedPackets == 0) {
                throw new AssertionError(
                        "Test did not exercise packet loss");
            }

            if (senderProtocol.getStats().totalRetransmissions == 0) {
                throw new AssertionError(
                        "Packets were dropped, but no retransmissions were recorded");
            }

            if (receiverProtocol.getStats().totalBytesDelivered != original.length) {
                throw new AssertionError(
                        "Receiver delivered an unexpected number of bytes");
            }

            System.out.println("PASS: Go-Back-N recovered from packet loss");
            System.out.println("Dropped packets: " + droppedPackets);
            System.out.println("Retransmissions: "
                    + senderProtocol.getStats().totalRetransmissions);
            System.out.println("Bytes delivered: "
                    + receiverProtocol.getStats().totalBytesDelivered);
        } finally {
            if (senderReceiver != null) senderReceiver.stop();
            if (receiverReceiver != null) receiverReceiver.stop();
            if (emulator != null) emulator.stop();
        }
    }
}