package tests;

import checksum.ChecksumUtil;

import java.util.Random;

/**
 * Test suite for ChecksumUtil — RFC 1071 Internet checksum.
 *
 * Run: javac -cp src -d out src/checksum/*.java tests/ChecksumTest.java
 *      java -cp out tests.ChecksumTest
 */
public class ChecksumTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("═══════════════════════════════════════");
        System.out.println("  Checksum Tests");
        System.out.println("═══════════════════════════════════════");

        testBasicComputeVerify();
        testAllZerosPayload();
        testAllOnesPayload();
        testSingleBytePayload();
        testOddLengthPayload();
        testSingleBitFlipDetected();
        testEveryBitPosition();
        testLargePayload();
        testComputeIsIdempotent();

        System.out.println("═══════════════════════════════════════");
        System.out.printf("  Results: %d passed, %d failed%n", passed, failed);
        System.out.println("═══════════════════════════════════════");

        if (failed > 0) System.exit(1);
    }

    // ── Basic compute + verify ───────────────────────────────────

    static void testBasicComputeVerify() {
        try {
            byte[] data = createTestData("Hello, world!", 20);

            short checksum = ChecksumUtil.compute(data);
            // Embed checksum at offset 12-13
            data[12] = (byte) ((checksum >> 8) & 0xFF);
            data[13] = (byte) (checksum & 0xFF);

            assertTrue("verify returns true", ChecksumUtil.verify(data));
            pass("testBasicComputeVerify");
        } catch (Exception e) {
            fail("testBasicComputeVerify", e);
        }
    }

    // ── All zeros ────────────────────────────────────────────────

    static void testAllZerosPayload() {
        try {
            byte[] data = new byte[40]; // All zeros, including checksum field

            short checksum = ChecksumUtil.compute(data);
            data[12] = (byte) ((checksum >> 8) & 0xFF);
            data[13] = (byte) (checksum & 0xFF);

            assertTrue("verify all-zeros", ChecksumUtil.verify(data));
            pass("testAllZerosPayload");
        } catch (Exception e) {
            fail("testAllZerosPayload", e);
        }
    }

    // ── All 0xFF ─────────────────────────────────────────────────

    static void testAllOnesPayload() {
        try {
            byte[] data = new byte[40];
            java.util.Arrays.fill(data, (byte) 0xFF);
            // Zero the checksum field
            data[12] = 0;
            data[13] = 0;

            short checksum = ChecksumUtil.compute(data);
            data[12] = (byte) ((checksum >> 8) & 0xFF);
            data[13] = (byte) (checksum & 0xFF);

            assertTrue("verify all-0xFF", ChecksumUtil.verify(data));
            pass("testAllOnesPayload");
        } catch (Exception e) {
            fail("testAllOnesPayload", e);
        }
    }

    // ── Single byte ──────────────────────────────────────────────

    static void testSingleBytePayload() {
        try {
            // Minimal: just 20 bytes (header) + 1 byte payload = 21 (odd)
            byte[] data = new byte[21];
            data[20] = 0x42;
            data[12] = 0;
            data[13] = 0;

            short checksum = ChecksumUtil.compute(data);
            data[12] = (byte) ((checksum >> 8) & 0xFF);
            data[13] = (byte) (checksum & 0xFF);

            assertTrue("verify single-byte", ChecksumUtil.verify(data));
            pass("testSingleBytePayload");
        } catch (Exception e) {
            fail("testSingleBytePayload", e);
        }
    }

    // ── Odd length ───────────────────────────────────────────────

    static void testOddLengthPayload() {
        try {
            byte[] data = new byte[33]; // 20 header + 13 payload (odd total)
            new Random(100).nextBytes(data);
            data[12] = 0;
            data[13] = 0;

            short checksum = ChecksumUtil.compute(data);
            data[12] = (byte) ((checksum >> 8) & 0xFF);
            data[13] = (byte) (checksum & 0xFF);

            assertTrue("verify odd-length", ChecksumUtil.verify(data));
            pass("testOddLengthPayload");
        } catch (Exception e) {
            fail("testOddLengthPayload", e);
        }
    }

    // ── Single bit flip detection ────────────────────────────────

    static void testSingleBitFlipDetected() {
        try {
            byte[] data = createTestData("Test data for flip", 20);

            short checksum = ChecksumUtil.compute(data);
            data[12] = (byte) ((checksum >> 8) & 0xFF);
            data[13] = (byte) (checksum & 0xFF);

            // Flip a single bit in the payload
            data[25] ^= 0x01;

            assertFalse("verify after flip", ChecksumUtil.verify(data));
            pass("testSingleBitFlipDetected");
        } catch (Exception e) {
            fail("testSingleBitFlipDetected", e);
        }
    }

    // ── Every bit position ───────────────────────────────────────

    static void testEveryBitPosition() {
        try {
            byte[] original = new byte[40];
            new Random(200).nextBytes(original);
            original[12] = 0;
            original[13] = 0;

            short checksum = ChecksumUtil.compute(original);
            original[12] = (byte) ((checksum >> 8) & 0xFF);
            original[13] = (byte) (checksum & 0xFF);

            int detected = 0;
            int total = 0;

            // Flip every single bit and verify detection
            for (int byteIdx = 0; byteIdx < original.length; byteIdx++) {
                if (byteIdx == 12 || byteIdx == 13) continue; // Skip checksum field
                for (int bit = 0; bit < 8; bit++) {
                    byte[] corrupted = original.clone();
                    corrupted[byteIdx] ^= (1 << bit);
                    total++;
                    if (!ChecksumUtil.verify(corrupted)) {
                        detected++;
                    }
                }
            }

            // Internet checksum detects all single-bit errors
            assertTrue("all single-bit errors detected (" + detected + "/" + total + ")",
                    detected == total);
            pass("testEveryBitPosition (" + total + " flips)");
        } catch (Exception e) {
            fail("testEveryBitPosition", e);
        }
    }

    // ── Large payload ────────────────────────────────────────────

    static void testLargePayload() {
        try {
            byte[] data = new byte[1420]; // Full max packet
            new Random(300).nextBytes(data);
            data[12] = 0;
            data[13] = 0;

            short checksum = ChecksumUtil.compute(data);
            data[12] = (byte) ((checksum >> 8) & 0xFF);
            data[13] = (byte) (checksum & 0xFF);

            assertTrue("verify large payload", ChecksumUtil.verify(data));

            // Corrupt it
            data[1000] ^= 0x80;
            assertFalse("detect corruption in large payload", ChecksumUtil.verify(data));

            pass("testLargePayload");
        } catch (Exception e) {
            fail("testLargePayload", e);
        }
    }

    // ── Idempotency ──────────────────────────────────────────────

    static void testComputeIsIdempotent() {
        try {
            byte[] data = new byte[40];
            new Random(400).nextBytes(data);
            data[12] = 0;
            data[13] = 0;

            short c1 = ChecksumUtil.compute(data);
            short c2 = ChecksumUtil.compute(data);

            assertTrue("idempotent", c1 == c2);
            pass("testComputeIsIdempotent");
        } catch (Exception e) {
            fail("testComputeIsIdempotent", e);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    static byte[] createTestData(String text, int headerSize) {
        byte[] textBytes = text.getBytes();
        byte[] data = new byte[headerSize + textBytes.length];
        System.arraycopy(textBytes, 0, data, headerSize, textBytes.length);
        // Zero checksum field
        data[12] = 0;
        data[13] = 0;
        return data;
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
}
