package com.ultralatency.matching.qualification.ga.correctness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises one small public-boundary matrix and its immutable publication. */
class GaCorrectnessRunnerTest {

    @Test
    void testMatrixPublishesConvergentEvidence(@TempDir final Path output) throws Exception {
        final GaCorrectnessCampaignResult result = new GaCorrectnessRunner().run(
                GaCorrectnessMatrix.test(), output);

        assertTrue(result.passed(), result.failures()::toString);
        assertEquals(1, result.cases().size());
        assertEquals(5, result.cases().get(0).observations().size());
        assertEquals(4, result.matrix().recoveryObservationCount());
        assertTrue(Files.isRegularFile(result.summaryPath()));
        assertTrue(Files.isRegularFile(result.manifestPath()));
        assertTrue(Files.isRegularFile(result.artifactHashesPath()));
        assertTrue(Files.readString(result.summaryPath()).contains("passed=true"));
        assertTrue(Files.readString(result.artifactHashesPath()).contains(
                "ga-g1-g2-summary-v1.txt"));
    }
}
