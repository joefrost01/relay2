package com.lbg.markets.surveillance.relay.tracker;

import com.lbg.markets.surveillance.relay.domain.FileRecord;
import com.lbg.markets.surveillance.relay.domain.FileRecord.FileStatus;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory tracker for development/testing.
 * Not persistent - state is lost on restart.
 */
@ApplicationScoped
public class InMemoryTracker implements Tracker {

    private final Map<String, FileRecord> filesById = new ConcurrentHashMap<>();
    private final Map<String, String> identityToFileId = new ConcurrentHashMap<>();

    @Override
    public void upsertFile(FileRecord record) {
        String identityKey = buildIdentityKey(
                record.feedId(),
                record.sourcePath(),
                record.mtimeEpochMs(),
                record.sizeBytes()
        );

        filesById.put(record.fileId(), record);
        identityToFileId.put(identityKey, record.fileId());
    }

    @Override
    public Optional<FileRecord> findByIdentity(String feedId, String sourcePath, long mtimeEpochMs, long sizeBytes) {
        String identityKey = buildIdentityKey(feedId, sourcePath, mtimeEpochMs, sizeBytes);
        String fileId = identityToFileId.get(identityKey);

        return fileId != null
                ? Optional.ofNullable(filesById.get(fileId))
                : Optional.empty();
    }

    @Override
    public void updateStatus(String fileId, FileStatus status, String gcsUri) {
        FileRecord existing = filesById.get(fileId);
        if (existing == null) {
            throw new IllegalArgumentException("File not found: " + fileId);
        }

        FileRecord updated = new FileRecord(
                existing.fileId(),
                existing.feedId(),
                existing.sourcePath(),
                existing.sizeBytes(),
                existing.mtimeEpochMs(),
                existing.checksumMd5(),
                status,
                gcsUri,
                existing.copiedAt(),
                existing.attempts()
        );

        filesById.put(fileId, updated);
    }

    private String buildIdentityKey(String feedId, String sourcePath, long mtimeEpochMs, long sizeBytes) {
        return feedId + "|" + sourcePath + "|" + mtimeEpochMs + "|" + sizeBytes;
    }
}