package com.lbg.markets.surveillance.relay.tracker;

import com.lbg.markets.surveillance.relay.domain.FileRecord;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
@IfBuildProfile("prod")
public class H2Tracker implements Tracker {
    @Override
    public void upsertFile(FileRecord record) {

    }

    @Override
    public Optional<FileRecord> findByIdentity(String feedId, String sourcePath, long mtimeEpochMs, long sizeBytes) {
        return Optional.empty();
    }

    @Override
    public void updateStatus(String fileId, FileRecord.FileStatus status, String gcsUri) {

    }

    @Override
    public boolean shouldSkip(String feedId, String sourcePath, long mtimeEpochMs, long sizeBytes) {
        return Tracker.super.shouldSkip(feedId, sourcePath, mtimeEpochMs, sizeBytes);
    }
}
