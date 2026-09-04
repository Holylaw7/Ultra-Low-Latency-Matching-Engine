package com.ultralatency.matching.qualification.ga.performance;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Result container for a complete formal G4 campaign. */
public record GaPerformanceFormalResult(
        boolean passed,
        GaPerformanceMatrix matrix,
        List<GaFormalPerformanceEvidencePublisher.PublishedRun> runs,
        Path evidenceDirectory,
        Path campaignManifestPath,
        Path gateResultPath) {
    public GaPerformanceFormalResult {
        Objects.requireNonNull(matrix, "matrix");
        runs = List.copyOf(Objects.requireNonNull(runs, "runs"));
        Objects.requireNonNull(evidenceDirectory, "evidenceDirectory");
        Objects.requireNonNull(campaignManifestPath, "campaignManifestPath");
        Objects.requireNonNull(gateResultPath, "gateResultPath");
        if (runs.size() != matrix.runCount()) {
            throw new IllegalArgumentException("formal result run count does not match matrix");
        }
    }
}
