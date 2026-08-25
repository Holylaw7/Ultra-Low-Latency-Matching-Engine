package com.ultralatency.matching.qualification;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/** Immutable result of one RC_ASSEMBLED_RUNTIME_V1 Full evidence unit. */
public record ReleaseCandidateAssembledFullRun(
        Path artifactDirectory,
        Path manifestPath,
        Path artifactHashesPath,
        String manifestSha256,
        boolean fullCriteriaPassed,
        Duration elapsed,
        long acceptedCommands) {

    public ReleaseCandidateAssembledFullRun {
        Objects.requireNonNull(artifactDirectory, "artifactDirectory");
        Objects.requireNonNull(manifestPath, "manifestPath");
        Objects.requireNonNull(artifactHashesPath, "artifactHashesPath");
        if (manifestSha256 == null || !manifestSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("manifestSha256 must be lowercase SHA-256");
        }
        Objects.requireNonNull(elapsed, "elapsed");
        if (elapsed.isNegative() || acceptedCommands < 0) {
            throw new IllegalArgumentException("invalid Full run result");
        }
    }
}

