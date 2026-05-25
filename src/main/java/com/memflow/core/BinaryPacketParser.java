package com.memflow.core;

import com.memflow.core.exception.MalformedPacketException;

import sun.misc.Unsafe;

/**
 * BinaryPacketParser processes high-throughput binary network packets.
 * Decodes raw off-heap byte streams into message packets using a custom fast protocol:
 * <pre>
 * [Magic Byte: 0x5F] [Type: 1 byte] [Payload Length: 4 bytes (int)] [Payload Data: N bytes]
 * </pre>
 */
public class BinaryPacketParser {

    private static final byte MAGIC_BYTE = 0x5F;
    private static final int HEADER_SIZE = 6; // 1 byte Magic + 1 byte Type + 4 bytes Length
    private static final int MAX_PAYLOAD_SIZE = 8192; // 8KB maximum packet size limit

    /**
     * Parses and extracts the payload of a packet from a binary stream off-heap buffer.
     * Copies the payload into a separate off-heap buffer for routing/dispatching.
     *
     * @param streamBuffer direct off-heap buffer containing the incoming stream
     * @param offset       byte offset inside the stream where the packet starts
     * @return a new {@link OffHeapBuffer} containing the isolated payload
     */
    public OffHeapBuffer parsePayload(OffHeapBuffer streamBuffer, int offset) {
        if (streamBuffer == null || streamBuffer.isReleased()) {
            throw new MalformedPacketException("Invalid stream buffer source.");
        }

        Unsafe unsafe = UnsafeHolder.get();
        long baseAddress = streamBuffer.getAddress() + offset;

        // 1. Verify Magic Byte
        byte magic = unsafe.getByte(baseAddress);
        if (magic != MAGIC_BYTE) {
            throw new MalformedPacketException(
                "Malformed packet: Invalid magic header byte " +
                String.format("0x%02X", magic)
            );
        }

        // 2. Extract packet type
        byte type = unsafe.getByte(baseAddress + 1);

        // 3. Extract payload length (4-byte int at offset 2)
        int payloadLength = unsafe.getInt(baseAddress + 2);

        // Validate payload length before allocation and memory operations
        if (payloadLength < 0 || payloadLength > MAX_PAYLOAD_SIZE) {
            throw new MalformedPacketException(
                "Invalid packet payload size: " + payloadLength
            );
        }

        // Allocate payload buffer only after validation
        int routeBufferSize = Math.max(64, payloadLength);
        OffHeapBuffer payloadBuffer = new OffHeapBuffer(routeBufferSize, 1);

        long srcPayloadAddress = baseAddress + HEADER_SIZE;
        long destPayloadAddress = payloadBuffer.getAddress();

        // Zero-copy native transfer from stream into the dedicated payload buffer.
        unsafe.copyMemory(srcPayloadAddress, destPayloadAddress, payloadLength);

        return payloadBuffer;
    }

    /**
     * Parses a sequence of back-to-back packets from a stream buffer.
     * Each parsed payload is returned in declaration order.
     *
     * @param streamBuffer direct off-heap buffer containing the incoming stream
     * @param startOffset  byte offset where the first packet begins
     * @param packetCount  number of packets in the stream
     * @return array of payload buffers, one per packet
     */
    public OffHeapBuffer[] parseBatch(OffHeapBuffer streamBuffer, int startOffset, int packetCount) {
        OffHeapBuffer[] payloads = new OffHeapBuffer[packetCount];
        int cursor = startOffset;
        Unsafe unsafe = UnsafeHolder.get();

        for (int i = 0; i < packetCount; i++) {
            payloads[i] = parsePayload(streamBuffer, cursor);
            int declaredLength = unsafe.getInt(streamBuffer.getAddress() + cursor + 2);
            cursor += HEADER_SIZE + declaredLength;
        }

        return payloads;
    }

    /**
     * Parses a packet and verifies its CRC32 footer before returning the
     * payload to the caller. The expected checksum is read from the four
     * bytes immediately following the payload region.
     *
     * @param streamBuffer direct off-heap buffer containing the incoming stream
     * @param offset       byte offset inside the stream where the packet starts
     * @return the verified payload buffer
     */
    public OffHeapBuffer parseVerifiedPayload(OffHeapBuffer streamBuffer, int offset) {
        OffHeapBuffer payload = parsePayload(streamBuffer, offset);

        Unsafe unsafe = UnsafeHolder.get();
        int payloadLength = unsafe.getInt(streamBuffer.getAddress() + offset + 2);

        int expectedCrc = unsafe.getInt(
            streamBuffer.getAddress() + offset + HEADER_SIZE + payloadLength
        );

        if (!ChecksumValidator.verify(
                payload.getAddress(),
                payloadLength,
                expectedCrc)) {

            throw new MalformedPacketException(
                "Packet checksum mismatch: expected=0x" +
                Integer.toHexString(expectedCrc)
            );
        }

        return payload;
    }
}