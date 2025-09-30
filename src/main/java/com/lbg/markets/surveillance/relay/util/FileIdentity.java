package com.lbg.markets.surveillance.relay.util;

import com.lbg.markets.surveillance.relay.domain.FileDescriptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Utility for generating stable file identities.
 * Identity is based on (feedId, sourcePath, mtime, size) to detect changes.
 */
public final class FileIdentity {

    private FileIdentity() {
        // Utility class
    }

    /**
     * Generate a stable file ID from feed and file descriptor.
     * Uses SHA-256 hash of the identity components.
     */
    public static String generateFileId(String feedId, FileDescriptor descriptor) {
        return generateFileId(feedId, descriptor.sourcePath(), descriptor.mtimeEpochMs(), descriptor.sizeBytes());
    }

    /**
     * Generate a stable file ID from individual components.
     */
    public static String generateFileId(String feedId, String sourcePath, long mtimeEpochMs, long sizeBytes) {
        String identity = feedId + "|" + sourcePath + "|" + mtimeEpochMs + "|" + sizeBytes;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(identity.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}