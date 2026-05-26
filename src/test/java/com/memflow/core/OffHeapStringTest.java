package com.memflow.core;

import com.memflow.core.exception.MemoryAccessException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

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

            // Unsafe strcpy style copy causing native corruption
            smallString.copyFrom(hugeString);

            // This may output long string indicating buffer overflow,
            // or crash with Access Violation during scan.
            System.out.println("String content: " + smallString.toString());
        }
    }
}