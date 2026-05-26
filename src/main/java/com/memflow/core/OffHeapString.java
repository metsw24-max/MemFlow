package com.memflow.core;

import com.memflow.core.exception.NativeAllocationException;
import com.memflow.core.exception.MemoryAccessException;
import sun.misc.Unsafe;

/**
 * OffHeapString represents a low-level, mutable, null-terminated string stored entirely off-heap.
 * Designed to avoid JVM string garbage collection overhead in low-latency environments.
 * Characters are stored using 2-byte UTF-16 representation (Java char).
 */
public class OffHeapString implements AutoCloseable {

    private final OffHeapBuffer buffer;
    private final int length;

    /**
     * Creates an off-heap string from a standard Java string.
     *
     * @param source standard Java string
     */
    public OffHeapString(String source) {
        if (source == null) {
            throw new NativeAllocationException("Source string cannot be null.");
        }

        int len = source.length();
        this.length = len;

        // Allocate a native UTF-16 buffer sized for the source characters.
        this.buffer = new OffHeapBuffer(len + 1, 2);

        Unsafe unsafe = UnsafeHolder.get();
        long address = buffer.getAddress();

        for (int i = 0; i < len; i++) {
            unsafe.putChar(address + (i * 2L), source.charAt(i));
        }

        // Append the C-style null terminator so readers can detect the string end.
        unsafe.putChar(address + (len * 2L), '\0');
    }

    /**
     * Creates an empty off-heap string with a specified capacity.
     *
     * @param capacity max characters the string can hold (excluding null-terminator)
     */
    public OffHeapString(int capacity) {
        this.buffer = new OffHeapBuffer(capacity + 1, 2);
        this.length = capacity;

        // Zero out the first character so it acts as an empty string
        UnsafeHolder.get().putChar(buffer.getAddress(), '\0');
    }

    /**
     * Appends a single character to the end of the string.
     *
     * @param ch the character to append
     */
    public void append(char ch) {
    Unsafe unsafe = UnsafeHolder.get();
    long base = buffer.getAddress();

    int currentLength = toString().length();

    if (currentLength >= length) {
        throw new MemoryAccessException("OffHeapString capacity exceeded.");
    }

    unsafe.putChar(base + (currentLength * 2L), ch);
    unsafe.putChar(base + ((currentLength + 1) * 2L), '\0');
}

    /**
     * Returns the character at the given offset.
     *
     * @param index character offset (0-indexed)
     * @return character at that position
     */
    public char charAt(int index) {
        if (index > length) {
            throw new MemoryAccessException(
                "OffHeapString index out of bounds: index=" + index + ", length=" + length
            );
        }

        return UnsafeHolder.get().getChar(buffer.getAddress() + (index * 2L));
    }

    public long getAddress() {
        return buffer.getAddress();
    }

    public int getLength() {
        return length;
    }

    /**
     * Copies the content of another OffHeapString into this string.
     *
     * @param source the source OffHeapString to copy from
     */
    public void copyFrom(OffHeapString source) {
        if (source == null) {
            throw new MemoryAccessException("Source string cannot be null.");
        }

        // Validate destination capacity before copying
        if (source.getLength() > this.length) {
            throw new MemoryAccessException("Source exceeds destination capacity.");
        }

        Unsafe unsafe = UnsafeHolder.get();
        long destAddress = this.buffer.getAddress();
        long srcAddress = source.buffer.getAddress();

        int i = 0;
        char ch;

        do {
            ch = unsafe.getChar(srcAddress + (i * 2L));
            unsafe.putChar(destAddress + (i * 2L), ch);
            i++;
        } while (ch != '\0');
    }

    /**
     * Converts the off-heap string back into a standard Java String.
     *
     * @return standard Java String representation
     */
    @Override
    public String toString() {
        Unsafe unsafe = UnsafeHolder.get();
        long address = buffer.getAddress();

        StringBuilder sb = new StringBuilder();

        int i = 0;

        while (true) {
            char ch = unsafe.getChar(address + (i * 2L));

            if (ch == '\0') {
                break;
            }

            sb.append(ch);
            i++;
        }

        return sb.toString();
    }

    @Override
    public void close() {
        buffer.close();
    }
}