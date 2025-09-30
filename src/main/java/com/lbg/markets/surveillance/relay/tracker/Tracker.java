package com.lbg.markets.surveillance.relay.tracker;

import com.lbg.markets.surveillance.relay.domain.FileRecord;

import java.util.Optional;

public interface Tracker {
    void upsertFile(FileRecord record);

    Optional<FileRecord> findByIdentity(String feedId, String sourcePath, long mtimeEpochMs, long sizeBytes);

    void updateStatus(String fileId, FileRecord.FileStatus status, String gcsUri);
}
