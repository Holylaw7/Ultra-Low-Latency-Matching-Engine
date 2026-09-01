package com.ultralatency.matching.qualification.ga.performance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests deterministic G4 percentiles, threshold boundaries and all-run conjunction. */
class GaPerformanceEvaluatorTest {

    @Test
    void exactThroughputAndLatencyThresholdsAreAccepted() {
        final long[] samples = new long[100];
        java.util.Arrays.fill(samples, 0, 50, 2_500_000L);
        java.util.Arrays.fill(samples, 50, 99, 5_000_000L);
        samples[99] = 10_000_000L;
        final GaPerformanceObservation observation = observation(100, 200_000_000L, samples);
        final GaPerformanceEvaluator.Evaluation evaluation =
                GaPerformanceEvaluator.evaluateRun(observation);
        assertTrue(evaluation.passed());
    }

    @Test
    void throughputBelowThresholdFailsWithoutFiltering() {
        final GaPerformanceObservation observation = observation(
                4, 8_000_001L, new long[]{1L, 1L, 1L, 1L});
        assertFalse(GaPerformanceEvaluator.evaluateRun(observation).passed());
    }

    @Test
    void allRunConjunctionRejectsAFailingRun() {
        final GaPerformanceMatrix matrix = GaPerformanceMatrix.test();
        final GaPerformanceObservation passing = observation(
                8, 8_000_000L, new long[]{1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L});
        final GaPerformanceObservation failing = observation(
                8, 16_000_001L, new long[]{1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L});
        final GaPerformanceEvaluator.Evaluation evaluation =
                GaPerformanceEvaluator.evaluateCampaign(matrix, List.of(failing));
        assertFalse(evaluation.passed());
        assertFalse(GaPerformanceEvaluator.evaluateRun(failing).passed());
        assertTrue(GaPerformanceEvaluator.evaluateRun(passing).passed());
    }

    @Test
    void wrongRunCountFailsCampaignInsteadOfReplacingRuns() {
        final GaPerformanceEvaluator.Evaluation evaluation =
                GaPerformanceEvaluator.evaluateCampaign(GaPerformanceMatrix.approved(), List.of());
        assertFalse(evaluation.passed());
        assertFalse(evaluation.formalEligible());
    }

    private static GaPerformanceObservation observation(
            final int commandCount, final long elapsedNanos, final long[] samples) {
        return new GaPerformanceObservation(
                commandCount,
                commandCount,
                commandCount,
                elapsedNanos,
                samples,
                new long[]{1L, 1L},
                new long[]{1L, 1L},
                1000.0,
                1000.0,
                1L,
                1L,
                0,
                0,
                0,
                true,
                true,
                true,
                true,
                true);
    }
}
