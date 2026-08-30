package com.ultralatency.matching.qualification.ga.durability;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Immutable result of one G3 durability qualification run. */
public record GaDurabilityCampaignResult(
        GaDurabilityMatrix matrix,
        List<GaDurabilityEvidence.RunReference> runs,
        boolean passed,
        Path artifactDirectory,
        Path gateResultPath,
        Path summaryPath) {

    /** Validates the result envelope. */
    public GaDurabilityCampaignResult {
        Objects.requireNonNull(matrix, "matrix");
        runs = List.copyOf(Objects.requireNonNull(runs, "runs"));
        Objects.requireNonNull(artifactDirectory, "artifactDirectory");
        Objects.requireNonNull(gateResultPath, "gateResultPath");
        Objects.requireNonNull(summaryPath, "summaryPath");
        if (runs.isEmpty()) {
            throw new IllegalArgumentException("durability campaign must have runs");
        }
    }
}
