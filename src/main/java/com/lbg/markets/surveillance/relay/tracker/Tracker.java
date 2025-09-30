package com.lbg.markets.surveillance.relay.tracker;

import com.lbg.markets.surveillance.relay.domain.FileRecord;
import com.lbg.markets.surveillance.relay.domain.FileRecord.FileStatus;

import java.util.Optional;

/**
 * Interface for tracking file transfer state.
 * Handles deduplication and resume capability.
 */
public interface Tracker {

    /**
     * Create or update a file record.
     */
    void upsertFile(FileRecord record);

    /**
     * Find a file by its identity (feedId, sourcePath, mtime, size).
     */
    Optional<FileRecord> findByIdentity(String feedId, String sourcePath, long mtimeEpochMs, long sizeBytes);

    /**
     * Update file status and optionally set destination URI.
     */
    void updateStatus(String fileId, FileStatus status, String gcsUri);

    /**
     * Check if a file should be skipped (already successfully copied).
     */
    default boolean shouldSkip(String feedId, String sourcePath, long mtimeEpochMs, long sizeBytes) {
        return findByIdentity(feedId, sourcePath, mtimeEpochMs, sizeBytes)
                .map(rec -> rec.status() == FileStatus.COPIED)
                .orElse(false);
    }
}