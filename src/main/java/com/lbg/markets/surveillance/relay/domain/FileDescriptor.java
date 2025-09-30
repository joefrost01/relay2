package com.lbg.markets.surveillance.relay.domain;

/**
 * Describes a file discovered from a source, before tracking.
 */
public record FileDescriptor(
        String sourcePath,
        long sizeBytes,
        long mtimeEpochMs
) {
    public FileDescriptor {
        if (sourcePath == null || sourcePath.isBlank()) {
            throw new IllegalArgumentException("sourcePath cannot be blank");
        }
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes cannot be negative");
        }
    }
}