package com.ultralatency.matching.qualification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Tests qualification configuration bounds before any resource allocation. */
class QualificationConfigurationTest {

    @Test
    void quickConfigurationUsesTheApprovedDefaultSeedAndBound() {
        final QualificationConfiguration configuration =
                QualificationConfiguration.quick(QualificationProfile.LIFECYCLE_MIX);

        assertEquals(20260823L, configuration.seed());
        assertEquals(10_000, configuration.commandCount());
        assertEquals(Path.of("qualification-results").toAbsolutePath().normalize(),
                configuration.outputDirectory());
    }

    @Test
    void invalidBoundsAreRejectedBeforeGeneration() {
        assertThrows(IllegalArgumentException.class, () -> new QualificationConfiguration(
                QualificationProfile.LIFECYCLE_MIX, -1, 1,
                Duration.ofSeconds(1), Path.of("results")));
        assertThrows(IllegalArgumentException.class, () -> new QualificationConfiguration(
                QualificationProfile.LIFECYCLE_MIX, 1, 0,
                Duration.ofSeconds(1), Path.of("results")));
        assertThrows(IllegalArgumentException.class, () -> new QualificationConfiguration(
                QualificationProfile.LIFECYCLE_MIX, 1,
                QualificationConfiguration.MAX_COMMAND_COUNT + 1,
                Duration.ofSeconds(1), Path.of("results")));
        assertThrows(IllegalArgumentException.class, () -> new QualificationConfiguration(
                QualificationProfile.LIFECYCLE_MIX, 1, 1,
                Duration.ZERO, Path.of("results")));
        assertThrows(IllegalArgumentException.class, () -> new QualificationConfiguration(
                QualificationProfile.LIFECYCLE_MIX, 1, 1,
                Duration.ofMinutes(6), Path.of("results")));
    }

    @Test
    void fullQualificationConfigurationFreezesTheApprovedThresholds() {
        final QualificationFullConfiguration configuration =
                QualificationFullConfiguration.full(Path.of("qualification-results"));

        assertEquals(QualificationLane.FULL, configuration.lane());
        assertEquals(QualificationFullConfiguration.FULL_MINIMUM_COMMANDS,
                configuration.commandCount());
        assertEquals(QualificationFullConfiguration.FULL_MINIMUM_DURATION,
                configuration.minimumDuration());
        assertEquals(QualificationFullConfiguration.FULL_MINIMUM_POST_GC_SAMPLES,
                configuration.minimumPostGcSamples());
    }

    @Test
    void fullLaneRejectsCommandsOrDurationBelowTheQualificationGate() {
        assertThrows(IllegalArgumentException.class, () -> new QualificationFullConfiguration(
                QualificationLane.FULL,
                QualificationProfile.LIFECYCLE_MIX,
                20260823L,
                QualificationFullConfiguration.FULL_MINIMUM_COMMANDS - 1,
                QualificationFullConfiguration.FULL_MINIMUM_DURATION,
                Duration.ofSeconds(1),
                Duration.ofMillis(10),
                QualificationFullConfiguration.FULL_MINIMUM_POST_GC_SAMPLES,
                Path.of("results")));
        assertThrows(IllegalArgumentException.class, () -> new QualificationFullConfiguration(
                QualificationLane.FULL,
                QualificationProfile.LIFECYCLE_MIX,
                20260823L,
                QualificationFullConfiguration.FULL_MINIMUM_COMMANDS,
                QualificationFullConfiguration.FULL_MINIMUM_DURATION.minusSeconds(1),
                Duration.ofSeconds(1),
                Duration.ofMillis(10),
                QualificationFullConfiguration.FULL_MINIMUM_POST_GC_SAMPLES,
                Path.of("results")));
    }

    @Test
    void testLaneIsExplicitlyNotFullEvidence() {
        final QualificationFullConfiguration configuration =
                QualificationFullConfiguration.test(Path.of("results"));

        assertTrue(configuration.minimumDuration().compareTo(
                QualificationFullConfiguration.FULL_MINIMUM_DURATION) < 0);
        assertTrue(configuration.commandCount()
                < QualificationFullConfiguration.FULL_MINIMUM_COMMANDS);
    }

    @Test
    void memorySteadyStateFullLaneIsExplicitlyVersionedWithoutStartingARun() {
        final QualificationFullConfiguration configuration =
                QualificationFullConfiguration.memorySteadyStateFull(Path.of("results"));

        assertEquals(QualificationLane.FULL, configuration.lane());
        assertEquals(QualificationProfile.MEMORY_STEADY_STATE_V1, configuration.profile());
        assertEquals(QualificationFullConfiguration.FULL_MINIMUM_COMMANDS,
                configuration.commandCount());
        assertEquals(QualificationFullConfiguration.FULL_MINIMUM_DURATION,
                configuration.minimumDuration());
    }

    @Test
    void memorySteadyStateManifestMayDescribeAContinuousQualificationPrefix() {
        final QualificationConfiguration configuration = new QualificationConfiguration(
                QualificationProfile.MEMORY_STEADY_STATE_V1, 20260823L,
                QualificationConfiguration.MEMORY_STEADY_STATE_MAX_COMMAND_COUNT,
                Duration.ofSeconds(1), Path.of("results"));

        assertEquals(QualificationConfiguration.MEMORY_STEADY_STATE_MAX_COMMAND_COUNT,
                configuration.commandCount());
    }

    @Test
    void continuousMemoryManifestUsesThePersistedPrefixLength() {
        final QualificationConfiguration base = new QualificationConfiguration(
                QualificationProfile.MEMORY_STEADY_STATE_V1, 20260823L,
                QualificationFullConfiguration.FULL_MINIMUM_COMMANDS,
                Duration.ofSeconds(1), Path.of("results"));

        final QualificationConfiguration manifest = QualificationFullRunner
                .manifestConfiguration(base, QualificationFullConfiguration.FULL_MINIMUM_COMMANDS + 1);

        assertEquals(QualificationFullConfiguration.FULL_MINIMUM_COMMANDS + 1,
                manifest.commandCount());
        assertEquals(base.profile(), manifest.profile());
        assertEquals(base.seed(), manifest.seed());
    }
}
