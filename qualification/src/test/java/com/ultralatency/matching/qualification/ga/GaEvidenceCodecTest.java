package com.ultralatency.matching.qualification.ga;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests GA evidence canonical bytes, field typing and immutable publication. */
class GaEvidenceCodecTest {

    private static final String GIT_SHA1 = "0123456789abcdef0123456789abcdef01234567";
    private static final String SHA256 =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsFullGitSha1AndRejectsWrongLexicalWidths() {
        assertDoesNotThrow(() -> GaEvidenceCodec.encode(
                GaEvidenceCodec.Schema.GATE, gateFields(GIT_SHA1, SHA256)));
        for (String value : new String[] {
            "0".repeat(39), "0".repeat(41), SHA256, GIT_SHA1.toUpperCase(),
            GIT_SHA1 + " "
        }) {
            final Map<String, String> fields = gateFields(GIT_SHA1, SHA256);
            fields.put("candidate.productionSha", value);
            assertThrows(IllegalArgumentException.class,
                    () -> GaEvidenceCodec.encode(GaEvidenceCodec.Schema.GATE, fields));
        }
    }

    @Test
    void sha256FieldsDoNotAcceptGitSha1Width() {
        final Map<String, String> fields = gateFields(GIT_SHA1, SHA256);
        fields.put("candidate.applicationJarSha256", GIT_SHA1);
        assertThrows(IllegalArgumentException.class,
                () -> GaEvidenceCodec.encode(GaEvidenceCodec.Schema.GATE, fields));
        fields.put("candidate.applicationJarSha256", SHA256);
        fields.put("controller.gitSha", SHA256);
        assertThrows(IllegalArgumentException.class,
                () -> GaEvidenceCodec.encode(GaEvidenceCodec.Schema.GATE, fields));
    }

    @Test
    void canonicalBytesAreStableAndMalformedBytesFailClosed() {
        final Map<String, String> fields = gateFields(GIT_SHA1, SHA256);
        final byte[] first = GaEvidenceCodec.encode(GaEvidenceCodec.Schema.GATE, fields);
        final byte[] second = GaEvidenceCodec.encode(GaEvidenceCodec.Schema.GATE,
                new LinkedHashMap<>(fields));
        assertEquals(new String(first, StandardCharsets.US_ASCII),
                new String(second, StandardCharsets.US_ASCII));
        assertEquals(fields, GaEvidenceCodec.decode(GaEvidenceCodec.Schema.GATE, first));
        assertThrows(IllegalArgumentException.class, () -> GaEvidenceCodec.decode(
                GaEvidenceCodec.Schema.GATE,
                "schema.version=ga-gate-result-v1\r\n".getBytes(StandardCharsets.US_ASCII)));
        assertThrows(IllegalArgumentException.class, () -> GaEvidenceCodec.decode(
                GaEvidenceCodec.Schema.GATE,
                (new String(first, StandardCharsets.US_ASCII)
                        + "unexpected.field=value\n").getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void gateEvaluatorRequiresAllCriteriaAndNoBlocker() {
        final Map<String, String> passing = gateFields(GIT_SHA1, SHA256);
        final GaGateEvaluator.GateDecision pass = GaGateEvaluator.evaluateGate(passing);
        assertTrue(pass.passed());
        final Map<String, String> failing = new LinkedHashMap<>(passing);
        failing.put("criterion.0001.result", "FAIL");
        failing.put("evidence.outcome", "FAIL");
        failing.put("blocker.classification", "B1");
        final GaGateEvaluator.GateDecision fail = GaGateEvaluator.evaluateGate(failing);
        assertFalse(fail.passed());
        assertEquals("B1", fail.blocker());
    }

    @Test
    void evidenceAndArtifactSidecarsPublishImmutably() throws Exception {
        final Path evidence = temporaryDirectory.resolve("gate.txt");
        final Map<String, String> fields = gateFields(GIT_SHA1, SHA256);
        final String digest = GaEvidenceStore.publish(
                evidence, GaEvidenceCodec.Schema.GATE, fields);
        assertEquals(GaEvidenceCodec.sha256(GaEvidenceCodec.Schema.GATE, fields), digest);
        assertEquals(fields, GaEvidenceStore.read(evidence, GaEvidenceCodec.Schema.GATE));
        assertThrows(java.io.IOException.class, () -> GaEvidenceStore.publish(
                evidence, GaEvidenceCodec.Schema.GATE, fields));

        final Path payload = temporaryDirectory.resolve("payload.txt");
        Files.writeString(payload, "payload\n", StandardCharsets.UTF_8);
        final Path sidecar = temporaryDirectory.resolve("SHA256SUMS");
        GaEvidenceStore.publishArtifactSidecar(sidecar, Map.of("payload.txt", payload));
        assertEquals(Map.of("payload.txt", com.ultralatency.matching.qualification
                .QualificationArtifactHasher.sha256(payload)),
                GaEvidenceStore.readArtifactSidecar(sidecar));
        assertThrows(java.io.IOException.class, () -> GaEvidenceStore.publishArtifactSidecar(
                sidecar, Map.of("payload.txt", payload)));
    }

    @Test
    void campaignAndReleaseUseGitSha1OnlyForGitIdentityFields() {
        assertDoesNotThrow(() -> GaEvidenceCodec.encode(
                GaEvidenceCodec.Schema.CAMPAIGN, campaignFields(GIT_SHA1, SHA256)));
        assertDoesNotThrow(() -> GaEvidenceCodec.encode(
                GaEvidenceCodec.Schema.RELEASE, releaseFields(GIT_SHA1, SHA256)));

        final Map<String, String> campaign = campaignFields(GIT_SHA1, SHA256);
        campaign.put("candidate.tagObjectSha", SHA256);
        assertThrows(IllegalArgumentException.class, () -> GaEvidenceCodec.encode(
                GaEvidenceCodec.Schema.CAMPAIGN, campaign));

        final Map<String, String> release = releaseFields(GIT_SHA1, SHA256);
        release.put("release.releaseSourceSha", SHA256);
        assertThrows(IllegalArgumentException.class, () -> GaEvidenceCodec.encode(
                GaEvidenceCodec.Schema.RELEASE, release));
    }

    private static Map<String, String> gateFields(
            final String gitSha1, final String sha256) {
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put("blocker.classification", "NONE");
        fields.put("candidate.applicationJarSha256", sha256);
        fields.put("candidate.productionSha", gitSha1);
        fields.put("candidate.productionTreeSha256", sha256);
        fields.put("candidate.tag", "v0.9.0-rc.1");
        fields.put("candidate.tagObjectSha", gitSha1);
        fields.put("comparability.identitySha256", sha256);
        fields.put("configuration.identitySha256", sha256);
        fields.put("controller.gitSha", gitSha1);
        fields.put("criterion.count", "1");
        fields.put("criterion.0001.id", "C1");
        fields.put("criterion.0001.actual", "1");
        fields.put("criterion.0001.operator", "EQ");
        fields.put("criterion.0001.required", "1");
        fields.put("criterion.0001.result", "PASS");
        fields.put("evidence.completedAtUtc", "2026-08-25T00:01:00Z");
        fields.put("evidence.outcome", "PASS");
        fields.put("evidence.startedAtUtc", "2026-08-25T00:00:00Z");
        fields.put("gate.id", "G1");
        fields.put("gate.version", "g1-v1");
        fields.put("limitation.count", "0");
        fields.put("manifest.count", "1");
        fields.put("manifest.0001.path", "run.txt");
        fields.put("manifest.0001.sha256", sha256);
        fields.put("schema.version", "ga-gate-result-v1");
        return fields;
    }

    private static Map<String, String> campaignFields(
            final String gitSha1, final String sha256) {
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put("campaign.completedAtUtc", "2026-08-25T00:01:00Z");
        fields.put("campaign.configurationIdentityEqual", "true");
        fields.put("campaign.id", "00000000-0000-4000-8000-000000000001");
        fields.put("campaign.outcome", "PASS");
        fields.put("campaign.requiredRunCount", "1");
        fields.put("campaign.startedAtUtc", "2026-08-25T00:00:00Z");
        fields.put("campaign.validRunCount", "1");
        fields.put("candidate.applicationJarSha256", sha256);
        fields.put("candidate.productionSha", gitSha1);
        fields.put("candidate.tag", "v0.9.0-rc.1");
        fields.put("candidate.tagObjectSha", gitSha1);
        fields.put("comparability.policy", "EXACT_ENVIRONMENT");
        fields.put("controller.gitSha", gitSha1);
        fields.put("gate.id", "G1");
        fields.put("run.count", "1");
        fields.put("run.0001.comparabilityIdentitySha256", sha256);
        fields.put("run.0001.configurationIdentitySha256", sha256);
        fields.put("run.0001.id", "00000000-0000-4000-8000-000000000002");
        fields.put("run.0001.manifestPath", "run.txt");
        fields.put("run.0001.manifestSha256", sha256);
        fields.put("run.0001.outcome", "PASS");
        fields.put("schema.version", "ga-campaign-summary-v1");
        return fields;
    }

    private static Map<String, String> releaseFields(
            final String gitSha1, final String sha256) {
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put("artifact.applicationJarPath", "matching-engine-rc.jar");
        fields.put("artifact.applicationJarSha256", sha256);
        fields.put("artifact.sbomPath", "bom.json");
        fields.put("artifact.sbomSha256", sha256);
        fields.put("artifact.sha256sumsPath", "SHA256SUMS");
        fields.put("artifact.sha256sumsSha256", sha256);
        fields.put("candidate.productionSha", gitSha1);
        fields.put("candidate.productionTreeSha256", sha256);
        fields.put("candidate.tag", "v0.9.0-rc.1");
        fields.put("candidate.tagObjectSha", gitSha1);
        fields.put("evidence.gateCount", "12");
        fields.put("release.channel", "GITHUB_BINARY");
        fields.put("release.knownLimitationCount", "0");
        fields.put("release.product", "ULTRA_LOW_LATENCY_MATCHING_ENGINE");
        fields.put("release.releaseSourceSha", gitSha1);
        fields.put("release.version", "1.0.0");
        fields.put("schema.version", "ga-release-manifest-v1");
        for (int index = 1; index <= 12; index++) {
            final String prefix = String.format("evidence.gate.%02d", index);
            fields.put(prefix + ".id", "G" + index);
            fields.put(prefix + ".path", "gates/G" + index + ".txt");
            fields.put(prefix + ".sha256", sha256);
        }
        return fields;
    }
}
