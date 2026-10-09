
import arq.GoBackN;
import packet.Packet;
import transport.UDPSender;
import transport.UDPReceiver;

import java.net.DatagramSocket;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

public class GoBackNIntegrationTest {

    public static void main(String[] args) throws Exception {
        DatagramSocket senderSocket = new DatagramSocket(0);
        DatagramSocket receiverSocket = new DatagramSocket(0);

        UDPReceiver senderReceiver =
                new UDPReceiver(senderSocket, 100);
        UDPReceiver receiverReceiver =
                new UDPReceiver(receiverSocket, 100);

        Thread senderReader =
                new Thread(senderReceiver, "sender-reader");
        Thread receiverReader =
                new Thread(receiverReceiver, "receiver-reader");

        senderReader.setDaemon(true);
        receiverReader.setDaemon(true);

        try {
            int senderPort = senderSocket.getLocalPort();
            int receiverPort = receiverSocket.getLocalPort();

            UDPSender sender = new UDPSender(
                    senderSocket, "127.0.0.1", receiverPort);

            UDPSender receiverSender = new UDPSender(
                    receiverSocket, "127.0.0.1", senderPort);

            GoBackN senderProtocol = new GoBackN(
                    sender, senderReceiver, 4, 500);

            GoBackN receiverProtocol = new GoBackN(
                    receiverSender, receiverReceiver, 4, 15000);

            byte[] original = new byte[5000];

            for (int i = 0; i < original.length; i++) {
                original[i] = (byte) (i % 251);
            }

            AtomicReference<byte[]> received =
                    new AtomicReference<>();

            AtomicReference<Throwable> failure =
                    new AtomicReference<>();

            senderReader.start();
            receiverReader.start();

            Thread receiving = new Thread(() -> {
                try {
                    received.set(receiverProtocol.receiveData());
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                }
            }, "gbn-receiving");

            Thread sending = new Thread(() -> {
                try {
                    senderProtocol.sendData(original);
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                }
            }, "gbn-sending");

            receiving.start();
            sending.start();

            sending.join(20000);
            receiving.join(20000);

            if (sending.isAlive() || receiving.isAlive()) {
                throw new AssertionError(
                        "Transfer timed out; a worker is still running");
            }

            if (failure.get() != null) {
                throw new AssertionError(
                        "Transfer failed", failure.get());
            }

            if (!Arrays.equals(original, received.get())) {
                throw new AssertionError(
                        "Received bytes differ from original file");
            }

            System.out.println("PASS: Go-Back-N transfer succeeded");
            System.out.println("Bytes transferred: " + received.get().length);
            System.out.println("Sender statistics: "
                    + senderProtocol.getStats());

        } finally {
            senderReceiver.stop();
            receiverReceiver.stop();
        }
    }
}
