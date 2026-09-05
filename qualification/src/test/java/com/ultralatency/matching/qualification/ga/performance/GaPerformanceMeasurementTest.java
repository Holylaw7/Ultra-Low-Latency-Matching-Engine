package com.ultralatency.matching.qualification.ga.performance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Verifies that formal latency samples are separate from the bounded drain population. */
class GaPerformanceMeasurementTest {

    @Test
    void postMeasurementResponseRemainsAccountedWithoutChangingLatencyPopulation() {
        final GaPerformanceMeasurement measurement = new GaPerformanceMeasurement(
                3, 3, 2, 1, 0, 0, true);

        assertTrue(measurement.complete());
        assertTrue(measurement.boundaryComplete());
        assertEqualsOutsidePopulation(measurement);
    }

    @Test
    void unfinishedRequestKeepsBoundaryIncompleteEvenWhenRetainedSamplesAreClosed() {
        final GaPerformanceMeasurement measurement = new GaPerformanceMeasurement(
                3, 2, 2, 0, 0, 1, true);

        assertTrue(measurement.complete());
        assertFalse(measurement.boundaryComplete());
    }

    @Test
    void crossBoundaryResponseIsRetainedForIndependentRecomputation() {
        final GaPerformanceMeasurement measurement = new GaPerformanceMeasurement(
                2, 2, 1, 0, 1, 0, true);

        assertTrue(measurement.complete());
        assertTrue(measurement.boundaryComplete());
        assertTrue(measurement.incompleteCommands() > 0);
    }

    private static void assertEqualsOutsidePopulation(final GaPerformanceMeasurement measurement) {
        assertTrue(measurement.postMeasurementDrainCommands() > 0);
        assertTrue(measurement.completedCommands() < measurement.offeredCommands());
    }
}
