package com.ultralatency.matching.qualification.ga.capacity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests the frozen G5 scale identity and support-envelope claim. */
class GaCapacityMatrixTest {

    @Test
    void approvedMatrixUsesAllFrozenScales() {
        final GaCapacityMatrix matrix = GaCapacityMatrix.approved();
        assertTrue(matrix.isApproved());
        assertEquals(4, matrix.commandScales().size());
        assertEquals(166_000, matrix.minimumRecoveredActiveOrders(1_000_000));
    }

    @Test
    void quickMatrixIsNotFormalCapacityEvidence() {
        final GaCapacityMatrix matrix = GaCapacityMatrix.quick();
        assertTrue(!matrix.isApproved());
        assertEquals(10_000, matrix.quickCommandCount());
        assertEquals(1, matrix.commandScales().size());
    }
}
