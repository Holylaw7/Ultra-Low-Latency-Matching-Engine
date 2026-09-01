package com.ultralatency.matching.qualification.ga.capacity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests G5 recovery/integrity criteria and non-formal Quick semantics. */
class GaCapacityEvaluatorTest {

    @Test
    void quickEvaluationClaimsOnlyTestedSupportEnvelope() {
        final GaCapacityObservation observation = observation(10_000, true);
        final GaCapacityEvaluator.Evaluation evaluation =
                GaCapacityEvaluator.evaluateQuick(observation);
        assertTrue(evaluation.passed());
        assertFalse(evaluation.formalEligible());
        assertEquals("QUICK_READINESS_ONLY", evaluation.claim());
    }

    @Test
    void completedRecoveryFailureCannotPass() {
        final GaCapacityObservation observation = observation(1_000_000, false);
        final GaCapacityEvaluator.Evaluation evaluation =
                GaCapacityEvaluator.evaluateScale(observation, 166_000);
        assertFalse(evaluation.passed());
        assertEquals("B2", evaluation.failureCode());
    }

    private static GaCapacityObservation observation(
            final int commandCount, final boolean converged) {
        return new GaCapacityObservation(
                commandCount, commandCount, commandCount, 166_000, 10, 100, 20, 1000, 2000,
                1_000_000L, converged, false, false, false, false, true, true, true, true);
    }
}
