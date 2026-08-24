package com.ultralatency.matching.qualification;

import java.util.Objects;

/** Immutable evidence for one independent TASK-038 child-process cycle. */
public record QualificationRestartCycle(
        int cycleNumber,
        QualificationRestartMode mode,
        int firstCommandIndex,
        int commandCount,
        long acknowledgedCommands,
        int processExitCode,
        boolean acknowledgedBoundary,
        boolean convergencePassed,
        long walEndSequence,
        String walCommandDigestHex,
        String checkpointDigestHex,
        String transcriptDigestHex,
        String artifactSha256) {

    /** Creates a validated cycle evidence value. */
    public QualificationRestartCycle {
        if (cycleNumber <= 0 || firstCommandIndex < 0 || commandCount <= 0
                || acknowledgedCommands < 0 || acknowledgedCommands > commandCount
                || walEndSequence < 0) {
            throw new IllegalArgumentException("invalid restart cycle counts or sequence");
        }
        Objects.requireNonNull(mode, "mode");
        requireDigest(walCommandDigestHex, "walCommandDigestHex");
        requireDigest(checkpointDigestHex, "checkpointDigestHex");
        requireDigest(transcriptDigestHex, "transcriptDigestHex");
        requireDigest(artifactSha256, "artifactSha256");
        if (convergencePassed && !acknowledgedBoundary) {
            throw new IllegalArgumentException("convergence requires an acknowledged boundary");
        }
    }

    private static void requireDigest(final String value, final String name) {
        Objects.requireNonNull(value, name);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must be lowercase SHA-256");
        }
    }
}
