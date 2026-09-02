package com.ultralatency.matching.qualification.ga.soak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ultralatency.matching.qualification.ga.GaCandidateVerifier;
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

/** Tests strict distinct G6/G8 physical-run binding publication. */
class GaG6G8PhysicalRunBindingTest {

    private static final String GIT = "0123456789abcdef0123456789abcdef01234567";
    private static final String SHA =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @TempDir
    Path temporaryDirectory;

    @Test
    void publishesAndVerifiesDistinctGateIdentities() throws Exception {
        final GaSoakEvidencePublisher.PublishedQuick published = publishFixture();
        final GaG6G8PhysicalRunBinding.Fields read =
                GaG6G8PhysicalRunBinding.verify(published.bindingPath());

        assertEquals(published.g6().runId(), read.g6RunId());
        assertEquals(published.g8().runId(), read.g8RunId());
        assertEquals(published.physicalExecutionId(), read.physicalExecutionId());
        assertEquals(17, Files.readAllLines(published.bindingPath()).size());
    }

    @Test
    void rejectsSharedRunIdAndTamperedBindingSidecar() throws Exception {
        final GaG6G8PhysicalRunBinding.Fields valid = fields();
        assertThrows(IllegalArgumentException.class, () ->
                new GaG6G8PhysicalRunBinding.Fields(
                        valid.physicalExecutionId(), valid.stage(), valid.g6RunId(),
                        valid.g6ManifestPath(), valid.g6ManifestSha256(), valid.g6RunId(),
                        valid.g8ManifestPath(), valid.g8ManifestSha256(), valid.controllerGitSha(),
                        valid.candidateTag(), valid.candidateTagObjectSha(),
                        valid.candidateProductionSha(), valid.candidateApplicationJarSha256(),
                        valid.candidateProductionTreeSha256(), valid.configurationIdentitySha256(),
                        valid.inventorySha256()));

        final Path target = temporaryDirectory.resolve("binding.txt");
        GaG6G8PhysicalRunBinding.publish(target, valid);
        Files.writeString(target.resolveSibling("binding.txt.sha256"), SHA + "  binding.txt\n");
        assertThrows(java.io.IOException.class, () -> GaG6G8PhysicalRunBinding.verify(target));
    }

    @Test
    void rejectsManifestRunStageControllerCandidateConfigAndInventorySubstitution() throws Exception {
        final GaSoakEvidencePublisher.PublishedQuick published = publishFixture();
        final GaG6G8PhysicalRunBinding.Fields base =
                GaG6G8PhysicalRunBinding.read(published.bindingPath());

        assertThrows(java.io.IOException.class, () -> GaG6G8PhysicalRunBinding.verify(
                publishTampered("wrong-physical.txt", copy(base,
                        "00000000-0000-4000-8000-000000000099", base.stage(), base.g6RunId(),
                        base.g8RunId(), base.controllerGitSha(), base.candidateTag(),
                        base.configurationIdentitySha256(), base.inventorySha256()))));
        assertThrows(java.io.IOException.class, () -> GaG6G8PhysicalRunBinding.verify(
                publishTampered("wrong-stage.txt", copy(base, base.physicalExecutionId(),
                        GaSoakMatrix.Stage.STAGE_A, base.g6RunId(), base.g8RunId(),
                        base.controllerGitSha(), base.candidateTag(),
                        base.configurationIdentitySha256(), base.inventorySha256()))));
        assertThrows(java.io.IOException.class, () -> GaG6G8PhysicalRunBinding.verify(
                publishTampered("wrong-run.txt", copy(base, base.physicalExecutionId(), base.stage(),
                        "00000000-0000-4000-8000-000000000099", base.g8RunId(),
                        base.controllerGitSha(), base.candidateTag(),
                        base.configurationIdentitySha256(), base.inventorySha256()))));
        assertThrows(java.io.IOException.class, () -> GaG6G8PhysicalRunBinding.verify(
                publishTampered("wrong-controller.txt", copy(base, base.physicalExecutionId(),
                        base.stage(), base.g6RunId(), base.g8RunId(),
                        "fedcba9876543210fedcba9876543210fedcba98", base.candidateTag(),
                        base.configurationIdentitySha256(), base.inventorySha256()))));
        assertThrows(java.io.IOException.class, () -> GaG6G8PhysicalRunBinding.verify(
                publishTampered("wrong-candidate.txt", copy(base, base.physicalExecutionId(),
                        base.stage(), base.g6RunId(), base.g8RunId(), base.controllerGitSha(),
                        "v0.9.0-rc.2", base.configurationIdentitySha256(), base.inventorySha256()))));
        assertThrows(java.io.IOException.class, () -> GaG6G8PhysicalRunBinding.verify(
                publishTampered("wrong-config.txt", copy(base, base.physicalExecutionId(), base.stage(),
                        base.g6RunId(), base.g8RunId(), base.controllerGitSha(), base.candidateTag(),
                        "abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd",
                        base.inventorySha256()))));
        assertThrows(java.io.IOException.class, () -> GaG6G8PhysicalRunBinding.verify(
                publishTampered("wrong-inventory.txt", copy(base, base.physicalExecutionId(),
                        base.stage(), base.g6RunId(), base.g8RunId(), base.controllerGitSha(),
                        base.candidateTag(), base.configurationIdentitySha256(),
                        "abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd"))));

        Files.delete(published.bindingPath().resolveSibling("raw.txt"));
        assertThrows(java.io.IOException.class,
                () -> GaG6G8PhysicalRunBinding.verify(published.bindingPath()));
    }

    private static GaG6G8PhysicalRunBinding.Fields fields() {
        return new GaG6G8PhysicalRunBinding.Fields(
                "00000000-0000-4000-8000-000000000001",
                GaSoakMatrix.Stage.QUICK,
                "00000000-0000-4000-8000-000000000002",
                "g6-run-manifest-v1.txt", SHA,
                "00000000-0000-4000-8000-000000000003",
                "g8-run-manifest-v1.txt", SHA,
                GIT, "v0.9.0-rc.1", GIT, GIT, SHA, SHA, SHA, SHA);
    }

    private GaSoakEvidencePublisher.PublishedQuick publishFixture() throws Exception {
        final GaSoakMatrix matrix = GaSoakMatrix.quick();
        final GaCorrectnessCanonicalContext context = new GaCorrectnessCanonicalContext(
                temporaryDirectory, GIT,
                new GaCandidateVerifier.Verified("v0.9.0-rc.1", GIT, GIT, SHA, SHA));
        final String physical = "00000000-0000-4000-8000-000000000010";
        final GaSoakObservation g6 = new GaSoakObservation(
                physical, GaSoakMatrix.Stage.QUICK, matrix.duration().toNanos(), 10_000L,
                10_000L, 0, 0, 0, new long[0], new long[0], new long[0], List.of(),
                List.of(), true, true, true, true, true, true, true, true, true, true,
                12_000L, 12_000L, 0L);
        final GaObservabilityObservation g8 = new GaObservabilityObservation(
                physical, GaSoakMatrix.Stage.QUICK, List.of(), GaGcEvidence.quick("NONE"),
                GaJfrEvidence.valid(temporaryDirectory.resolve("fixture.jfr")),
                List.of(GaManagementEvidence.live(1, true), GaManagementEvidence.ready(1, true),
                        GaManagementEvidence.status(1, true, true, "READY", "NONE", true,
                                "PURE_WAL", 10, 0, 20),
                        GaManagementEvidence.metrics(1, true, true, "READY", "NONE", true,
                                "PURE_WAL", 10, 0, 20, 4, 0)),
                true, true, 0, false, false, true, true, true, true);
        final Path output = temporaryDirectory.resolve("published");
        Files.createDirectories(output);
        final Path raw = output.resolve("raw.txt");
        Files.writeString(raw, "fixture=true\n");
        final Path samples = output.resolve("resource-samples-v1.csv");
        Files.writeString(samples, "physicalExecutionId,stage,sequence,monotonicNanos,threads,"
                + "transientCount,transientBytes,heapUsedBytes\n"
                + physical + ",QUICK,0,1,1,0,0,1\n");
        return GaSoakEvidencePublisher.publishQuick(output, matrix,
                g6, g8, GaSoakEvaluator.evaluateQuick(matrix, g6),
                GaObservabilityEvaluator.evaluateQuick(matrix, g8), context,
                Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-01T00:01:00Z"),
                Map.of("raw.txt", raw, "resource-samples-v1.csv", samples));
    }

    private Path publishTampered(
            final String name,
            final GaG6G8PhysicalRunBinding.Fields fields) throws Exception {
        return GaG6G8PhysicalRunBinding.publish(
                temporaryDirectory.resolve("published").resolve(name), fields).path();
    }

    private static GaG6G8PhysicalRunBinding.Fields copy(
            final GaG6G8PhysicalRunBinding.Fields base,
            final String physicalExecutionId,
            final GaSoakMatrix.Stage stage,
            final String g6RunId,
            final String g8RunId,
            final String controller,
            final String candidateTag,
            final String configuration,
            final String inventory) {
        return new GaG6G8PhysicalRunBinding.Fields(physicalExecutionId, stage, g6RunId,
                base.g6ManifestPath(), base.g6ManifestSha256(), g8RunId,
                base.g8ManifestPath(), base.g8ManifestSha256(), controller, candidateTag,
                base.candidateTagObjectSha(), base.candidateProductionSha(),
                base.candidateApplicationJarSha256(), base.candidateProductionTreeSha256(),
                configuration, inventory);
    }
}
