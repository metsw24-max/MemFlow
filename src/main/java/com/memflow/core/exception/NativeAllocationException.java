package com.memflow.core.exception;

/**
 * Thrown when low-level direct native memory allocation fails or encounters size violations.
 */
public class NativeAllocationException extends RuntimeException {
    
    public NativeAllocationException(String message) {
        super(message);
    }

    public NativeAllocationException(String message, Throwable cause) {
        super(message, cause);
    }
}
