package com.ultralatency.matching.qualification.ga.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.qualification.ga.soak.GaNaturalGcSample;
import com.ultralatency.matching.qualification.ga.soak.GaSoakMatrix;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests the chronological natural-GC applicability and heap-retention guard. */
class GaNaturalGcGuardTest {

    private static final String PHYSICAL_ID = "physical-a";

    @Test
    void fewerThanFiveCompleteNaturalCyclesAbortWithoutSyntheticSamples() {
        assertEquals("ABORTED", GaNaturalGcGuard.evaluate(List.of()).outcome());
        assertEquals("B3", GaNaturalGcGuard.evaluate(samples(1, 100L)).failureCode());
        assertEquals("B3", GaNaturalGcGuard.evaluate(samples(4, 100L)).failureCode());
    }

    @Test
    void fiveCyclesUseFirstAndFinalCohortMedians() {
        final GaNaturalGcGuard.Evaluation evaluation =
                GaNaturalGcGuard.evaluate(samples(5, 100L));

        assertTrue(evaluation.passed());
        assertEquals("PASS", evaluation.outcome());
        assertEquals("NONE", evaluation.failureCode());
        assertEquals(5, evaluation.sampleCount());
        assertEquals(100L, evaluation.firstMedianBytes());
        assertEquals(100L, evaluation.finalMedianBytes());
    }

    @Test
    void heapGrowthBeyondAllowanceFailsAsCandidateSemanticEvidence() {
        final List<GaNaturalGcSample> samples = samples(5, 100_000_000L);
        final List<GaNaturalGcSample> growing = new ArrayList<>(samples);
        growing.set(4, new GaNaturalGcSample(PHYSICAL_ID, GaSoakMatrix.Stage.STAGE_A,
                4L, 4L, 4L, 200_000_000L, true));

        final GaNaturalGcGuard.Evaluation evaluation = GaNaturalGcGuard.evaluate(growing);

        assertFalse(evaluation.passed());
        assertEquals("FAIL", evaluation.outcome());
        assertEquals("B1", evaluation.failureCode());
    }

    @Test
    void incompleteCycleDoesNotCountAndIdentityOrOrderingCannotBeRepaired() {
        final List<GaNaturalGcSample> incomplete = List.of(
                new GaNaturalGcSample(PHYSICAL_ID, GaSoakMatrix.Stage.STAGE_A,
                        0L, 0L, 0L, 100L, false));
        assertEquals("ABORTED", GaNaturalGcGuard.evaluate(incomplete).outcome());

        final List<GaNaturalGcSample> duplicate = samples(5, 100L);
        duplicate.set(4, new GaNaturalGcSample(PHYSICAL_ID, GaSoakMatrix.Stage.STAGE_A,
                3L, 4L, 4L, 100L, true));
        assertEquals("B0", GaNaturalGcGuard.evaluate(duplicate).failureCode());

        final List<GaNaturalGcSample> outOfOrder = samples(5, 100L);
        outOfOrder.set(4, new GaNaturalGcSample(PHYSICAL_ID, GaSoakMatrix.Stage.STAGE_A,
                4L, 2L, 4L, 100L, true));
        assertEquals("B0", GaNaturalGcGuard.evaluate(outOfOrder).failureCode());

        assertEquals("B0", GaNaturalGcGuard.evaluate(samples(5, 100L),
                "other-physical", GaSoakMatrix.Stage.STAGE_A).failureCode());
    }

    private static List<GaNaturalGcSample> samples(final int count, final long heap) {
        final List<GaNaturalGcSample> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            result.add(new GaNaturalGcSample(PHYSICAL_ID, GaSoakMatrix.Stage.STAGE_A,
                    index, index, index, heap, true));
        }
        return result;
    }
}
