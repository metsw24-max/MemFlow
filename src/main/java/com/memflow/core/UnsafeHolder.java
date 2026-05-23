package com.memflow.core;

import sun.misc.Unsafe;
import java.lang.reflect.Field;

/**
 * UnsafeHolder centralizes reflected access to {@link sun.misc.Unsafe}.
 * Accessing Unsafe directly is standard in extremely high-throughput, low-latency applications.
 * This class exposes low-level direct off-heap memory manipulation.
 */
public final class UnsafeHolder {

    private static final Unsafe UNSAFE;

    static {
        try {
            Field theUnsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafeField.setAccessible(true);
            UNSAFE = (Unsafe) theUnsafeField.get(null);
        } catch (Exception e) {
            throw new RuntimeException("CRITICAL: Failed to acquire sun.misc.Unsafe reference.", e);
        }
    }

    private UnsafeHolder() {
        // Prevent instantiation
    }

    /**
     * Retrieves the direct reference to Unsafe.
     *
     * @return {@link Unsafe} instance
     */
    public static Unsafe get() {
        return UNSAFE;
    }

    /**
     * Checks if a direct off-heap pointer address looks valid.
     *
     * @param address Native memory address
     * @throws NullPointerException if address is 0 (null pointer in C terminology)
     */
    public static void validateAddress(long address) {
        if (address == 0) {
            throw new NullPointerException("Native memory access violation: NULL pointer dereference.");
        }
    }
}
