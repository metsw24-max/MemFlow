package com.memflow.core;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.memflow.core.exception.MemoryAccessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;


import com.memflow.core.exception.NativeAllocationException;


/**
 * Tests for {@link OffHeapBuffer} — covers basic read/write correctness plus
 * the disabled reproducers for memory-safety issues that contributors are
 * expected to investigate.
 */
public class OffHeapBufferTest {

    @Test
public void testSliceRejectsOutOfBoundsView() {
    OffHeapBuffer buffer = new OffHeapBuffer(10, 1);

    assertThrows(MemoryAccessException.class, () ->
        buffer.slice(8, 5)
    );

    buffer.release();
}

    @Test
    public void testBasicBufferReadWrite() {
        try (OffHeapBuffer buffer = new OffHeapBuffer(10, 4)) {
            assertNotNull(buffer);
            assertEquals(10, buffer.getCapacity());
            assertEquals(4, buffer.getElementSize());

            buffer.writeInt(0, 100);
            buffer.writeInt(5, 500);
            buffer.writeInt(9, 999);

            assertEquals(100, buffer.readInt(0));
            assertEquals(500, buffer.readInt(5));
            assertEquals(999, buffer.readInt(9));
        }
    }

    @Test
    public void testAllocationIntegerOverflowIsRejected() {
        // (2^30 + 1) * 4 = 4,294,967,300 which overflows int to 4.
        // Before the fix the constructor silently allocated 4 bytes while
        // recording capacity as 1,073,741,825 — every subsequent bounds check
        // passed against the recorded value while writes landed outside the
        // real native block. The fix widens the multiply to long and throws.
        int capacity = (1 << 30) + 1;
        int elementSize = 4;

        assertThrows(NativeAllocationException.class, () ->
            new OffHeapBuffer(capacity, elementSize)
        );
    }

    @Test
    @Disabled("Intentionally demonstrates the silent no-op when OffHeapBuffer.fill receives an inverted range")
    public void reproduceFillInvertedRangeSilentNoOp() {
        try (OffHeapBuffer buffer = new OffHeapBuffer(8, 1)) {
            for (int i = 0; i < 8; i++) {
                buffer.writeByte(i, (byte) 0xAA);
            }
            // Inverted range: toIndex < fromIndex. A correct implementation
            // should reject this, but the current one silently does nothing.
            buffer.fill((byte) 0x00, 6, 2);

            // The buffer is still 0xAA everywhere — the caller has no way of
            // knowing the fill never happened.
            assertEquals((byte) 0x00, buffer.readByte(4),
                    "fill() should reject inverted ranges instead of silently no-op'ing.");
        }
    }

    @Test
    @Disabled("Intentionally demonstrates aliasing UAF between a parent buffer and its slice view")
    public void reproduceSliceAliasingUseAfterFree() {
        OffHeapBuffer parent = new OffHeapBuffer(64, 1);
        parent.writeByte(10, (byte) 0x77);

        OffHeapBuffer view = parent.slice(8, 16);
        // The view shares the parent's underlying allocation: a read at view[2]
        // should see the byte parent wrote at index 10.
        assertEquals((byte) 0x77, view.readByte(2));

        // Releasing the parent frees the native allocation backing the view.
        parent.release();

        // The view now holds a dangling pointer — accessing it is UAF.
        view.readByte(2);
        view.release();
    }
}
