package com.ultralatency.matching.qualification.ga.correctness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.qualification.QualificationProfile;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests fail-closed cross-mode and deterministic-repetition evaluation. */
class GaCorrectnessEvaluatorTest {

    private static final String WAL = "a".repeat(64);
    private static final String CHECKPOINT = "b".repeat(64);
    private static final String TRANSCRIPT = "c".repeat(64);
    private static final String PROBE = "d".repeat(64);
    private static final String PREFIX_ONE = "e".repeat(64);
    private static final String PREFIX_TWO = "f".repeat(64);

    @Test
    void matchingLivePureAndSnapshotEvidencePasses() {
        final GaCorrectnessMatrix matrix = new GaCorrectnessMatrix(
                "ga-g1-g2-test-v1", 4, 65_536,
                List.of(QualificationProfile.CROSSING_MULTI_MATCH), List.of(7L), 1,
                List.of(1, 2));
        final GaCorrectnessCase matrixCase = matrix.cases().get(0);
        final GaCorrectnessCaseResult result = result(
                matrixCase, PREFIX_ONE, PREFIX_TWO, PREFIX_ONE, PREFIX_TWO);

        assertTrue(GaCorrectnessEvaluator.passesCase(matrix, result));
        assertEquals(List.of(), GaCorrectnessEvaluator.evaluate(matrix, List.of(result)));
    }

    @Test
    void changedSnapshotSuffixIsRejected() {
        final GaCorrectnessMatrix matrix = new GaCorrectnessMatrix(
                "ga-g1-g2-test-v1", 4, 65_536,
                List.of(QualificationProfile.CROSSING_MULTI_MATCH), List.of(7L), 1,
                List.of(1, 2));
        final GaCorrectnessCase matrixCase = matrix.cases().get(0);
        final GaCorrectnessCaseResult result = result(
                matrixCase, PREFIX_ONE, PREFIX_TWO, PREFIX_ONE, "0".repeat(64));

        assertTrue(GaCorrectnessEvaluator.evaluate(matrix, List.of(result)).stream()
                .anyMatch(failure -> failure.contains("Snapshot-tail transcript mismatch at 2")));
    }

    private static GaCorrectnessCaseResult result(
            final GaCorrectnessCase matrixCase,
            final String prefixOne,
            final String prefixTwo,
            final String expectedPrefixOne,
            final String expectedPrefixTwo) {
        final GaCorrectnessObservation live = observation("LIVE", 0, 4, TRANSCRIPT);
        final GaCorrectnessObservation pure = observation("PURE_WAL", 0, 4, TRANSCRIPT);
        final GaCorrectnessObservation snapshotOne = observation(
                "SNAPSHOT_THEN_WAL", 1, 3, prefixOne);
        final GaCorrectnessObservation snapshotTwo = observation(
                "SNAPSHOT_THEN_WAL", 2, 2, prefixTwo);
        final Map<Integer, String> expected = new LinkedHashMap<>();
        expected.put(1, expectedPrefixOne);
        expected.put(2, expectedPrefixTwo);
        return new GaCorrectnessCaseResult(
                matrixCase,
                List.of(live, pure, snapshotOne, snapshotTwo),
                expected,
                true,
                List.of(),
                Path.of("results", matrixCase.id()));
    }

    private static GaCorrectnessObservation observation(
            final String mode,
            final int sequence,
            final int accepted,
            final String transcript) {
        return new GaCorrectnessObservation(
                mode, sequence, accepted, 0, WAL, CHECKPOINT, transcript, PROBE);
    }
}
