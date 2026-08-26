package com.ultralatency.matching.qualification.ga.correctness;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Immutable G1/G2 campaign result and published evidence references. */
public record GaCorrectnessCampaignResult(
        GaCorrectnessMatrix matrix,
        List<GaCorrectnessCaseResult> cases,
        boolean passed,
        List<String> failures,
        Path artifactDirectory,
        Path summaryPath,
        Path manifestPath,
        Path artifactHashesPath,
        String summarySha256) {

    /** Creates a validated campaign result. */
    public GaCorrectnessCampaignResult {
        Objects.requireNonNull(matrix, "matrix");
        cases = List.copyOf(Objects.requireNonNull(cases, "cases"));
        failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
        Objects.requireNonNull(artifactDirectory, "artifactDirectory");
        Objects.requireNonNull(summaryPath, "summaryPath");
        Objects.requireNonNull(manifestPath, "manifestPath");
        Objects.requireNonNull(artifactHashesPath, "artifactHashesPath");
        if (!summarySha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("summarySha256 must be lowercase SHA-256");
        }
        if (passed && !failures.isEmpty()) {
            throw new IllegalArgumentException("a passed campaign cannot contain failures");
        }
    }
}
