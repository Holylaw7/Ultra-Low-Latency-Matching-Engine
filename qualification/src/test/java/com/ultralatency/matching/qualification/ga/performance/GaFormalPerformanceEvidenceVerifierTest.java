package com.ultralatency.matching.qualification.ga.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.qualification.QualificationArtifactHasher;
import com.ultralatency.matching.qualification.ga.GaEvidenceCodec;
import com.ultralatency.matching.qualification.ga.GaEvidenceStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Direct regression coverage for the independent formal G4 evidence verifier. */
class GaFormalPerformanceEvidenceVerifierTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void missingRunEvidenceFailsClosedAsIntegrityFinding() {
        final GaFormalPerformanceEvidenceVerifier.Verification verification =
                GaFormalPerformanceEvidenceVerifier.verifyRun(temporaryDirectory);

        assertFalse(verification.passed());
        assertTrue(verification.findings().stream()
                .anyMatch(finding -> finding.contains("evidence reconstruction failed")));
    }

    @Test
    void independentPerformancePredicatesRejectFalsePass() {
        assertFalse(GaFormalPerformanceEvidenceVerifier.independentPerformancePass(
                299_999L, 600_000_000_000L, new long[]{1_000_000L}));
        assertFalse(GaFormalPerformanceEvidenceVerifier.independentPerformancePass(
                300_001L, 600_000_000_000L, new long[]{6_000_000L}));
    }

    @Test
    void independentPerformancePredicatesUseAcceptedAndRawLatency() {
        assertTrue(GaFormalPerformanceEvidenceVerifier.independentPerformancePass(
                300_001L, 600_000_000_000L,
                new long[]{1_000_000L, 2_000_000L, 3_000_000L}));
    }

    @Test
    void verifierFindingsPreserveBlockerTaxonomy() {
        assertEquals("B0", GaFormalPerformanceEvidenceVerifier.classifyFindings(
                List.of("payload hash mismatch")));
        assertEquals("B1", GaFormalPerformanceEvidenceVerifier.classifyFindings(
                List.of("raw evidence fails the independent G4 performance predicates")));
        assertEquals("B2", GaFormalPerformanceEvidenceVerifier.classifyFindings(
                List.of("PASS raw evidence is missing candidate health field: candidate.ready")));
        assertEquals("B3", GaFormalPerformanceEvidenceVerifier.classifyFindings(
                List.of("lifecycle configuration identity is not stable")));
        assertEquals("B4", GaFormalPerformanceEvidenceVerifier.classifyFindings(
                List.of("active governance contract is ambiguous")));
    }

    @Test
    void lifecycleThresholdIsAppliedIndependentlyOfSummary() {
        final long[] startup = new long[GaFormalPerformanceContract.LIFECYCLE_CYCLES];
        final long[] shutdown = new long[GaFormalPerformanceContract.LIFECYCLE_CYCLES];
        Arrays.fill(startup, GaPerformanceEvaluator.MAX_LIFECYCLE_P99_NANOS);
        Arrays.fill(shutdown, GaPerformanceEvaluator.MAX_LIFECYCLE_P99_NANOS);
        assertTrue(GaFormalPerformanceEvidenceVerifier.independentLifecyclePass(
                startup, shutdown));

        startup[startup.length - 1] = GaPerformanceEvaluator.MAX_LIFECYCLE_P99_NANOS + 1L;
        assertFalse(GaFormalPerformanceEvidenceVerifier.independentLifecyclePass(
                startup, shutdown));
    }

    @Test
    void performanceRunComparabilityIsRecomputedAcrossAllRuns() {
        assertTrue(GaFormalPerformanceEvidenceVerifier.independentPerformanceComparable(
                List.of("config", "config", "config"),
                List.of("environment", "environment", "environment")));
        assertFalse(GaFormalPerformanceEvidenceVerifier.independentPerformanceComparable(
                List.of("config", "different", "config"),
                List.of("environment", "environment", "environment")));
        assertFalse(GaFormalPerformanceEvidenceVerifier.independentPerformanceComparable(
                List.of("config", "config", "config"),
                List.of("environment", "different", "environment")));
    }

    @Test
    void campaignPassCannotContainFewerThanTheFrozenThreeRuns() throws Exception {
        final Path campaign = temporaryDirectory.resolve("campaign");
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put("campaign.completedAtUtc", Instant.EPOCH.plusSeconds(1).toString());
        fields.put("campaign.configurationIdentityEqual", "true");
        fields.put("campaign.id", UUID.randomUUID().toString());
        fields.put("campaign.outcome", "PASS");
        fields.put("campaign.requiredRunCount", "3");
        fields.put("campaign.startedAtUtc", Instant.EPOCH.toString());
        fields.put("campaign.validRunCount", "2");
        fields.put("candidate.applicationJarSha256", "0".repeat(64));
        fields.put("candidate.productionSha", "1".repeat(40));
        fields.put("candidate.tag", "v0.9.0-rc.2");
        fields.put("candidate.tagObjectSha", "2".repeat(40));
        fields.put("comparability.policy", "exact-runtime");
        fields.put("controller.gitSha", "3".repeat(40));
        fields.put("gate.id", "G4");
        fields.put("run.count", "2");
        fields.put("schema.version", GaEvidenceCodec.Schema.CAMPAIGN.version());
        Files.createDirectories(campaign);
        for (int index = 1; index <= 2; index++) {
            final Path run = campaign.resolve(String.format("run-%02d", index));
            Files.createDirectories(run);
            final Path manifest = run.resolve("ga-run-manifest-v1.txt");
            Files.writeString(manifest, "not-canonical\n", StandardCharsets.US_ASCII);
            final String prefix = String.format("run.%04d.", index);
            fields.put(prefix + "comparabilityIdentitySha256", "4".repeat(64));
            fields.put(prefix + "configurationIdentitySha256", "5".repeat(64));
            fields.put(prefix + "id", String.format(
                    "00000000-0000-0000-0000-%012d", index));
            fields.put(prefix + "manifestPath", campaign.relativize(manifest)
                    .toString().replace('\\', '/'));
            fields.put(prefix + "manifestSha256", QualificationArtifactHasher.sha256(manifest));
            fields.put(prefix + "outcome", "PASS");
        }
        GaEvidenceStore.publish(campaign.resolve("g4-campaign-manifest-v1.txt"),
                GaEvidenceCodec.Schema.CAMPAIGN, fields);

        final GaFormalPerformanceEvidenceVerifier.Verification verification =
                GaFormalPerformanceEvidenceVerifier.verifyCampaign(campaign);
        assertFalse(verification.passed());
        assertTrue(verification.findings().stream()
                .anyMatch(finding -> finding.contains("complete run set")));
    }
}
