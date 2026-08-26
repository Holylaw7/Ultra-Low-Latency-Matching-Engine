package com.ultralatency.matching.qualification.ga.correctness;

import java.util.Objects;

/** Immutable evidence for one live or offline recovery observation. */
public record GaCorrectnessObservation(
        String mode,
        int snapshotSequence,
        long acceptedCommands,
        long tradeCount,
        String walDigestHex,
        String checkpointDigestHex,
        String transcriptDigestHex,
        String publicProbeDigestHex) {

    /** Creates one validated observation. */
    public GaCorrectnessObservation {
        requireText(mode, "mode");
        if (snapshotSequence < 0 || acceptedCommands < 0 || tradeCount < 0) {
            throw new IllegalArgumentException("observation counters must not be negative");
        }
        requireDigest(walDigestHex, "walDigestHex");
        requireDigest(checkpointDigestHex, "checkpointDigestHex");
        requireDigest(transcriptDigestHex, "transcriptDigestHex");
        requireDigest(publicProbeDigestHex, "publicProbeDigestHex");
    }

    /** @return whether this observation represents a live public-boundary run */
    public boolean live() {
        return "LIVE".equals(mode);
    }

    /** @return whether this observation represents a PURE_WAL recovery */
    public boolean pureWal() {
        return "PURE_WAL".equals(mode);
    }

    /** @return whether this observation represents Snapshot-tail recovery */
    public boolean snapshotTail() {
        return "SNAPSHOT_THEN_WAL".equals(mode);
    }

    private static void requireText(final String value, final String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static void requireDigest(final String value, final String name) {
        Objects.requireNonNull(value, name);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must be lowercase SHA-256");
        }
    }
}
