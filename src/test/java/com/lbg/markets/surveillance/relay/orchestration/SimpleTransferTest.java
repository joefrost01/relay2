package com.lbg.markets.surveillance.relay.orchestration;

import com.lbg.markets.surveillance.relay.domain.Feed;
import com.lbg.markets.surveillance.relay.domain.FileDescriptor;
import com.lbg.markets.surveillance.relay.source.LocalFsSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class SimpleTransferTest {

    @Inject
    LocalFsSource source;

    private Path sourceDir;

    @BeforeEach
    void setup() throws IOException {
        sourceDir = Files.createTempDirectory("test-source-");
    }

    @AfterEach
    void cleanup() throws IOException {
        if (sourceDir != null && Files.exists(sourceDir)) {
            deleteRecursively(sourceDir);
        }
    }

    @Test
    void shouldListAllFilesWhenNoPatterns() throws IOException {
        // Create test files
        Files.writeString(sourceDir.resolve("test.txt"), "content");
        Files.writeString(sourceDir.resolve("test.csv"), "data");

        Feed feed = new Feed(
                "test",
                sourceDir.toString(),
                List.of(), // no includes - should match all
                List.of(),
                "",
                true,
                Map.of()
        );

        List<FileDescriptor> files = source.list(feed).collect(Collectors.toList());

        System.out.println("Found " + files.size() + " files:");
        files.forEach(f -> System.out.println("  - " + f.sourcePath()));

        assertEquals(2, files.size(), "Should find both files");
    }

    @Test
    void shouldMatchTxtPattern() throws IOException {
        Files.writeString(sourceDir.resolve("test.txt"), "content");
        Files.writeString(sourceDir.resolve("test.csv"), "data");

        Feed feed = new Feed(
                "test",
                sourceDir.toString(),
                List.of("*.txt"),
                List.of(),
                "",
                true,
                Map.of()
        );

        List<FileDescriptor> files = source.list(feed).collect(Collectors.toList());

        System.out.println("Found " + files.size() + " files with *.txt pattern:");
        files.forEach(f -> System.out.println("  - " + f.sourcePath()));

        assertEquals(1, files.size(), "Should only find .txt file");
        assertTrue(files.get(0).sourcePath().endsWith("test.txt"));
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
}