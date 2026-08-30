package com.ultralatency.matching.qualification.ga.durability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.qualification.ga.GaCandidateVerifier;
import com.ultralatency.matching.qualification.ga.GaEvidenceCodec;
import com.ultralatency.matching.qualification.ga.GaGateEvaluator;
import com.ultralatency.matching.qualification.ga.correctness.GaCorrectnessCanonicalContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises a small G3 matrix and its canonical evidence publication. */
class GaDurabilityRunnerTest {

    @Test
    void testMatrixPublishesConvergentEvidence(@TempDir final Path output) throws Exception {
        final GaDurabilityCampaignResult result = new GaDurabilityRunner(testContext(output))
                .run(GaDurabilityMatrix.test(), output);

        assertTrue(result.passed(), result.runs()::toString);
        assertEquals(6, result.runs().size());
        assertTrue(Files.isRegularFile(result.gateResultPath()));
        assertTrue(Files.isRegularFile(result.summaryPath()));
        assertTrue(Files.readString(result.gateResultPath()).contains("evidence.outcome=PASS"));
        assertTrue(Files.isRegularFile(result.gateResultPath().resolveSibling(
                result.gateResultPath().getFileName() + ".sha256")));
        final Map<String, String> campaign = GaEvidenceCodec.decode(
                GaEvidenceCodec.Schema.CAMPAIGN,
                Files.readAllBytes(result.summaryPath()));
        assertEquals("G3", campaign.get("gate.id"));
        assertEquals("6", campaign.get("campaign.requiredRunCount"));
        assertEquals("6", campaign.get("run.count"));
        assertTrue(GaGateEvaluator.evaluateCampaign(campaign).passed());
        final String forcedRaw = result.runs().stream()
                .map(reference -> reference.manifestPath().getParent())
                .map(path -> path.resolve("raw-evidence-v1.txt"))
                .map(path -> {
                    try {
                        return Files.readString(path);
                    } catch (final Exception exception) {
                        return exception.toString();
                    }
                })
                .filter(raw -> raw.contains("termination=FORCED_AFTER_COMPLETED_RESPONSE"))
                .findFirst()
                .orElseThrow();
        assertTrue(forcedRaw.contains("forcedTerminationObserved=true"));
        assertTrue(forcedRaw.contains("responseBoundaryObserved=true"));
        assertTrue(forcedRaw.contains("responseCount=24"));
        final String processA = forcedRaw.lines()
                .filter(line -> line.startsWith("processA.pid="))
                .findFirst()
                .orElseThrow();
        final String processB = forcedRaw.lines()
                .filter(line -> line.startsWith("processB.pid="))
                .findFirst()
                .orElseThrow();
        assertNotEquals(processA, processB);
    }

    @Test
    void focusedRunCoversEveryApprovedCorruptionFixture(@TempDir final Path output) throws Exception {
        final GaDurabilityMatrix matrix = new GaDurabilityMatrix(
                "ga-g3-all-fixtures-test-v1",
                java.util.List.of(com.ultralatency.matching.persistence.wal.WalCommandCodec
                        .MIN_SEGMENT_SIZE_BYTES),
                1,
                0,
                24,
                GaDurabilityMatrix.APPROVED_SEED,
                java.util.List.of(GaDurabilityFixture.values()));
        final GaDurabilityCampaignResult result = new GaDurabilityRunner(testContext(output))
                .run(matrix, output);

        assertTrue(result.passed(), result.runs()::toString);
        assertEquals(1 + GaDurabilityFixture.values().length, result.runs().size());
        assertTrue(result.runs().stream().allMatch(
                GaDurabilityEvidence.RunReference::passed));
    }

    private static GaCorrectnessCanonicalContext testContext(final Path repository) {
        return new GaCorrectnessCanonicalContext(
                repository,
                "2".repeat(40),
                new GaCandidateVerifier.Verified(
                        "v0.9.0-rc.1",
                        "0".repeat(40),
                        "1".repeat(40),
                        "2".repeat(64),
                        "3".repeat(64)));
    }
}
