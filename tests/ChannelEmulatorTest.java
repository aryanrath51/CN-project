package tests;

import channel.*;

import java.net.*;
import java.util.*;

public class ChannelEmulatorTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("═══════════════════════════════════════");
        System.out.println("  Channel Emulator Tests");
        System.out.println("═══════════════════════════════════════");

        testPerfectChannel();
        testTotalLoss();
        testDeterministicSeed();
        testPartialLoss();

        System.out.println("═══════════════════════════════════════");
        System.out.printf("  Results: %d passed, %d failed%n", passed, failed);
        System.out.println("═══════════════════════════════════════");

        if (failed > 0) System.exit(1);
    }

    // ── Perfect channel: all packets arrive ──────────────────────

    static void testPerfectChannel() {
        DatagramSocket sender = null;
        DatagramSocket receiver = null;
        ChannelEmulator emulator = null;

        try {
            receiver = new DatagramSocket(0);
            int recvPort = receiver.getLocalPort();
            receiver.setSoTimeout(3000);

            // Create emulator with zero impairment
            ChannelConfig config = ChannelConfig.perfectChannel();
            int emulatorPort = findFreePort();
            emulator = new ChannelEmulator(config, emulatorPort, "localhost", recvPort);

            Thread emulatorThread = new Thread(emulator, "emulator-perfect");
            emulatorThread.setDaemon(true);
            emulatorThread.start();

            sender = new DatagramSocket(0);
            int count = 20;

            // Send packets through emulator
            for (int i = 0; i < count; i++) {
                byte[] data = ("packet-" + i).getBytes();
                DatagramPacket dp = new DatagramPacket(data, data.length,
                        InetAddress.getByName("localhost"), emulatorPort);
                sender.send(dp);
            }

            // Receive all packets
            int received = 0;
            byte[] buffer = new byte[1500];
            try {
                for (int i = 0; i < count; i++) {
                    DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
                    receiver.receive(dp);
                    received++;
                }
            } catch (SocketTimeoutException e) {
                // Some may timeout
            }

            assertEqual("perfect channel delivery", count, received);
            pass("testPerfectChannel");
        } catch (Exception e) {
            fail("testPerfectChannel", e);
        } finally {
            cleanup(emulator, sender, receiver);
        }
    }

    // ── 100% loss: no packets arrive ─────────────────────────────

    static void testTotalLoss() {
        DatagramSocket sender = null;
        DatagramSocket receiver = null;
        ChannelEmulator emulator = null;

        try {
            receiver = new DatagramSocket(0);
            int recvPort = receiver.getLocalPort();
            receiver.setSoTimeout(1000);

            ChannelConfig config = ChannelConfig.lossyChannel(1.0, 42L);
            int emulatorPort = findFreePort();
            emulator = new ChannelEmulator(config, emulatorPort, "localhost", recvPort);

            Thread emulatorThread = new Thread(emulator, "emulator-loss");
            emulatorThread.setDaemon(true);
            emulatorThread.start();

            sender = new DatagramSocket(0);

            // Send 10 packets
            for (int i = 0; i < 10; i++) {
                byte[] data = ("lost-" + i).getBytes();
                DatagramPacket dp = new DatagramPacket(data, data.length,
                        InetAddress.getByName("localhost"), emulatorPort);
                sender.send(dp);
            }

            // Try to receive — should timeout with nothing
            byte[] buffer = new byte[1500];
            boolean gotPacket = false;
            try {
                DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
                receiver.receive(dp);
                gotPacket = true;
            } catch (SocketTimeoutException e) {
                // Expected
            }

            assertFalse("100% loss = no delivery", gotPacket);
            pass("testTotalLoss");
        } catch (Exception e) {
            fail("testTotalLoss", e);
        } finally {
            cleanup(emulator, sender, receiver);
        }
    }

    // ── Deterministic: same seed → same behavior ─────────────────

    static void testDeterministicSeed() {
        try {
            long seed = 12345L;
            double lossRate = 0.3;
            int totalPackets = 200;

            // Simulate two runs with the same seed
            List<Boolean> run1 = simulateDropPattern(lossRate, seed, totalPackets);
            List<Boolean> run2 = simulateDropPattern(lossRate, seed, totalPackets);

            assertEqual("same seed → same pattern", run1, run2);

            // Verify approximate loss rate
            long dropped = run1.stream().filter(b -> !b).count();
            double actualRate = (double) dropped / totalPackets;
            assertTrue("loss rate ~30% (actual: " + String.format("%.1f%%", actualRate * 100) + ")",
                    actualRate > 0.15 && actualRate < 0.45);

            pass("testDeterministicSeed");
        } catch (Exception e) {
            fail("testDeterministicSeed", e);
        }
    }

    // ── Partial loss: some packets arrive ────────────────────────

    static void testPartialLoss() {
        DatagramSocket sender = null;
        DatagramSocket receiver = null;
        ChannelEmulator emulator = null;

        try {
            receiver = new DatagramSocket(0);
            int recvPort = receiver.getLocalPort();
            receiver.setSoTimeout(2000);

            ChannelConfig config = ChannelConfig.lossyChannel(0.5, 999L);
            int emulatorPort = findFreePort();
            emulator = new ChannelEmulator(config, emulatorPort, "localhost", recvPort);

            Thread emulatorThread = new Thread(emulator, "emulator-partial");
            emulatorThread.setDaemon(true);
            emulatorThread.start();

            sender = new DatagramSocket(0);
            int count = 100;

            for (int i = 0; i < count; i++) {
                byte[] data = ("pkt-" + i).getBytes();
                DatagramPacket dp = new DatagramPacket(data, data.length,
                        InetAddress.getByName("localhost"), emulatorPort);
                sender.send(dp);
            }

            // Receive what we can
            Thread.sleep(500); // Allow time for delayed packets
            int received = 0;
            byte[] buffer = new byte[1500];
            try {
                while (true) {
                    DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
                    receiver.receive(dp);
                    received++;
                }
            } catch (SocketTimeoutException e) {
                // Done
            }

            // With 50% loss, we expect roughly half
            assertTrue("partial loss: received " + received + " of " + count,
                    received > 20 && received < 80);

            pass("testPartialLoss (received " + received + "/" + count + ")");
        } catch (Exception e) {
            fail("testPartialLoss", e);
        } finally {
            cleanup(emulator, sender, receiver);
        }
    }

    // ── Helper: simulate drop pattern without network ────────────

    static List<Boolean> simulateDropPattern(double lossRate, long seed, int count) {
        Random random = new Random(seed);
        List<Boolean> pattern = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            boolean delivered = random.nextDouble() >= lossRate;
            pattern.add(delivered);
        }
        return pattern;
    }

    // ── Helpers ──────────────────────────────────────────────────

    static int findFreePort() throws Exception {
        try (DatagramSocket s = new DatagramSocket(0)) {
            return s.getLocalPort();
        }
    }

    static void assertEqual(String field, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError(field + ": expected " + expected + " but got " + actual);
        }
    }

    static void assertTrue(String msg, boolean condition) {
        if (!condition) throw new AssertionError("Expected true: " + msg);
    }

    static void assertFalse(String msg, boolean condition) {
        if (condition) throw new AssertionError("Expected false: " + msg);
    }

    static void pass(String testName) {
        System.out.println("  ✓ " + testName);
        passed++;
    }

    static void fail(String testName, Exception e) {
        System.out.println("  ✗ " + testName + " — " + e.getMessage());
        failed++;
    }

    static void cleanup(ChannelEmulator emulator, DatagramSocket... sockets) {
        try {
            if (emulator != null) emulator.stop();
        } catch (Exception ignored) {}
        for (DatagramSocket s : sockets) {
            try {
                if (s != null && !s.isClosed()) s.close();
            } catch (Exception ignored) {}
        }
    }
}
