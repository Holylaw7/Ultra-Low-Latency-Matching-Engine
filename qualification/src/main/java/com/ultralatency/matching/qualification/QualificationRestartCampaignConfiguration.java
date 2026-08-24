package com.ultralatency.matching.qualification;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/** Immutable configuration for one TASK-038 restart/termination campaign. */
public record QualificationRestartCampaignConfiguration(
        QualificationConfiguration workloadConfiguration,
        int gracefulRestartCycles,
        int forcedTerminationCycles,
        Duration startupTimeout,
        Duration processTimeout) {

    /** Approved Full campaign graceful restart count. */
    public static final int FULL_GRACEFUL_RESTART_CYCLES = 20;

    /** Approved Full campaign forced termination count. */
    public static final int FULL_FORCED_TERMINATION_CYCLES = 10;

    /** Creates a validated restart campaign configuration. */
    public QualificationRestartCampaignConfiguration {
        Objects.requireNonNull(workloadConfiguration, "workloadConfiguration");
        if (gracefulRestartCycles < 0 || forcedTerminationCycles < 0
                || gracefulRestartCycles + forcedTerminationCycles <= 0) {
            throw new IllegalArgumentException("campaign cycle counts must be positive");
        }
        if (gracefulRestartCycles + forcedTerminationCycles
                > workloadConfiguration.commandCount()) {
            throw new IllegalArgumentException("each campaign cycle requires a command boundary");
        }
        startupTimeout = requireTimeout(startupTimeout, "startupTimeout");
        processTimeout = requireTimeout(processTimeout, "processTimeout");
    }

    /** Returns the approved Full campaign shape. */
    public static QualificationRestartCampaignConfiguration full(
            final QualificationConfiguration workloadConfiguration) {
        return new QualificationRestartCampaignConfiguration(
                workloadConfiguration,
                FULL_GRACEFUL_RESTART_CYCLES,
                FULL_FORCED_TERMINATION_CYCLES,
                Duration.ofSeconds(30),
                Duration.ofSeconds(30));
    }

    /** Returns a bounded deterministic configuration for focused tests. */
    public static QualificationRestartCampaignConfiguration test(final Path outputDirectory) {
        return new QualificationRestartCampaignConfiguration(
                new QualificationConfiguration(
                        QualificationProfile.CROSSING_MULTI_MATCH,
                        20260824L,
                        16,
                        Duration.ofSeconds(5),
                        outputDirectory),
                2,
                2,
                Duration.ofSeconds(15),
                Duration.ofSeconds(15));
    }

    /** Returns the total number of child-process lifecycle cycles. */
    public int totalCycles() {
        return gracefulRestartCycles + forcedTerminationCycles;
    }

    private static Duration requireTimeout(final Duration value, final String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()
                || value.compareTo(QualificationConfiguration.MAX_COMMAND_TIMEOUT) > 0) {
            throw new IllegalArgumentException(name + " must be positive and bounded");
        }
        return value;
    }
}
