package com.lbg.markets.surveillance.relay.source;

import com.lbg.markets.surveillance.relay.domain.Feed;
import com.lbg.markets.surveillance.relay.domain.FileDescriptor;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * Source provider for local filesystem.
 * Handles file:// URIs or absolute paths.
 */
@ApplicationScoped
public class LocalFsSource implements SourceProvider {

    @Override
    public Stream<FileDescriptor> list(Feed feed) throws IOException {
        Path basePath = extractPath(feed.sourceUri());

        if (!Files.exists(basePath)) {
            throw new IOException("Source path does not exist: " + basePath);
        }

        return Files.walk(basePath)
                .filter(Files::isRegularFile)
                .filter(p -> matchesPatterns(basePath, p, feed))
                .map(p -> toDescriptor(basePath, p));
    }

    @Override
    public InputStream open(FileDescriptor file, long offset) throws IOException {
        Path path = Paths.get(file.sourcePath());
        InputStream in = Files.newInputStream(path);

        if (offset > 0) {
            long skipped = in.skip(offset);
            if (skipped != offset) {
                throw new IOException("Could not skip to offset " + offset);
            }
        }

        return in;
    }

    private Path extractPath(String uri) {
        if (uri.startsWith("file://")) {
            return Paths.get(uri.substring(7));
        }
        return Paths.get(uri);
    }

    private boolean matchesPatterns(Path base, Path file, Feed feed) {
        String relativePath = base.relativize(file).toString();

        // Check excludes first
        for (String exclude : feed.excludePatterns()) {
            if (matchesGlob(relativePath, exclude)) {
                return false;
            }
        }

        // If no includes specified, accept all (that aren't excluded)
        if (feed.includePatterns().isEmpty()) {
            return true;
        }

        // Check includes
        for (String include : feed.includePatterns()) {
            if (matchesGlob(relativePath, include)) {
                return true;
            }
        }

        return false;
    }

    private boolean matchesGlob(String path, String pattern) {
        // Simple glob matching - converts ** to .* and * to [^/]*
        String regex = pattern
                .replace(".", "\\.")
                .replace("**", "###DOUBLESTAR###")
                .replace("*", "[^/]*")
                .replace("###DOUBLESTAR###", ".*");

        return path.matches(regex);
    }

    private FileDescriptor toDescriptor(Path base, Path file) {
        try {
            return new FileDescriptor(
                    file.toString(),
                    Files.size(file),
                    Files.getLastModifiedTime(file).toMillis()
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file attributes: " + file, e);
        }
    }
}