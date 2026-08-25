package com.ultralatency.matching.qualification;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/** Fixed configuration for the bounded Phase 10 characterization evidence unit. */
public record ReleaseCandidateCharacterizationConfiguration(
        Path packagedArtifact,
        Path outputDirectory,
        String gitSha,
        String baselineTag,
        int emptyWalSamples,
        int snapshotTailSamples,
        int liveResponseSamples,
        Duration pairedTrialDuration,
        Duration startupTimeout,
        Duration commandTimeout,
        Duration processTimeout,
        Duration managementInterval) {

    /** Required empty-WAL lifecycle samples. */
    public static final int FULL_EMPTY_WAL_SAMPLES = 30;
    /** Required Snapshot-tail lifecycle samples. */
    public static final int FULL_SNAPSHOT_TAIL_SAMPLES = 30;
    /** Required fixed duration for each management-overhead trial. */
    public static final Duration FULL_PAIRED_TRIAL_DURATION = Duration.ofMinutes(10);
    /** One STATUS request per second in the management trial. */
    public static final Duration FULL_MANAGEMENT_INTERVAL = Duration.ofSeconds(1);

    public ReleaseCandidateCharacterizationConfiguration {
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        gitSha = requireText(gitSha, "gitSha");
        baselineTag = requireText(baselineTag, "baselineTag");
        if (packagedArtifact != null && !packagedArtifact.toString().endsWith(".jar")) {
            throw new IllegalArgumentException("packagedArtifact must be a JAR");
        }
        if (emptyWalSamples <= 0 || snapshotTailSamples <= 0 || liveResponseSamples <= 0) {
            throw new IllegalArgumentException("characterization sample counts must be positive");
        }
        pairedTrialDuration = requirePositive(pairedTrialDuration, "pairedTrialDuration");
        startupTimeout = requireTimeout(startupTimeout, "startupTimeout");
        commandTimeout = requireTimeout(commandTimeout, "commandTimeout");
        processTimeout = requireTimeout(processTimeout, "processTimeout");
        managementInterval = requirePositive(managementInterval, "managementInterval");
        outputDirectory = outputDirectory.toAbsolutePath().normalize();
    }

    /** Returns the approved 30/30/10-minute production characterization shape. */
    public static ReleaseCandidateCharacterizationConfiguration full(
            final Path packagedArtifact,
            final Path outputDirectory,
            final String gitSha,
            final String baselineTag) {
        return new ReleaseCandidateCharacterizationConfiguration(
                packagedArtifact,
                outputDirectory,
                gitSha,
                baselineTag,
                FULL_EMPTY_WAL_SAMPLES,
                FULL_SNAPSHOT_TAIL_SAMPLES,
                256,
                FULL_PAIRED_TRIAL_DURATION,
                Duration.ofSeconds(30),
                Duration.ofSeconds(5),
                Duration.ofSeconds(30),
                FULL_MANAGEMENT_INTERVAL);
    }

    /** Short deterministic configuration used by unit/integration tests only. */
    public static ReleaseCandidateCharacterizationConfiguration test(
            final Path packagedArtifact,
            final Path outputDirectory) {
        return new ReleaseCandidateCharacterizationConfiguration(
                packagedArtifact,
                outputDirectory,
                "test-git-sha",
                "test-baseline",
                1,
                1,
                8,
                Duration.ofMillis(100),
                Duration.ofSeconds(15),
                Duration.ofSeconds(5),
                Duration.ofSeconds(15),
                Duration.ofMillis(50));
    }

    private static Duration requirePositive(final Duration value, final String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static Duration requireTimeout(final Duration value, final String name) {
        requirePositive(value, name);
        if (value.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException(name + " must be no longer than five minutes");
        }
        return value;
    }

    private static String requireText(final String value, final String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
