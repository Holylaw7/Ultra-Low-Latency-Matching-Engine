package com.ultralatency.matching.qualification.ga.capacity;

import java.nio.file.Path;
import java.util.Objects;

/** Result of one non-formal G5 Quick readiness run. */
public record GaCapacityQuickResult(
        GaCapacityObservation observation,
        GaCapacityEvaluator.Evaluation evaluation,
        Path evidenceDirectory,
        Path manifestPath,
        Path gateResultPath) {

    /** Validates the immutable Quick result references. */
    public GaCapacityQuickResult {
        Objects.requireNonNull(observation, "observation");
        Objects.requireNonNull(evaluation, "evaluation");
        Objects.requireNonNull(evidenceDirectory, "evidenceDirectory");
        Objects.requireNonNull(manifestPath, "manifestPath");
        Objects.requireNonNull(gateResultPath, "gateResultPath");
        if (evaluation.formalEligible()) {
            throw new IllegalArgumentException("Quick result cannot be formal evidence");
        }
    }
}
