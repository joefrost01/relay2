package com.lbg.markets.surveillance.relay.orchestration;

import com.lbg.markets.surveillance.relay.domain.Feed;
import com.lbg.markets.surveillance.relay.domain.TransferResult;
import com.lbg.markets.surveillance.relay.service.TransferOrchestrationService;
import com.lbg.markets.surveillance.relay.sink.LocalFsSink;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class TransferOrchestrationServiceTest {

    @Inject
    TransferOrchestrationService orchestrator;

    @Inject
    LocalFsSink localSink;

    private Path sourceDir;
    private Path sinkDir;
    private Path originalSinkPath;

    @BeforeEach
    void setup() throws Exception {
        // Create temp directories
        sourceDir = Files.createTempDirectory("test-source-");
        sinkDir = Files.createTempDirectory("test-sink-");

        // Store original sink path and update it via reflection for testing
        Field basePathField = LocalFsSink.class.getDeclaredField("basePath");
        basePathField.setAccessible(true);
        originalSinkPath = (Path) basePathField.get(localSink);
        basePathField.set(localSink, sinkDir);
    }

    @AfterEach
    void cleanup() throws Exception {
        // Restore original sink path
        if (originalSinkPath != null) {
            Field basePathField = LocalFsSink.class.getDeclaredField("basePath");
            basePathField.setAccessible(true);
            basePathField.set(localSink, originalSinkPath);
        }

        // Clean up temp directories
        if (sourceDir != null && Files.exists(sourceDir)) {
            deleteRecursively(sourceDir);
        }
        if (sinkDir != null && Files.exists(sinkDir)) {
            deleteRecursively(sinkDir);
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                stream.forEach(p -> {
                    try {
                        deleteRecursively(p);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }
        Files.deleteIfExists(path);
    }

    @Test
    void shouldTransferSimpleFile() throws IOException {
        // Create a test file
        Path testFile = sourceDir.resolve("test.txt");
        Files.writeString(testFile, "Hello, world!");

        // Create a feed
        Feed feed = new Feed(
                "test-feed",
                sourceDir.toString(),
                List.of("*.txt"),  // Simple pattern for files in root
                List.of(),
                "output",
                true,
                Map.of()
        );

        // Execute transfer
        List<TransferResult> results = orchestrator.executeTransfer(feed);

        // Verify
        assertEquals(1, results.size());
        TransferResult result = results.getFirst();
        assertEquals(TransferResult.Status.SUCCESS, result.status());
        assertTrue(result.bytesTransferred() > 0);

        // Verify file exists in sink
        Path expectedOutput = sinkDir.resolve("output/test.txt");
        assertTrue(Files.exists(expectedOutput), "Output file should exist at: " + expectedOutput);
        assertEquals("Hello, world!", Files.readString(expectedOutput));
    }

    @Test
    void shouldSkipAlreadyCopiedFile() throws IOException {
        // Create test file
        Path testFile = sourceDir.resolve("duplicate.txt");
        Files.writeString(testFile, "Content");

        Feed feed = new Feed(
                "test-feed",
                sourceDir.toString(),
                List.of("*.txt"),
                List.of(),
                "output",
                true,
                Map.of()
        );

        // First transfer
        List<TransferResult> firstRun = orchestrator.executeTransfer(feed);
        assertEquals(1, firstRun.size());
        assertEquals(TransferResult.Status.SUCCESS, firstRun.getFirst().status());

        // Second transfer - should skip
        List<TransferResult> secondRun = orchestrator.executeTransfer(feed);
        assertEquals(1, secondRun.size());
        assertEquals(TransferResult.Status.SKIPPED, secondRun.getFirst().status());
    }

    @Test
    void shouldRespectIncludePatterns() throws IOException {
        // Create multiple files
        Files.writeString(sourceDir.resolve("included.csv"), "data");
        Files.writeString(sourceDir.resolve("excluded.txt"), "text");

        Feed feed = new Feed(
                "test-feed",
                sourceDir.toString(),
                List.of("*.csv"),  // Only CSV
                List.of(),
                "output",
                true,
                Map.of()
        );

        List<TransferResult> results = orchestrator.executeTransfer(feed);

        // Should only transfer the CSV
        assertEquals(1, results.size());
        assertTrue(results.getFirst().sourcePath().endsWith("included.csv"));
    }

    @Test
    void shouldRespectExcludePatterns() throws IOException {
        Files.writeString(sourceDir.resolve("good.txt"), "keep");
        Path tmpDir = sourceDir.resolve("tmp");
        Files.createDirectories(tmpDir);
        Files.writeString(tmpDir.resolve("bad.txt"), "exclude");

        Feed feed = new Feed(
                "test-feed",
                sourceDir.toString(),
                List.of("**/*.txt"),
                List.of("**/tmp/**"),  // Exclude tmp directory
                "output",
                true,
                Map.of()
        );

        List<TransferResult> results = orchestrator.executeTransfer(feed);

        // Should only transfer good.txt
        assertEquals(1, results.size());
        assertTrue(results.getFirst().sourcePath().endsWith("good.txt"));
    }
}