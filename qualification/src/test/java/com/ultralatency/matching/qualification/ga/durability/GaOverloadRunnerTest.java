package com.ultralatency.matching.qualification.ga.durability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.qualification.ga.GaCandidateVerifier;
import com.ultralatency.matching.qualification.ga.GaEvidenceCodec;
import com.ultralatency.matching.qualification.ga.GaGateEvaluator;
import com.ultralatency.matching.qualification.ga.correctness.GaCorrectnessCanonicalContext;
import java.io.EOFException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises the bounded public-boundary G7 probes and evidence publication. */
class GaOverloadRunnerTest {

    @Test
    void timeoutIsNotEvidenceOfDeterministicFrameRejection() {
        assertTrue(GaOverloadRunner.isDeterministicFrameClose(
                new EOFException("peer closed after rejection")));
        assertFalse(GaOverloadRunner.isDeterministicFrameClose(
                new SocketTimeoutException("no response")));
    }

    @Test
    void focusedMatrixProvesBoundedRejection(@TempDir final Path output) throws Exception {
        final GaOverloadCampaignResult result = new GaOverloadRunner(testContext(output))
                .run(GaOverloadMatrix.test(), output);

        assertTrue(result.passed(), () -> result.runs().stream()
                .map(reference -> {
                    try {
                        return reference.manifestPath().getParent().resolve("raw-evidence-v1.txt")
                                + "=" + Files.readString(reference.manifestPath().getParent()
                                .resolve("raw-evidence-v1.txt"));
                    } catch (final Exception exception) {
                        return exception.toString();
                    }
                }).toList().toString());
        assertEquals(GaOverloadScenario.values().length, result.runs().size());
        assertTrue(Files.isRegularFile(result.gateResultPath()));
        assertTrue(Files.isRegularFile(result.summaryPath()));
        assertTrue(Files.readString(result.gateResultPath()).contains("evidence.outcome=PASS"));
        final Map<String, String> campaign = GaEvidenceCodec.decode(
                GaEvidenceCodec.Schema.CAMPAIGN,
                Files.readAllBytes(result.summaryPath()));
        assertEquals("G7", campaign.get("gate.id"));
        assertEquals(Integer.toString(GaOverloadScenario.values().length),
                campaign.get("campaign.requiredRunCount"));
        assertEquals(Integer.toString(GaOverloadScenario.values().length), campaign.get("run.count"));
        assertTrue(GaGateEvaluator.evaluateCampaign(campaign).passed());
        assertTrue(result.runs().stream().map(reference -> reference.manifestPath().getParent())
                .map(path -> path.resolve("raw-evidence-v1.txt"))
                .map(path -> {
                    try {
                        return Files.readString(path);
                    } catch (final Exception exception) {
                        return exception.toString();
                    }
                })
                .allMatch(raw -> raw.contains("observableContract=")));
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
