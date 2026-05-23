package com.memflow.core.exception;

/**
 * Thrown when low-level native memory operations encounter off-heap boundary violations or illegal states.
 */
public class MemoryAccessException extends RuntimeException {

    public MemoryAccessException(String message) {
        super(message);
    }

    public MemoryAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
