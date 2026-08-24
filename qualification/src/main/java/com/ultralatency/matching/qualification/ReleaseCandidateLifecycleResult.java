package com.ultralatency.matching.qualification;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Immutable pre-campaign lifecycle evidence and artifact location. */
public record ReleaseCandidateLifecycleResult(
        boolean success,
        List<Cycle> cycles,
        Path artifactDirectory,
        String summarySha256) {

    /** Validates one immutable lifecycle result. */
    public ReleaseCandidateLifecycleResult {
        cycles = List.copyOf(Objects.requireNonNull(cycles, "cycles"));
        Objects.requireNonNull(artifactDirectory, "artifactDirectory");
        requireDigest(summarySha256, "summarySha256");
        if (cycles.isEmpty()) {
            throw new IllegalArgumentException("lifecycle evidence must contain a cycle");
        }
    }

    /** One lifecycle matrix cycle and its recovery/resource evidence. */
    public record Cycle(
            String scenario,
            int cycleNumber,
            boolean forcedTermination,
            int processExitCode,
            boolean ready,
            boolean commandRoundTrip,
            boolean recoveryConverged,
            boolean leaseReacquired,
            boolean temporaryFilesClear,
            String artifactSha256) {

        public Cycle {
            Objects.requireNonNull(scenario, "scenario");
            if (scenario.isBlank() || cycleNumber <= 0) {
                throw new IllegalArgumentException("invalid lifecycle cycle identity");
            }
            requireDigest(artifactSha256, "artifactSha256");
        }
    }

    private static void requireDigest(final String value, final String name) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must be lowercase SHA-256");
        }
    }
}
