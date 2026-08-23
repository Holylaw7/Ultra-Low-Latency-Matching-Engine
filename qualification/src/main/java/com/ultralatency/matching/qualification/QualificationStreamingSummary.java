package com.ultralatency.matching.qualification;

import java.util.Objects;

/** Bounded streaming evidence produced by one public-boundary qualification run. */
public record QualificationStreamingSummary(
        String commandDigestHex,
        String transcriptDigestHex,
        String publicProbeDigestHex,
        long responseCount,
        long tradeCount,
        int retainedProbeCount) {

    /** Validates immutable streaming evidence. */
    public QualificationStreamingSummary {
        requireDigest(commandDigestHex, "commandDigestHex");
        requireDigest(transcriptDigestHex, "transcriptDigestHex");
        requireDigest(publicProbeDigestHex, "publicProbeDigestHex");
        if (responseCount < 0 || tradeCount < 0 || retainedProbeCount < 0) {
            throw new IllegalArgumentException("streaming counts must not be negative");
        }
    }

    private static void requireDigest(final String value, final String name) {
        Objects.requireNonNull(value, name);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must be lowercase SHA-256");
        }
    }
}
