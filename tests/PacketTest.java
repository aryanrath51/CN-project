package tests;

import checksum.ChecksumUtil;
import packet.*;

import java.util.Arrays;
import java.util.Random;

/**
 * Test suite for Packet serialization, checksum, and deserialization.
 *
 * Run: javac -cp src -d out src/packet/*.java src/checksum/*.java tests/PacketTest.java
 *      java -cp out tests.PacketTest
 *
 * Uses simple assertions (no JUnit dependency required).
 */
public class PacketTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("═══════════════════════════════════════");
        System.out.println("  Packet & Serializer Tests");
        System.out.println("═══════════════════════════════════════");

        testDataPacketRoundtrip();
        testAckPacketRoundtrip();
        testNakPacketRoundtrip();
        testFinPacketRoundtrip();
        testSynPacketRoundtrip();
        testEmptyPayload();
        testMaxPayload();
        testOddLengthPayload();
        testLastFragmentFlag();
        testPayloadTooLargeThrows();
        testCorruptionDetected();
        testSingleBitFlipDetected();
        testMultipleRoundtrips();

        System.out.println("═══════════════════════════════════════");
        System.out.printf("  Results: %d passed, %d failed%n", passed, failed);
        System.out.println("═══════════════════════════════════════");

        if (failed > 0) System.exit(1);
    }

    // ── DATA packet roundtrip ────────────────────────────────────

    static void testDataPacketRoundtrip() {
        try {
            byte[] payload = "Hello, reliable UDP!".getBytes();
            Packet original = Packet.createData(42, payload, false);

            byte[] serialized = PacketSerializer.serialize(original);
            Packet deserialized = PacketSerializer.deserialize(serialized);

            assertEqual("type", PacketType.DATA, deserialized.getType());
            assertEqual("seqNum", 42, deserialized.getSequenceNumber());
            assertArrayEqual("payload", payload, deserialized.getPayload());
            assertEqual("payloadLen", (short) payload.length, deserialized.getPayloadLength());
            assertEqual("isLastFragment", false, deserialized.isLastFragment());

            pass("testDataPacketRoundtrip");
        } catch (Exception e) {
            fail("testDataPacketRoundtrip", e);
        }
    }

    // ── ACK packet roundtrip ─────────────────────────────────────

    static void testAckPacketRoundtrip() {
        try {
            Packet original = Packet.createAck(100);

            byte[] serialized = PacketSerializer.serialize(original);
            Packet deserialized = PacketSerializer.deserialize(serialized);

            assertEqual("type", PacketType.ACK, deserialized.getType());
            assertEqual("ackNum", 100, deserialized.getAckNumber());
            assertEqual("payloadLen", (short) 0, deserialized.getPayloadLength());

            pass("testAckPacketRoundtrip");
        } catch (Exception e) {
            fail("testAckPacketRoundtrip", e);
        }
    }

    // ── NAK packet roundtrip ─────────────────────────────────────

    static void testNakPacketRoundtrip() {
        try {
            Packet original = Packet.createNak(55);

            byte[] serialized = PacketSerializer.serialize(original);
            Packet deserialized = PacketSerializer.deserialize(serialized);

            assertEqual("type", PacketType.NAK, deserialized.getType());
            assertEqual("seqNum", 55, deserialized.getSequenceNumber());

            pass("testNakPacketRoundtrip");
        } catch (Exception e) {
            fail("testNakPacketRoundtrip", e);
        }
    }

    // ── FIN packet roundtrip ─────────────────────────────────────

    static void testFinPacketRoundtrip() {
        try {
            Packet original = Packet.createFin();

            byte[] serialized = PacketSerializer.serialize(original);
            Packet deserialized = PacketSerializer.deserialize(serialized);

            assertEqual("type", PacketType.FIN, deserialized.getType());
            assertEqual("payloadLen", (short) 0, deserialized.getPayloadLength());

            pass("testFinPacketRoundtrip");
        } catch (Exception e) {
            fail("testFinPacketRoundtrip", e);
        }
    }

    // ── SYN packet roundtrip ─────────────────────────────────────

    static void testSynPacketRoundtrip() {
        try {
            Packet original = Packet.createSyn();

            byte[] serialized = PacketSerializer.serialize(original);
            Packet deserialized = PacketSerializer.deserialize(serialized);

            assertEqual("type", PacketType.SYN, deserialized.getType());

            pass("testSynPacketRoundtrip");
        } catch (Exception e) {
            fail("testSynPacketRoundtrip", e);
        }
    }

    // ── Empty payload ────────────────────────────────────────────

    static void testEmptyPayload() {
        try {
            Packet original = Packet.createData(0, new byte[0], true);

            byte[] serialized = PacketSerializer.serialize(original);
            assertEqual("serializedSize", Packet.HEADER_SIZE, serialized.length);

            Packet deserialized = PacketSerializer.deserialize(serialized);
            assertEqual("payloadLen", (short) 0, deserialized.getPayloadLength());
            assertEqual("isLast", true, deserialized.isLastFragment());

            pass("testEmptyPayload");
        } catch (Exception e) {
            fail("testEmptyPayload", e);
        }
    }

    // ── Max payload (1400 bytes) ─────────────────────────────────

    static void testMaxPayload() {
        try {
            byte[] payload = new byte[Packet.MAX_PAYLOAD_SIZE];
            new Random(123).nextBytes(payload);
            Packet original = Packet.createData(999, payload, true);

            byte[] serialized = PacketSerializer.serialize(original);
            assertEqual("serializedSize",
                    Packet.HEADER_SIZE + Packet.MAX_PAYLOAD_SIZE, serialized.length);

            Packet deserialized = PacketSerializer.deserialize(serialized);
            assertArrayEqual("maxPayload", payload, deserialized.getPayload());

            pass("testMaxPayload");
        } catch (Exception e) {
            fail("testMaxPayload", e);
        }
    }

    // ── Odd-length payload ───────────────────────────────────────

    static void testOddLengthPayload() {
        try {
            byte[] payload = new byte[127]; // Odd length — tests checksum padding
            new Random(456).nextBytes(payload);
            Packet original = Packet.createData(7, payload, false);

            byte[] serialized = PacketSerializer.serialize(original);
            Packet deserialized = PacketSerializer.deserialize(serialized);

            assertArrayEqual("oddPayload", payload, deserialized.getPayload());

            pass("testOddLengthPayload");
        } catch (Exception e) {
            fail("testOddLengthPayload", e);
        }
    }

    // ── Last fragment flag ───────────────────────────────────────

    static void testLastFragmentFlag() {
        try {
            Packet notLast = Packet.createData(0, "a".getBytes(), false);
            Packet isLast = Packet.createData(1, "b".getBytes(), true);

            byte[] s1 = PacketSerializer.serialize(notLast);
            byte[] s2 = PacketSerializer.serialize(isLast);

            Packet d1 = PacketSerializer.deserialize(s1);
            Packet d2 = PacketSerializer.deserialize(s2);

            assertEqual("notLast", false, d1.isLastFragment());
            assertEqual("isLast", true, d2.isLastFragment());

            pass("testLastFragmentFlag");
        } catch (Exception e) {
            fail("testLastFragmentFlag", e);
        }
    }

    // ── Oversized payload rejection ──────────────────────────────

    static void testPayloadTooLargeThrows() {
        try {
            byte[] oversized = new byte[Packet.MAX_PAYLOAD_SIZE + 1];
            Packet.createData(0, oversized, false);
            fail("testPayloadTooLargeThrows", "Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            pass("testPayloadTooLargeThrows");
        } catch (Exception e) {
            fail("testPayloadTooLargeThrows", e);
        }
    }

    // ── Corruption detection ─────────────────────────────────────

    static void testCorruptionDetected() {
        try {
            Packet original = Packet.createData(1, "Sensitive data".getBytes(), false);
            byte[] serialized = PacketSerializer.serialize(original);

            // Flip a byte in the payload
            serialized[Packet.HEADER_SIZE + 5] ^= 0xFF;

            try {
                PacketSerializer.deserialize(serialized);
                fail("testCorruptionDetected", "Expected CorruptPacketException");
            } catch (CorruptPacketException e) {
                pass("testCorruptionDetected");
            }
        } catch (Exception e) {
            fail("testCorruptionDetected", e);
        }
    }

    // ── Single-bit flip detection ────────────────────────────────

    static void testSingleBitFlipDetected() {
        try {
            Packet original = Packet.createData(1, "Test data for single bit".getBytes(), false);
            byte[] serialized = PacketSerializer.serialize(original);

            // Flip just one bit
            serialized[Packet.HEADER_SIZE + 3] ^= 0x01;

            try {
                PacketSerializer.deserialize(serialized);
                fail("testSingleBitFlipDetected", "Expected CorruptPacketException");
            } catch (CorruptPacketException e) {
                pass("testSingleBitFlipDetected");
            }
        } catch (Exception e) {
            fail("testSingleBitFlipDetected", e);
        }
    }

    // ── Multiple sequential roundtrips ───────────────────────────

    static void testMultipleRoundtrips() {
        try {
            Random rand = new Random(789);
            for (int i = 0; i < 100; i++) {
                int payloadSize = rand.nextInt(Packet.MAX_PAYLOAD_SIZE + 1);
                byte[] payload = new byte[payloadSize];
                rand.nextBytes(payload);

                Packet original = Packet.createData(i, payload, i == 99);
                byte[] serialized = PacketSerializer.serialize(original);
                Packet deserialized = PacketSerializer.deserialize(serialized);

                if (!Arrays.equals(payload, deserialized.getPayload())) {
                    fail("testMultipleRoundtrips", "Mismatch at iteration " + i);
                    return;
                }
            }
            pass("testMultipleRoundtrips (100 iterations)");
        } catch (Exception e) {
            fail("testMultipleRoundtrips", e);
        }
    }

    // ── Assertion helpers ────────────────────────────────────────

    static void assertEqual(String field, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError(field + ": expected " + expected + " but got " + actual);
        }
    }

    static void assertArrayEqual(String field, byte[] expected, byte[] actual) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(field + ": byte arrays differ " +
                    "(expected " + expected.length + " bytes, got " + actual.length + " bytes)");
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

    static void fail(String testName, String reason) {
        System.out.println("  ✗ " + testName + " — " + reason);
        failed++;
    }
}
