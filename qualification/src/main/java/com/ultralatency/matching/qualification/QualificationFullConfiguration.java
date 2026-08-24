package com.ultralatency.matching.qualification;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Immutable configuration for the Phase 9 full soak/resource qualification.
 *
 * <p>Only {@link QualificationLane#FULL} configurations can claim full-lane evidence. The
 * short test lane exists to exercise the harness without pretending to satisfy the 60-minute
 * and one-million-command acceptance criteria.</p>
 *
 * @param lane explicit qualification lane
 * @param profile deterministic workload profile
 * @param seed deterministic workload seed
 * @param commandCount commands required by the run
 * @param minimumDuration minimum elapsed duration for full evidence
 * @param commandTimeout per-command socket timeout
 * @param sampleInterval resource sampling interval
 * @param minimumPostGcSamples minimum natural post-GC samples required by one run
 * @param outputDirectory ignored/raw artifact root
 */
public record QualificationFullConfiguration(
        QualificationLane lane,
        QualificationProfile profile,
        long seed,
        int commandCount,
        Duration minimumDuration,
        Duration commandTimeout,
        Duration sampleInterval,
        int minimumPostGcSamples,
        Path outputDirectory) {

    /** Full-lane minimum duration required by ADR-0017 D5. */
    public static final Duration FULL_MINIMUM_DURATION = Duration.ofMinutes(60);

    /** Full-lane command count required by ADR-0017 D5. */
    public static final int FULL_MINIMUM_COMMANDS = 1_000_000;

    /** Minimum natural post-GC samples required by one qualifying run. */
    public static final int FULL_MINIMUM_POST_GC_SAMPLES =
            QualificationHeapGuard.MINIMUM_PER_RUN_SAMPLES;

    /** Minimum natural samples required across a qualifying campaign. */
    public static final int CAMPAIGN_MINIMUM_POST_GC_SAMPLES = 5;

    /** Minimum independently qualifying runs required by a campaign. */
    public static final int CAMPAIGN_MINIMUM_RUNS = 2;

    /** Creates and validates a lane-specific configuration. */
    public QualificationFullConfiguration {
        Objects.requireNonNull(lane, "lane");
        Objects.requireNonNull(profile, "profile");
        if (seed < 0) {
            throw new IllegalArgumentException("seed must be non-negative");
        }
        if (commandCount <= 0 || commandCount > QualificationConfiguration.MAX_COMMAND_COUNT) {
            throw new IllegalArgumentException("commandCount is outside qualification bounds");
        }
        Objects.requireNonNull(minimumDuration, "minimumDuration");
        if (minimumDuration.isNegative()) {
            throw new IllegalArgumentException("minimumDuration must not be negative");
        }
        Objects.requireNonNull(commandTimeout, "commandTimeout");
        if (commandTimeout.isZero() || commandTimeout.isNegative()
                || commandTimeout.compareTo(QualificationConfiguration.MAX_COMMAND_TIMEOUT) > 0) {
            throw new IllegalArgumentException("commandTimeout is outside qualification bounds");
        }
        Objects.requireNonNull(sampleInterval, "sampleInterval");
        if (sampleInterval.isZero() || sampleInterval.isNegative()) {
            throw new IllegalArgumentException("sampleInterval must be positive");
        }
        if (minimumPostGcSamples < 0) {
            throw new IllegalArgumentException("minimumPostGcSamples must not be negative");
        }
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        if (outputDirectory.toString().isBlank()) {
            throw new IllegalArgumentException("outputDirectory must not be blank");
        }
        outputDirectory = outputDirectory.toAbsolutePath().normalize();
        if (lane == QualificationLane.FULL
                && (commandCount < FULL_MINIMUM_COMMANDS
                || minimumDuration.compareTo(FULL_MINIMUM_DURATION) < 0
                || minimumPostGcSamples < FULL_MINIMUM_POST_GC_SAMPLES)) {
            throw new IllegalArgumentException(
                    "FULL lane requires 60 minutes, 1,000,000 commands and two post-GC samples");
        }
    }

    /** Returns the immutable workload configuration consumed by the public-boundary runner. */
    public QualificationConfiguration workloadConfiguration() {
        return new QualificationConfiguration(
                profile, seed, commandCount, commandTimeout, outputDirectory);
    }

    /** Returns the approved full qualification configuration. */
    public static QualificationFullConfiguration full(final Path outputDirectory) {
        return new QualificationFullConfiguration(
                QualificationLane.FULL,
                QualificationProfile.LIFECYCLE_MIX,
                20260823L,
                FULL_MINIMUM_COMMANDS,
                FULL_MINIMUM_DURATION,
                Duration.ofSeconds(5),
                Duration.ofSeconds(5),
                FULL_MINIMUM_POST_GC_SAMPLES,
                outputDirectory);
    }

    /** Returns the separately versioned bounded-state Full lane for a future approved campaign. */
    public static QualificationFullConfiguration memorySteadyStateFull(
            final Path outputDirectory) {
        return new QualificationFullConfiguration(
                QualificationLane.FULL,
                QualificationProfile.MEMORY_STEADY_STATE_V1,
                20260823L,
                FULL_MINIMUM_COMMANDS,
                FULL_MINIMUM_DURATION,
                Duration.ofSeconds(5),
                Duration.ofSeconds(5),
                FULL_MINIMUM_POST_GC_SAMPLES,
                outputDirectory);
    }

    /** Returns a short harness-only configuration; it is not full qualification evidence. */
    public static QualificationFullConfiguration test(final Path outputDirectory) {
        return new QualificationFullConfiguration(
                QualificationLane.TEST,
                QualificationProfile.CROSSING_MULTI_MATCH,
                20260823L,
                12,
                Duration.ofMillis(1),
                Duration.ofSeconds(5),
                Duration.ofMillis(10),
                0,
                outputDirectory);
    }

    /** Returns a short public-path bounded-state memory qualification configuration. */
    public static QualificationFullConfiguration memorySteadyStateTest(
            final Path outputDirectory) {
        return new QualificationFullConfiguration(
                QualificationLane.TEST,
                QualificationProfile.MEMORY_STEADY_STATE_V1,
                20260823L,
                32,
                Duration.ofMillis(1),
                Duration.ofSeconds(5),
                Duration.ofMillis(10),
                0,
                outputDirectory);
    }
}
