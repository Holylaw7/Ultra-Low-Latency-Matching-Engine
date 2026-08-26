package com.ultralatency.matching.qualification.ga.correctness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.qualification.ga.GaCandidateVerifier;
import com.ultralatency.matching.qualification.ga.GaEvidenceCodec;
import com.ultralatency.matching.qualification.ga.GaEvidenceStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests the one-physical-execution/two-canonical-view evidence mapping. */
class GaCorrectnessCanonicalEvidenceTest {

    private static final String DIGEST =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @TempDir
    Path temporaryDirectory;

    @Test
    void onePhysicalCasePublishesIndependentG1AndG2Views() throws Exception {
        final GaCorrectnessMatrix matrix = GaCorrectnessMatrix.test();
        final GaCorrectnessCase matrixCase = matrix.cases().get(0);
        final Path caseDirectory = temporaryDirectory.resolve(matrixCase.id());
        Files.createDirectories(caseDirectory.resolve("wal"));
        Files.writeString(caseDirectory.resolve("wal/segment.log"), "wal\n",
                StandardCharsets.UTF_8);
        final GaCorrectnessCaseResult result = passedResult(matrixCase, caseDirectory);
        final GaCorrectnessCanonicalContext context =
                GaCorrectnessCanonicalContext.test(temporaryDirectory);
        final Instant started = Instant.parse("2026-08-26T00:00:00Z");
        final Instant completed = Instant.parse("2026-08-26T00:00:01Z");
        final String physicalId = UUID.randomUUID().toString();
        final GaCorrectnessCanonicalEvidence.ViewPair views =
                GaCorrectnessCanonicalEvidence.publishCaseViews(
                        caseDirectory,
                        matrix,
                        result,
                        context,
                        physicalId,
                        started,
                        completed,
                        1_000_000_000L);

        final Map<String, String> g1 = GaEvidenceStore.read(
                views.g1ManifestPath(), GaEvidenceCodec.Schema.RUN);
        final Map<String, String> g2 = GaEvidenceStore.read(
                views.g2ManifestPath(), GaEvidenceCodec.Schema.RUN);
        GaCorrectnessCanonicalEvidence.verifyRunManifestArtifacts(views.g1ManifestPath());
        GaCorrectnessCanonicalEvidence.verifyRunManifestArtifacts(views.g2ManifestPath());
        assertTrue(Files.isRegularFile(caseDirectory.resolve("wal/segment.log.sha256")));
        assertTrue(Files.isRegularFile(caseDirectory.resolve("SHA256SUMS.sha256")));
        assertTrue(Files.isRegularFile(caseDirectory.resolve(
                "ga-g1-run-manifest-v1.txt.sha256")));
        assertTrue(Files.isRegularFile(caseDirectory.resolve(
                "ga-g2-run-manifest-v1.txt.sha256")));
        assertTrue(Files.isRegularFile(caseDirectory.resolve(
                "ga-g1-g2-physical-run-binding-v1.txt.sha256")));
        assertEquals("G1", g1.get("gate.id"));
        assertEquals("G2", g2.get("gate.id"));
        assertEquals("g1-v1", g1.get("gate.version"));
        assertEquals("g2-v1", g2.get("gate.version"));
        assertNotEquals(g1.get("run.id"), g2.get("run.id"));
        assertEquals(g1.get("evidence.startedAtUtc"), g2.get("evidence.startedAtUtc"));
        assertEquals(g1.get("evidence.completedAtUtc"), g2.get("evidence.completedAtUtc"));
        assertEquals(g1.get("comparability.identitySha256"),
                g2.get("comparability.identitySha256"));
        assertEquals(g1.get("artifact.inventory.sha256"),
                g2.get("artifact.inventory.sha256"));

        final Map<String, String> binding = GaCorrectnessCanonicalEvidence.readBinding(
                views.bindingPath());
        assertEquals(physicalId, binding.get("physicalExecution.id"));
        assertEquals(views.g1ManifestSha256(), binding.get("g1.runManifestSha256"));
        assertEquals(views.g2ManifestSha256(), binding.get("g2.runManifestSha256"));

        final GaCorrectnessCanonicalEvidence.GatePair gates =
                GaCorrectnessCanonicalEvidence.publishGateResults(
                        temporaryDirectory,
                        matrix,
                        List.of(views),
                        context,
                        started,
                        completed,
                        true);
        assertTrue(GaEvidenceStore.read(
                gates.g1ResultPath(), GaEvidenceCodec.Schema.GATE).get("evidence.outcome")
                .equals("PASS"));
        assertTrue(GaEvidenceStore.read(
                gates.g2ResultPath(), GaEvidenceCodec.Schema.GATE).get("evidence.outcome")
                .equals("PASS"));
        assertTrue(Files.isRegularFile(temporaryDirectory.resolve(
                "ga-g1-gate-result-v1.txt.sha256")));
        assertTrue(Files.isRegularFile(temporaryDirectory.resolve(
                "ga-g2-gate-result-v1.txt.sha256")));

        final GaCorrectnessCanonicalEvidence.ViewPair swapped =
                new GaCorrectnessCanonicalEvidence.ViewPair(
                        views.physicalExecutionId(),
                        views.caseId(),
                        true,
                        views.recoveryObservationCount(),
                        views.g2ManifestPath(),
                        views.g2ManifestSha256(),
                        views.g1ManifestPath(),
                        views.g1ManifestSha256(),
                        views.bindingPath(),
                        views.bindingSha256());
        assertThrows(java.io.IOException.class, () ->
                GaCorrectnessCanonicalEvidence.publishGateResults(
                        temporaryDirectory.resolve("swapped"),
                        matrix,
                        List.of(swapped),
                        context,
                        started,
                        completed,
                        true));

        final GaCandidateVerifier.Verified candidate = context.candidate();
        final GaCorrectnessCanonicalContext changedCandidate =
                new GaCorrectnessCanonicalContext(
                        temporaryDirectory,
                        context.controllerGitSha(),
                        new GaCandidateVerifier.Verified(
                                candidate.tag(),
                                candidate.tagObjectSha(),
                                "3".repeat(40),
                                candidate.productionTreeSha256(),
                                candidate.applicationJarSha256()));
        assertThrows(IOException.class, () ->
                GaCorrectnessCanonicalEvidence.publishGateResults(
                        temporaryDirectory,
                        matrix,
                        List.of(views),
                        changedCandidate,
                        started,
                        completed,
                        true));

        final GaCorrectnessMatrix changedMatrix = new GaCorrectnessMatrix(
                "ga-g1-g2-test-mutated-v1",
                matrix.commandCount(),
                matrix.walSegmentSizeBytes(),
                matrix.profiles(),
                matrix.seeds(),
                matrix.repetitions(),
                matrix.snapshotPrefixes());
        assertThrows(IOException.class, () ->
                GaCorrectnessCanonicalEvidence.publishGateResults(
                        temporaryDirectory,
                        changedMatrix,
                        List.of(views),
                        context,
                        started,
                        completed,
                        true));
    }

    @Test
    void rejectsCandidateIdentityOverrides() throws Exception {
        final String property = "qualification.candidate.productionSha";
        final String previous = System.getProperty(property);
        System.setProperty(property, "0".repeat(40));
        try {
            assertThrows(IOException.class, GaCorrectnessCanonicalContext::fromSystem);
        } finally {
            if (previous == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previous);
            }
        }
    }

    @Test
    void rejectsCrossCaseSwapBindingMismatchAndUnlistedArtifacts() throws Exception {
        final GaCorrectnessMatrix matrix = new GaCorrectnessMatrix(
                "ga-g1-g2-two-case-test-v1",
                96,
                GaCorrectnessMatrix.APPROVED_WAL_SEGMENT_SIZE_BYTES,
                List.of(com.ultralatency.matching.qualification.QualificationProfile
                        .CROSSING_MULTI_MATCH),
                List.of(20260823L),
                2,
                List.of(24, 48, 72));
        final GaCorrectnessCanonicalContext context =
                GaCorrectnessCanonicalContext.test(temporaryDirectory);
        final java.util.ArrayList<GaCorrectnessCanonicalEvidence.ViewPair> views =
                new java.util.ArrayList<>();
        for (GaCorrectnessCase matrixCase : matrix.cases()) {
            final Path caseDirectory = temporaryDirectory.resolve(matrixCase.id());
            Files.createDirectories(caseDirectory.resolve("wal"));
            Files.writeString(caseDirectory.resolve("wal/segment.log"), "wal\n",
                    StandardCharsets.UTF_8);
            final Map<String, String> runtime = new TreeMap<>(
                    GaCorrectnessRuntimeProvenance.capture(caseDirectory));
            if (matrixCase.repetition() == 2) {
                runtime.put("runtime.osVersion", "mutated-for-test");
            }
            views.add(GaCorrectnessCanonicalEvidence.publishCaseViews(
                    caseDirectory,
                    matrix,
                    passedResult(matrixCase, caseDirectory),
                    context,
                    UUID.randomUUID().toString(),
                    Instant.parse("2026-08-26T00:00:00Z"),
                    Instant.parse("2026-08-26T00:00:01Z"),
                    1_000_000_000L,
                    runtime));
        }
        assertThrows(java.io.IOException.class, () ->
                GaCorrectnessCanonicalEvidence.publishGateResults(
                        temporaryDirectory,
                        matrix,
                        List.of(views.get(1), views.get(0)),
                        context,
                        Instant.parse("2026-08-26T00:00:00Z"),
                        Instant.parse("2026-08-26T00:00:03Z"),
                        true));

        assertThrows(IOException.class, () ->
                GaCorrectnessCanonicalEvidence.publishGateResults(
                        temporaryDirectory,
                        matrix,
                        List.copyOf(views),
                        context,
                        Instant.parse("2026-08-26T00:00:00Z"),
                        Instant.parse("2026-08-26T00:00:03Z"),
                        true));

        final GaCorrectnessCanonicalEvidence.ViewPair bindingMismatch =
                new GaCorrectnessCanonicalEvidence.ViewPair(
                        views.get(1).physicalExecutionId(),
                        views.get(1).caseId(),
                        views.get(1).casePassed(),
                        views.get(1).recoveryObservationCount(),
                        views.get(1).g1ManifestPath(),
                        views.get(1).g1ManifestSha256(),
                        views.get(1).g2ManifestPath(),
                        views.get(1).g2ManifestSha256(),
                        views.get(0).bindingPath(),
                        views.get(0).bindingSha256());
        assertThrows(java.io.IOException.class, () ->
                GaCorrectnessCanonicalEvidence.publishGateResults(
                        temporaryDirectory,
                        matrix,
                        List.of(views.get(0), bindingMismatch),
                        context,
                        Instant.parse("2026-08-26T00:00:00Z"),
                        Instant.parse("2026-08-26T00:00:01Z"),
                        true));

        Files.writeString(temporaryDirectory.resolve(matrix.cases().get(0).id())
                .resolve("unexpected.txt"), "unexpected\n", StandardCharsets.UTF_8);
        assertThrows(java.io.IOException.class, () ->
                GaCorrectnessCanonicalEvidence.verifyRunManifestArtifacts(
                        views.get(0).g1ManifestPath()));
        Files.deleteIfExists(temporaryDirectory.resolve(matrix.cases().get(0).id())
                .resolve("unexpected.txt"));

        Files.writeString(temporaryDirectory.resolve(matrix.cases().get(0).id())
                .resolve("wal/segment.log.sha256"),
                "0".repeat(64) + "  segment.log\n", StandardCharsets.US_ASCII);
        assertThrows(IOException.class, () ->
                GaCorrectnessCanonicalEvidence.verifyRunManifestArtifacts(
                        views.get(0).g1ManifestPath()));
    }

    private static GaCorrectnessCaseResult passedResult(
            final GaCorrectnessCase matrixCase,
            final Path directory) {
        final GaCorrectnessObservation live = observation("LIVE", 0);
        final GaCorrectnessObservation pure = observation("PURE_WAL", 0);
        final GaCorrectnessObservation first = observation("SNAPSHOT_THEN_WAL", 24);
        final GaCorrectnessObservation second = observation("SNAPSHOT_THEN_WAL", 48);
        final GaCorrectnessObservation third = observation("SNAPSHOT_THEN_WAL", 72);
        return new GaCorrectnessCaseResult(
                matrixCase,
                List.of(live, pure, first, second, third),
                Map.of(24, DIGEST, 48, DIGEST, 72, DIGEST),
                true,
                List.of(),
                directory);
    }

    private static GaCorrectnessObservation observation(final String mode, final int prefix) {
        return new GaCorrectnessObservation(mode, prefix, 96, 0, DIGEST, DIGEST, DIGEST, DIGEST);
    }
}
