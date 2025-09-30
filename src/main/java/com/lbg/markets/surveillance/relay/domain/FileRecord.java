package com.lbg.markets.surveillance.relay.domain;

import java.time.Instant;

/**
 * Database record for a discovered/copied file.
 */
public record FileRecord(
        String fileId,
        String feedId,
        String sourcePath,
        long sizeBytes,
        long mtimeEpochMs,
        String checksumMd5,
        FileStatus status,
        String gcsUri,
        Instant copiedAt,
        int attempts
) {
    public FileRecord {
        if (fileId == null || fileId.isBlank()) {
            throw new IllegalArgumentException("fileId cannot be blank");
        }
        if (feedId == null || feedId.isBlank()) {
            throw new IllegalArgumentException("feedId cannot be blank");
        }
    }

    public enum FileStatus {
        DISCOVERED,
        COPYING,
        COPIED,
        FAILED,
        SKIPPED
    }
}