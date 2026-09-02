package com.ultralatency.matching.qualification.ga.soak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.qualification.ga.GaCandidateVerifier;
import com.ultralatency.matching.qualification.ga.GaEvidenceCodec;
import com.ultralatency.matching.qualification.ga.GaEvidenceStore;
import com.ultralatency.matching.qualification.ga.correctness.GaCorrectnessCanonicalContext;
import com.ultralatency.matching.qualification.ga.observability.GaGcEvidence;
import com.ultralatency.matching.qualification.ga.observability.GaJfrEvidence;
import com.ultralatency.matching.qualification.ga.observability.GaManagementEvidence;
import com.ultralatency.matching.qualification.ga.observability.GaObservabilityEvaluator;
import com.ultralatency.matching.qualification.ga.observability.GaObservabilityObservation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests the paired Quick raw-to-manifest-to-gate evidence chain. */
class GaSoakEvidencePublisherTest {

    private static final String GIT = "0123456789abcdef0123456789abcdef01234567";
    private static final String SHA =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @TempDir
    Path temporaryDirectory;

    @Test
    void publishesIndependentG6AndG8QuickChainsBoundToOnePhysicalRun() throws Exception {
        final GaSoakMatrix matrix = GaSoakMatrix.quick();
        final GaCorrectnessCanonicalContext context = context();
        final GaSoakObservation g6 = new GaSoakObservation(
                "00000000-0000-4000-8000-000000000010", GaSoakMatrix.Stage.QUICK,
                GaSoakMatrix.QUICK_DURATION.toNanos(), 10_000L, 10_000L, 0, 0, 0,
                new long[0], new long[0],
                new long[0], List.<GaSoakResourceSample>of(), List.<GaNaturalGcSample>of(),
                true, true, true, true, true, true, true, true, true, true);
        final GaObservabilityObservation g8 = new GaObservabilityObservation(
                g6.physicalExecutionId(), GaSoakMatrix.Stage.QUICK, List.of(),
                GaGcEvidence.quick("NONE"), GaJfrEvidence.valid(temporaryDirectory.resolve("jfr")),
                List.of(
                        GaManagementEvidence.live(1, true),
                        GaManagementEvidence.ready(1, true),
                        GaManagementEvidence.status(1, true, true, "READY", "NONE", true,
                                "PURE_WAL", 10, 0, 20),
                        GaManagementEvidence.metrics(1, true, true, "READY", "NONE", true,
                                "PURE_WAL", 10, 0, 20, 4, 0)),
                true, true, 0, false, false, true, true, true, true);
        final GaSoakEvaluator.Evaluation g6Evaluation =
                GaSoakEvaluator.evaluateQuick(matrix, g6);
        final GaObservabilityEvaluator.Evaluation g8Evaluation =
                GaObservabilityEvaluator.evaluateQuick(matrix, g8);
        final Path root = temporaryDirectory.resolve("quick");
        final Path raw = root.resolve("raw.txt");
        Files.createDirectories(root);
        Files.writeString(raw, "quick=true\n");
        final Path samples = root.resolve("resource-samples-v1.csv");
        Files.writeString(samples, "physicalExecutionId,stage,sequence,monotonicNanos,threads,"
                + "transientCount,transientBytes,heapUsedBytes\n"
                + g6.physicalExecutionId() + ",QUICK,0,1,1,0,0,1\n");
        final GaSoakEvidencePublisher.PublishedQuick publication =
                GaSoakEvidencePublisher.publishQuick(root, matrix, g6, g8, g6Evaluation,
                        g8Evaluation, context, Instant.parse("2026-09-01T00:00:00Z"),
                        Instant.parse("2026-09-01T00:00:01Z"),
                        Map.of("raw.txt", raw, "resource-samples-v1.csv", samples));

        assertEquals("PASS", g6Evaluation.outcome());
        assertEquals("PASS", g8Evaluation.outcome());
        assertNotEquals(publication.g6().runId(), publication.g8().runId());
        assertEquals("G6", GaEvidenceStore.read(publication.g6().manifestPath(),
                GaEvidenceCodec.Schema.RUN).get("gate.id"));
        assertEquals("G8", GaEvidenceStore.read(publication.g8().manifestPath(),
                GaEvidenceCodec.Schema.RUN).get("gate.id"));
        assertEquals("PASS", GaEvidenceStore.read(publication.g6GatePath(),
                GaEvidenceCodec.Schema.GATE).get("evidence.outcome"));
        assertEquals("PASS", GaEvidenceStore.read(publication.g8GatePath(),
                GaEvidenceCodec.Schema.GATE).get("evidence.outcome"));
        assertTrue(Files.isRegularFile(publication.inventoryPath()));
        assertEquals(publication.bindingSha256(),
                com.ultralatency.matching.qualification.QualificationArtifactHasher.sha256(
                        publication.bindingPath()));
        assertEquals(publication.physicalExecutionId(),
                GaG6G8PhysicalRunBinding.verify(publication.bindingPath()).physicalExecutionId());
    }

    private GaCorrectnessCanonicalContext context() {
        return new GaCorrectnessCanonicalContext(temporaryDirectory, GIT,
                new GaCandidateVerifier.Verified("v0.9.0-rc.1", GIT, GIT, SHA, SHA));
    }
}
