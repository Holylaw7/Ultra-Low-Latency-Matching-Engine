package com.ultralatency.matching.qualification.ga.performance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
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
        final GaPerformanceEvaluator.Evaluation evaluation =
                GaPerformanceEvaluator.evaluateRun(observation);
        assertFalse(evaluation.passed());
        assertEquals("B1", evaluation.failureCode());
    }

    @Test
    void throughputUsesAcceptedPopulationRatherThanInWindowCompletions() {
        final GaPerformanceObservation observation = new GaPerformanceObservation(
                Integer.MAX_VALUE, 300_001L, 1L, 600_000_000_000L,
                new long[]{1L}, new long[0], new long[0], 0.0d, 0.0d, 0L, 0L,
                0, 0, 0, true, true, true, true, true,
                new GaPerformanceMeasurement(300_001L, 300_001L, 1L, 0L, 0L,
                        300_000L, true), true, "NONE", 0L, 0, true);

        assertEquals(500.00166666666667d, GaPerformanceEvaluator.throughput(observation),
                0.000000001d);
    }

    @Test
    void unhealthyCandidateCannotPassHealthyPerformanceSlo() {
        final GaPerformanceObservation observation = new GaPerformanceObservation(
                8, 8L, 8L, 8_000_000L,
                new long[]{1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L},
                new long[0], new long[0], 1000.0d, 1000.0d, 0L, 0L,
                0, 0, 0, true, true, true, true, true,
                new GaPerformanceMeasurement(8L, 8L, 8L, 0L, 0L, 0L, true),
                false, "FAILED", 1L, 0, true);

        final GaPerformanceEvaluator.Evaluation evaluation =
                GaPerformanceEvaluator.evaluateRun(observation);
        assertFalse(evaluation.passed());
        assertEquals("B1", evaluation.failureCode());
    }

    @Test
    void trustworthyLatencySloFailureIsCandidateB1() {
        final GaPerformanceObservation observation = observation(
                8, 8_000_000L,
                new long[]{6_000_000L, 6_000_000L, 6_000_000L, 6_000_000L,
                    6_000_000L, 6_000_000L, 6_000_000L, 6_000_000L});
        final GaPerformanceEvaluator.Evaluation evaluation =
                GaPerformanceEvaluator.evaluateRun(observation);
        assertFalse(evaluation.passed());
        assertEquals("B1", evaluation.failureCode());
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
    void allRunConjunctionAcceptsEveryRunWhenThreeRunsPass() {
        final GaPerformanceMatrix matrix = multiRunMatrix();
        final GaPerformanceObservation passing = observation(
                8, 8_000_000L, new long[]{1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L});

        final GaPerformanceEvaluator.Evaluation evaluation =
                GaPerformanceEvaluator.evaluateCampaign(matrix, List.of(passing, passing, passing));

        assertTrue(evaluation.passed());
    }

    @Test
    void allRunConjunctionRejectsFailureAtEveryRunPosition() {
        final GaPerformanceMatrix matrix = multiRunMatrix();
        final GaPerformanceObservation passing = observation(
                8, 8_000_000L, new long[]{1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L});
        final GaPerformanceObservation failing = observation(
                8, 16_000_001L, new long[]{1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L});

        for (int failingIndex = 0; failingIndex < matrix.runCount(); failingIndex++) {
            final int currentFailingIndex = failingIndex;
            final List<GaPerformanceObservation> observations = List.of(
                    currentFailingIndex == 0 ? failing : passing,
                    currentFailingIndex == 1 ? failing : passing,
                    currentFailingIndex == 2 ? failing : passing);
            final GaPerformanceEvaluator.Evaluation evaluation =
                    GaPerformanceEvaluator.evaluateCampaign(matrix, observations);

            assertFalse(evaluation.passed(), "failure at run " + (currentFailingIndex + 1));
            assertTrue(evaluation.criteria().stream().anyMatch(criterion ->
                    criterion.id().equals("run." + (currentFailingIndex + 1) + ".result")
                            && !criterion.passed()));
        }
    }

    @Test
    void wrongRunCountFailsCampaignInsteadOfReplacingRuns() {
        final GaPerformanceEvaluator.Evaluation evaluation =
                GaPerformanceEvaluator.evaluateCampaign(GaPerformanceMatrix.approved(), List.of());
        assertFalse(evaluation.passed());
        assertFalse(evaluation.formalEligible());
    }

    private static GaPerformanceMatrix multiRunMatrix() {
        return new GaPerformanceMatrix(
                "ga-g4-performance-multi-run-test-v1",
                GaPerformanceMatrix.APPROVED_PROFILE,
                GaPerformanceMatrix.APPROVED_SEED,
                3,
                Duration.ofMillis(1),
                2,
                8);
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
