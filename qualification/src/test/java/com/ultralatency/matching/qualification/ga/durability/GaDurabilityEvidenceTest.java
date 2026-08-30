package com.ultralatency.matching.qualification.ga.durability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ultralatency.matching.qualification.ga.GaCandidateVerifier;
import com.ultralatency.matching.qualification.ga.GaEvidenceCodec;
import com.ultralatency.matching.qualification.ga.GaEvidenceStore;
import com.ultralatency.matching.qualification.ga.correctness.GaCorrectnessCanonicalContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies that campaign publication cannot turn invalid run membership into a PASS. */
class GaDurabilityEvidenceTest {

    private static final Instant STARTED = Instant.parse("2026-08-30T00:00:00Z");
    private static final Instant COMPLETED = Instant.parse("2026-08-30T00:00:01Z");

    @Test
    void manifestDigestMismatchCannotPublishGate(@TempDir final Path output) throws Exception {
        final GaCorrectnessCanonicalContext context = context(output);
        final GaDurabilityEvidence.RunReference reference = validReference(output, context);
        final GaDurabilityEvidence.RunReference tampered = new GaDurabilityEvidence.RunReference(
                reference.runId(), reference.gate(), reference.manifestPath(), "0".repeat(64),
                reference.configurationIdentitySha256(), reference.comparabilityIdentitySha256(),
                true);

        assertThrows(IOException.class, () -> GaDurabilityEvidence.publishGate(
                output, "G3", "g3-v1", List.of(tampered), context, STARTED, COMPLETED,
                List.of(new GaDurabilityEvidence.Criterion("manifest", "1", "EQ", "1", true)),
                List.of()));
    }

    @Test
    void missingManifestSidecarCannotPublishGate(@TempDir final Path output) throws Exception {
        final GaCorrectnessCanonicalContext context = context(output);
        final GaDurabilityEvidence.RunReference reference = validReference(output, context);
        Files.delete(reference.manifestPath().resolveSibling(
                reference.manifestPath().getFileName() + ".sha256"));

        assertThrows(IOException.class, () -> GaDurabilityEvidence.publishGate(
                output, "G3", "g3-v1", List.of(reference), context, STARTED, COMPLETED,
                List.of(new GaDurabilityEvidence.Criterion("manifest", "1", "EQ", "1", true)),
                List.of()));
    }

    @Test
    void duplicateMembershipProducesFailGateNotPass(@TempDir final Path output) throws Exception {
        final GaCorrectnessCanonicalContext context = context(output);
        final GaDurabilityEvidence.RunReference reference = validReference(output, context);
        final Path gate = GaDurabilityEvidence.publishGate(
                output, "G3", "g3-v1", List.of(reference, reference), context,
                STARTED, COMPLETED,
                List.of(new GaDurabilityEvidence.Criterion("membership", "2", "EQ", "2", true)),
                List.of());

        final Map<String, String> fields = GaEvidenceStore.read(gate, GaEvidenceCodec.Schema.GATE);
        assertEquals("FAIL", fields.get("evidence.outcome"));
        assertEquals("B0", fields.get("blocker.classification"));
    }

    private static GaDurabilityEvidence.RunReference validReference(
            final Path output,
            final GaCorrectnessCanonicalContext context) throws Exception {
        return GaDurabilityEvidence.publishRun(
                output.resolve("run-001"),
                "G3",
                "g3-v1",
                20_260_823L,
                24,
                4_128,
                "workload-v1",
                context,
                STARTED,
                COMPLETED,
                true,
                "NONE",
                "fixture=valid\n");
    }

    private static GaCorrectnessCanonicalContext context(final Path output) {
        return new GaCorrectnessCanonicalContext(
                output,
                "2".repeat(40),
                new GaCandidateVerifier.Verified(
                        "v0.9.0-rc.1",
                        "0".repeat(40),
                        "1".repeat(40),
                        "2".repeat(64),
                        "3".repeat(64)));
    }
}
