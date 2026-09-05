package com.ultralatency.matching.qualification.ga.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.qualification.QualificationArtifactHasher;
import com.ultralatency.matching.qualification.QualificationJfrRecording;
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

    @Test
    void managementPassRequiresEveryCanonicalBinding() {
        final Map<String, String> binding = new LinkedHashMap<>();
        binding.put("configuration.bound", "true");
        binding.put("environment.bound", "true");
        binding.put("candidate.bound", "true");
        binding.put("controller.bound", "true");
        binding.put("evidence.mandatoryComplete", "true");
        assertTrue(GaFormalPerformanceEvidenceVerifier.managementBindingComplete(binding));
        for (String key : List.copyOf(binding.keySet())) {
            binding.put(key, "false");
            assertFalse(GaFormalPerformanceEvidenceVerifier.managementBindingComplete(binding),
                    key);
            binding.put(key, "true");
        }
    }

    @Test
    void statusEvidenceUsesAbsoluteOneHzClockAndRejectsCatchUpOrOverlap() {
        final Map<String, String> valid = statusEvidence();
        assertTrue(GaFormalPerformanceEvidenceVerifier.statusEvidenceComplete(valid),
                () -> "status findings: " + valid);

        final Map<String, String> wrongOperation = new LinkedHashMap<>(valid);
        wrongOperation.put("status.sample.1.operation", "METRICS");
        assertFalse(GaFormalPerformanceEvidenceVerifier.statusEvidenceComplete(wrongOperation));

        final Map<String, String> catchUp = new LinkedHashMap<>(valid);
        catchUp.put("status.sample.2.deadlineNanos", "1000000000000");
        assertFalse(GaFormalPerformanceEvidenceVerifier.statusEvidenceComplete(catchUp));

        final Map<String, String> overlap = new LinkedHashMap<>(valid);
        overlap.put("status.sample.2.startedNanos", "1000000000001");
        assertFalse(GaFormalPerformanceEvidenceVerifier.statusEvidenceComplete(overlap));
    }

    @Test
    void managementIdentityIsCanonicalAndCandidateOwned() {
        final String campaignId = UUID.randomUUID().toString();
        final String physicalId = UUID.randomUUID().toString();
        final Map<String, String> campaignEvidence = campaignIdentity(campaignId);
        final Map<String, String> summary = managementSummaryIdentity(
                campaignId, physicalId, "cfg", "env");
        final Map<String, String> trial = managementTrialIdentity(
                campaignId, physicalId, "cfg", "env");

        assertTrue(GaFormalPerformanceEvidenceVerifier.managementIdentityComplete(
                summary, trial, campaignEvidence, 1));
        assertTrue(GaFormalPerformanceEvidenceVerifier.managementBindingComplete(
                managementBinding()));

        final Map<String, String> wrongTagObject = new LinkedHashMap<>(trial);
        wrongTagObject.put("candidate.tagObjectSha", "f".repeat(40));
        assertFalse(GaFormalPerformanceEvidenceVerifier.managementIdentityComplete(
                summary, wrongTagObject, campaignEvidence, 1));

        final Map<String, String> wrongPhysical = new LinkedHashMap<>(summary);
        wrongPhysical.put("trial.1.physicalExecutionId", UUID.randomUUID().toString());
        assertFalse(GaFormalPerformanceEvidenceVerifier.managementIdentityComplete(
                wrongPhysical, trial, campaignEvidence, 1));
    }

    @Test
    void requestAccountingIsRecomputedInsteadOfTrustingSummary() {
        final Map<String, String> raw = accountingEvidence();
        assertTrue(GaFormalPerformanceEvidenceVerifier.accountingMatchesRaw(raw));

        for (Map.Entry<String, String> entry : Map.of(
                "measurement.offeredCommands", "2",
                "measurement.acceptedCommands", "2",
                "measurement.completedCommands", "2",
                "measurement.postMeasurementDrainCommands", "1",
                "measurement.crossBoundaryCommands", "1",
                "measurement.unfinishedCommands", "1",
                "offeredCommands", "2",
                "acceptedCommands", "2",
                "responseCount", "2").entrySet()) {
            final Map<String, String> tampered = accountingEvidence();
            tampered.put(entry.getKey(), entry.getValue());
            assertFalse(GaFormalPerformanceEvidenceVerifier.accountingMatchesRaw(tampered),
                    entry.getKey());
        }
    }

    @Test
    void managementAccountingUsesTheSameRequestLevelRecomputation() {
        final Map<String, String> managementRaw = accountingEvidence();
        managementRaw.put("pollStatus", "false");
        managementRaw.put("status.pollCount", "0");
        assertTrue(GaFormalPerformanceEvidenceVerifier.accountingMatchesRaw(managementRaw));

        managementRaw.put("measurement.completedCommands", "0");
        assertFalse(GaFormalPerformanceEvidenceVerifier.accountingMatchesRaw(managementRaw));
    }

    @Test
    void requestAccountingRejectsUnknownOutcomesAndDuplicateSequences() {
        for (String outcome : List.of("0", "4", "-2")) {
            final Map<String, String> tampered = accountingEvidence();
            tampered.put("request.1.outcomeCode", outcome);
            assertFalse(GaFormalPerformanceEvidenceVerifier.accountingMatchesRaw(tampered),
                    outcome);
        }

        final Map<String, String> duplicateSequence = accountingEvidence();
        duplicateSequence.put("request.2.commandSequence", "1");
        duplicateSequence.put("request.2.offeredNanos", "120");
        duplicateSequence.put("request.2.inMeasurement", "true");
        duplicateSequence.put("request.2.completedNanos", "160");
        duplicateSequence.put("request.2.capacityReleaseNanos", "161");
        duplicateSequence.put("request.2.outcomeCode", "1");
        assertFalse(GaFormalPerformanceEvidenceVerifier.accountingMatchesRaw(
                duplicateSequence));
    }

    @Test
    void latencyAndCapacityRowsMustReferenceTheRawRequestLedger() throws Exception {
        final Map<String, String> raw = accountingEvidence();
        raw.put("request.1.latencyNanos", "40");
        final Path latency = temporaryDirectory.resolve("latency-samples-v2.csv");
        final Path capacity = temporaryDirectory.resolve("capacity-evidence-v2.txt");
        Files.writeString(latency, "requestId,commandSequence,offeredNanos,completedNanos,"
                + "capacityReleaseNanos,latencyNanos\n1,1,110,150,151,40\n",
                StandardCharsets.US_ASCII);
        Files.writeString(capacity, "releaseSampleCount=1\n"
                + "releaseSample.000001.requestId=1\n"
                + "releaseSample.000001.commandSequence=1\n"
                + "releaseSample.000001.offeredNanos=110\n"
                + "releaseSample.000001.responseCompleteNanos=150\n"
                + "releaseSample.000001.capacityReleaseNanos=151\n"
                + "releaseSample.000001.schedulerConsumedNanos=152\n"
                + "releaseSample.000001.releaseDelayNanos=1\n",
                StandardCharsets.US_ASCII);

        assertTrue(GaFormalPerformanceEvidenceVerifier.requestCorrelationsComplete(
                raw, latency, capacity));

        Files.writeString(latency, "requestId,commandSequence,offeredNanos,completedNanos,"
                + "capacityReleaseNanos,latencyNanos\n2,2,110,150,151,40\n",
                StandardCharsets.US_ASCII);
        assertFalse(GaFormalPerformanceEvidenceVerifier.requestCorrelationsComplete(
                raw, latency, capacity));
    }

    @Test
    void campaignGateMustInventoryCampaignAndRunManifests() throws Exception {
        final Path root = temporaryDirectory.resolve("campaign-chain");
        final Path runDirectory = root.resolve("run-01");
        Files.createDirectories(runDirectory);
        final Path campaignManifest = root.resolve("g4-campaign-manifest-v1.txt");
        final Path runManifest = runDirectory.resolve("ga-run-manifest-v1.txt");
        Files.writeString(campaignManifest, "campaign=true\n", StandardCharsets.US_ASCII);
        Files.writeString(runManifest, "run=true\n", StandardCharsets.US_ASCII);
        final Map<String, String> campaign = new LinkedHashMap<>();
        campaign.put("run.count", "1");
        campaign.put("run.0001.manifestPath", "run-01/ga-run-manifest-v1.txt");
        final Map<String, String> gate = new LinkedHashMap<>();
        gate.put("manifest.count", "2");
        gate.put("manifest.0001.path", "run-01/ga-run-manifest-v1.txt");
        gate.put("manifest.0001.sha256", QualificationArtifactHasher.sha256(runManifest));
        gate.put("manifest.0002.path", "g4-campaign-manifest-v1.txt");
        gate.put("manifest.0002.sha256", QualificationArtifactHasher.sha256(campaignManifest));
        assertTrue(GaFormalPerformanceEvidenceVerifier.campaignGateBindsRequiredManifests(
                root, campaign, gate));
        assertTrue(GaFormalPerformanceEvidenceVerifier.campaignGateInventoryComplete(root, gate));

        final Path unlisted = root.resolve("unlisted-payload.txt");
        Files.writeString(unlisted, "not in the gate inventory\n", StandardCharsets.US_ASCII);
        assertFalse(GaFormalPerformanceEvidenceVerifier.campaignGateInventoryComplete(root, gate));

        gate.remove("manifest.0002.sha256");
        assertFalse(GaFormalPerformanceEvidenceVerifier.campaignGateBindsRequiredManifests(
                root, campaign, gate));

        gate.put("manifest.0002.sha256", QualificationArtifactHasher.sha256(campaignManifest));
        gate.remove("manifest.0001.path");
        assertFalse(GaFormalPerformanceEvidenceVerifier.campaignGateBindsRequiredManifests(
                root, campaign, gate));
    }

    @Test
    void campaignRunBindingRequiresActualManifestIdentityAndPhysicalOwnership() {
        final String campaignId = UUID.randomUUID().toString();
        final String physicalId = UUID.randomUUID().toString();
        final String runId = UUID.randomUUID().toString();
        final String manifestPath = "run-01/ga-run-manifest-v1.txt";
        final String manifestSha = "a".repeat(64);
        final Map<String, String> campaign = new LinkedHashMap<>();
        campaign.put("campaign.id", campaignId);
        campaign.put("run.0001.id", runId);
        campaign.put("run.0001.outcome", "PASS");
        campaign.put("run.0001.manifestPath", manifestPath);
        campaign.put("run.0001.manifestSha256", manifestSha);
        campaign.put("run.0001.configurationIdentitySha256", "b".repeat(64));
        campaign.put("run.0001.comparabilityIdentitySha256", "c".repeat(64));
        campaign.put("run.0001.physicalExecutionId", physicalId);
        final Map<String, String> evidence = new LinkedHashMap<>();
        evidence.put("run.0001.id", runId);
        evidence.put("run.0001.outcome", "PASS");
        evidence.put("run.0001.manifestPath", manifestPath);
        evidence.put("run.0001.manifestSha256", manifestSha);
        evidence.put("run.0001.physicalExecutionId", physicalId);
        evidence.put("run.0001.configurationIdentitySha256", "b".repeat(64));
        evidence.put("run.0001.comparabilityIdentitySha256", "c".repeat(64));
        final Map<String, String> run = new LinkedHashMap<>();
        run.put("run.id", runId);
        run.put("evidence.outcome", "PASS");
        run.put("physicalExecution.id", physicalId);
        run.put("campaign.id", campaignId);
        run.put("configuration.identitySha256", "b".repeat(64));
        run.put("comparability.identitySha256", "c".repeat(64));

        assertTrue(GaFormalPerformanceEvidenceVerifier.campaignRunBindingComplete(
                campaign, evidence, run, 1, manifestPath, manifestSha));

        final Map<String, String> wrongOutcome = new LinkedHashMap<>(run);
        wrongOutcome.put("evidence.outcome", "FAIL");
        assertFalse(GaFormalPerformanceEvidenceVerifier.campaignRunBindingComplete(
                campaign, evidence, wrongOutcome, 1, manifestPath, manifestSha));
    }

    @Test
    void formalCampaignAndRunSemanticsRejectQuickOrPartialArtifacts() {
        final String campaignId = UUID.randomUUID().toString();
        final Map<String, String> campaign = formalCampaign(campaignId, "3");
        final Map<String, String> evidence = formalCampaignEvidence(campaignId);
        final Map<String, String> gate = formalGate(campaignId);
        assertTrue(GaFormalPerformanceEvidenceVerifier.formalCampaignSemanticsComplete(
                campaign, evidence, gate));

        final Map<String, String> quickEvidence = new LinkedHashMap<>(evidence);
        quickEvidence.put("lane", "QUICK");
        assertFalse(GaFormalPerformanceEvidenceVerifier.formalCampaignSemanticsComplete(
                campaign, quickEvidence, gate));

        final Map<String, String> partialCampaign = new LinkedHashMap<>(campaign);
        partialCampaign.put("run.count", "2");
        assertFalse(GaFormalPerformanceEvidenceVerifier.formalCampaignSemanticsComplete(
                partialCampaign, evidence, gate));

        final String physicalId = UUID.randomUUID().toString();
        final Map<String, String> raw = formalRunRaw(campaignId, physicalId);
        final Map<String, String> manifest = formalRunManifest(campaignId, physicalId);
        assertTrue(GaFormalPerformanceEvidenceVerifier.formalRunSemanticsComplete(raw, manifest));

        final Map<String, String> quickRun = new LinkedHashMap<>(raw);
        quickRun.put("schema", "ga-g4-performance-quick-v1");
        assertFalse(GaFormalPerformanceEvidenceVerifier.formalRunSemanticsComplete(
                quickRun, manifest));

        final Map<String, String> wrongDuration = new LinkedHashMap<>(manifest);
        wrongDuration.put("run.measurementDurationNanos", "1");
        assertFalse(GaFormalPerformanceEvidenceVerifier.formalRunSemanticsComplete(
                raw, wrongDuration));
    }

    @Test
    void mandatoryRuntimeArtifactsRequireReadableOwnedHashesAndPhysicalIdentity() throws Exception {
        final Path root = temporaryDirectory.resolve("mandatory-runtime");
        final Path processEvidence = root.resolve("process-evidence");
        Files.createDirectories(processEvidence);
        Files.createDirectories(root.resolve("storage/wal"));
        final Path configuration = root.resolve("runtime.properties");
        Files.writeString(configuration, "wal.mode=SYNC_EACH_APPEND\n", StandardCharsets.US_ASCII);
        final Path resource = processEvidence.resolve("resource-evidence.csv");
        Files.writeString(resource, "#baselineThreadCount=1\n#finalThreadCount=1\n"
                + "#threadBaselineRestored=true\n#baselineRuntimeThreads=\n#finalRuntimeThreads=\n"
                + "timestamp,threadCount,peakThreadCount,gcCollections,gcTimeMillis,heapUsed,"
                + "naturalPostGcHeapUsed\n2026-08-23T00:00:00Z,1,1,1,1,100,100\n",
                StandardCharsets.US_ASCII);
        final Path jfr = processEvidence.resolve("qualification.jfr");
        try (QualificationJfrRecording ignored = QualificationJfrRecording.start(jfr)) {
            final Thread worker = new Thread(() -> {
                byte[][] allocations = new byte[1_024][];
                for (int index = 0; index < 100_000; index++) {
                    allocations[index % allocations.length] = new byte[1_024];
                }
            });
            worker.start();
            worker.join();
            System.gc();
            Thread.sleep(2_100L);
        }
        for (Path artifact : List.of(configuration, resource, jfr)) {
            GaFormalPerformanceEvidencePublisher.publishArtifactSidecar(artifact);
        }
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put("physicalExecutionId", UUID.randomUUID().toString());
        fields.put("candidate.tagObjectSha", "a".repeat(40));
        fields.put("controller.gitSha", "b".repeat(40));
        fields.put("configuration.identitySha256", "c".repeat(64));
        final Map<String, String> environment = Map.of("os.name", "test");
        fields.put("environment.identitySha256", GaPerformanceEnvironment.identity(environment));
        fields.put("environment.os.name", "test");
        fields.put("configuration.filePath", "runtime.properties");
        fields.put("configuration.fileSha256", QualificationArtifactHasher.sha256(configuration));
        fields.put("processEvidence.resourcePath", "process-evidence/resource-evidence.csv");
        fields.put("processEvidence.resourceSha256", QualificationArtifactHasher.sha256(resource));
        fields.put("processEvidence.jfrPath", "process-evidence/qualification.jfr");
        fields.put("processEvidence.jfrSha256", QualificationArtifactHasher.sha256(jfr));
        final Map<String, String> inventory = new LinkedHashMap<>();
        inventory.put("runtime.properties", fields.get("configuration.fileSha256"));
        inventory.put("process-evidence/resource-evidence.csv",
                fields.get("processEvidence.resourceSha256"));
        inventory.put("process-evidence/qualification.jfr", fields.get("processEvidence.jfrSha256"));

        assertTrue(GaFormalPerformanceEvidenceVerifier.mandatoryRuntimeEvidenceComplete(
                root, fields, inventory),
                () -> "mandatory findings: "
                        + GaFormalPerformanceEvidenceVerifier.mandatoryRuntimeEvidenceFindings(
                        root, fields, inventory));

        final Map<String, String> wrongHash = new LinkedHashMap<>(fields);
        wrongHash.put("processEvidence.resourceSha256", "d".repeat(64));
        assertFalse(GaFormalPerformanceEvidenceVerifier.mandatoryRuntimeEvidenceComplete(
                root, wrongHash, inventory));

        final Map<String, String> missingInventory = new LinkedHashMap<>(inventory);
        missingInventory.remove("process-evidence/qualification.jfr");
        assertFalse(GaFormalPerformanceEvidenceVerifier.mandatoryRuntimeEvidenceComplete(
                root, fields, missingInventory));
    }

    private static Map<String, String> statusEvidence() {
        final long measurementStart = 1_000_000_000_000L;
        final Map<String, String> evidence = new LinkedHashMap<>();
        evidence.put("pollStatus", "true");
        evidence.put("status.operation", "STATUS");
        evidence.put("metrics.operation", "METRICS");
        evidence.put("status.pollCount", "300");
        evidence.put("status.sampleCount", "300");
        evidence.put("measurementStartNanos", Long.toString(measurementStart));
        evidence.put("measurementEndNanos", Long.toString(
                measurementStart + 300_000_000_000L));
        for (int index = 1; index <= 300; index++) {
            final long deadline = measurementStart + (index - 1L) * 1_000_000_000L;
            final String prefix = "status.sample." + index + ".";
            evidence.put(prefix + "operation", "STATUS");
            evidence.put(prefix + "ordinal", Integer.toString(index));
            evidence.put(prefix + "deadlineNanos", Long.toString(deadline));
            evidence.put(prefix + "startedNanos", Long.toString(deadline + 1L));
            evidence.put(prefix + "completedNanos", Long.toString(deadline + 2L));
            evidence.put(prefix + "latencyNanos", "1");
        }
        return evidence;
    }

    private static Map<String, String> campaignIdentity(final String campaignId) {
        final Map<String, String> identity = new LinkedHashMap<>();
        identity.put("campaign.id", campaignId);
        identity.put("candidate.tag", "v0.9.0-rc.2");
        identity.put("candidate.tagObjectSha", "a".repeat(40));
        identity.put("candidate.productionSha", "b".repeat(40));
        identity.put("candidate.applicationJarSha256", "c".repeat(64));
        identity.put("qualification.jarSha256", "d".repeat(64));
        identity.put("controller.gitSha", "e".repeat(40));
        return identity;
    }

    private static Map<String, String> managementSummaryIdentity(
            final String campaignId,
            final String physicalId,
            final String configuration,
            final String environment) {
        final Map<String, String> summary = new LinkedHashMap<>();
        summary.put("campaign.id", campaignId);
        summary.put("gate.id", "G4");
        summary.put("gate.version", GaFormalPerformanceContract.CAMPAIGN);
        summary.put("trial.1.id", "pair-a-idle");
        summary.put("trial.1.ordinal", "1");
        summary.put("trial.1.pairId", "A");
        summary.put("trial.1.physicalExecutionId", physicalId);
        summary.put("trial.1.configuration.identitySha256", configuration);
        summary.put("trial.1.environment.identitySha256", environment);
        return summary;
    }

    private static Map<String, String> managementTrialIdentity(
            final String campaignId,
            final String physicalId,
            final String configuration,
            final String environment) {
        final Map<String, String> trial = new LinkedHashMap<>(campaignIdentity(campaignId));
        trial.put("campaign.id", campaignId);
        trial.put("gate.id", "G4");
        trial.put("gate.version", GaFormalPerformanceContract.CAMPAIGN);
        trial.put("trial.id", "pair-a-idle");
        trial.put("trial.ordinal", "1");
        trial.put("pair.id", "A");
        trial.put("physicalExecutionId", physicalId);
        trial.put("configuration.identitySha256", configuration);
        trial.put("environment.identitySha256", environment);
        return trial;
    }

    private static Map<String, String> managementBinding() {
        final Map<String, String> binding = new LinkedHashMap<>();
        binding.put("configuration.bound", "true");
        binding.put("environment.bound", "true");
        binding.put("candidate.bound", "true");
        binding.put("controller.bound", "true");
        binding.put("evidence.mandatoryComplete", "true");
        return binding;
    }

    private static Map<String, String> formalCampaign(
            final String campaignId, final String runCount) {
        final Map<String, String> campaign = new LinkedHashMap<>();
        campaign.put("campaign.id", campaignId);
        campaign.put("campaign.outcome", "PASS");
        campaign.put("gate.id", "G4");
        campaign.put("gate.version", GaFormalPerformanceContract.CAMPAIGN);
        campaign.put("run.count", runCount);
        campaign.put("campaign.requiredRunCount", "3");
        campaign.put("campaign.validRunCount", "3");
        return campaign;
    }

    private static Map<String, String> formalCampaignEvidence(final String campaignId) {
        final Map<String, String> evidence = new LinkedHashMap<>();
        evidence.put("campaign.id", campaignId);
        evidence.put("schema", "ga-g4-performance-campaign-v2");
        evidence.put("formal", "true");
        evidence.put("lane", "FORMAL_G4");
        evidence.put("matrix.version", GaPerformanceMatrix.APPROVED_VERSION);
        evidence.put("gate.id", "G4");
        evidence.put("gate.version", GaFormalPerformanceContract.CAMPAIGN);
        evidence.put("campaign.gate", "G4");
        evidence.put("protocol", GaPerformanceMatrix.APPROVED_PROTOCOL);
        evidence.put("protocolV2.window", Integer.toString(
                GaPerformanceMatrix.APPROVED_PROTOCOL_V2_WINDOW));
        evidence.put("walMode", GaPerformanceMatrix.APPROVED_WAL_MODE);
        evidence.put("loadModel", GaPerformanceMatrix.APPROVED_LOAD_MODEL);
        evidence.put("profile", GaPerformanceMatrix.APPROVED_PROFILE);
        evidence.put("seed", Long.toString(GaPerformanceMatrix.APPROVED_SEED));
        evidence.put("warmup.duration.nanos", Long.toString(
                GaFormalPerformanceContract.WARMUP.toNanos()));
        evidence.put("measurement.duration.nanos", Long.toString(
                GaFormalPerformanceContract.MEASUREMENT.toNanos()));
        evidence.put("lifecycle.cycles", Integer.toString(
                GaFormalPerformanceContract.LIFECYCLE_CYCLES));
        evidence.put("management.warmup.duration.nanos", Long.toString(
                GaFormalPerformanceContract.MANAGEMENT_WARMUP.toNanos()));
        evidence.put("management.measurement.duration.nanos", Long.toString(
                GaFormalPerformanceContract.MANAGEMENT_MEASUREMENT.toNanos()));
        evidence.put("management.status.interval.nanos", Long.toString(
                GaFormalPerformanceContract.MANAGEMENT_INTERVAL.toNanos()));
        evidence.put("management.status.requestCount", Integer.toString(
                GaFormalPerformanceContract.MANAGEMENT_STATUS_REQUESTS));
        return evidence;
    }

    private static Map<String, String> formalGate(final String campaignId) {
        final Map<String, String> gate = new LinkedHashMap<>();
        gate.put("gate.id", "G4");
        gate.put("gate.version", GaFormalPerformanceContract.CAMPAIGN);
        gate.put("campaign.id", campaignId);
        gate.put("campaign.gate", "G4");
        return gate;
    }

    private static Map<String, String> formalRunRaw(
            final String campaignId, final String physicalId) {
        final Map<String, String> raw = new LinkedHashMap<>();
        raw.put("schema", GaPerformanceMatrix.APPROVED_VERSION);
        raw.put("formal", "true");
        raw.put("lane", "FORMAL_G4");
        raw.put("run.formal", "true");
        raw.put("protocol", GaPerformanceMatrix.APPROVED_PROTOCOL);
        raw.put("window", Integer.toString(GaPerformanceMatrix.APPROVED_PROTOCOL_V2_WINDOW));
        raw.put("walMode", GaPerformanceMatrix.APPROVED_WAL_MODE);
        raw.put("loadModel", GaPerformanceMatrix.APPROVED_LOAD_MODEL);
        raw.put("profile", GaPerformanceMatrix.APPROVED_PROFILE);
        raw.put("seed", Long.toString(GaPerformanceMatrix.APPROVED_SEED));
        raw.put("warmupDurationNanos", Long.toString(
                GaFormalPerformanceContract.WARMUP.toNanos()));
        raw.put("measurementDurationNanos", Long.toString(
                GaFormalPerformanceContract.MEASUREMENT.toNanos()));
        raw.put("runOrdinal", "1");
        raw.put("physicalExecutionId", physicalId);
        raw.put("campaign.id", campaignId);
        raw.put("candidate.tag", "v0.9.0-rc.2");
        raw.put("candidate.tagObjectSha", "9e2a67ada0e3b6220b730131d0bae79dc03073ed");
        raw.put("candidate.productionSha", "740e8a3dea0a759c707c597778c26c41e9bb3e47");
        raw.put("candidate.productionTreeSha256",
                "ef1d9f4cb64a9d6e331fb326ebe8f3b0abb29a53bf6045a5d4999a53e73b4bbc");
        raw.put("candidate.applicationJarSha256",
                "0b77d37985b9124ac4fd1b90d669db550efd0cf00c23af65fdc29b35071703c4");
        return raw;
    }

    private static Map<String, String> formalRunManifest(
            final String campaignId, final String physicalId) {
        final Map<String, String> manifest = new LinkedHashMap<>();
        manifest.put("run.formal", "true");
        manifest.put("run.measurementDurationNanos", Long.toString(
                GaFormalPerformanceContract.MEASUREMENT.toNanos()));
        manifest.put("run.warmupDurationNanos", Long.toString(
                GaFormalPerformanceContract.WARMUP.toNanos()));
        manifest.put("run.protocolV2Window", "8");
        manifest.put("gate.id", "G4");
        manifest.put("gate.version", GaFormalPerformanceContract.CAMPAIGN);
        manifest.put("physicalExecution.id", physicalId);
        manifest.put("campaign.id", campaignId);
        manifest.put("candidate.tag", "v0.9.0-rc.2");
        manifest.put("candidate.tagObjectSha", "9e2a67ada0e3b6220b730131d0bae79dc03073ed");
        manifest.put("candidate.productionSha", "740e8a3dea0a759c707c597778c26c41e9bb3e47");
        return manifest;
    }

    private static Map<String, String> accountingEvidence() {
        final Map<String, String> raw = new LinkedHashMap<>();
        raw.put("measurementStartNanos", "100");
        raw.put("measurementEndNanos", "200");
        raw.put("measurement.offeredCommands", "1");
        raw.put("measurement.acceptedCommands", "1");
        raw.put("measurement.completedCommands", "1");
        raw.put("measurement.postMeasurementDrainCommands", "0");
        raw.put("measurement.crossBoundaryCommands", "0");
        raw.put("measurement.unfinishedCommands", "0");
        raw.put("offeredCommands", "1");
        raw.put("acceptedCommands", "1");
        raw.put("responseCount", "1");
        raw.put("request.1.commandSequence", "1");
        raw.put("request.1.offeredNanos", "110");
        raw.put("request.1.inMeasurement", "true");
        raw.put("request.1.completedNanos", "150");
        raw.put("request.1.capacityReleaseNanos", "151");
         raw.put("request.1.outcomeCode", "1");
        return raw;
    }
}
