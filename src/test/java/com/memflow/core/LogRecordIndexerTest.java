package com.memflow.core;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link LogRecordIndexer} — covers indexing and slot resolution
 * correctness, plus disabled reproducers for null-pointer, double-close, and
 * header-injection defects contributors are expected to investigate.
 */
public class LogRecordIndexerTest {

    @Test
    public void testLogRecordIndexerCorrectness() {
        try (LogRecordIndexer indexer = new LogRecordIndexer()) {
            indexer.indexRecord(0, "INFO: Node 1 active");
            indexer.indexRecord(3, "WARN: Out of memory warning");

            assertEquals("INFO: Node 1 active", indexer.getRecord(0));
            assertEquals("WARN: Out of memory warning", indexer.getRecord(3));
        }
    }

    @Test
    @Disabled("Intentionally crashes the JVM with Segmentation Fault via direct null pointer dereference")
    public void reproduceNullPointerCrash() {
        try (LogRecordIndexer indexer = new LogRecordIndexer()) {
            // Index 5 is unassigned (contains address 0L).
            // Attempting to read this direct NULL address triggers instant JVM crash
            indexer.getRecord(5);
        }
    }

    @Test
    @Disabled("Intentionally demonstrates double-free when LogRecordIndexer.close is invoked twice")
    public void reproduceIndexerDoubleClose() {
        LogRecordIndexer indexer = new LogRecordIndexer();
        indexer.indexRecord(0, "first");
        indexer.indexRecord(1, "second");

        indexer.close();
        // Second close re-frees every populated slot — JVM crash territory.
        indexer.close();
    }

    @Test
    @Disabled("Intentionally demonstrates indexName injection corrupting the export header")
    public void reproduceIndexNameHeaderInjection() throws IOException {
        try (LogRecordIndexer indexer = new LogRecordIndexer()) {
            indexer.indexRecord(0, "alpha");

            // A malicious indexName containing a newline forges an extra
            // 'Slots Count' line into the metadata header.
            String malicious = "report\nSlots Count: 999999\nName: hijacked";
            indexer.exportIndex(malicious, "./build/test-export");

            File expected = new File("./build/test-export", malicious + ".idx");
            assertTrue(expected.exists());
            String content = new String(Files.readAllBytes(expected.toPath()));
            assertFalse(content.contains("Slots Count: 999999"),
                    "exportIndex must sanitize indexName to prevent header injection.");
        }
    }
}
