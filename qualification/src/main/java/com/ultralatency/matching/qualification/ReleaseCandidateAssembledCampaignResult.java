package com.ultralatency.matching.qualification;

import java.nio.file.Path;
import java.util.Objects;

/** Immutable result of the two-run assembled-runtime campaign evaluation. */
public record ReleaseCandidateAssembledCampaignResult(
        Path artifactDirectory,
        Path summaryPath,
        Path artifactHashesPath,
        String summarySha256,
        boolean passed) {

    public ReleaseCandidateAssembledCampaignResult {
        Objects.requireNonNull(artifactDirectory, "artifactDirectory");
        Objects.requireNonNull(summaryPath, "summaryPath");
        Objects.requireNonNull(artifactHashesPath, "artifactHashesPath");
        if (summarySha256 == null || !summarySha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("summarySha256 must be lowercase SHA-256");
        }
    }
}

