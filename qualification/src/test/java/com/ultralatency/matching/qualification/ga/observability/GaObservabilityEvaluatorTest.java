package com.ultralatency.matching.qualification.ga.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.qualification.ga.soak.GaNaturalGcSample;
import com.ultralatency.matching.qualification.ga.soak.GaSoakMatrix;
import com.ultralatency.matching.qualification.ga.soak.GaSoakResourceSample;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests G8 hard predicates and independent Quick/formal outcome semantics. */
class GaObservabilityEvaluatorTest {

    private static final String PHYSICAL_ID = "physical-a";

    @Test
    void validQuickIsReadinessOnlyAndDoesNotNeedNaturalGcSamples() {
        final GaObservabilityEvaluator.Evaluation evaluation =
                GaObservabilityEvaluator.evaluateQuick(GaSoakMatrix.quick(), quickObservation());

        assertTrue(evaluation.passed());
        assertFalse(evaluation.formalEligible());
        assertEquals("PASS", evaluation.outcome());
    }

    @Test
    void quickManagementFailureIsQualificationFailureAndJfrCapabilityIsAborted() {
        final GaObservabilityObservation malformedManagement = new GaObservabilityObservation(
                PHYSICAL_ID, GaSoakMatrix.Stage.QUICK, List.of(), GaGcEvidence.quick("NONE"),
                GaJfrEvidence.valid(Path.of("quick.jfr")),
                List.of(GaManagementEvidence.live(1, true)), false, true, 0, false,
                false, true, true, true, true);
        final GaObservabilityEvaluator.Evaluation semantic =
                GaObservabilityEvaluator.evaluateQuick(GaSoakMatrix.quick(), malformedManagement);
        assertFalse(semantic.passed());
        assertEquals("FAIL", semantic.outcome());
        assertEquals("B2", semantic.failureCode());

        final GaJfrEvidence unavailable = new GaJfrEvidence(Path.of("missing.jfr"), true, true,
                false, true, GaJfrEvidence.REQUIRED_EVENT_FAMILIES, false, "B3");
        final GaObservabilityObservation missingJfr = new GaObservabilityObservation(
                PHYSICAL_ID, GaSoakMatrix.Stage.QUICK, List.of(), GaGcEvidence.quick("NONE"),
                unavailable, validManagement(), true, true, 0, false, false, true, true, true,
                true);
        final GaObservabilityEvaluator.Evaluation infrastructure =
                GaObservabilityEvaluator.evaluateQuick(GaSoakMatrix.quick(), missingJfr);
        assertFalse(infrastructure.passed());
        assertEquals("ABORTED", infrastructure.outcome());
        assertEquals("B3", infrastructure.failureCode());
    }

    @Test
    void formalG8UsesIndependentResourceAndNaturalGcGates() {
        final GaObservabilityEvaluator.Evaluation evaluation =
                GaObservabilityEvaluator.evaluateFormal(GaSoakMatrix.stageA(), formalObservation());

        assertTrue(evaluation.passed(), () -> evaluation.criteria().toString());
        assertTrue(evaluation.formalEligible());
        assertEquals("PASS", evaluation.outcome());
    }

    @Test
    void formalInsufficientNaturalGcIsAbortedNotMemoryLeakFailure() {
        final GaObservabilityObservation observation = formalObservationWithGc(List.of(
                new GaNaturalGcSample(PHYSICAL_ID, GaSoakMatrix.Stage.STAGE_A,
                        0L, 0L, 0L, 1_000L, true)));
        final GaObservabilityEvaluator.Evaluation evaluation =
                GaObservabilityEvaluator.evaluateFormal(GaSoakMatrix.stageA(), observation);

        assertFalse(evaluation.passed());
        assertEquals("ABORTED", evaluation.outcome());
        assertEquals("B3", evaluation.failureCode());
    }

    private static GaObservabilityObservation quickObservation() {
        return new GaObservabilityObservation(PHYSICAL_ID, GaSoakMatrix.Stage.QUICK, List.of(),
                GaGcEvidence.quick("NONE"), GaJfrEvidence.valid(Path.of("quick.jfr")),
                validManagement(), true, true, 0, false, false, true, true, true, true);
    }

    private static GaObservabilityObservation formalObservation() {
        return formalObservationWithGc(naturalGcSamples());
    }

    private static GaObservabilityObservation formalObservationWithGc(
            final List<GaNaturalGcSample> gcSamples) {
        return new GaObservabilityObservation(PHYSICAL_ID, GaSoakMatrix.Stage.STAGE_A,
                resourceSamples(), new GaGcEvidence(gcSamples, true, true, true, "NONE"),
                GaJfrEvidence.valid(Path.of("formal.jfr")), validManagement(), true, true, 0,
                false, false, true, true, true, true);
    }

    private static List<GaManagementEvidence> validManagement() {
        return List.of(
                GaManagementEvidence.live(1, true),
                GaManagementEvidence.ready(1, true),
                GaManagementEvidence.status(1, true, true, "READY", "NONE", true,
                        "PURE_WAL", 10, 0, 100),
                GaManagementEvidence.metrics(1, true, true, "READY", "NONE", true,
                        "PURE_WAL", 10, 0, 100, 4, 0));
    }

    private static List<GaNaturalGcSample> naturalGcSamples() {
        final List<GaNaturalGcSample> result = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            result.add(new GaNaturalGcSample(PHYSICAL_ID, GaSoakMatrix.Stage.STAGE_A,
                    index, index, index, 1_000L, true));
        }
        return result;
    }

    private static List<GaSoakResourceSample> resourceSamples() {
        final List<GaSoakResourceSample> result = new ArrayList<>();
        for (int index = 0; index < 600; index++) {
            final boolean first = index < 300;
            result.add(new GaSoakResourceSample(PHYSICAL_ID, GaSoakMatrix.Stage.STAGE_A,
                    index, index, first ? 10L : 12L, first ? 0L : 0L,
                    first ? 0L : 0L));
        }
        return result;
    }
}
