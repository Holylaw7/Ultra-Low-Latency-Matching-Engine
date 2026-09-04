package com.ultralatency.matching.qualification.ga.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.qualification.ga.GaCandidateVerifier;
import com.ultralatency.matching.qualification.ga.correctness.GaCorrectnessCanonicalContext;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests the fail-closed identity and immutable boundary of formal G4. */
class GaFormalPerformanceContractTest {

    @Test
    void formalContractExposesFrozenRc2Configuration() {
        final GaPerformanceMatrix matrix = GaPerformanceMatrix.approved();
        final GaCorrectnessCanonicalContext context = rc2Context();

        final Map<String, String> fields = GaFormalPerformanceContract.configurationFields(
                context, matrix, "0".repeat(64), "1".repeat(64));

        assertEquals(GaFormalPerformanceContract.CAMPAIGN, fields.get("contract.version"));
        assertEquals("v2", fields.get("protocol.version"));
        assertEquals("8", fields.get("protocol.v2.window"));
        assertEquals("SYNC_EACH_APPEND", fields.get("wal.mode"));
        assertEquals("20260823", fields.get("workload.seed"));
        assertEquals(Duration.ofSeconds(60).toString(), fields.get("warmup.duration"));
        assertEquals(Duration.ofMinutes(10).toString(), fields.get("measurement.duration"));
        assertTrue(fields.get("load.model").contains("CLOSED_LOOP"));
    }

    @Test
    void formalIdentityRejectsNonRc2CandidateBeforeReadingArtifact() {
        final GaPerformanceMatrix matrix = GaPerformanceMatrix.approved();
        final GaCorrectnessCanonicalContext nonRc2 = new GaCorrectnessCanonicalContext(
                Path.of("."),
                "2".repeat(40),
                new GaCandidateVerifier.Verified(
                        "v0.9.0-rc.1", "0".repeat(40), "1".repeat(40),
                        "2".repeat(64), "3".repeat(64)));

        assertThrows(IOException.class, () -> GaFormalPerformanceContract.requireFrozenIdentity(
                nonRc2, matrix, Path.of("missing-candidate.jar")));
    }

    @Test
    void formalIdentityRejectsCandidateJarDigestMismatch() throws Exception {
        final Path artifact = java.nio.file.Files.createTempFile("ga-g4-contract-", ".jar");
        try {
            assertThrows(IOException.class, () -> GaFormalPerformanceContract
                    .requireFrozenIdentity(rc2Context(), GaPerformanceMatrix.approved(), artifact));
        } finally {
            java.nio.file.Files.deleteIfExists(artifact);
        }
    }

    private static GaCorrectnessCanonicalContext rc2Context() {
        return new GaCorrectnessCanonicalContext(
                Path.of("."),
                "2".repeat(40),
                new GaCandidateVerifier.Verified(
                        "v0.9.0-rc.2",
                        "9e2a67ada0e3b6220b730131d0bae79dc03073ed",
                        "740e8a3dea0a759c707c597778c26c41e9bb3e47",
                        "ef1d9f4cb64a9d6e331fb326ebe8f3b0abb29a53bf6045a5d4999a53e73b4bbc",
                        "0b77d37985b9124ac4fd1b90d669db550efd0cf00c23af65fdc29b35071703c4"));
    }
}
