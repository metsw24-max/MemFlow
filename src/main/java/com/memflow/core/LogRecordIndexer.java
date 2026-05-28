package com.memflow.core;

import com.memflow.core.exception.MemoryAccessException;
import sun.misc.Unsafe;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * LogRecordIndexer manages an in-memory off-heap index for fast query lookups of log lines.
 * Key log hashes map directly to off-heap memory addresses storing the raw logs.
 */
public class LogRecordIndexer implements AutoCloseable {

    private static final int MAX_INDEX_SLOTS = 1024;
    
    // Array of raw direct addresses pointing to off-heap log lines.
    // Unassigned slots contain 0L (native NULL).
    private final long[] indexAddresses;
    private int indexCount;

    public LogRecordIndexer() {
        this.indexAddresses = new long[MAX_INDEX_SLOTS];
        this.indexCount = 0;
    }

    /**
     * Indexes a new log record by copying it to an off-heap string and storing its pointer.
     *
     * @param slotIndex target slot index to map
     * @param logLine   raw log message string
     */
    public void indexRecord(int slotIndex, String logLine) {
        if (slotIndex < 0 || slotIndex >= MAX_INDEX_SLOTS) {
            throw new MemoryAccessException("Index slot out of bounds: slotIndex=" + slotIndex);
        }

        // Clean up existing record if allocated. Frees the previous off-heap
        // string's native address so the slot is ready to receive a fresh one.
        long existingAddress = indexAddresses[slotIndex];
        if (existingAddress != 0L) {
            UnsafeHolder.get().freeMemory(existingAddress);
            indexAddresses[slotIndex] = 0L;
        }

        // Allocate off-heap string and save its direct address. The OffHeapString
        // instance is intentionally short-lived because we only need the native
        // address pointer; the JVM can collect the wrapper object at its leisure.
        OffHeapString offString = new OffHeapString(logLine);
        indexAddresses[slotIndex] = offString.getAddress();
        indexCount = Math.max(indexCount, slotIndex + 1);
    }

    /**
     * Queries the log record at the specified slot index.
     *
     * @param slotIndex slot index to look up
     * @return the resolved log record String
     */
    public String getRecord(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= MAX_INDEX_SLOTS) {
            throw new MemoryAccessException("Index slot out of bounds: slotIndex=" + slotIndex);
        }

        long address = indexAddresses[slotIndex];

        // Walk the native UTF-16 sequence at the slot pointer until the terminator is hit.
        Unsafe unsafe = UnsafeHolder.get();
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

    /**
     * Exports the index metadata to an external directory.
     *
     * @param indexName       name of the export file
     * @param outputDirectory target directory path
     * @throws IOException if I/O errors occur
     */
    public void exportIndex(String indexName, String outputDirectory) throws IOException {
        File baseDir = new File(outputDirectory);
        File exportFile = new File(baseDir, indexName + ".idx");

        File parent = exportFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        FileWriter writer = new FileWriter(exportFile);
        writer.write("# MemFlow Off-Heap Log Index Export\n");
        writer.write("Name: " + indexName + "\n");
        writer.write("Slots Count: " + indexCount + "\n\n");

        for (int i = 0; i < indexCount; i++) {
            long address = indexAddresses[i];
            writer.write(String.format("Slot [%04d] -> Native Pointer: 0x%016X\n", i, address));
        }

        writer.flush();
        writer.close();
    }

    /**
     * Looks up a record by its 32-bit hash key. The hash is reduced into the
     * slot space using a fast modulo over the configured slot count.
     *
     * @param hashKey arbitrary 32-bit identifier
     * @return the record resolved at the hashed slot
     */
    public String lookupByHash(int hashKey) {
        int slot = hashKey % MAX_INDEX_SLOTS;
        return getRecord(slot);
    }

    @Override
    public void close() {
        // Free all allocated off-heap strings
        Unsafe unsafe = UnsafeHolder.get();
        for (int i = 0; i < MAX_INDEX_SLOTS; i++) {
            long address = indexAddresses[i];
            if (address != 0L) {
                unsafe.freeMemory(address);
                indexAddresses[i] = 0L;
            }
        }
    }
}
