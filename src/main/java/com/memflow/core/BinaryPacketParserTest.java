package com.memflow.core;

import com.memflow.core.exception.MalformedPacketException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class BinaryPacketParserTest {

    @Test
    void shouldRejectNegativePayloadLength() {
        OffHeapBuffer buffer = new OffHeapBuffer(64, 1);

        try {
            long address = buffer.getAddress();

            UnsafeHolder.get().putByte(address, (byte) 0x5F);
            UnsafeHolder.get().putByte(address + 1, (byte) 0x01);

            // Negative payload length
            UnsafeHolder.get().putInt(address + 2, -1);

            BinaryPacketParser parser = new BinaryPacketParser();

            assertThrows(
                MalformedPacketException.class,
                () -> parser.parsePayload(buffer, 0)
            );

        } finally {
            buffer.release();
        }
    }
}