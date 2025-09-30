package com.lbg.markets.surveillance.relay.domain;

/**
 * Result of a single file transfer operation.
 */
public record TransferResult(
        String fileId,
        String sourcePath,
        String destPath,
        long bytesTransferred,
        Status status,
        String errorMessage
) {
    public enum Status {
        SUCCESS,
        SKIPPED,
        FAILED
    }

    public static TransferResult success(String fileId, String sourcePath, String destPath, long bytes) {
        return new TransferResult(fileId, sourcePath, destPath, bytes, Status.SUCCESS, null);
    }

    public static TransferResult skipped(String fileId, String sourcePath, String reason) {
        return new TransferResult(fileId, sourcePath, null, 0, Status.SKIPPED, reason);
    }

    public static TransferResult failed(String fileId, String sourcePath, String error) {
        return new TransferResult(fileId, sourcePath, null, 0, Status.FAILED, error);
    }
}