package com.ultralatency.matching.qualification.ga.correctness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ultralatency.matching.qualification.QualificationProfile;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests the immutable dimensions and expansion order of the G1/G2 matrix. */
class GaCorrectnessMatrixTest {

    @Test
    void approvedMatrixHasFrozenDimensions() {
        final GaCorrectnessMatrix matrix = GaCorrectnessMatrix.approved();

        assertEquals(GaCorrectnessMatrix.APPROVED_VERSION, matrix.version());
        assertEquals(100_000, matrix.commandCount());
        assertEquals(65_536, matrix.walSegmentSizeBytes());
        assertEquals(List.of(QualificationProfile.values()), matrix.profiles());
        assertEquals(List.of(20260823L, 20260824L, 20260825L), matrix.seeds());
        assertEquals(2, matrix.repetitions());
        assertEquals(List.of(25_000, 50_000, 75_000), matrix.snapshotPrefixes());
        assertEquals(24, matrix.cases().size());
        assertEquals(96, matrix.recoveryObservationCount());
    }

    @Test
    void matrixRejectsInvalidSnapshotPrefix() {
        assertThrows(IllegalArgumentException.class, () -> new GaCorrectnessMatrix(
                "test", 10, 1, List.of(QualificationProfile.LIFECYCLE_MIX),
                List.of(1L), 1, List.of(0)));
    }
}
