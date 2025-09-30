package com.lbg.markets.surveillance.relay.domain;

import java.util.List;
import java.util.Map;

/**
 * Represents a logical feed configuration - where to pull from and basic rules.
 */
public record Feed(
        String id,
        String sourceUri,
        List<String> includePatterns,
        List<String> excludePatterns,
        String destinationPrefix,
        boolean active,
        Map<String, Object> metadata
) {
    public Feed {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Feed id cannot be blank");
        }
        if (sourceUri == null || sourceUri.isBlank()) {
            throw new IllegalArgumentException("Feed sourceUri cannot be blank");
        }
        includePatterns = includePatterns != null ? List.copyOf(includePatterns) : List.of();
        excludePatterns = excludePatterns != null ? List.copyOf(excludePatterns) : List.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }
}