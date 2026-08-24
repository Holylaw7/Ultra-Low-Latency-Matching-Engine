package com.ultralatency.matching.qualification;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests campaign-level qualification without combining run timelines. */
class QualificationCampaignEvaluatorTest {

    @Test
    void twoIndependentRunsAndFiveCumulativeSamplesPass() {
        final QualificationCampaignResult result =
                QualificationCampaignEvaluator.evaluate(List.of(
                        run("run-1", QualificationFullConfiguration.full(
                                java.nio.file.Path.of("results-1")), 500L, 400L, 300L),
                        run("run-2", QualificationFullConfiguration.full(
                                java.nio.file.Path.of("results-2")), 500L, 400L)));

        assertTrue(result.passed());
        assertTrue(result.qualifyingRunCount() >= 2);
        assertTrue(result.cumulativeNaturalPostGcSamples() >= 5);
    }

    @Test
    void oneRunCannotPassCampaignEvenWithFiveSamples() {
        final QualificationCampaignResult result =
                QualificationCampaignEvaluator.evaluate(List.of(
                        run("run-1", QualificationFullConfiguration.full(
                                java.nio.file.Path.of("results-1")),
                                500L, 400L, 300L, 200L, 100L)));

        assertFalse(result.passed());
        assertTrue(result.failures().stream()
                .anyMatch(failure -> failure.contains("at least two qualifying runs")));
    }

    @Test
    void mismatchedEnvironmentCannotPassCampaign() {
        final QualificationCampaignRun first = run(
                "run-1", QualificationFullConfiguration.full(
                        java.nio.file.Path.of("results-1")), 500L, 400L, 300L);
        final QualificationCampaignRun second = new QualificationCampaignRun(
                "run-2",
                QualificationFullConfiguration.full(java.nio.file.Path.of("results-2")),
                QualificationFullConfiguration.FULL_MINIMUM_DURATION,
                QualificationFullConfiguration.FULL_MINIMUM_COMMANDS,
                true,
                true,
                true,
                evidence(500L, 400L),
                "v0.7.0-engineering-baseline",
                Map.of("java.version", "different"));

        final QualificationCampaignResult result =
                QualificationCampaignEvaluator.evaluate(List.of(first, second));

        assertFalse(result.passed());
        assertTrue(result.failures().stream()
                .anyMatch(failure -> failure.contains("environment differs")));
    }

    @Test
    void samplesCannotBeBorrowedFromASecondRunThatFailsItsPerRunMinimum() {
        final QualificationCampaignResult result =
                QualificationCampaignEvaluator.evaluate(List.of(
                        run("run-1", QualificationFullConfiguration.full(
                                java.nio.file.Path.of("results-1")), 500L, 400L, 300L, 200L),
                        run("run-2", QualificationFullConfiguration.full(
                                java.nio.file.Path.of("results-2")), 500L)));

        assertFalse(result.passed());
        assertTrue(result.cumulativeNaturalPostGcSamples() >= 5);
        assertTrue(result.failures().stream()
                .anyMatch(failure -> failure.contains("minimum per-run natural")));
    }

    private static QualificationCampaignRun run(
            final String runId,
            final QualificationFullConfiguration configuration,
            final long... samples) {
        return new QualificationCampaignRun(
                runId,
                configuration,
                QualificationFullConfiguration.FULL_MINIMUM_DURATION,
                QualificationFullConfiguration.FULL_MINIMUM_COMMANDS,
                true,
                true,
                true,
                evidence(samples),
                "v0.7.0-engineering-baseline",
                Map.of("java.version", "21", "gc.collectors", "G1 Young Generation"));
    }

    private static QualificationResourceEvidence evidence(final long... heapMiB) {
        final Instant start = Instant.parse("2026-08-23T00:00:00Z");
        final List<QualificationResourceSample> samples = java.util.stream.LongStream
                .range(0, heapMiB.length)
                .mapToObj(index -> new QualificationResourceSample(
                        start.plusSeconds(index), 10, 10, index + 1, index,
                        heapMiB[(int) index] * 1024L * 1024L,
                        heapMiB[(int) index] * 1024L * 1024L))
                .toList();
        return new QualificationResourceEvidence(
                samples,
                QualificationHeapGuard.naturalPostGcSamples(samples).stream()
                        .map(QualificationResourceSample::naturalPostGcHeapBytes).toList(),
                10, 10, List.of(), List.of(), true, true,
                QualificationHeapGuard.passes(samples, 2));
    }
}
