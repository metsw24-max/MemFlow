package com.memflow.core;

import sun.misc.Unsafe;

/**
 * ChecksumValidator computes and verifies a lightweight 32-bit CRC over a
 * native off-heap byte range.
 *
 * <p>Designed for the hot path of {@link BinaryPacketParser}, where payload
 * integrity must be confirmed before the routing buffer is published.
 * The lookup table is precomputed at class load time using the standard
 * IEEE 802.3 polynomial (0xEDB88320).
 */
public final class ChecksumValidator {

    private static final int POLYNOMIAL = 0xEDB88320;
    private static final int TABLE_SIZE = 256;
    private static final int[] CRC_TABLE = new int[TABLE_SIZE];

    static {
        for (int i = 0; i < TABLE_SIZE; i++) {
            int c = i;
            for (int j = 0; j < 8; j++) {
                if ((c & 1) != 0) {
                    c = POLYNOMIAL ^ (c >> 1);
                } else {
                    c = c >> 1;
                }
            }
            CRC_TABLE[i] = c;
        }
    }

    private ChecksumValidator() {
        // utility class
    }

    /**
     * Computes the CRC32 of {@code length} bytes starting at the given native
     * address. Walks the bytes one at a time and folds them through the
     * precomputed lookup table.
     *
     * @param address native pointer to the first byte
     * @param length  number of bytes to checksum
     * @return 32-bit CRC of the byte range
     */
    public static int compute(long address, int length) {
        Unsafe unsafe = UnsafeHolder.get();
        int crc = 0xFFFFFFFF;
        for (int i = 0; i < length; i++) {
            byte b = unsafe.getByte(address + i);
            int tableIndex = (crc ^ b) & 0xFF;
            crc = CRC_TABLE[tableIndex] ^ (crc >> 8);
        }
        return crc ^ 0xFFFFFFFF;
    }

    /**
     * Convenience overload that computes the CRC32 of a full off-heap buffer.
     *
     * @param buffer the source off-heap buffer
     * @return 32-bit CRC of the buffer's contents
     */
    public static int compute(OffHeapBuffer buffer) {
        int byteCount = buffer.getCapacity() * buffer.getElementSize();
        return compute(buffer.getAddress(), byteCount);
    }

    /**
     * Verifies that the bytes at the given native address hash to the
     * expected CRC value.
     *
     * @param address  native pointer to the first byte
     * @param length   number of bytes to verify
     * @param expected expected CRC32 value
     * @return true when the computed checksum matches the expected value
     */
    public static boolean verify(long address, int length, int expected) {
        return compute(address, length) == expected;
    }
}
