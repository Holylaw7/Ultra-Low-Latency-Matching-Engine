package com.ultralatency.matching.qualification.ga.durability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Tests the frozen G7 matrix dimensions and focused expansion. */
class GaOverloadMatrixTest {

    @Test
    void approvedMatrixHasFrozenBounds() {
        final GaOverloadMatrix matrix = GaOverloadMatrix.approved();

        assertEquals(GaOverloadMatrix.APPROVED_VERSION, matrix.version());
        assertEquals(1_024, matrix.pipelineCapacity());
        assertEquals(104, matrix.maxRequestFrameBytes());
        assertEquals(32, matrix.maxManagementRequestBytes());
        assertEquals(7, matrix.scenarios().size());
        assertTrue(matrix.isApproved());
    }

    @Test
    void focusedMatrixRetainsAllExecutableProbes() {
        final GaOverloadMatrix matrix = GaOverloadMatrix.test();

        assertEquals(GaOverloadScenario.values().length, matrix.scenarios().size());
        assertEquals(2, matrix.pipelineCapacity());
        assertTrue(matrix.scenarios().contains(GaOverloadScenario.PIPELINE_FULL));
    }

    @Test
    void rejectsMatrixValuesOutsideRuntimeBounds() {
        assertThrows(IllegalArgumentException.class, () -> new GaOverloadMatrix(
                "invalid-pipeline", 2_097_152, 104, 32, 1, 2,
                java.util.List.of(GaOverloadScenario.RESOURCE_BOUND)));
        assertThrows(IllegalArgumentException.class, () -> new GaOverloadMatrix(
                "invalid-frame", 2, 105, 32, 1, 2,
                java.util.List.of(GaOverloadScenario.RESOURCE_BOUND)));
        assertThrows(IllegalArgumentException.class, () -> new GaOverloadMatrix(
                "invalid-management", 2, 104, 33, 1, 2,
                java.util.List.of(GaOverloadScenario.RESOURCE_BOUND)));
        assertThrows(IllegalArgumentException.class, () -> new GaOverloadMatrix(
                "invalid-attempts", 2, 104, 32, 65, 2,
                java.util.List.of(GaOverloadScenario.RESOURCE_BOUND)));
    }
}
