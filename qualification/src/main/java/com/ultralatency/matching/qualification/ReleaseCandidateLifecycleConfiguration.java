package com.ultralatency.matching.qualification;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/** Bounded pre-campaign lifecycle matrix configuration for the assembled runtime. */
public record ReleaseCandidateLifecycleConfiguration(
        Path packagedArtifact,
        Path outputDirectory,
        int emptyPureWalStarts,
        int snapshotTailStarts,
        int forcedTerminationCycles,
        Duration startupTimeout,
        Duration commandTimeout,
        Duration processTimeout) {

    /** Required empty/PURE_WAL lifecycle samples before the Full Campaign. */
    public static final int FULL_EMPTY_STARTS = 10;

    /** Required Snapshot-plus-tail lifecycle samples before the Full Campaign. */
    public static final int FULL_SNAPSHOT_TAIL_STARTS = 10;

    /** Required approved post-response forced terminations before the Full Campaign. */
    public static final int FULL_FORCED_TERMINATION_CYCLES = 10;

    /** Creates a validated lifecycle matrix configuration. */
    public ReleaseCandidateLifecycleConfiguration {
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        if (outputDirectory.toString().isBlank()) {
            throw new IllegalArgumentException("outputDirectory must not be blank");
        }
        outputDirectory = outputDirectory.toAbsolutePath().normalize();
        if (emptyPureWalStarts < 0 || snapshotTailStarts < 0 || forcedTerminationCycles < 0
                || emptyPureWalStarts + snapshotTailStarts + forcedTerminationCycles == 0) {
            throw new IllegalArgumentException("lifecycle cycle counts must be non-negative and non-zero");
        }
        startupTimeout = requireTimeout(startupTimeout, "startupTimeout");
        commandTimeout = requireTimeout(commandTimeout, "commandTimeout");
        processTimeout = requireTimeout(processTimeout, "processTimeout");
    }

    /** Returns the full pre-campaign lifecycle matrix shape. */
    public static ReleaseCandidateLifecycleConfiguration full(final Path outputDirectory) {
        return full(null, outputDirectory);
    }

    /** Returns the full matrix shape for an explicitly packaged child artifact. */
    public static ReleaseCandidateLifecycleConfiguration full(
            final Path packagedArtifact,
            final Path outputDirectory) {
        return new ReleaseCandidateLifecycleConfiguration(
                packagedArtifact,
                outputDirectory,
                FULL_EMPTY_STARTS,
                FULL_SNAPSHOT_TAIL_STARTS,
                FULL_FORCED_TERMINATION_CYCLES,
                Duration.ofSeconds(30),
                Duration.ofSeconds(5),
                Duration.ofSeconds(30));
    }

    /** Returns one bounded deterministic lifecycle sample of each kind for CI. */
    public static ReleaseCandidateLifecycleConfiguration test(final Path outputDirectory) {
        return new ReleaseCandidateLifecycleConfiguration(
                null,
                outputDirectory,
                1,
                1,
                1,
                Duration.ofSeconds(15),
                Duration.ofSeconds(5),
                Duration.ofSeconds(15));
    }

    private static Duration requireTimeout(final Duration value, final String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative() || value.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException(name + " must be positive and bounded");
        }
        return value;
    }
}
