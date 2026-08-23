package com.ultralatency.matching.qualification;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable result contract populated by later qualification tasks.
 *
 * @param success whether all applicable qualification assertions passed
 * @param acceptedCommands accepted command count
 * @param responseCount response count observed by the client
 * @param tradeCount trade count observed by the public result stream
 * @param checkpointDigestHex final checkpoint digest
 * @param transcriptDigestHex ordered result transcript digest
 * @param publicProbeDigestHex final public probe digest
 * @param measurements bounded named measurements
 */
public record QualificationResult(
        boolean success,
        long acceptedCommands,
        long responseCount,
        long tradeCount,
        String checkpointDigestHex,
        String transcriptDigestHex,
        String publicProbeDigestHex,
        Map<String, String> measurements) {

    /** Creates a validated immutable result contract. */
    public QualificationResult {
        if (acceptedCommands < 0 || responseCount < 0 || tradeCount < 0) {
            throw new IllegalArgumentException("qualification counts must be non-negative");
        }
        requireDigest(checkpointDigestHex, "checkpointDigestHex");
        requireDigest(transcriptDigestHex, "transcriptDigestHex");
        requireDigest(publicProbeDigestHex, "publicProbeDigestHex");
        measurements = Map.copyOf(Objects.requireNonNull(measurements, "measurements"));
    }

    private static void requireDigest(final String value, final String name) {
        Objects.requireNonNull(value, name);
        if (value.length() != 64 || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must be a lowercase SHA-256 value");
        }
    }
}
