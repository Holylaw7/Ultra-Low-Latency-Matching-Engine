package com.ultralatency.matching.qualification.ga.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.qualification.ga.soak.GaSoakMatrix;
import com.ultralatency.matching.qualification.ga.soak.GaSoakResourceSample;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests exact resource-window, baseline-zero and identity semantics. */
class GaResourceGuardsTest {

    private static final String PHYSICAL_ID = "physical-a";

    @Test
    void eachHardResourceMetricAcceptsExactTwentyPercentBoundary() {
        final List<GaSoakResourceSample> samples = samples(100L, 120L, 10L, 12L, 20L, 24L);

        assertTrue(GaResourceGuards.evaluate(GaResourceGuards.Metric.THREADS, samples,
                PHYSICAL_ID, GaSoakMatrix.Stage.STAGE_A).passed());
        assertTrue(GaResourceGuards.evaluate(GaResourceGuards.Metric.TRANSIENT_FILE_COUNT,
                samples, PHYSICAL_ID, GaSoakMatrix.Stage.STAGE_A).passed());
        assertTrue(GaResourceGuards.evaluate(GaResourceGuards.Metric.TRANSIENT_FILE_BYTES,
                samples, PHYSICAL_ID, GaSoakMatrix.Stage.STAGE_A).passed());
    }

    @Test
    void anyHardResourceMetricAboveTwentyPercentFailsB1() {
        assertFalse(GaResourceGuards.driftPasses(100L, 121L));
        assertEquals("B1", GaResourceGuards.evaluatePair(
                GaResourceGuards.Metric.THREADS, 100L, 121L).failureCode());
        assertEquals("B1", GaResourceGuards.evaluatePair(
                GaResourceGuards.Metric.TRANSIENT_FILE_COUNT, 10L, 13L).failureCode());
        assertEquals("B1", GaResourceGuards.evaluatePair(
                GaResourceGuards.Metric.TRANSIENT_FILE_BYTES, 20L, 25L).failureCode());
    }

    @Test
    void zeroBaselineRequiresZeroFinalValue() {
        assertTrue(GaResourceGuards.driftPasses(0L, 0L));
        assertFalse(GaResourceGuards.driftPasses(0L, 1L));
        assertEquals("B1", GaResourceGuards.evaluatePair(
                GaResourceGuards.Metric.TRANSIENT_FILE_BYTES, 0L, 1L).failureCode());
    }

    @Test
    void missingWindowAndCrossRunOrOrderingContaminationAbortOrFailClosed() {
        final List<GaSoakResourceSample> shortSamples = samples(100L, 100L, 10L, 10L, 20L, 20L)
                .subList(0, GaResourceGuards.WINDOW_SIZE);
        assertEquals("ABORTED", GaResourceGuards.evaluate(
                GaResourceGuards.Metric.THREADS, shortSamples).outcome());

        final List<GaSoakResourceSample> foreign = samples(100L, 100L, 10L, 10L, 20L, 20L);
        foreign.set(300, new GaSoakResourceSample("other-physical", GaSoakMatrix.Stage.STAGE_A,
                300L, 300L, 120L, 12L, 24L));
        assertEquals("B0", GaResourceGuards.evaluate(GaResourceGuards.Metric.THREADS, foreign,
                PHYSICAL_ID, GaSoakMatrix.Stage.STAGE_A).failureCode());

        final List<GaSoakResourceSample> duplicate = samples(100L, 100L, 10L, 10L, 20L, 20L);
        duplicate.set(300, new GaSoakResourceSample(PHYSICAL_ID, GaSoakMatrix.Stage.STAGE_A,
                299L, 300L, 120L, 12L, 24L));
        assertEquals("B0", GaResourceGuards.evaluate(
                GaResourceGuards.Metric.THREADS, duplicate).failureCode());
    }

    private static List<GaSoakResourceSample> samples(
            final long firstThreads,
            final long finalThreads,
            final long firstCount,
            final long finalCount,
            final long firstBytes,
            final long finalBytes) {
        final List<GaSoakResourceSample> result = new ArrayList<>();
        for (int index = 0; index < GaResourceGuards.WINDOW_SIZE * 2; index++) {
            final boolean first = index < GaResourceGuards.WINDOW_SIZE;
            result.add(new GaSoakResourceSample(PHYSICAL_ID, GaSoakMatrix.Stage.STAGE_A,
                    index, index, first ? firstThreads : finalThreads,
                    first ? firstCount : finalCount, first ? firstBytes : finalBytes));
        }
        return result;
    }
}
