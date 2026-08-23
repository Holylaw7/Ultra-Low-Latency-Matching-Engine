package com.ultralatency.matching.qualification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
