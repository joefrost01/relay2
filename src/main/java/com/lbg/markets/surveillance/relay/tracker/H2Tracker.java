package com.lbg.markets.surveillance.relay.tracker;

import com.lbg.markets.surveillance.relay.domain.FileRecord;
import com.lbg.markets.surveillance.relay.domain.FileRecord.FileStatus;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * H2-based tracker for dev/test environments.
 * For now using in-memory implementation, will add JDBC later.
 */
@ApplicationScoped
@IfBuildProfile(anyOf = {"dev", "test"})  // Fixed profile
public class H2Tracker implements Tracker {

    private static final Logger LOG = Logger.getLogger(H2Tracker.class);

    // Temporary in-memory implementation until we add H2 JDBC
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

        LOG.debugf("Upserted file: %s (status: %s)", record.fileId(), record.status());
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
                status == FileStatus.COPIED ? java.time.Instant.now() : existing.copiedAt(),
                existing.attempts() + (status == FileStatus.FAILED ? 1 : 0)
        );

        filesById.put(fileId, updated);
        LOG.debugf("Updated file %s status to %s", fileId, status);
    }

    private String buildIdentityKey(String feedId, String sourcePath, long mtimeEpochMs, long sizeBytes) {
        return feedId + "|" + sourcePath + "|" + mtimeEpochMs + "|" + sizeBytes;
    }
}