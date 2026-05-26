package com.memflow.core;

import com.memflow.core.exception.MemoryAccessException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link OffHeapString} — round-trip correctness against the JVM
 * heap string equivalent, plus regression coverage for unsafe copy behavior.
 */
public class OffHeapStringTest {

    @Test
    public void testOffHeapStringCorrectness() {
        try (OffHeapString original = new OffHeapString("MemFlow")) {
            assertEquals(7, original.getLength());
            assertEquals("MemFlow", original.toString());
        }
    }

    /**
     * Regression test for: null terminator written out of native buffer bounds.
     * Fix: allocate (len+1) elements so the terminator slot is within bounds.
     */
    @Test
    public void testNullTerminatorWithinBounds() {
        assertDoesNotThrow(() -> {
            try (OffHeapString s = new OffHeapString("A")) {
                assertEquals(1, s.getLength());
                assertEquals("A", s.toString());
            }
        });
    }

    @Test
    public void testRoundTripMultipleStrings() {
        // Back-to-back allocations: pre-fix, OOB write from first
        // allocation could corrupt the second allocation's header.
        assertDoesNotThrow(() -> {
            try (OffHeapString s1 = new OffHeapString("Hello");
                 OffHeapString s2 = new OffHeapString("World")) {
                assertEquals("Hello", s1.toString());
                assertEquals("World", s2.toString());
            }
        });
    }

    @Test
    public void testEmptyString() {
        assertDoesNotThrow(() -> {
            try (OffHeapString s = new OffHeapString("")) {
                assertEquals(0, s.getLength());
                assertEquals("", s.toString());
            }
        });
    }

    @Test
    public void copyFromShouldThrowWhenSourceExceedsDestinationCapacity() {
        try (OffHeapString dest = new OffHeapString(4);
             OffHeapString src = new OffHeapString("TOO_LONG")) {

            assertThrows(MemoryAccessException.class, () -> {
                dest.copyFrom(src);
            });
        }
    }

    @Test
    @Disabled("Intentionally demonstrates Buffer Overflow during String copying")
    public void reproduceBufferOverflow() {
        try (OffHeapString smallString = new OffHeapString(4);
             OffHeapString hugeString = new OffHeapString("SUPER_LONG_OVERFLOWING_PAYLOAD")) {
            smallString.copyFrom(hugeString);
            System.out.println("String content: " + smallString.toString());
        }
    }
}