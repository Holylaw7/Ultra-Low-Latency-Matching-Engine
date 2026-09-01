package com.ultralatency.matching.qualification.ga.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.qualification.QualificationWorkloadV1;
import com.ultralatency.matching.qualification.ga.GaCandidateVerifier;
import com.ultralatency.matching.qualification.ga.GaEvidenceCodec;
import com.ultralatency.matching.qualification.ga.GaEvidenceStore;
import com.ultralatency.matching.qualification.ga.correctness.GaCorrectnessCanonicalContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests the canonical raw/sidecar/inventory/manifest publication chain. */
class GaQuickEvidencePublisherTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void publishesValidatedRunAndReadinessGate() throws Exception {
        final Path root = temporaryDirectory.resolve("g4-quick");
        final String digest = "0".repeat(64);
        final GaCorrectnessCanonicalContext context = new GaCorrectnessCanonicalContext(
                temporaryDirectory,
                "2".repeat(40),
                new GaCandidateVerifier.Verified(
                        "v0.9.0-rc.1", "0".repeat(40), "1".repeat(40), digest, digest));
        final GaPerformanceObservation observation = new GaPerformanceObservation(
                2, 2, 2, 2_000_000L, new long[]{1L, 2L}, new long[]{1L}, new long[]{1L},
                1000.0, 1000.0, 1L, 1L, 0, 0, 0, true, true, true, true, true);
        final GaPerformanceEvaluator.Evaluation evaluation =
                GaPerformanceEvaluator.evaluateQuick(observation);
        final Map<String, String> configuration = new LinkedHashMap<>();
        configuration.put("lane", "QUICK");
        configuration.put("profile", "MEMORY_STEADY_STATE_V1");
        final Instant started = Instant.parse("2026-09-01T00:00:00Z");
        final Instant completed = Instant.parse("2026-09-01T00:00:01Z");
        final GaQuickEvidencePublisher.RunInput input = new GaQuickEvidencePublisher.RunInput(
                "G4", "ga-g4-quick-v1", "MEMORY_STEADY_STATE_V1", 20260823L, 2,
                QualificationWorkloadV1.MEMORY_STEADY_STATE_VERSION, started, completed,
                "PASS", "NONE", 2, 2, 0, "quick=true\n", new long[]{1L, 2L},
                configuration, context);
        final GaQuickEvidencePublisher.PublishedRun run =
                GaQuickEvidencePublisher.publishRun(root, input);
        final Path gate = GaQuickEvidencePublisher.publishGate(
                temporaryDirectory, "G4", "ga-g4-quick-v1", run, evaluation.criteria(), context,
                started, completed, "QUICK_READINESS_ONLY", "not formal");
        final Map<String, String> manifest = GaEvidenceStore.read(
                run.manifestPath(), GaEvidenceCodec.Schema.RUN);
        final Map<String, String> gateFields = GaEvidenceStore.read(gate, GaEvidenceCodec.Schema.GATE);
        assertEquals("G4", manifest.get("gate.id"));
        assertEquals("PASS", manifest.get("evidence.outcome"));
        assertEquals("PASS", gateFields.get("evidence.outcome"));
        assertTrue(Files.isRegularFile(root.resolve("raw-evidence-v1.txt.sha256")));
        assertTrue(Files.isRegularFile(root.resolve("SHA256SUMS.sha256")));
    }
}
