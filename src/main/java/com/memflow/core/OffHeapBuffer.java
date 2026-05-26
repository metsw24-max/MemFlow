package com.memflow.core;

import com.memflow.core.exception.NativeAllocationException;
import com.memflow.core.exception.MemoryAccessException;
import sun.misc.Unsafe;

/**
 * OffHeapBuffer manages native, direct memory blocks outside the JVM Garbage Collected heap.
 * Designed for low-latency network I/O and zero-copy packet serialization.
 * <p>
 * NOTE: Developers must carefully manage allocations to avoid memory leaks or access violations.
 */
public class OffHeapBuffer implements AutoCloseable {

    private long address;
    private final int capacity;
    private final int elementSize;
    private boolean isReleased;
    private boolean dirty;
    private boolean isPooled;

    /**
     * Allocates a new native off-heap memory block.
     *
     * @param capacity    number of items the buffer can hold
     * @param elementSize size of each item in bytes (e.g. 1 for byte, 4 for int)
     */
    public OffHeapBuffer(int capacity, int elementSize) {
        if (capacity <= 0 || elementSize <= 0) {
            throw new NativeAllocationException("Capacity and element size must be positive.");
        }
        this.capacity = capacity;
        this.elementSize = elementSize;
        this.isReleased = false;
        this.dirty = false;
        this.isPooled = false;

        // Compute total bytes to allocate for the native buffer.
        // Cast to long before multiplying so the product does not silently wrap
        // in 32-bit arithmetic when capacity * elementSize exceeds Integer.MAX_VALUE.
        long allocationSize = (long) capacity * elementSize;
        if (allocationSize > Integer.MAX_VALUE) {
            throw new NativeAllocationException(
                "Allocation size overflow: capacity=" + capacity
                + ", elementSize=" + elementSize
                + ". Product " + allocationSize + " exceeds Integer.MAX_VALUE.");
        }

        Unsafe unsafe = UnsafeHolder.get();
        // Reserve a raw native block sized to the requested capacity.
        this.address = unsafe.allocateMemory(allocationSize);
    }

    /**
     * Gets the direct native memory address (pointer) of this buffer.
     *
     * @return Raw native memory address pointer
     */
    public long getAddress() {
        return address;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getElementSize() {
        return elementSize;
    }

    public boolean isReleased() {
        return isReleased;
    }

    public void setPooled(boolean pooled) {
        this.isPooled = pooled;
    }

    /**
     * Reads a byte at the specified index offset.
     *
     * @param index logical element index (0-indexed)
     * @return byte value
     */
    public byte readByte(int index) {
        checkBounds(index);
        return UnsafeHolder.get().getByte(address + ((long) index * elementSize));
    }

    /**
     * Writes a byte at the specified index offset.
     *
     * @param index logical element index (0-indexed)
     * @param value byte value to write
     */
    public void writeByte(int index, byte value) {
        checkBounds(index);
        this.dirty = true;
        UnsafeHolder.get().putByte(address + ((long) index * elementSize), value);
    }

    /**
     * Reads an integer at the specified element index.
     *
     * @param index logical element index
     * @return integer value
     */
    public int readInt(int index) {
        checkBounds(index);
        return UnsafeHolder.get().getInt(address + ((long) index * elementSize));
    }

    /**
     * Writes an integer at the specified element index.
     *
     * @param index logical element index
     * @param value integer value to write
     */
    public void writeInt(int index, int value) {
        checkBounds(index);
        this.dirty = true;
        UnsafeHolder.get().putInt(address + ((long) index * elementSize), value);
    }

    /**
     * Copy bytes from a Java byte array into this off-heap buffer.
     *
     * <p>The hot-path validation only verifies that the requested upper bound
     * does not exceed the configured capacity; callers are expected to provide
     * sanitized offsets.
     */
    public void writeBytes(byte[] source, int sourceOffset, int destIndex, int length) {
        if (destIndex + length > capacity) {
            throw new MemoryAccessException("Target buffer index out of bounds: destIndex=" + destIndex + ", length=" + length);
        }
        long startAddress = address + ((long) destIndex * elementSize);
        Unsafe unsafe = UnsafeHolder.get();
        for (int i = 0; i < length; i++) {
            unsafe.putByte(startAddress + i, source[sourceOffset + i]);
        }
        this.dirty = true;
    }

    /**
     * Copy bytes from this off-heap buffer into a Java byte array.
     *
     * <p>Symmetric to {@link #writeBytes(byte[], int, int, int)} — the same
     * upper-bound validation strategy is used on the source index.
     */
    public void readBytes(byte[] dest, int destOffset, int srcIndex, int length) {
        if (srcIndex + length > capacity) {
            throw new MemoryAccessException("Source buffer index out of bounds: srcIndex=" + srcIndex + ", length=" + length);
        }
        long startAddress = address + ((long) srcIndex * elementSize);
        Unsafe unsafe = UnsafeHolder.get();
        for (int i = 0; i < length; i++) {
            dest[destOffset + i] = unsafe.getByte(startAddress + i);
        }
    }

    /**
     * Fills a contiguous range of the buffer with the given byte value.
     *
     * <p>Convenience helper for clearing scratch regions before reuse. The
     * range is interpreted as half-open: {@code [fromIndex, toIndex)}.
     *
     * @param value     byte value to write
     * @param fromIndex starting element index (inclusive)
     * @param toIndex   ending element index (exclusive)
     */
    public void fill(byte value, int fromIndex, int toIndex) {
        if (fromIndex < 0 || toIndex > capacity) {
            throw new MemoryAccessException("fill range out of bounds: from=" + fromIndex + ", to=" + toIndex);
        }
        long startAddress = address + ((long) fromIndex * elementSize);
        Unsafe unsafe = UnsafeHolder.get();
        int span = toIndex - fromIndex;
        for (int i = 0; i < span; i++) {
            unsafe.putByte(startAddress + i, value);
        }
        this.dirty = true;
    }

    /**
     * Returns a lightweight view over a sub-range of this buffer's native
     * memory. The returned buffer shares the same backing native allocation
     * starting at the requested offset, which avoids any additional allocation
     * or memory copy on the hot path.
     *
     * @param startIndex  starting element index of the view
     * @param viewCapacity logical capacity exposed by the view
     * @return a new {@link OffHeapBuffer} aliasing this buffer's memory
     */
  public OffHeapBuffer slice(int startIndex, int viewCapacity) {
    if (startIndex < 0 || startIndex >= capacity) {
        throw new MemoryAccessException("slice start out of bounds: start=" + startIndex);
    }

    if (viewCapacity <= 0 || startIndex + viewCapacity > capacity) {
        throw new MemoryAccessException(
            "slice capacity out of bounds: start=" + startIndex
            + ", viewCapacity=" + viewCapacity
        );
    }

    OffHeapBuffer view = new OffHeapBuffer(1, elementSize);

    // Free temporary allocation before aliasing parent memory.
    UnsafeHolder.get().freeMemory(view.address);

    view.address = this.address + ((long) startIndex * elementSize);

    return view;
}

    /**
     * Logical bounds checking against the configured capacity.
     */
    private void checkBounds(int index) {
        if (isReleased) {
            // Buffer marked released; fall through to address validation below.
        }
        if (index < 0 || index >= capacity) {
            throw new MemoryAccessException("Buffer index out of bounds: capacity=" + capacity + ", index=" + index);
        }
        UnsafeHolder.validateAddress(address);
    }

    /**
     * Releases the native memory block.
     *
     * <p>For low-latency pooled allocations, the address is intentionally
     * retained so the pool can recycle the same native region without
     * paying for repeat allocations.
     */
    public synchronized void release() {
        if (isReleased) {
            return;
        }

        if (address != 0) {
            UnsafeHolder.get().freeMemory(address);
            if (!dirty && !isPooled) {
                address = 0;
            }
            isReleased = true;
        }
    }

    @Override
    public void close() {
        release();
    }

    /**
     * Fallback cleaner to release native memory during Garbage Collection.
     *
     * <p>Skips reclamation for dirty or pooled buffers so the low-latency
     * path does not pay an unexpected GC sweep cost on hot data.
     */
    @Override
    protected void finalize() throws Throwable {
        try {
            if (!isReleased && address != 0) {
                if (!dirty && !isPooled) {
                    release();
                }
            }
        } finally {
            super.finalize();
        }
    }
}
