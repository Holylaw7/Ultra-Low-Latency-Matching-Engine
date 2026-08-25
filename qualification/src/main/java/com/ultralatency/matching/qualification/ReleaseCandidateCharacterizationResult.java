package com.ultralatency.matching.qualification;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Immutable result of one Phase 10 qualification-only characterization unit. */
public record ReleaseCandidateCharacterizationResult(
        boolean success,
        Path artifactDirectory,
        Path summaryPath,
        Path artifactHashesPath,
        String summarySha256,
        List<LifecycleSample> lifecycleSamples,
        TrialResult managementIdle,
        TrialResult statusOneHz) {

    public ReleaseCandidateCharacterizationResult {
        Objects.requireNonNull(artifactDirectory, "artifactDirectory");
        Objects.requireNonNull(summaryPath, "summaryPath");
        Objects.requireNonNull(artifactHashesPath, "artifactHashesPath");
        if (summarySha256 == null || !summarySha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("summarySha256 must be lowercase SHA-256");
        }
        lifecycleSamples = List.copyOf(Objects.requireNonNull(lifecycleSamples, "lifecycleSamples"));
        if (lifecycleSamples.isEmpty()) {
            throw new IllegalArgumentException("lifecycle samples must not be empty");
        }
        Objects.requireNonNull(managementIdle, "managementIdle");
        Objects.requireNonNull(statusOneHz, "statusOneHz");
    }

    /** One packaged lifecycle observation retained in raw and summary evidence. */
    public record LifecycleSample(
            String scenario,
            int sampleNumber,
            long startupToReadyNanos,
            long shutdownNanos,
            long responseNanos,
            boolean ready,
            boolean responsePassed,
            boolean recoveryConverged,
            boolean leaseReacquired,
            boolean temporaryFilesClear,
            boolean passed,
            String artifactDirectory) {

        public LifecycleSample {
            Objects.requireNonNull(scenario, "scenario");
            Objects.requireNonNull(artifactDirectory, "artifactDirectory");
            if (scenario.isBlank() || sampleNumber <= 0 || startupToReadyNanos < 0
                    || shutdownNanos < 0 || responseNanos < 0) {
                throw new IllegalArgumentException("invalid lifecycle sample");
            }
        }
    }

    /** One fixed management-overhead trial and its response distributions. */
    public record TrialResult(
            String name,
            long elapsedMillis,
            long acceptedCommands,
            long managementRequests,
            boolean throughputPassed,
            boolean responsePassed,
            QualificationPercentiles.Summary responseLatency,
            QualificationPercentiles.Summary managementLatency,
            String rawResponsePath,
            String rawManagementPath,
            String jfrPath,
            String resourcePath,
            String configurationSha256,
            String artifactSha256) {

        public TrialResult {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(responseLatency, "responseLatency");
            Objects.requireNonNull(managementLatency, "managementLatency");
            Objects.requireNonNull(rawResponsePath, "rawResponsePath");
            Objects.requireNonNull(rawManagementPath, "rawManagementPath");
            Objects.requireNonNull(jfrPath, "jfrPath");
            Objects.requireNonNull(resourcePath, "resourcePath");
            Objects.requireNonNull(configurationSha256, "configurationSha256");
            Objects.requireNonNull(artifactSha256, "artifactSha256");
            if (elapsedMillis < 0 || acceptedCommands < 0 || managementRequests < 0) {
                throw new IllegalArgumentException("invalid trial counts");
            }
        }
    }
}
