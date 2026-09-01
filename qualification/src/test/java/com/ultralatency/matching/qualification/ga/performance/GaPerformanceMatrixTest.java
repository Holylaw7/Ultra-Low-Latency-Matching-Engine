package com.ultralatency.matching.qualification.ga.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Tests the frozen G4 matrix identities and the non-formal Quick variant. */
class GaPerformanceMatrixTest {

    @Test
    void approvedMatrixUsesFrozenContract() {
        final GaPerformanceMatrix matrix = GaPerformanceMatrix.approved();
        assertTrue(matrix.isApproved());
        assertEquals(3, matrix.runCount());
        assertEquals(Duration.ofMinutes(10), matrix.runDuration());
        assertEquals(60, matrix.lifecycleSamples());
        assertEquals(3, matrix.latencyThresholdsNanos().size());
    }

    @Test
    void quickMatrixCannotBeFormal() {
        final GaPerformanceMatrix matrix = GaPerformanceMatrix.quick();
        assertTrue(!matrix.isApproved());
        assertEquals(1, matrix.runCount());
        assertEquals(256, matrix.quickCommandCount());
    }
}
