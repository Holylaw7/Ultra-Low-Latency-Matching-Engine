package com.ultralatency.matching.qualification.ga.soak;

import com.ultralatency.matching.qualification.ga.observability.GaObservabilityEvaluator;
import java.nio.file.Path;
import java.util.Objects;

/** Result of the one shared non-formal G6/G8 Quick physical execution. */
public record GaSoakQuickResult(
        GaSoakObservation g6Observation,
        GaObservabilityEvaluator.Evaluation g8Evaluation,
        GaSoakEvaluator.Evaluation g6Evaluation,
        GaSoakEvidencePublisher.PublishedQuick publication) {

    public GaSoakQuickResult {
        Objects.requireNonNull(g6Observation, "g6Observation");
        Objects.requireNonNull(g8Evaluation, "g8Evaluation");
        Objects.requireNonNull(g6Evaluation, "g6Evaluation");
        Objects.requireNonNull(publication, "publication");
        if (g6Evaluation.formalEligible() || g8Evaluation.formalEligible()) {
            throw new IllegalArgumentException("Quick result cannot be formal evidence");
        }
    }

    /** Returns the shared physical-execution identifier. */
    public String physicalExecutionId() {
        return publication.physicalExecutionId();
    }

    /** Returns the evidence root containing both independent gate chains. */
    public Path evidenceRoot() {
        return publication.evidenceRoot();
    }

    /** Returns the G6 canonical run identifier. */
    public String g6RunId() {
        return publication.g6().runId();
    }

    /** Returns the G8 canonical run identifier. */
    public String g8RunId() {
        return publication.g8().runId();
    }
}
