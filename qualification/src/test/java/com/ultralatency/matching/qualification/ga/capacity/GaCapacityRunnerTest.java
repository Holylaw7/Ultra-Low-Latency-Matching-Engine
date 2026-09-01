package com.ultralatency.matching.qualification.ga.capacity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.qualification.ga.GaCandidateVerifier;
import com.ultralatency.matching.qualification.ga.correctness.GaCorrectnessCanonicalContext;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises the G5 public recovery-backed Quick readiness path. */
class GaCapacityRunnerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void quickSmokePublishesNonFormalEvidence() throws Exception {
        final GaCapacityQuickResult result = new GaCapacityRunner(testContext())
                .runQuick(temporaryDirectory);
        assertTrue(result.evaluation().passed());
        assertFalse(result.evaluation().formalEligible());
        assertTrue(Files.isRegularFile(result.manifestPath()));
        assertTrue(Files.isRegularFile(result.gateResultPath()));
    }

    private GaCorrectnessCanonicalContext testContext() {
        final String digest = "0".repeat(64);
        return new GaCorrectnessCanonicalContext(
                temporaryDirectory,
                "2".repeat(40),
                new GaCandidateVerifier.Verified(
                        "v0.9.0-rc.1", "0".repeat(40), "1".repeat(40), digest, digest));
    }
}
