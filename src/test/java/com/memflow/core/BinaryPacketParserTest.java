package com.memflow.core;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for {@link BinaryPacketParser} — covers the happy-path packet decode
 * alongside disabled reproducers for the length-validation and integer-handling
 * defects contributors are expected to investigate.
 */
public class BinaryPacketParserTest {

    @Test
    public void testBinaryPacketParserCorrectness() {
        BinaryPacketParser parser = new BinaryPacketParser();

        // Header: [Magic: 0x5F (1B)] [Type: 0x02 (1B)] [Length: 100 (4B)] -> Total 6 Bytes
        // Payload: 100 Bytes
        try (OffHeapBuffer stream = new OffHeapBuffer(128, 1)) {
            stream.writeByte(0, (byte) 0x5F); // Magic
            stream.writeByte(1, (byte) 0x02); // Type
            stream.writeInt(2, 100);          // Length

            for (int i = 0; i < 100; i++) {
                stream.writeByte(6 + i, (byte) i);
            }

            try (OffHeapBuffer parsedPayload = parser.parsePayload(stream, 0)) {
                assertNotNull(parsedPayload);
                assertEquals(100, parsedPayload.getCapacity());
                assertEquals(0, parsedPayload.readByte(0));
                assertEquals(99, parsedPayload.readByte(99));
            }
        }
    }

    @Test
    @Disabled("Intentionally crashes the JVM with Segmentation Fault via integer underflow native copy")
    public void reproduceIntegerUnderflowCrash() {
        BinaryPacketParser parser = new BinaryPacketParser();
        try (OffHeapBuffer stream = new OffHeapBuffer(16, 1)) {
            stream.writeByte(0, (byte) 0x5F); // Magic
            stream.writeByte(1, (byte) 0x01); // Type
            stream.writeInt(2, -50);          // Negative length underflow

            // Triggers crash inside unsafe.copyMemory as negative int becomes massive unsigned long
            parser.parsePayload(stream, 0);
        }
    }

    @Test
    @Disabled("Intentionally demonstrates packet payload over-read past the stream buffer bounds")
    public void reproducePayloadOverRead() {
        BinaryPacketParser parser = new BinaryPacketParser();

        // Construct a stream that *claims* a 4000-byte payload but only contains 10 actual bytes.
        // The declared length passes the MAX_PAYLOAD_SIZE upper-bound check, so the parser
        // proceeds to copy 4000 bytes from native memory, reading far past the source buffer
        // and silently leaking adjacent off-heap contents into the routing buffer.
        try (OffHeapBuffer stream = new OffHeapBuffer(32, 1)) {
            stream.writeByte(0, (byte) 0x5F); // Magic
            stream.writeByte(1, (byte) 0x03); // Type
            stream.writeInt(2, 4000);         // Declared length (within MAX_PAYLOAD_SIZE)

            for (int i = 0; i < 10; i++) {
                stream.writeByte(6 + i, (byte) (0xA0 + i));
            }

            try (OffHeapBuffer payload = parser.parsePayload(stream, 0)) {
                assertNotNull(payload);
                assertEquals(4000, payload.getCapacity(),
                        "Parser should have rejected the packet whose declared length exceeds the stream's remaining bytes.");
            }
        }
    }
}
