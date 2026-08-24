package com.ultralatency.matching.qualification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests v2 canonical evidence, identity separation and immutable publication. */
class QualificationV2EvidenceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void canonicalCodecSortsKeysAndPercentEncodesValues() {
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put("zeta", "a value/中文");
        fields.put("alpha", "stable");

        final byte[] bytes = QualificationV2CanonicalCodec.encode(fields);

        assertEquals("alpha=stable\nzeta=a%20value%2F%E4%B8%AD%E6%96%87\n",
                new String(bytes, StandardCharsets.US_ASCII));
        assertEquals("0bf9ab98bdafcd59af3cc516f790f4ee996b3454ba3727837d060b224d5aa0b9",
                QualificationV2CanonicalCodec.sha256(fields));
        assertEquals(fields, QualificationV2CanonicalCodec.decode(bytes));
    }

    @Test
    void malformedAndNonCanonicalEvidenceFailsClosed() {
        assertThrows(IllegalArgumentException.class, () ->
                QualificationV2CanonicalCodec.decode("x=1\nx=2\n".getBytes(StandardCharsets.US_ASCII)));
        assertThrows(IllegalArgumentException.class, () ->
                QualificationV2CanonicalCodec.decode("x=%2f\n".getBytes(StandardCharsets.US_ASCII)));
        assertThrows(IllegalArgumentException.class, () ->
                QualificationV2CanonicalCodec.decode("x=1".getBytes(StandardCharsets.US_ASCII)));
        assertThrows(IllegalArgumentException.class, () ->
                QualificationV2CanonicalCodec.rejectPathValue("C:/absolute/path"));
        assertThrows(IllegalArgumentException.class, () ->
                QualificationV2CanonicalCodec.rejectPathValue("artifacts/../secret"));
    }

    @Test
    void identitiesExcludeVolatileRuntimeValues() {
        final QualificationFullConfiguration configuration = QualificationFullConfiguration.test(
                temporaryDirectory.resolve("results"));
        final Map<String, String> first = new LinkedHashMap<>();
        first.put("runtime.mustMatch.java.runtimeVersion", "21.0.12");
        first.put("runtime.mustMatch.osName", "Windows");
        first.put("runtime.recordOnly.pid", "11");
        first.put("runtime.recordOnly.startTime", "2026-08-24T00:00:00Z");
        final Map<String, String> second = new LinkedHashMap<>(first);
        second.put("runtime.recordOnly.pid", "12");
        second.put("runtime.recordOnly.startTime", "2026-08-24T00:01:00Z");

        final QualificationIdentity.Pair left = QualificationIdentity.forRun(
                configuration, first, "abc", "v0.7.0");
        final QualificationIdentity.Pair right = QualificationIdentity.forRun(
                configuration, second, "abc", "v0.7.0");

        assertEquals(left.configurationIdentitySha256(), right.configurationIdentitySha256());
        assertEquals(left.comparabilityIdentitySha256(), right.comparabilityIdentitySha256());
    }

    @Test
    void manifestAndSummaryPublishAtomicallyWithoutOverwrite() throws Exception {
        final QualificationManifestV2 first = manifest("run-b", "0".repeat(64));
        final QualificationManifestV2 second = manifest("run-a", "0".repeat(64));
        final Path firstPath = temporaryDirectory.resolve("run-b.manifest");
        final Path secondPath = temporaryDirectory.resolve("run-a.manifest");
        QualificationManifestV2Store.publish(firstPath, first);
        QualificationManifestV2Store.publish(secondPath, second);
        assertEquals(first.sha256Hex(), QualificationManifestV2Store.read(firstPath).sha256Hex());
        assertThrows(java.io.IOException.class,
                () -> QualificationManifestV2Store.publish(firstPath, first));
        final Path firstHashes = temporaryDirectory.resolve("run-b-artifact-hashes.txt");
        final Path secondHashes = temporaryDirectory.resolve("run-a-artifact-hashes.txt");
        QualificationManifestV2Store.publishArtifactHashes(
                firstHashes, Map.of("manifest.txt", firstPath));
        QualificationManifestV2Store.publishArtifactHashes(
                secondHashes, Map.of("manifest.txt", secondPath));
        final Path summaryPath = temporaryDirectory.resolve("campaign-summary.txt");
        final QualificationCampaignSummary summary = QualificationCampaignSummaryPublisher.publish(
                summaryPath,
                "campaign-1",
                "evaluator-v1",
                List.of(
                        new QualificationCampaignSummaryPublisher.ManifestReference(
                                firstPath, firstHashes),
                        new QualificationCampaignSummaryPublisher.ManifestReference(
                                secondPath, secondHashes)),
                2,
                5,
                true);
        assertEquals(summary.sha256Hex(), QualificationCampaignSummaryStore.read(summaryPath).sha256Hex());
        assertEquals("run-a", QualificationCampaignSummaryStore.read(summaryPath)
                .fields().get("run.0001.runId"));
    }

    @Test
    void campaignPublisherReferencesImmutableManifestAndSidecarHashes() throws Exception {
        final QualificationManifestV2 manifest = manifest("run-a", "0".repeat(64));
        final Path runDirectory = temporaryDirectory.resolve("run-a");
        java.nio.file.Files.createDirectories(runDirectory);
        final Path manifestPath = runDirectory.resolve("qualification-manifest-v2.txt");
        final Path sidecarPath = runDirectory.resolve("artifact-hashes-v2.txt");
        QualificationManifestV2Store.publish(manifestPath, manifest);
        java.nio.file.Files.writeString(sidecarPath, "resource-evidence.csv\t"
                + "0".repeat(64) + "\n", StandardCharsets.UTF_8);

        final Path summaryPath = temporaryDirectory.resolve("campaign-summary-v1.txt");
        final QualificationCampaignSummary summary =
                QualificationCampaignSummaryPublisher.publish(
                        summaryPath,
                        "campaign-v2",
                        "evaluator-v2",
                        List.of(new QualificationCampaignSummaryPublisher.ManifestReference(
                                manifestPath, sidecarPath)),
                        1,
                        3,
                        true);

        assertTrue(summary.fields().containsKey("run.0001.manifestRelativePath"));
        assertEquals("run-a/qualification-manifest-v2.txt",
                summary.fields().get("run.0001.manifestRelativePath"));
        assertEquals(QualificationArtifactHasher.sha256(sidecarPath),
                summary.fields().get("run.0001.artifactHashesSha256"));
        assertThrows(java.io.IOException.class, () ->
                QualificationCampaignSummaryPublisher.publish(
                        summaryPath,
                        "campaign-v2",
                        "evaluator-v2",
                        List.of(new QualificationCampaignSummaryPublisher.ManifestReference(
                                manifestPath, sidecarPath)),
                        1,
                        3,
                        true));
    }

    @Test
    void failAndAbortedManifestStatusesAreRepresentableButNotPassing() {
        final QualificationManifestV2 failed = manifestWithStatus("run-fail", "FAIL");
        final QualificationManifestV2 aborted = manifestWithStatus("run-aborted", "ABORTED");

        final QualificationCampaignSummary summary = QualificationCampaignSummary.fromManifests(
                "campaign-fail", "evaluator-v1", List.of(failed, aborted), 2, 5, false);

        assertEquals("false", summary.fields().get("campaign.result"));
        assertEquals("0", summary.fields().get("campaign.qualifyingRunCount"));
    }

    @Test
    void manifestRejectsWrongSchemaAndAbsoluteArtifactReference() {
        final Map<String, String> fields = manifestFields("run-1", "0".repeat(64));
        fields.put("schemaVersion", "qualification-run-manifest-v1");
        assertThrows(IllegalArgumentException.class, () -> QualificationManifestV2.of(fields));
        final Map<String, String> pathFields = manifestFields("run-1", "0".repeat(64));
        pathFields.put("artifact.jfr.relativePath", "C:/jfr");
        assertThrows(IllegalArgumentException.class, () -> QualificationManifestV2.of(pathFields));
        final Map<String, String> traversalFields = manifestFields("run-1", "0".repeat(64));
        traversalFields.put("artifact.jfr.relativePath", "artifacts/../jfr");
        traversalFields.put("artifact.jfr.size", "1");
        traversalFields.put("artifact.jfr.sha256", "0".repeat(64));
        assertThrows(IllegalArgumentException.class, () -> QualificationManifestV2.of(traversalFields));
        final Map<String, String> unknownFields = manifestFields("run-1", "0".repeat(64));
        unknownFields.put("unexpected.field", "true");
        assertThrows(IllegalArgumentException.class, () -> QualificationManifestV2.of(unknownFields));
    }

    @Test
    void persistedFailAndAbortedStatusesAreReadBack() throws Exception {
        final QualificationManifestV2 failed = manifestWithStatus("run-fail", "FAIL");
        final QualificationManifestV2 aborted = manifestWithStatus("run-aborted", "ABORTED");
        final Path failedPath = temporaryDirectory.resolve("failed.manifest");
        final Path abortedPath = temporaryDirectory.resolve("aborted.manifest");
        QualificationManifestV2Store.publish(failedPath, failed);
        QualificationManifestV2Store.publish(abortedPath, aborted);
        assertEquals("FAIL", QualificationManifestV2Store.read(failedPath).value("result.status"));
        assertEquals("ABORTED", QualificationManifestV2Store.read(abortedPath).value("result.status"));
    }

    @Test
    void malformedArtifactSidecarAndUnderqualifiedCampaignFailClosed() throws Exception {
        final Path sidecar = temporaryDirectory.resolve("malformed-sidecar.txt");
        java.nio.file.Files.writeString(sidecar, "artifacts/../secret\t" + "0".repeat(64) + "\n",
                StandardCharsets.UTF_8);
        assertThrows(java.io.IOException.class,
                () -> QualificationManifestV2Store.readArtifactHashes(sidecar));

        final Map<String, String> underqualifiedFields = manifestFields("run-short", "0".repeat(64));
        underqualifiedFields.put("result.status", "PASS");
        underqualifiedFields.put("result.elapsedMillis", "1000");
        underqualifiedFields.put("result.acceptedCommands", "10");
        underqualifiedFields.put("result.naturalPostGcSampleCount", "1");
        underqualifiedFields.put("result.heapGuardAssessed", "true");
        underqualifiedFields.put("result.heapGuardPassed", "true");
        underqualifiedFields.put("result.threadBaselineRestored", "true");
        underqualifiedFields.put("result.listenerRebound", "true");
        underqualifiedFields.put("result.recoveryLeaseReacquired", "true");
        underqualifiedFields.put("result.inventoryStable", "true");
        final QualificationManifestV2 underqualified = QualificationManifestV2.of(underqualifiedFields);
        assertThrows(IllegalArgumentException.class, () -> QualificationCampaignSummary.fromManifests(
                "campaign-short", "evaluator-v1", List.of(underqualified), 1, 1, true));
    }

    private static QualificationManifestV2 manifest(final String runId, final String salt) {
        return manifestWithStatus(runId, "PASS", salt);
    }

    private static QualificationManifestV2 manifestWithStatus(
            final String runId, final String status) {
        return manifestWithStatus(runId, status, "0".repeat(64));
    }

    private static QualificationManifestV2 manifestWithStatus(
            final String runId, final String status, final String salt) {
        final Map<String, String> fields = manifestFields(runId, salt);
        fields.put("result.status", status);
        fields.put("result.elapsedMillis", "3600000");
        fields.put("result.acceptedCommands", "1000000");
        fields.put("result.naturalPostGcSampleCount", "3");
        fields.put("result.heapGuardAssessed", "true");
        fields.put("result.heapGuardPassed", "true");
        fields.put("result.threadBaselineRestored", "true");
        fields.put("result.listenerRebound", "true");
        fields.put("result.recoveryLeaseReacquired", "true");
        fields.put("result.inventoryStable", "true");
        return QualificationManifestV2.of(fields);
    }

    private static Map<String, String> manifestFields(final String runId, final String salt) {
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put("schemaVersion", QualificationV2CanonicalCodec.MANIFEST_SCHEMA);
        fields.put("canonicalizationVersion", QualificationV2CanonicalCodec.CANONICALIZATION_VERSION);
        fields.put("source.runId", runId);
        fields.put("source.gitSha", "abc123");
        fields.put("source.baselineTag", "v0.7.0-engineering-baseline");
        fields.put("source.startedAtUtc", "2026-08-24T00:00:00Z");
        fields.put("source.completedAtUtc", "2026-08-24T01:00:00Z");
        fields.put("identity.configurationIdentitySha256", salt);
        fields.put("identity.comparabilityIdentitySha256", salt);
        return fields;
    }
}
