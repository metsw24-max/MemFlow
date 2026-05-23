package com.memflow.core.exception;

/**
 * Thrown when BinaryPacketParser encounters corrupted, invalid, or truncated binary protocol packets.
 */
public class MalformedPacketException extends RuntimeException {

    public MalformedPacketException(String message) {
        super(message);
    }

    public MalformedPacketException(String message, Throwable cause) {
        super(message, cause);
    }
}
