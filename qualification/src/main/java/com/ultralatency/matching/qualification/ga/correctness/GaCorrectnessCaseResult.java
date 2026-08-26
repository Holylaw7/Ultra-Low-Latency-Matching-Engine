package com.ultralatency.matching.qualification.ga.correctness;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable result and evidence references for one matrix case. */
public record GaCorrectnessCaseResult(
        GaCorrectnessCase matrixCase,
        List<GaCorrectnessObservation> observations,
        Map<Integer, String> expectedSnapshotTranscriptDigests,
        boolean passed,
        List<String> failures,
        Path artifactDirectory) {

    /** Creates a validated case result. */
    public GaCorrectnessCaseResult {
        Objects.requireNonNull(matrixCase, "matrixCase");
        observations = List.copyOf(Objects.requireNonNull(observations, "observations"));
        expectedSnapshotTranscriptDigests = Map.copyOf(Objects.requireNonNull(
                expectedSnapshotTranscriptDigests, "expectedSnapshotTranscriptDigests"));
        failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
        Objects.requireNonNull(artifactDirectory, "artifactDirectory");
        if (passed && !failures.isEmpty()) {
            throw new IllegalArgumentException("a passed case cannot contain failures");
        }
    }

    /** Creates a failed case preserving the partial artifact directory. */
    public static GaCorrectnessCaseResult failed(
            final GaCorrectnessCase matrixCase,
            final Path artifactDirectory,
            final String failure) {
        return new GaCorrectnessCaseResult(
                matrixCase,
                List.of(),
                Map.of(),
                false,
                List.of(Objects.requireNonNull(failure, "failure")),
                artifactDirectory);
    }
}
