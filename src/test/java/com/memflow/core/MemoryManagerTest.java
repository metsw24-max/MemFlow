package com.memflow.core;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Tests for {@link MemoryManager} — verifies pool lease/return/reuse semantics
 * and exposes a disabled reproducer for the stale-reference use-after-free
 * issue contributors are expected to investigate.
 */
public class MemoryManagerTest {

    @Test
    public void testMemoryManagerCorrectness() {
        MemoryManager memoryManager = new MemoryManager();
        OffHeapBuffer b1 = memoryManager.leaseBuffer();
        OffHeapBuffer b2 = memoryManager.leaseBuffer();

        assertNotEquals(b1.getAddress(), b2.getAddress());

        memoryManager.returnBuffer(b1);
        OffHeapBuffer b3 = memoryManager.leaseBuffer();
        assertEquals(b1.getAddress(), b3.getAddress()); // Pooled reusage

        memoryManager.shutdown();
    }

    @Test
    @Disabled("Intentionally demonstrates Use-After-Free logic vulnerability")
    public void reproduceUseAfterFree() {
        MemoryManager memoryManager = new MemoryManager();
        OffHeapBuffer b1 = memoryManager.leaseBuffer();

        b1.writeInt(0, 1111);
        memoryManager.returnBuffer(b1); // Returns but b1 holds reference

        OffHeapBuffer b2 = memoryManager.leaseBuffer(); // Re-leases the same buffer
        b2.writeInt(0, 2222);

        // Stale buffer access (reads 2222 instead of 1111 due to UAF)
        int staleVal = b1.readInt(0);
        assertEquals(2222, staleVal);

        memoryManager.shutdown();
    }
}
