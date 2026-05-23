package com.memflow.core;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link OffHeapString} — round-trip correctness against the JVM
 * heap string equivalent, plus the disabled reproducer for the strcpy-style
 * buffer overflow contributors are expected to investigate.
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
