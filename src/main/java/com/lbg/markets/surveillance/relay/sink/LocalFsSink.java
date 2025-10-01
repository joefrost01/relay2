package com.lbg.markets.surveillance.relay.sink;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;

/**
 * Simple local filesystem sink for development.
 * Writes files to a configured directory.
 */
@ApplicationScoped
@IfBuildProfile(anyOf = {"dev", "test"})
public class LocalFsSink implements Sink {

    private final Path basePath;
    private final int bufferSize;

    public LocalFsSink(
            @ConfigProperty(name = "sink.local.path", defaultValue = "/tmp/relay-sink") String path,
            @ConfigProperty(name = "sink.buffer.size", defaultValue = "8192") int bufferSize
    ) {
        this.basePath = Paths.get(path);
        this.bufferSize = bufferSize;
    }

    @Override
    public long write(String destPath, InputStream in, long offset, long length, Map<String, String> metadata)
            throws IOException {

        Path target = basePath.resolve(destPath);
        Files.createDirectories(target.getParent());

        // Use temp file then atomic rename for safety
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");

        try {
            long written = writeToFile(temp, in, offset);
            Files.move(temp, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            return written;
        } catch (IOException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
    }

    private long writeToFile(Path target, InputStream in, long offset) throws IOException {
        StandardOpenOption[] options = offset > 0
                ? new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.WRITE}
                : new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING};

        try (OutputStream out = Files.newOutputStream(target, options)) {
            if (offset > 0) {
                out.write(new byte[(int) offset]); // Skip to offset (simplified)
            }

            byte[] buffer = new byte[bufferSize];
            long totalWritten = 0;
            int bytesRead;

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalWritten += bytesRead;
            }

            return totalWritten;
        }
    }
}