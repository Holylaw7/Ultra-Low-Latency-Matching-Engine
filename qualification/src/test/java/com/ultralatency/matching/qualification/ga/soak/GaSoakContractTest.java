package com.ultralatency.matching.qualification.ga.soak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Tests the frozen G6 matrix, ordinal windows and conjunction predicates. */
class GaSoakContractTest {

    @Test
    void quickAndFormalMatricesUseSeparateFrozenCounts() {
        final GaSoakMatrix quick = GaSoakMatrix.quick();
        final GaSoakMatrix stageA = GaSoakMatrix.stageA();
        final GaSoakMatrix stageB = GaSoakMatrix.stageB();

        assertTrue(quick.isApprovedQuick());
        assertFalse(quick.isApprovedFormal());
        assertEquals(200, quick.offeredRatePerSecond());
        assertEquals(10_000L, quick.acceptedFloor());
        assertEquals(Duration.ofSeconds(60), quick.duration());
        assertTrue(stageA.isApprovedFormal());
        assertTrue(stageB.isApprovedFormal());
        assertEquals(1_440_000L, stageA.acceptedFloor());
        assertEquals(4_320_000L, stageB.acceptedFloor());
        assertFalse(stageA.version().equals(stageB.version()));
    }

    @Test
    void unapprovedQuickConfigurationCannotBecomeReadinessEvidence() {
        final GaSoakMatrix altered = new GaSoakMatrix(
                GaSoakMatrix.QUICK_VERSION,
                GaSoakMatrix.APPROVED_PROFILE,
                GaSoakMatrix.APPROVED_SEED,
                201,
                GaSoakMatrix.QUICK_DURATION,
                GaSoakMatrix.QUICK_ACCEPTED_FLOOR,
                GaSoakMatrix.SAMPLE_RATE_HZ,
                GaSoakMatrix.Stage.QUICK);
        assertFalse(altered.isApprovedQuick());
    }

    @Test
    void durationAndCountAreAnAllRunConjunction() {
        final long required = Duration.ofHours(2).toNanos();
        assertTrue(GaSoakEvaluator.durationAndCountPasses(required, required,
                GaSoakMatrix.STAGE_A_ACCEPTED_FLOOR, GaSoakMatrix.STAGE_A_ACCEPTED_FLOOR));
        assertFalse(GaSoakEvaluator.durationAndCountPasses(required - 1, required,
                GaSoakMatrix.STAGE_A_ACCEPTED_FLOOR, GaSoakMatrix.STAGE_A_ACCEPTED_FLOOR));
        assertFalse(GaSoakEvaluator.durationAndCountPasses(required, required,
                GaSoakMatrix.STAGE_A_ACCEPTED_FLOOR - 1, GaSoakMatrix.STAGE_A_ACCEPTED_FLOOR));
        assertFalse(GaSoakEvaluator.durationAndCountPasses(required - 1, required,
                GaSoakMatrix.STAGE_A_ACCEPTED_FLOOR - 1, GaSoakMatrix.STAGE_A_ACCEPTED_FLOOR));
    }

    @Test
    void p99DriftUsesExactInclusiveTwentyPercentBoundary() {
        assertTrue(GaSoakEvaluator.p99DriftPasses(100L, 80L));
        assertTrue(GaSoakEvaluator.p99DriftPasses(100L, 100L));
        assertTrue(GaSoakEvaluator.p99DriftPasses(100L, 120L));
        assertFalse(GaSoakEvaluator.p99DriftPasses(100L, 121L));
        assertTrue(GaSoakEvaluator.p99DriftPasses(0L, 0L));
        assertFalse(GaSoakEvaluator.p99DriftPasses(0L, 1L));
    }

    @Test
    void latencyWindowsBindToAcceptedOrdinalsAndNearestRank() {
        final long[] first = new long[GaLatencyWindow.COMPARISON_WINDOW_SAMPLES];
        java.util.Arrays.fill(first, 1L);
        java.util.Arrays.fill(first, 118_799, first.length, 2L);
        final GaLatencyWindow window = GaLatencyWindow.first(first);

        assertEquals(60_001L, window.firstAcceptedOrdinal());
        assertEquals(180_000L, window.lastAcceptedOrdinal());
        assertEquals(2L, window.p99Nanos());
        assertTrue(window.excludesWarmup());
        assertThrows(IllegalArgumentException.class,
                () -> GaLatencyWindow.first(new long[1]));
        assertThrows(IllegalArgumentException.class,
                () -> GaLatencyWindow.finalWindow(119_999L, first));
    }

    @Test
    void formalExecutionEntryPointRejectsFutureStages() {
        assertThrows(IllegalArgumentException.class,
                () -> new GaSoakRunner().run(GaSoakMatrix.stageA(), null));
    }
}
