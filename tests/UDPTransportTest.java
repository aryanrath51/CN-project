package tests;

import transport.*;
import packet.*;

import java.net.DatagramSocket;

/**
 * Test suite for UDPSender and UDPReceiver on localhost loopback.
 *
 * Run: javac -cp src -d out src/transport/*.java src/packet/*.java src/checksum/*.java tests/UDPTransportTest.java
 *      java -cp out tests.UDPTransportTest
 */
public class UDPTransportTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("═══════════════════════════════════════");
        System.out.println("  UDP Transport Tests");
        System.out.println("═══════════════════════════════════════");

        testLoopbackSendReceive();
        testPacketSendReceive();
        testMultiplePackets();
        testReceiverTimeout();
        testTryReceiveEmpty();
        testReceiverStats();

        System.out.println("═══════════════════════════════════════");
        System.out.printf("  Results: %d passed, %d failed%n", passed, failed);
        System.out.println("═══════════════════════════════════════");

        if (failed > 0) System.exit(1);
    }

    // ── Loopback send/receive raw bytes ──────────────────────────

    static void testLoopbackSendReceive() {
        DatagramSocket senderSocket = null;
        DatagramSocket receiverSocket = null;
        UDPReceiver receiver = null;
        Thread recvThread = null;

        try {
            senderSocket = new DatagramSocket(0);     // Ephemeral port
            receiverSocket = new DatagramSocket(0);    // Ephemeral port
            int recvPort = receiverSocket.getLocalPort();

            receiver = new UDPReceiver(receiverSocket, 100);
            recvThread = new Thread(receiver, "recv-test");
            recvThread.setDaemon(true);
            recvThread.start();

            UDPSender sender = new UDPSender(senderSocket, "localhost", recvPort);

            // Create and send a data packet
            Packet pkt = Packet.createData(0, "test payload".getBytes(), false);
            byte[] serialized = PacketSerializer.serialize(pkt);
            sender.send(serialized);

            // Receive with timeout
            byte[] received = receiver.receive(2000);
            assertNotNull("received data", received);
            assertEqual("received length", serialized.length, received.length);

            // Verify deserialization works
            Packet decoded = PacketSerializer.deserialize(received);
            assertEqual("type", PacketType.DATA, decoded.getType());
            assertEqual("seqNum", 0, decoded.getSequenceNumber());

            pass("testLoopbackSendReceive");
        } catch (Exception e) {
            fail("testLoopbackSendReceive", e);
        } finally {
            cleanup(receiver, senderSocket);
        }
    }

    // ── High-level sendPacket → receive ──────────────────────────

    static void testPacketSendReceive() {
        DatagramSocket senderSocket = null;
        DatagramSocket receiverSocket = null;
        UDPReceiver receiver = null;

        try {
            senderSocket = new DatagramSocket(0);
            receiverSocket = new DatagramSocket(0);

            receiver = new UDPReceiver(receiverSocket, 100);
            Thread recvThread = new Thread(receiver, "recv-pkt");
            recvThread.setDaemon(true);
            recvThread.start();

            UDPSender sender = new UDPSender(senderSocket, "localhost",
                    receiverSocket.getLocalPort());

            // Send using high-level sendPacket
            Packet original = Packet.createData(42, "Hello, transport!".getBytes(), true);
            sender.sendPacket(original);

            byte[] rawReceived = receiver.receive(2000);
            assertNotNull("rawReceived", rawReceived);

            Packet decoded = PacketSerializer.deserialize(rawReceived);
            assertEqual("seqNum", 42, decoded.getSequenceNumber());
            assertEqual("isLast", true, decoded.isLastFragment());
            assertArrayEqual("payload", "Hello, transport!".getBytes(), decoded.getPayload());

            pass("testPacketSendReceive");
        } catch (Exception e) {
            fail("testPacketSendReceive", e);
        } finally {
            cleanup(receiver, senderSocket);
        }
    }

    // ── Multiple packets in sequence ─────────────────────────────

    static void testMultiplePackets() {
        DatagramSocket senderSocket = null;
        DatagramSocket receiverSocket = null;
        UDPReceiver receiver = null;

        try {
            senderSocket = new DatagramSocket(0);
            receiverSocket = new DatagramSocket(0);

            receiver = new UDPReceiver(receiverSocket, 200);
            Thread recvThread = new Thread(receiver, "recv-multi");
            recvThread.setDaemon(true);
            recvThread.start();

            UDPSender sender = new UDPSender(senderSocket, "localhost",
                    receiverSocket.getLocalPort());

            int count = 50;
            for (int i = 0; i < count; i++) {
                Packet pkt = Packet.createData(i, ("msg-" + i).getBytes(), i == count - 1);
                sender.sendPacket(pkt);
            }

            // Receive all
            int received = 0;
            for (int i = 0; i < count; i++) {
                byte[] raw = receiver.receive(2000);
                if (raw != null) {
                    Packet decoded = PacketSerializer.deserialize(raw);
                    received++;
                }
            }

            assertEqual("all received", count, received);
            pass("testMultiplePackets (" + count + " packets)");
        } catch (Exception e) {
            fail("testMultiplePackets", e);
        } finally {
            cleanup(receiver, senderSocket);
        }
    }

    // ── Receiver timeout ─────────────────────────────────────────

    static void testReceiverTimeout() {
        DatagramSocket receiverSocket = null;
        UDPReceiver receiver = null;

        try {
            receiverSocket = new DatagramSocket(0);
            receiver = new UDPReceiver(receiverSocket, 10);
            Thread recvThread = new Thread(receiver, "recv-timeout");
            recvThread.setDaemon(true);
            recvThread.start();

            // Nothing sent → should timeout
            long start = System.currentTimeMillis();
            byte[] result = receiver.receive(500);
            long elapsed = System.currentTimeMillis() - start;

            assertNull("timeout returns null", result);
            assertTrue("timeout duration", elapsed >= 400 && elapsed < 1500);

            pass("testReceiverTimeout");
        } catch (Exception e) {
            fail("testReceiverTimeout", e);
        } finally {
            cleanup(receiver, null);
        }
    }

    // ── tryReceive on empty queue ────────────────────────────────

    static void testTryReceiveEmpty() {
        DatagramSocket receiverSocket = null;
        UDPReceiver receiver = null;

        try {
            receiverSocket = new DatagramSocket(0);
            receiver = new UDPReceiver(receiverSocket, 10);
            Thread recvThread = new Thread(receiver, "recv-try");
            recvThread.setDaemon(true);
            recvThread.start();

            byte[] result = receiver.tryReceive();
            assertNull("tryReceive empty", result);

            pass("testTryReceiveEmpty");
        } catch (Exception e) {
            fail("testTryReceiveEmpty", e);
        } finally {
            cleanup(receiver, null);
        }
    }

    // ── Statistics tracking ──────────────────────────────────────

    static void testReceiverStats() {
        DatagramSocket senderSocket = null;
        DatagramSocket receiverSocket = null;
        UDPReceiver receiver = null;

        try {
            senderSocket = new DatagramSocket(0);
            receiverSocket = new DatagramSocket(0);

            receiver = new UDPReceiver(receiverSocket, 100);
            Thread recvThread = new Thread(receiver, "recv-stats");
            recvThread.setDaemon(true);
            recvThread.start();

            UDPSender sender = new UDPSender(senderSocket, "localhost",
                    receiverSocket.getLocalPort());

            // Send 5 packets
            for (int i = 0; i < 5; i++) {
                sender.sendPacket(Packet.createAck(i));
            }

            // Drain all
            Thread.sleep(200);
            int drained = 0;
            while (receiver.tryReceive() != null) drained++;

            assertEqual("senderPacketsSent", 5L, sender.getPacketsSent());
            assertTrue("senderBytesSent > 0", sender.getBytesSent() > 0);
            assertEqual("receivedCount", 5L, receiver.getPacketsReceived());

            pass("testReceiverStats");
        } catch (Exception e) {
            fail("testReceiverStats", e);
        } finally {
            cleanup(receiver, senderSocket);
        }
    }

    // ── Assertion helpers ────────────────────────────────────────

    static void assertEqual(String field, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError(field + ": expected " + expected + " but got " + actual);
        }
    }

    static void assertNotNull(String field, Object obj) {
        if (obj == null) throw new AssertionError(field + " should not be null");
    }

    static void assertNull(String field, Object obj) {
        if (obj != null) throw new AssertionError(field + " should be null");
    }

    static void assertTrue(String msg, boolean condition) {
        if (!condition) throw new AssertionError("Expected true: " + msg);
    }

    static void assertArrayEqual(String field, byte[] expected, byte[] actual) {
        if (!java.util.Arrays.equals(expected, actual)) {
            throw new AssertionError(field + ": byte arrays differ");
        }
    }

    static void pass(String testName) {
        System.out.println("  ✓ " + testName);
        passed++;
    }

    static void fail(String testName, Exception e) {
        System.out.println("  ✗ " + testName + " — " + e.getMessage());
        failed++;
    }

    static void cleanup(UDPReceiver receiver, DatagramSocket senderSocket) {
        try {
            if (receiver != null) receiver.stop();
            if (senderSocket != null && !senderSocket.isClosed()) senderSocket.close();
        } catch (Exception ignored) {}
    }
}
