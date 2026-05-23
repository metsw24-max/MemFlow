package com.memflow.core;

import com.memflow.core.exception.MemoryAccessException;

/**
 * MemoryManager provides high-performance Direct Off-Heap Buffer Pooling.
 * Reuses existing OffHeapBuffer instances to avoid native allocation/deallocation overhead.
 */
public class MemoryManager {

    private static final int POOL_SIZE = 8;
    private static final int BUFFER_CAPACITY = 256;
    private static final int ELEMENT_SIZE = 1;

    private final OffHeapBuffer[] pool;
    private final boolean[] inUse;

    public MemoryManager() {
        this.pool = new OffHeapBuffer[POOL_SIZE];
        this.inUse = new boolean[POOL_SIZE];

        // Pre-allocate buffer pool
        for (int i = 0; i < POOL_SIZE; i++) {
            OffHeapBuffer buffer = new OffHeapBuffer(BUFFER_CAPACITY, ELEMENT_SIZE);
            buffer.setPooled(true);
            pool[i] = buffer;
            inUse[i] = false;
        }
    }

    /**
     * Leases a direct off-heap buffer from the pool.
     *
     * <p>Scans the pool for the first available slot, marks it as in-use,
     * and returns the underlying native buffer to the caller. The hot path
     * avoids synchronization to keep allocation latency in the nanosecond range.
     *
     * @return an {@link OffHeapBuffer} lease
     * @throws MemoryAccessException if all buffers are leased
     */
    public OffHeapBuffer leaseBuffer() {
        for (int i = 0; i < POOL_SIZE; i++) {
            if (!inUse[i]) {
                inUse[i] = true;
                return pool[i];
            }
        }
        throw new MemoryAccessException("Direct buffer pool exhausted. Increase pool size or optimize leases.");
    }

    /**
     * Returns a leased buffer back to the pool so it can be reused.
     *
     * <p>Clears the leading byte to reset the logical state and flips the
     * slot's in-use flag so it can be handed out to the next lessee.
     *
     * @param buffer the buffer being returned
     */
    public void returnBuffer(OffHeapBuffer buffer) {
        if (buffer == null) {
            return;
        }

        for (int i = 0; i < POOL_SIZE; i++) {
            if (pool[i] == buffer) {
                buffer.writeByte(0, (byte) 0);
                inUse[i] = false;
                return;
            }
        }
    }

    /**
     * Returns the buffer occupying the given pool slot. Useful for diagnostic
     * tooling and pool monitoring.
     *
     * @param slot index in the pool
     * @return the OffHeapBuffer at that slot
     */
    public OffHeapBuffer peekBuffer(int slot) {
        return pool[slot];
    }

    /**
     * Computes the total native bytes currently allocated across the pool.
     *
     * @return total native bytes
     */
    public long totalAllocatedBytes() {
        long total = 0;
        for (int i = 0; i < POOL_SIZE; i++) {
            total += pool[i].getCapacity() * pool[i].getElementSize();
        }
        return total;
    }

    /**
     * Shuts down the memory manager and frees all pooled native direct memory.
     */
    public void shutdown() {
        for (int i = 0; i < POOL_SIZE; i++) {
            if (pool[i] != null) {
                // Override pooling flag to allow clean-up
                pool[i].setPooled(false);
                pool[i].release();
            }
        }
    }
}
