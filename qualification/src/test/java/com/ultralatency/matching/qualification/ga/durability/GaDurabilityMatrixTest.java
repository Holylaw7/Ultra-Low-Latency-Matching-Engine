package com.ultralatency.matching.qualification.ga.durability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Tests the frozen G3 matrix dimensions and validation guards. */
class GaDurabilityMatrixTest {

    @Test
    void approvedMatrixHasFrozenDimensions() {
        final GaDurabilityMatrix matrix = GaDurabilityMatrix.approved();

        assertEquals(GaDurabilityMatrix.APPROVED_VERSION, matrix.version());
        assertEquals(3, matrix.walSegmentSizes().size());
        assertEquals(50, matrix.gracefulCycles());
        assertEquals(50, matrix.forcedCycles());
        assertEquals(10_000, matrix.commandsPerCycle());
        assertEquals(100, matrix.lifecycleExecutionCount());
        assertEquals(4_128, matrix.walSegmentSizes().get(0));
        assertEquals(3 * GaDurabilityFixture.values().length,
                matrix.corruptionExecutionCount());
        assertEquals(GaDurabilityFixture.values().length, matrix.corruptionFixtures().size());
        assertEquals(true, matrix.isApproved());
    }

    @Test
    void rejectsDuplicateSegmentSizesAndFixtures() {
        assertThrows(IllegalArgumentException.class, () -> new GaDurabilityMatrix(
                "test", java.util.List.of(8_192, 8_192), 1, 1, 2, 1,
                java.util.List.of(GaDurabilityFixture.SEGMENT_MAGIC)));
        assertThrows(IllegalArgumentException.class, () -> new GaDurabilityMatrix(
                "test", java.util.List.of(8_192), 1, 1, 2, 1,
                java.util.List.of(GaDurabilityFixture.SEGMENT_MAGIC,
                        GaDurabilityFixture.SEGMENT_MAGIC)));
    }

    @Test
    void rejectsTheRemovedBelowMinimumWalBoundary() {
        assertThrows(IllegalArgumentException.class, () -> new GaDurabilityMatrix(
                "test", java.util.List.of(4_096), 1, 1, 2, 1,
                java.util.List.of(GaDurabilityFixture.SEGMENT_MAGIC)));
    }

    @Test
    void rejectsWalSegmentAboveApplicationMaximum() {
        assertThrows(IllegalArgumentException.class, () -> new GaDurabilityMatrix(
                "test", java.util.List.of(1_073_741_825), 1, 1, 2, 1,
                java.util.List.of(GaDurabilityFixture.SEGMENT_MAGIC)));
    }
}
