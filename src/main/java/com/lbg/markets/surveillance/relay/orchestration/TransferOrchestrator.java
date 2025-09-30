package com.lbg.markets.surveillance.relay.orchestration;

import com.lbg.markets.surveillance.relay.domain.*;
import com.lbg.markets.surveillance.relay.domain.FileRecord.FileStatus;
import com.lbg.markets.surveillance.relay.sink.Sink;
import com.lbg.markets.surveillance.relay.source.SourceProvider;
import com.lbg.markets.surveillance.relay.tracker.Tracker;
import com.lbg.markets.surveillance.relay.util.FileIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates file transfers from source to sink with tracking.
 * Handles the main flow: list → filter → dedupe → copy → track.
 */
@ApplicationScoped
public class TransferOrchestrator {

    private static final Logger LOG = Logger.getLogger(TransferOrchestrator.class);

    @Inject
    SourceProvider sourceProvider;

    @Inject
    Sink sink;

    @Inject
    Tracker tracker;

    /**
     * Execute a transfer run for the given feed.
     * Returns a list of results for each file processed.
     */
    public List<TransferResult> executeTransfer(Feed feed) {
        LOG.infof("Starting transfer for feed: %s", feed.id());
        List<TransferResult> results = new ArrayList<>();

        try (var fileStream = sourceProvider.list(feed)) {
            fileStream.forEach(descriptor -> {
                TransferResult result = processFile(feed, descriptor);
                results.add(result);
            });
        } catch (IOException e) {
            LOG.errorf(e, "Failed to list files for feed: %s", feed.id());
            throw new RuntimeException("Transfer failed during file listing", e);
        }

        LOG.infof("Transfer complete for feed %s: %d files processed", feed.id(), results.size());
        return results;
    }

    private TransferResult processFile(Feed feed, FileDescriptor descriptor) {
        String fileId = FileIdentity.generateFileId(feed.id(), descriptor);

        LOG.debugf("Processing file: %s (id: %s)", descriptor.sourcePath(), fileId);

        // Check if already copied
        if (tracker.shouldSkip(feed.id(), descriptor.sourcePath(),
                descriptor.mtimeEpochMs(), descriptor.sizeBytes())) {
            LOG.debugf("Skipping already copied file: %s", descriptor.sourcePath());
            return TransferResult.skipped(fileId, descriptor.sourcePath(), "Already copied");
        }

        // Create file record as DISCOVERED
        FileRecord record = new FileRecord(
                fileId,
                feed.id(),
                descriptor.sourcePath(),
                descriptor.sizeBytes(),
                descriptor.mtimeEpochMs(),
                null, // checksum - will add later
                FileStatus.DISCOVERED,
                null, // gcsUri
                null, // copiedAt
                0    // attempts
        );
        tracker.upsertFile(record);

        try {
            // Update to COPYING
            tracker.updateStatus(fileId, FileStatus.COPYING, null);

            // Perform the transfer
            String destPath = buildDestPath(feed, descriptor);
            long bytesWritten = transferFile(descriptor, destPath);

            // Update to COPIED
            tracker.updateStatus(fileId, FileStatus.COPIED, destPath);

            LOG.infof("Successfully copied %s → %s (%d bytes)",
                    descriptor.sourcePath(), destPath, bytesWritten);

            return TransferResult.success(fileId, descriptor.sourcePath(), destPath, bytesWritten);

        } catch (Exception e) {
            LOG.errorf(e, "Failed to transfer file: %s", descriptor.sourcePath());
            tracker.updateStatus(fileId, FileStatus.FAILED, null);
            return TransferResult.failed(fileId, descriptor.sourcePath(), e.getMessage());
        }
    }

    private long transferFile(FileDescriptor descriptor, String destPath) throws IOException {
        try (InputStream in = sourceProvider.open(descriptor, 0)) {
            return sink.write(
                    destPath,
                    in,
                    0,
                    descriptor.sizeBytes(),
                    Map.of(
                            "source", descriptor.sourcePath(),
                            "size", String.valueOf(descriptor.sizeBytes()),
                            "mtime", String.valueOf(descriptor.mtimeEpochMs())
                    )
            );
        }
    }

    private String buildDestPath(Feed feed, FileDescriptor descriptor) {
        // Simple strategy: prefix + filename
        // Later we can add date partitioning
        String filename = extractFilename(descriptor.sourcePath());

        if (feed.destinationPrefix() != null && !feed.destinationPrefix().isBlank()) {
            return feed.destinationPrefix() + "/" + filename;
        }

        return filename;
    }

    private String extractFilename(String path) {
        int lastSep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSep >= 0 ? path.substring(lastSep + 1) : path;
    }
}