package com.memflow.core;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ChecksumValidator} — verifies that the precomputed CRC32
 * table produces a deterministic checksum and exposes a disabled reproducer
 * for the broken verification short-circuit contributors are expected to
 * investigate.
 */
public class ChecksumValidatorTest {

    @Test
    public void testChecksumIsDeterministic() {
        try (OffHeapBuffer src = new OffHeapBuffer(16, 1)) {
            for (int i = 0; i < 16; i++) {
                src.writeByte(i, (byte) (i * 7));
            }
            int first = ChecksumValidator.compute(src.getAddress(), 16);
            int second = ChecksumValidator.compute(src.getAddress(), 16);
            assertEquals(first, second);
        }
    }

    @Test
    public void testChecksumAcceptsMatchingValue() {
        try (OffHeapBuffer src = new OffHeapBuffer(16, 1)) {
            for (int i = 0; i < 16; i++) {
                src.writeByte(i, (byte) (i * 11));
            }
            int crc = ChecksumValidator.compute(src.getAddress(), 16);
            assertTrue(ChecksumValidator.verify(src.getAddress(), 16, crc));
        }
    }

    @Test
    public void reproduceBrokenChecksumVerify() {
        // Compute the real CRC of a known payload, then verify with an intentionally
        // mismatched expected value. A correct implementation must return false.
        try (OffHeapBuffer src = new OffHeapBuffer(16, 1)) {
            for (int i = 0; i < 16; i++) {
                src.writeByte(i, (byte) (i * 7));
            }
            int realCrc = ChecksumValidator.compute(src.getAddress(), 16);
            int tampered = realCrc ^ 0x01010101; // flip a bit in every byte

            boolean accepted = ChecksumValidator.verify(src.getAddress(), 16, tampered);
            assertFalse(accepted, "Tampered checksums must be rejected by verify().");
        }
    }
}
