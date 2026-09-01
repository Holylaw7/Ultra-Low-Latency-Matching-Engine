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

    @Test
    void eachFrozenFailurePredicateFailsClosed() {
        assertScaleFails("acceptedCommands", observation(
                1_000_000, 999_999, 166_000, true, false, false, false, false,
                true, true, true), 166_000);
        assertScaleFails("recoveredActiveOrders", observation(
                1_000_000, 1_000_000, 165_999, true, false, false, false, false,
                true, true, true), 166_000);
        assertScaleFails("recovery.converged", observation(
                1_000_000, 1_000_000, 166_000, false, false, false, false, false,
                true, true, true), 166_000);
        assertScaleFails("resource.outOfMemory", observation(
                1_000_000, 1_000_000, 166_000, true, true, false, false, false,
                true, true, true), 166_000);
        assertScaleFails("recovery.sequenceGap", observation(
                1_000_000, 1_000_000, 166_000, true, false, true, false, false,
                true, true, true), 166_000);
        assertScaleFails("recovery.invalidTrade", observation(
                1_000_000, 1_000_000, 166_000, true, false, false, true, false,
                true, true, true), 166_000);
        assertScaleFails("recovery.timeout", observation(
                1_000_000, 1_000_000, 166_000, true, false, false, false, true,
                true, true, true), 166_000);
        assertScaleFails("candidate.bound", observation(
                1_000_000, 1_000_000, 166_000, true, false, false, false, false,
                true, false, true), 166_000);
    }

    private static void assertScaleFails(
            final String criterionId,
            final GaCapacityObservation observation,
            final int minimumRecoveredActiveOrders) {
        final GaCapacityEvaluator.Evaluation evaluation = GaCapacityEvaluator.evaluateScale(
                observation, minimumRecoveredActiveOrders);

        assertFalse(evaluation.passed(), criterionId);
        assertEquals("B2", evaluation.failureCode());
        assertTrue(evaluation.criteria().stream().anyMatch(criterion ->
                criterion.id().equals(criterionId) && !criterion.passed()), criterionId);
    }

    private static GaCapacityObservation observation(
            final int commandCount, final boolean converged) {
        return observation(commandCount, commandCount, 166_000, converged,
                false, false, false, false, true, true, true);
    }

    private static GaCapacityObservation observation(
            final int commandCount,
            final long acceptedCommands,
            final long recoveredActiveOrders,
            final boolean converged,
            final boolean outOfMemory,
            final boolean sequenceGap,
            final boolean invalidTrade,
            final boolean timeout,
            final boolean configurationBound,
            final boolean candidateBound,
            final boolean controllerBound) {
        return new GaCapacityObservation(
                commandCount, acceptedCommands, commandCount, recoveredActiveOrders,
                10, 100, 20, 1000, 2000, 1_000_000L, converged, outOfMemory,
                sequenceGap, invalidTrade, timeout, true, configurationBound,
                candidateBound, controllerBound);
    }
}
