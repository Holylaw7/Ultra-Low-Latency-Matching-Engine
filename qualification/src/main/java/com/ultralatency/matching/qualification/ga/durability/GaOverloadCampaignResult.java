package com.ultralatency.matching.qualification.ga.durability;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Immutable result of one G7 bounded-overload qualification run. */
public record GaOverloadCampaignResult(
        GaOverloadMatrix matrix,
        List<GaDurabilityEvidence.RunReference> runs,
        boolean passed,
        Path artifactDirectory,
        Path gateResultPath,
        Path summaryPath) {

    /** Validates the result envelope. */
    public GaOverloadCampaignResult {
        Objects.requireNonNull(matrix, "matrix");
        runs = List.copyOf(Objects.requireNonNull(runs, "runs"));
        Objects.requireNonNull(artifactDirectory, "artifactDirectory");
        Objects.requireNonNull(gateResultPath, "gateResultPath");
        Objects.requireNonNull(summaryPath, "summaryPath");
        if (runs.isEmpty()) {
            throw new IllegalArgumentException("overload campaign must have runs");
        }
    }
}
