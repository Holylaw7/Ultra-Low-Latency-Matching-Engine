package com.ultralatency.matching.qualification;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Immutable bounds for one qualification workload generation or run.
 *
 * @param profile deterministic workload profile
 * @param seed deterministic workload seed
 * @param commandCount number of commands to generate
 * @param commandTimeout maximum allowed command timeout
 * @param outputDirectory directory reserved for a later qualification runner
 */
public record QualificationConfiguration(
        QualificationProfile profile,
        long seed,
        int commandCount,
        Duration commandTimeout,
        Path outputDirectory) {

    /** Maximum command count permitted by the Phase 9 foundation contract. */
    public static final int MAX_COMMAND_COUNT = 1_000_000;

    /** Maximum command timeout accepted by the foundation contract. */
    public static final Duration MAX_COMMAND_TIMEOUT = Duration.ofMinutes(5);

    /** Creates a validated immutable configuration. */
    public QualificationConfiguration {
        Objects.requireNonNull(profile, "profile");
        if (seed < 0) {
            throw new IllegalArgumentException("seed must be non-negative");
        }
        if (commandCount <= 0 || commandCount > MAX_COMMAND_COUNT) {
            throw new IllegalArgumentException(
                    "commandCount must be between 1 and " + MAX_COMMAND_COUNT);
        }
        Objects.requireNonNull(commandTimeout, "commandTimeout");
        if (commandTimeout.isZero() || commandTimeout.isNegative()
                || commandTimeout.compareTo(MAX_COMMAND_TIMEOUT) > 0) {
            throw new IllegalArgumentException(
                    "commandTimeout must be positive and at most " + MAX_COMMAND_TIMEOUT);
        }
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        if (outputDirectory.toString().isBlank()) {
            throw new IllegalArgumentException("outputDirectory must not be blank");
        }
        outputDirectory = outputDirectory.toAbsolutePath().normalize();
    }

    /** Returns the default bounded CI configuration. */
    public static QualificationConfiguration quick(final QualificationProfile profile) {
        return new QualificationConfiguration(
                profile,
                20260823L,
                10_000,
                Duration.ofSeconds(5),
                Path.of("qualification-results"));
    }
}
