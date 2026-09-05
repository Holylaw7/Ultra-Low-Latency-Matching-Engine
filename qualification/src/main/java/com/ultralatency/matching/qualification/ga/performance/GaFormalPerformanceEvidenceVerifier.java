package com.ultralatency.matching.qualification.ga.performance;

import com.ultralatency.matching.qualification.QualificationArtifactHasher;
import com.ultralatency.matching.qualification.QualificationPercentiles;
import com.ultralatency.matching.qualification.QualificationResourceEvidence;
import com.ultralatency.matching.qualification.QualificationResourceEvidenceReader;
import com.ultralatency.matching.qualification.ga.GaEvidenceCodec;
import com.ultralatency.matching.qualification.ga.GaEvidenceStore;
import com.ultralatency.matching.qualification.ga.observability.GaJfrEvidence;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Read-only integrity checks for the raw artifacts of a formal G4 run. */
public final class GaFormalPerformanceEvidenceVerifier {

    private static final String LATENCY_HEADER =
            "requestId,commandSequence,offeredNanos,completedNanos,"
                    + "capacityReleaseNanos,latencyNanos";

    private GaFormalPerformanceEvidenceVerifier() {
    }

    /** Immutable result of an evidence reconstruction attempt. */
    public record Verification(boolean passed, String blocker, List<String> findings) {
        public Verification {
            Objects.requireNonNull(blocker, "blocker");
            findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
        }
    }

    /** Verifies the canonical campaign chain and every linked physical run. */
    public static Verification verifyCampaign(final Path campaignDirectory) {
        final List<String> findings = new ArrayList<>();
        try {
            final Path root = Objects.requireNonNull(campaignDirectory, "campaignDirectory")
                    .toAbsolutePath().normalize();
            final Path manifest = root.resolve("g4-campaign-manifest-v1.txt");
            final Path gate = root.resolve("g4-gate-result-v1.txt");
            final Map<String, String> fields = GaEvidenceStore.read(
                    manifest, GaEvidenceCodec.Schema.CAMPAIGN);
            verifySidecar(manifest.resolveSibling(manifest.getFileName() + ".sha256"),
                    manifest, findings);
            Map<String, String> gateFields = Map.of();
            if (!Files.isRegularFile(gate)) {
                findings.add("missing campaign gate result");
            } else {
                verifySidecar(gate.resolveSibling(gate.getFileName() + ".sha256"), gate, findings);
                gateFields = GaEvidenceStore.read(
                        gate, GaEvidenceCodec.Schema.GATE);
                if (!Objects.equals(fields.get("campaign.outcome"),
                        gateFields.get("evidence.outcome"))) {
                    findings.add("campaign/gate outcome mismatch");
                }
            }
            if (!campaignGateBindsRequiredManifests(root, fields, gateFields)) {
                findings.add("campaign gate does not bind canonical campaign/run manifests");
            }
            final Map<String, String> campaignEvidence = verifyCampaignEvidence(
                    root, fields, gateFields, findings);
            verifyFormalCampaignSemantics(fields, campaignEvidence, gateFields, findings);
            verifyCampaignGateInventory(root, gateFields, findings);
            final int runCount = Math.toIntExact(longValue(fields, "run.count"));
            final List<Map<String, String>> runManifests = new ArrayList<>();
            final Set<String> physicalExecutions = new HashSet<>();
            for (int index = 1; index <= runCount; index++) {
                final String prefix = String.format("run.%04d.", index);
                final Path runManifest = resolvePayload(root, fields.get(prefix + "manifestPath"));
                final String declared = fields.get(prefix + "manifestSha256");
                if (!declared.equals(QualificationArtifactHasher.sha256(runManifest))) {
                    findings.add("campaign run manifest hash mismatch: " + runManifest);
                }
                try {
                    final Map<String, String> runFields = GaEvidenceStore.read(
                            runManifest, GaEvidenceCodec.Schema.RUN);
                    runManifests.add(runFields);
                    verifyCampaignRunBinding(fields, campaignEvidence, runFields, index,
                            root.relativize(runManifest).toString().replace('\\', '/'),
                            QualificationArtifactHasher.sha256(runManifest),
                            physicalExecutions, findings);
                } catch (IOException | RuntimeException failure) {
                    findings.add("campaign run manifest is not readable: " + runManifest);
                }
                final Verification runVerification = verifyRun(runManifest.getParent());
                if (!runVerification.passed()) {
                    findings.addAll(runVerification.findings());
                }
            }
            if ("PASS".equals(fields.get("campaign.outcome"))) {
                if (runCount != GaFormalPerformanceContract.RUN_COUNT
                        || integer(fields, "campaign.requiredRunCount")
                        != GaFormalPerformanceContract.RUN_COUNT
                        || longValue(fields, "campaign.validRunCount") != runCount) {
                    findings.add("PASS campaign does not contain the complete run set");
                }
                verifyPerformanceComparability(runManifests, fields, findings);
                verifyLifecycleDirectory(root.resolve("lifecycle"), fields, campaignEvidence,
                        findings);
                verifyManagementDirectory(root.resolve("management"), fields, campaignEvidence,
                        findings);
            }
        } catch (final IOException | RuntimeException failure) {
            findings.add("campaign reconstruction failed: " + failure.getMessage());
        }
        return new Verification(findings.isEmpty(), findings.isEmpty() ? "NONE"
                : classifyFindings(findings), findings);
    }

    private static Map<String, String> verifyCampaignEvidence(
            final Path root,
            final Map<String, String> fields,
            final Map<String, String> gate,
            final List<String> findings) throws IOException {
        final Path evidence = root.resolve("campaign-evidence-v2.txt");
        if (!Files.isRegularFile(evidence)) {
            findings.add("missing campaign evidence payload");
            return Map.of();
        }
        if (!isGatePayloadBound(root, gate, evidence)) {
            findings.add("campaign evidence hash or size mismatch");
        }
        verifySidecar(evidence.resolveSibling(evidence.getFileName() + ".sha256"),
                evidence, findings);
        final Map<String, String> campaignEvidence = readKeyValue(evidence);
        verifyCampaignIdentity(fields, campaignEvidence, findings);
        if (!"PASS".equals(fields.get("campaign.outcome"))) {
            return campaignEvidence;
        }
        final Path lifecycle = root.resolve("lifecycle").resolve("SHA256SUMS");
        final Path management = root.resolve("management").resolve("SHA256SUMS");
        if (!isGatePayloadBound(root, gate, lifecycle)) {
            findings.add("campaign lifecycle inventory is not hash-bound");
        }
        if (!isGatePayloadBound(root, gate, management)) {
            findings.add("campaign management inventory is not hash-bound");
        }
        verifyDirectoryInventory(lifecycle, findings);
        verifyDirectoryInventory(management, findings);
        verifyDirectoryIdentity(lifecycle, campaignEvidence, findings);
        verifyDirectoryIdentity(management, campaignEvidence, findings);
        return campaignEvidence;
    }

    private static void verifyFormalCampaignSemantics(
            final Map<String, String> campaign,
            final Map<String, String> evidence,
            final Map<String, String> gate,
            final List<String> findings) {
        if (!"PASS".equals(campaign.get("campaign.outcome"))) {
            return;
        }
        requireExact(campaign, "gate.id", "G4", findings, "formal campaign gate");
        requireExact(campaign, "gate.version", GaFormalPerformanceContract.CAMPAIGN,
                findings, "formal campaign gate version");
        requireExact(campaign, "run.count", Integer.toString(GaFormalPerformanceContract.RUN_COUNT),
                findings, "formal campaign run count");
        requireExact(campaign, "campaign.requiredRunCount",
                Integer.toString(GaFormalPerformanceContract.RUN_COUNT), findings,
                "formal campaign required run count");
        requireExact(campaign, "campaign.validRunCount",
                Integer.toString(GaFormalPerformanceContract.RUN_COUNT), findings,
                "formal campaign valid run count");
        requireExact(evidence, "schema", "ga-g4-performance-campaign-v2", findings,
                "formal campaign schema");
        requireExact(evidence, "formal", "true", findings, "formal campaign lane");
        requireExact(evidence, "lane", "FORMAL_G4", findings, "formal campaign lane");
        requireExact(evidence, "matrix.version", GaPerformanceMatrix.APPROVED_VERSION,
                findings, "formal campaign matrix");
        requireExact(evidence, "gate.id", "G4", findings, "formal campaign gate");
        requireExact(evidence, "gate.version", GaFormalPerformanceContract.CAMPAIGN,
                findings, "formal campaign gate version");
        requireExact(evidence, "campaign.gate", "G4", findings, "formal campaign owner");
        requireExact(evidence, "protocol", GaPerformanceMatrix.APPROVED_PROTOCOL,
                findings, "formal campaign protocol");
        requireExact(evidence, "protocolV2.window", Integer.toString(
                GaPerformanceMatrix.APPROVED_PROTOCOL_V2_WINDOW), findings,
                "formal campaign protocol window");
        requireExact(evidence, "walMode", GaPerformanceMatrix.APPROVED_WAL_MODE,
                findings, "formal campaign WAL");
        requireExact(evidence, "loadModel", GaPerformanceMatrix.APPROVED_LOAD_MODEL,
                findings, "formal campaign load model");
        requireExact(evidence, "profile", GaPerformanceMatrix.APPROVED_PROFILE, findings,
                "formal campaign profile");
        requireExact(evidence, "seed", Long.toString(GaPerformanceMatrix.APPROVED_SEED),
                findings, "formal campaign seed");
        requireExact(evidence, "warmup.duration.nanos", Long.toString(
                GaFormalPerformanceContract.WARMUP.toNanos()), findings,
                "formal campaign warmup");
        requireExact(evidence, "measurement.duration.nanos", Long.toString(
                GaFormalPerformanceContract.MEASUREMENT.toNanos()), findings,
                "formal campaign measurement");
        requireExact(evidence, "lifecycle.cycles", Integer.toString(
                GaFormalPerformanceContract.LIFECYCLE_CYCLES), findings,
                "formal lifecycle semantics");
        requireExact(evidence, "management.warmup.duration.nanos", Long.toString(
                GaFormalPerformanceContract.MANAGEMENT_WARMUP.toNanos()), findings,
                "formal management warmup");
        requireExact(evidence, "management.measurement.duration.nanos", Long.toString(
                GaFormalPerformanceContract.MANAGEMENT_MEASUREMENT.toNanos()), findings,
                "formal management measurement");
        requireExact(evidence, "management.status.interval.nanos", Long.toString(
                GaFormalPerformanceContract.MANAGEMENT_INTERVAL.toNanos()), findings,
                "formal management interval");
        requireExact(evidence, "management.status.requestCount", Integer.toString(
                GaFormalPerformanceContract.MANAGEMENT_STATUS_REQUESTS), findings,
                "formal management request count");
        if (evidence.get("campaign.id") == null || evidence.get("campaign.id").isBlank()
                || !Objects.equals(evidence.get("campaign.id"), campaign.get("campaign.id"))) {
            findings.add("formal campaign identity is missing or mismatched");
        }
        if (!gate.isEmpty()) {
            requireExact(gate, "gate.id", "G4", findings, "formal gate id");
            requireExact(gate, "gate.version", GaFormalPerformanceContract.CAMPAIGN,
                    findings, "formal gate version");
            requireExact(gate, "campaign.id", campaign.get("campaign.id"), findings,
                    "formal gate campaign owner");
            requireExact(gate, "campaign.gate", "G4", findings, "formal gate campaign owner");
        }
    }

    private static void requireExact(
            final Map<String, String> fields,
            final String key,
            final String expected,
            final List<String> findings,
            final String description) {
        if (expected == null || !Objects.equals(expected, fields.get(key))) {
            findings.add(description + " mismatch: " + key);
        }
    }

    private static void verifyCampaignRunBinding(
            final Map<String, String> campaign,
            final Map<String, String> evidence,
            final Map<String, String> run,
            final int index,
            final String actualManifestPath,
            final String actualManifestSha,
            final Set<String> physicalExecutions,
            final List<String> findings) {
        final String prefix = String.format("run.%04d.", index);
        final Map<String, String> expected = new LinkedHashMap<>();
        expected.put("id", campaign.get(prefix + "id"));
        expected.put("outcome", campaign.get(prefix + "outcome"));
        expected.put("manifestPath", campaign.get(prefix + "manifestPath"));
        expected.put("manifestSha256", campaign.get(prefix + "manifestSha256"));
        expected.put("configurationIdentitySha256",
                campaign.get(prefix + "configurationIdentitySha256"));
        expected.put("comparabilityIdentitySha256",
                campaign.get(prefix + "comparabilityIdentitySha256"));
        expected.forEach((suffix, value) -> {
            if (value == null || value.isBlank()) {
                findings.add("campaign run binding is missing: " + prefix + suffix);
            }
            final String runKey = switch (suffix) {
                case "id" -> "run.id";
                case "outcome" -> "evidence.outcome";
                case "manifestPath" -> null;
                case "manifestSha256" -> null;
                case "configurationIdentitySha256" -> "configuration.identitySha256";
                case "comparabilityIdentitySha256" -> "comparability.identitySha256";
                default -> null;
            };
            if (runKey != null && !Objects.equals(value, run.get(runKey))) {
                findings.add("campaign run binding mismatch: " + prefix + suffix);
            }
        });
        if (!Objects.equals(campaign.get(prefix + "manifestPath"), actualManifestPath)
                || !Objects.equals(campaign.get(prefix + "manifestSha256"), actualManifestSha)) {
            findings.add("campaign run manifest path/hash is not bound: " + index);
        }
        final String physical = campaign.get(prefix + "physicalExecutionId");
        if (!physicalExecutionIdWellFormed(physical) || !physicalExecutions.add(physical)
                || !Objects.equals(physical, run.get("physicalExecution.id"))) {
            findings.add("campaign run physical execution ownership mismatch: " + index);
        }
        if (!Objects.equals(campaign.get("campaign.id"), run.get("campaign.id"))) {
            findings.add("campaign run campaign identity mismatch: " + index);
        }
        final String evidencePrefix = prefix;
        for (String suffix : List.of("id", "outcome", "manifestPath", "manifestSha256",
                "physicalExecutionId", "configurationIdentitySha256",
                "comparabilityIdentitySha256")) {
            if (!Objects.equals(evidence.get(evidencePrefix + suffix),
                    "physicalExecutionId".equals(suffix) ? physical
                            : "configurationIdentitySha256".equals(suffix)
                            ? campaign.get(prefix + "configurationIdentitySha256")
                            : "comparabilityIdentitySha256".equals(suffix)
                            ? campaign.get(prefix + "comparabilityIdentitySha256")
                            : campaign.get(prefix + suffix))) {
                findings.add("campaign evidence run binding mismatch: " + prefix + suffix);
            }
        }
    }

    private static void verifyCampaignGateInventory(
            final Path root,
            final Map<String, String> gate,
            final List<String> findings) throws IOException {
        if (gate.isEmpty()) {
            return;
        }
        final int count = integerUnchecked(gate.get("manifest.count"));
        final Map<String, String> entries = new TreeMap<>();
        for (int index = 1; index <= count; index++) {
            final String prefix = String.format("manifest.%04d.", index);
            final String path = gate.get(prefix + "path");
            final String sha = gate.get(prefix + "sha256");
            if (path == null || sha == null || entries.put(path, sha) != null) {
                findings.add("campaign gate manifest inventory has duplicate or missing path");
                continue;
            }
            final Path payload = resolvePayload(root, path);
            if (!Files.isRegularFile(payload)
                    || !sha.equals(QualificationArtifactHasher.sha256(payload))) {
                findings.add("campaign gate manifest hash mismatch: " + path);
            }
        }
        try (var paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                final Path gatePath = root.resolve("g4-gate-result-v1.txt").normalize();
                final Path gateSidecar = root.resolve("g4-gate-result-v1.txt.sha256").normalize();
                if (path.equals(gatePath) || path.equals(gateSidecar)) {
                    return;
                }
                final String relative = root.relativize(path).toString().replace('\\', '/');
                final String actual = entries.get(relative);
                try {
                    if (actual == null || !actual.equals(QualificationArtifactHasher.sha256(path))) {
                        findings.add("campaign gate inventory does not cover payload: " + relative);
                    }
                } catch (IOException failure) {
                    findings.add("campaign gate inventory cannot hash payload: " + relative);
                }
            });
        }
    }

    private static boolean physicalExecutionIdValid(
            final String value,
            final Set<String> seen) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            return UUID.fromString(value).toString().equals(value) && seen.add(value);
        } catch (IllegalArgumentException failure) {
            return false;
        }
    }

    private static boolean isGatePayloadBound(
            final Path root, final Map<String, String> gate, final Path payload) throws IOException {
        if (gate.isEmpty() || !Files.isRegularFile(payload)) {
            return false;
        }
        final String expectedPath = root.relativize(payload.toAbsolutePath().normalize())
                .toString().replace('\\', '/');
        final String expectedSha = QualificationArtifactHasher.sha256(payload);
        for (int index = 1; index <= integerUnchecked(gate.get("manifest.count")); index++) {
            final String prefix = String.format("manifest.%04d.", index);
            if (expectedPath.equals(gate.get(prefix + "path"))
                    && expectedSha.equals(gate.get(prefix + "sha256"))) {
                return true;
            }
        }
        return false;
    }

    /** Returns whether the authoritative campaign gate inventories its campaign and run manifests. */
    static boolean campaignGateBindsRequiredManifests(
            final Path root,
            final Map<String, String> campaign,
            final Map<String, String> gate) throws IOException {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(campaign, "campaign");
        Objects.requireNonNull(gate, "gate");
        final Path normalizedRoot = root.toAbsolutePath().normalize();
        if (!isGatePayloadBound(normalizedRoot, gate,
                normalizedRoot.resolve("g4-campaign-manifest-v1.txt"))) {
            return false;
        }
        final int runCount = integer(campaign, "run.count");
        for (int index = 1; index <= runCount; index++) {
            final String path = campaign.get(String.format("run.%04d.manifestPath", index));
            if (path == null || !isGatePayloadBound(normalizedRoot, gate,
                    resolvePayload(normalizedRoot, path))) {
                return false;
            }
        }
        return runCount > 0;
    }

    /** Returns whether every regular campaign payload is covered by the authoritative gate. */
    static boolean campaignGateInventoryComplete(
            final Path root,
            final Map<String, String> gate) {
        final List<String> findings = new ArrayList<>();
        try {
            verifyCampaignGateInventory(root.toAbsolutePath().normalize(), gate, findings);
        } catch (IOException | RuntimeException failure) {
            return false;
        }
        return findings.isEmpty();
    }

    /** Returns whether one run's manifest, campaign index, and campaign evidence agree. */
    static boolean campaignRunBindingComplete(
            final Map<String, String> campaign,
            final Map<String, String> evidence,
            final Map<String, String> run,
            final int index,
            final String actualManifestPath,
            final String actualManifestSha) {
        final List<String> findings = new ArrayList<>();
        verifyCampaignRunBinding(campaign, evidence, run, index, actualManifestPath,
                actualManifestSha, new HashSet<>(), findings);
        return findings.isEmpty();
    }

    /** Returns whether a PASS campaign carries the complete frozen formal contract. */
    static boolean formalCampaignSemanticsComplete(
            final Map<String, String> campaign,
            final Map<String, String> evidence,
            final Map<String, String> gate) {
        final List<String> findings = new ArrayList<>();
        verifyFormalCampaignSemantics(campaign, evidence, gate, findings);
        return findings.isEmpty();
    }

    /** Returns whether raw latency/capacity rows are linked to authoritative request identities. */
    static boolean requestCorrelationsComplete(
            final Map<String, String> raw,
            final Path latencySamples,
            final Path capacityEvidence) {
        final List<String> findings = new ArrayList<>();
        try {
            final List<LatencyRow> latency = readLatencySamples(latencySamples);
            final Map<String, String> capacity = readKeyValue(capacityEvidence);
            final int count = integer(capacity, "releaseSampleCount");
            final List<CapacityRow> releaseRows = count > 0
                    ? readCapacityRows(capacity) : List.of();
            verifyRequestCorrelations(raw, latency, releaseRows, findings);
        } catch (IOException | RuntimeException failure) {
            return false;
        }
        return findings.isEmpty();
    }

    /** Returns whether required resource, JFR, and configuration payloads are owned and hashed. */
    static boolean mandatoryRuntimeEvidenceComplete(
            final Path root,
            final Map<String, String> fields,
            final Map<String, String> inventory) {
        return mandatoryRuntimeEvidenceFindings(root, fields, inventory).isEmpty();
    }

    /** Returns mandatory-artifact findings for direct qualification regression fixtures. */
    static List<String> mandatoryRuntimeEvidenceFindings(
            final Path root,
            final Map<String, String> fields,
            final Map<String, String> inventory) {
        final List<String> findings = new ArrayList<>();
        verifyMandatoryRuntimeEvidence(root.toAbsolutePath().normalize(), fields, findings,
                inventory);
        return List.copyOf(findings);
    }

    private static int integerUnchecked(final String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Math.toIntExact(Long.parseLong(value));
        } catch (NumberFormatException | ArithmeticException failure) {
            return 0;
        }
    }

    private static void verifyCampaignIdentity(
            final Map<String, String> manifest,
            final Map<String, String> evidence,
            final List<String> findings) throws IOException {
        final Map<String, String> manifestBindings = Map.ofEntries(
                Map.entry("campaign.id", "campaign.id"),
                Map.entry("campaign.gate", "gate.id"),
                Map.entry("candidate.tag", "candidate.tag"),
                Map.entry("candidate.tagObjectSha", "candidate.tagObjectSha"),
                Map.entry("candidate.productionSha", "candidate.productionSha"),
                Map.entry("candidate.applicationJarSha256", "candidate.applicationJarSha256"),
                Map.entry("qualification.controllerSha", "controller.gitSha"),
                Map.entry("campaign.requiredRunCount", "campaign.requiredRunCount"),
                Map.entry("campaign.runCount", "run.count"));
        for (Map.Entry<String, String> entry : manifestBindings.entrySet()) {
            if (!Objects.equals(evidence.get(entry.getKey()), manifest.get(entry.getValue()))) {
                findings.add("campaign identity mismatch: " + entry.getKey());
            }
        }
        final Map<String, String> fixedBindings = Map.of(
                "candidate.tag", "v0.9.0-rc.2",
                "candidate.tagObjectSha", "9e2a67ada0e3b6220b730131d0bae79dc03073ed",
                "candidate.productionSha", "740e8a3dea0a759c707c597778c26c41e9bb3e47",
                "candidate.applicationJarSha256",
                        "0b77d37985b9124ac4fd1b90d669db550efd0cf00c23af65fdc29b35071703c4",
                "protocol", GaPerformanceMatrix.APPROVED_PROTOCOL,
                "protocolV2.window", Integer.toString(
                        GaPerformanceMatrix.APPROVED_PROTOCOL_V2_WINDOW),
                "walMode", GaPerformanceMatrix.APPROVED_WAL_MODE,
                "loadModel", GaPerformanceMatrix.APPROVED_LOAD_MODEL,
                "profile", GaPerformanceMatrix.APPROVED_PROFILE,
                "seed", Long.toString(GaPerformanceMatrix.APPROVED_SEED));
        for (Map.Entry<String, String> entry : fixedBindings.entrySet()) {
            if (!Objects.equals(entry.getValue(), evidence.get(entry.getKey()))) {
                findings.add("campaign fixed identity mismatch: " + entry.getKey());
            }
        }
        if ("PASS".equals(manifest.get("campaign.outcome"))
                && (!evidence.containsKey("qualification.jarSha256")
                || !evidence.get("qualification.jarSha256")
                .matches("[0-9a-f]{64}"))) {
            findings.add("campaign evidence is missing qualification JAR identity");
        }
        for (int index = 1; index <= integer(manifest, "run.count"); index++) {
            final String prefix = String.format("run.%04d.", index);
            if (!Objects.equals(evidence.get(prefix + "id"),
                    manifest.get(prefix + "id"))
                    || !Objects.equals(evidence.get(prefix + "manifestPath"),
                    manifest.get(prefix + "manifestPath"))
                    || !Objects.equals(evidence.get(prefix + "manifestSha256"),
                    manifest.get(prefix + "manifestSha256"))) {
                findings.add("campaign evidence run binding mismatch: " + prefix);
            }
        }
    }


    private static void verifyDirectoryIdentity(
            final Path inventory,
            final Map<String, String> campaignEvidence,
            final List<String> findings) throws IOException {
        final Path directory = inventory.getParent();
        final Map<String, String> expected = Map.ofEntries(
                Map.entry("candidate.tag", campaignEvidence.get("candidate.tag")),
                Map.entry("candidate.productionSha", campaignEvidence.get("candidate.productionSha")),
                Map.entry("candidate.applicationJarSha256",
                        campaignEvidence.get("candidate.applicationJarSha256")),
                Map.entry("qualification.jarSha256", campaignEvidence.get("qualification.jarSha256")),
                Map.entry("controller.gitSha", campaignEvidence.get("qualification.controllerSha")),
                Map.entry("protocol", campaignEvidence.get("protocol")),
                Map.entry("window", campaignEvidence.get("protocolV2.window")),
                Map.entry("walMode", campaignEvidence.get("walMode")));
        try (var paths = Files.walk(directory)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".txt"))
                    .forEach(path -> {
                        try {
                            final Map<String, String> fields = readKeyValue(path);
                            if (fields.containsKey("formal")
                                    && "true".equals(fields.get("formal"))) {
                                for (Map.Entry<String, String> entry : expected.entrySet()) {
                                    if (entry.getValue() == null
                                            || !Objects.equals(entry.getValue(),
                                                    fields.get(entry.getKey()))) {
                                        findings.add("campaign child identity mismatch: "
                                                + path.getFileName() + " " + entry.getKey());
                                    }
                                }
                            }
                        } catch (IOException | RuntimeException failure) {
                            findings.add("invalid campaign child evidence: " + path);
                        }
                    });
        }
    }

    private static void verifyDirectoryInventory(
            final Path inventory,
            final List<String> findings) throws IOException {
        verifySidecar(inventory.resolveSibling(inventory.getFileName() + ".sha256"),
                inventory, findings);
        final Path directory = inventory.getParent();
        final Map<String, String> entries = readCanonicalInventory(inventory);
        try (var paths = Files.walk(directory)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                final String name = directory.toAbsolutePath().normalize().relativize(
                        path.toAbsolutePath().normalize()).toString().replace('\\', '/');
                if (name.equals(inventory.getFileName().toString())
                        || name.toLowerCase().endsWith(".sha256")) {
                    return;
                }
                if (!entries.containsKey(name)) {
                    findings.add("unlisted campaign evidence payload: " + name);
                }
            });
        }
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            final Path payload = resolvePayload(directory, entry.getKey());
            final String actual = QualificationArtifactHasher.sha256(payload);
            if (!actual.equals(entry.getValue())) {
                findings.add("campaign inventory hash mismatch: " + entry.getKey());
            }
            verifySidecar(payload.resolveSibling(payload.getFileName() + ".sha256"),
                    payload, findings);
        }
    }

    private static Map<String, String> readCanonicalInventory(final Path inventory)
            throws IOException {
        final Map<String, String> result = new TreeMap<>();
        String previous = "";
        for (String line : Files.readAllLines(inventory, StandardCharsets.US_ASCII)) {
            if (line.length() < 68 || line.charAt(64) != ' ' || line.charAt(65) != ' ') {
                throw new IOException("malformed campaign inventory");
            }
            final String digest = line.substring(0, 64);
            final String path = line.substring(66);
            if (!digest.matches("[0-9a-f]{64}") || path.isBlank()
                    || path.compareTo(previous) <= 0 || result.put(path, digest) != null) {
                throw new IOException("non-canonical campaign inventory");
            }
            previous = path;
        }
        if (result.isEmpty()) {
            throw new IOException("empty campaign inventory");
        }
        return Map.copyOf(result);
    }

    /** Verifies a run inventory, all payload hashes and the canonical run manifest. */
    public static Verification verifyRun(final Path runDirectory) {
        final List<String> findings = new ArrayList<>();
        try {
            final Path root = Objects.requireNonNull(runDirectory, "runDirectory")
                    .toAbsolutePath().normalize();
            final Path inventory = root.resolve("SHA256SUMS");
            final Path inventorySidecar = root.resolve("SHA256SUMS.sha256");
            final var inventoryEntries = readInventory(root, inventory);
            verifySidecar(inventorySidecar, inventory, findings);
            for (var entry : inventoryEntries) {
                final Path payload = resolvePayload(root, entry.path());
                if (!Files.isRegularFile(payload)) {
                    findings.add("missing payload: " + entry.path());
                    continue;
                }
                final String actual = QualificationArtifactHasher.sha256(payload);
                if (!actual.equals(entry.sha256())) {
                    findings.add("payload hash mismatch: " + entry.path());
                }
                verifySidecar(payload.resolveSibling(payload.getFileName() + ".sha256"),
                        payload, findings);
            }
            verifyNoUnlistedPayloads(root, inventoryEntries, findings);
            final Path manifest = root.resolve("ga-run-manifest-v1.txt");
            if (!Files.isRegularFile(manifest)) {
                findings.add("missing canonical run manifest");
            } else {
                try {
                    final Map<String, String> fields = GaEvidenceStore.read(
                            manifest, GaEvidenceCodec.Schema.RUN);
                    verifySidecar(manifest.resolveSibling(manifest.getFileName() + ".sha256"),
                            manifest, findings);
                    final String inventorySha = fields.get("artifact.inventory.sha256");
                    final String inventorySize = fields.get("artifact.inventory.size");
                    if (!"SHA256SUMS".equals(fields.get("artifact.inventory.path"))
                            || !Objects.equals(inventorySha,
                            QualificationArtifactHasher.sha256(inventory))
                            || !Objects.equals(inventorySize, Long.toString(Files.size(inventory)))) {
                        findings.add("run manifest inventory binding is not authoritative");
                    }
                    verifySemanticEvidence(root, fields, findings);
                    verifyGate(root, fields, findings);
                } catch (final IOException failure) {
                    findings.add("invalid canonical run manifest: " + failure.getMessage());
                }
            }
        } catch (final IOException | RuntimeException failure) {
            findings.add("evidence reconstruction failed: " + failure.getMessage());
        }
        return new Verification(findings.isEmpty(), findings.isEmpty() ? "NONE"
                : classifyFindings(findings), findings);
    }

    /**
     * Classifies verifier findings without collapsing every evidence failure into B0.
     * Integrity and identity failures are B0; trustworthy candidate/SLO failures are B1;
     * qualification evaluator defects are B2; material runtime/environment mismatches are
     * B3; and contract/governance defects are B4.
     */
    static String classifyFindings(final List<String> findings) {
        Objects.requireNonNull(findings, "findings");
        if (findings.stream().anyMatch(GaFormalPerformanceEvidenceVerifier::isGovernanceFinding)) {
            return "B4";
        }
        if (findings.stream().anyMatch(GaFormalPerformanceEvidenceVerifier::isIntegrityFinding)) {
            return "B0";
        }
        if (findings.stream().anyMatch(GaFormalPerformanceEvidenceVerifier::isEnvironmentFinding)) {
            return "B3";
        }
        if (findings.stream().anyMatch(GaFormalPerformanceEvidenceVerifier::isCandidateFinding)) {
            return "B1";
        }
        return "B2";
    }

    private static boolean isGovernanceFinding(final String finding) {
        return finding.contains("contract") || finding.contains("governance")
                || finding.contains("historical") || finding.contains("RC1");
    }

    private static boolean isIntegrityFinding(final String finding) {
        return finding.contains("hash") || finding.contains("sidecar")
                || finding.contains("inventory") || finding.contains("manifest")
                || finding.contains("payload") || finding.contains("tamper")
                || finding.contains("canonical") || finding.contains("identity mismatch")
                || finding.contains("identity is not manifest-bound");
    }

    private static boolean isEnvironmentFinding(final String finding) {
        return finding.contains("environment") || finding.contains("configuration")
                || finding.contains("JVM") || finding.contains("GC")
                || finding.contains("heap") || finding.contains("CPU");
    }

    private static boolean isCandidateFinding(final String finding) {
        return finding.contains("performance predicates") || finding.contains("throughput")
                || finding.contains("latency SLO") || finding.contains("regression predicate")
                || finding.contains("non-zero errors") || finding.contains("non-zero timeouts")
                || finding.contains("non-zero mismatches") || finding.contains("terminalFailures")
                || finding.contains("candidate state") || finding.contains("candidate failure")
                || finding.contains("reports invalid candidate health");
    }

    private static void verifyNoUnlistedPayloads(
            final Path root,
            final List<InventoryEntry> inventoryEntries,
            final List<String> findings) throws IOException {
        final Set<String> listed = new HashSet<>();
        for (InventoryEntry entry : inventoryEntries) {
            listed.add(entry.path());
        }
        try (var paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                final String relative = root.toAbsolutePath().normalize().relativize(
                        path.toAbsolutePath().normalize()).toString().replace('\\', '/');
                final String name = path.getFileName().toString();
                if ("SHA256SUMS".equals(name) || "ga-run-manifest-v1.txt".equals(name)
                        || "g4-gate-result-v1.txt".equals(name)
                        || name.toLowerCase().endsWith(".sha256")) {
                    return;
                }
                if (!listed.contains(relative)) {
                    findings.add("unlisted payload: " + relative);
                }
            });
        }
    }

    private static void verifySemanticEvidence(
            final Path root,
            final Map<String, String> manifest,
            final List<String> findings) throws IOException {
        final Path rawPath = root.resolve("raw-evidence-v2.txt");
        final Path samplesPath = root.resolve("latency-samples-v2.csv");
        final Path capacityPath = root.resolve("capacity-evidence-v2.txt");
        if (!Files.isRegularFile(rawPath) || !Files.isRegularFile(samplesPath)
                || !Files.isRegularFile(capacityPath)) {
            findings.add("missing raw performance evidence payload");
            return;
        }
        final Map<String, String> raw = readKeyValue(rawPath);
        compareRawManifest(raw, manifest, findings);
        final boolean formal = "true".equals(manifest.get("run.formal"))
                || "true".equals(raw.get("formal"));
        if (formal) {
            verifyFormalRunSemantics(raw, manifest, findings);
        } else if (!"false".equals(manifest.get("run.formal"))
                || !"false".equals(raw.get("formal"))) {
            findings.add("run lane/formal declaration is ambiguous");
        }
        final List<LatencyRow> latency = readLatencySamples(samplesPath);
        verifyAccounting(raw, manifest, latency, findings);
        verifyLatency(raw, manifest, latency, findings);
        verifyCapacity(raw, manifest, latency, capacityPath, findings);
        if ("PASS".equals(manifest.get("evidence.outcome"))) {
            verifyMandatoryRuntimeEvidence(root, raw, findings, readInventoryMap(root));
            verifyCandidateHealth(raw, findings);
            verifyIndependentPerformancePass(raw, latency, findings);
        }
    }

    private static void verifyGate(
            final Path root,
            final Map<String, String> manifest,
            final List<String> findings) throws IOException {
        final Path gate = root.resolve("g4-gate-result-v1.txt");
        if (!Files.isRegularFile(gate)) {
            findings.add("missing run gate result");
            return;
        }
        verifySidecar(gate.resolveSibling(gate.getFileName() + ".sha256"), gate, findings);
        final Map<String, String> fields = GaEvidenceStore.read(
                gate, GaEvidenceCodec.Schema.GATE);
        if (!Objects.equals(manifest.get("evidence.outcome"), fields.get("evidence.outcome"))) {
            findings.add("run manifest/gate outcome mismatch");
        }
        if (!"G4".equals(fields.get("gate.id"))
                || !GaFormalPerformanceContract.CAMPAIGN.equals(fields.get("gate.version"))) {
            findings.add("run gate is not the frozen G4 gate id/version");
        }
        if (manifest.containsKey("campaign.id")
                && (!Objects.equals(manifest.get("campaign.id"), fields.get("campaign.id"))
                || !"G4".equals(fields.get("campaign.gate")))) {
            findings.add("run gate campaign ownership is missing or mismatched");
        }
        if (!Objects.equals(manifest.get("candidate.tag"), fields.get("candidate.tag"))
                || !Objects.equals(manifest.get("candidate.tagObjectSha"),
                        fields.get("candidate.tagObjectSha"))
                || !Objects.equals(manifest.get("candidate.applicationJarSha256"),
                        fields.get("candidate.applicationJarSha256"))
                || !Objects.equals(manifest.get("controller.gitSha"),
                        fields.get("controller.gitSha"))) {
            findings.add("run gate identity is not bound to the run manifest");
        }
        if (!Objects.equals(manifest.get("configuration.identitySha256"),
                fields.get("configuration.identitySha256"))
                || !Objects.equals(manifest.get("comparability.identitySha256"),
                fields.get("comparability.identitySha256"))) {
            findings.add("run gate configuration/comparability identity is not manifest-bound");
        }
        final int manifestCount = integer(fields, "manifest.count");
        if (manifestCount < 1) {
            findings.add("run gate has no manifest reference");
        }
        boolean runManifestBound = false;
        for (int index = 1; index <= manifestCount; index++) {
            final String prefix = String.format("manifest.%04d.", index);
            final Path referenced = resolvePayload(root, fields.get(prefix + "path"));
            if (referenced.equals(root.resolve("ga-run-manifest-v1.txt").normalize())
                    && Objects.equals(fields.get(prefix + "sha256"),
                            QualificationArtifactHasher.sha256(referenced))) {
                runManifestBound = true;
            }
        }
        if (!runManifestBound) {
            findings.add("run gate does not bind the canonical run manifest");
        }
        if ("PASS".equals(fields.get("evidence.outcome"))) {
            if (!"NONE".equals(fields.get("blocker.classification"))) {
                findings.add("PASS run gate has a blocker classification");
            }
            for (int index = 1; index <= integer(fields, "criterion.count"); index++) {
                if (!"PASS".equals(fields.get(String.format(
                        "criterion.%04d.result", index)))) {
                    findings.add("PASS run gate has a failing criterion");
                }
            }
        }
    }

    private static void verifyFormalRunSemantics(
            final Map<String, String> raw,
            final Map<String, String> manifest,
            final List<String> findings) {
        final Map<String, String> expected = Map.ofEntries(
                Map.entry("schema", GaPerformanceMatrix.APPROVED_VERSION),
                Map.entry("formal", "true"),
                Map.entry("lane", "FORMAL_G4"),
                Map.entry("run.formal", "true"),
                Map.entry("protocol", GaPerformanceMatrix.APPROVED_PROTOCOL),
                Map.entry("window", Integer.toString(
                        GaPerformanceMatrix.APPROVED_PROTOCOL_V2_WINDOW)),
                Map.entry("walMode", GaPerformanceMatrix.APPROVED_WAL_MODE),
                Map.entry("loadModel", GaPerformanceMatrix.APPROVED_LOAD_MODEL),
                Map.entry("profile", GaPerformanceMatrix.APPROVED_PROFILE),
                Map.entry("seed", Long.toString(GaPerformanceMatrix.APPROVED_SEED)),
                Map.entry("warmupDurationNanos", Long.toString(
                        GaFormalPerformanceContract.WARMUP.toNanos())));
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            if (!Objects.equals(entry.getValue(), raw.get(entry.getKey()))) {
                findings.add("formal run semantic mismatch: " + entry.getKey());
            }
        }
        final long expectedMeasurement = GaFormalPerformanceContract.MEASUREMENT.toNanos();
        if (longValueOrDefault(raw.get("measurementDurationNanos"), -1L) != expectedMeasurement
                || longValueOrDefault(manifest.get("run.measurementDurationNanos"), -1L)
                != expectedMeasurement
                || longValueOrDefault(manifest.get("run.warmupDurationNanos"), -1L)
                != GaFormalPerformanceContract.WARMUP.toNanos()) {
            findings.add("formal run warmup/measurement duration is not exact");
        }
        final int ordinal = integerValue(raw.get("runOrdinal"), -1);
        if (ordinal < 1 || ordinal > GaFormalPerformanceContract.RUN_COUNT) {
            findings.add("formal run ordinal is outside the exact campaign set");
        }
        for (String key : List.of("gate.id", "gate.version")) {
            final String expectedValue = "gate.id".equals(key) ? "G4"
                    : GaFormalPerformanceContract.CAMPAIGN;
            if (!expectedValue.equals(manifest.get(key))) {
                findings.add("formal run gate semantic mismatch: " + key);
            }
        }
        if (!Objects.equals(raw.get("physicalExecutionId"), manifest.get("physicalExecution.id"))) {
            findings.add("formal run physical execution is not manifest-bound");
        }
        final String campaignId = manifest.get("campaign.id");
        if (campaignId == null || campaignId.isBlank()
                || !campaignId.equals(raw.get("campaign.id"))) {
            findings.add("formal run campaign identity is missing or mismatched");
        }
        verifyFixedRunIdentity(raw, manifest, findings);
    }

    private static void verifyCandidateHealth(
            final Map<String, String> raw,
            final List<String> findings) {
        final Map<String, String> rawExpected = Map.ofEntries(
                Map.entry("candidate.ready", "true"),
                Map.entry("candidate.failureCode", "NONE"),
                Map.entry("candidate.terminalFailures", "0"),
                Map.entry("process.exitCode", "0"),
                Map.entry("candidate.healthEvidenceComplete", "true"),
                Map.entry("evidence.mandatoryComplete", "true"));
        for (Map.Entry<String, String> entry : rawExpected.entrySet()) {
            final String observed = raw.get(entry.getKey());
            if (observed == null || observed.isBlank()) {
                findings.add("PASS raw evidence is missing candidate health field: "
                        + entry.getKey());
            } else if (!Objects.equals(entry.getValue(), observed)) {
                findings.add("PASS raw evidence reports invalid candidate health: "
                        + entry.getKey());
            }
        }
        for (String key : List.of("errors", "timeouts", "mismatches")) {
            final String observed = raw.get(key);
            if (observed == null || observed.isBlank()) {
                findings.add("PASS raw evidence is missing candidate health field: " + key);
            } else if (!"0".equals(observed)) {
                findings.add("PASS raw evidence has non-zero " + key);
            }
        }
    }

    private static void verifyIndependentPerformancePass(
            final Map<String, String> raw,
            final List<LatencyRow> rows,
            final List<String> findings) {
        try {
            final long accepted = longValue(raw, "measurement.acceptedCommands");
            final long duration = longValue(raw, "measurementDurationNanos");
            if (!independentPerformancePass(accepted, duration,
                    rows.stream().mapToLong(LatencyRow::latencyNanos).toArray())) {
                findings.add("raw evidence fails the independent G4 performance predicates");
            }
        } catch (IOException | ArithmeticException failure) {
            findings.add("cannot independently evaluate G4 performance predicates: "
                    + failure.getMessage());
        }
    }

    static boolean independentPerformancePass(
            final long accepted, final long durationNanos, final long[] latencyNanos) {
        if (accepted <= 0L || durationNanos <= 0L || latencyNanos.length == 0) {
            return false;
        }
        final double throughput = accepted * 1_000_000_000.0d / durationNanos;
        final QualificationPercentiles.Summary summary =
                QualificationPercentiles.summarize(latencyNanos);
        return throughput >= 500.0d
                && summary.p50Nanos() <= GaPerformanceEvaluator.MAX_P50_NANOS
                && summary.p99Nanos() <= GaPerformanceEvaluator.MAX_P99_NANOS
                && summary.p999Nanos() <= GaPerformanceEvaluator.MAX_P999_NANOS;
    }

    private static void verifyMandatoryRuntimeEvidence(
            final Path root,
            final Map<String, String> fields,
            final List<String> findings,
            final Map<String, String> inventory) {
        try {
            if (!mandatoryIdentityFieldsComplete(fields)) {
                for (String key : List.of("physicalExecutionId", "candidate.tagObjectSha",
                        "controller.gitSha", "configuration.identitySha256",
                        "environment.identitySha256")) {
                    if (!nonBlank(fields.get(key)) || "UNAVAILABLE".equals(fields.get(key))) {
                        findings.add("mandatory evidence ownership field is missing: " + key);
                    }
                }
            }
            final Path resource = verifyMandatoryArtifact(root, fields,
                    "processEvidence.resourcePath", "processEvidence.resourceSha256",
                    inventory, findings, "resource evidence");
            final Path jfr = verifyMandatoryArtifact(root, fields,
                    "processEvidence.jfrPath", "processEvidence.jfrSha256",
                    inventory, findings, "JFR evidence");
            if (resource != null) {
                final QualificationResourceEvidence parsed =
                        QualificationResourceEvidenceReader.read(resource, 0);
                if (parsed.samples().isEmpty()) {
                    findings.add("PASS run has no resource evidence samples");
                }
            }
            if (jfr != null) {
                final GaJfrEvidence parsed = GaJfrEvidence.inspect(jfr, true, true, true);
                if (!parsed.complete()) {
                    findings.add("PASS run JFR evidence is not complete for the formal contract");
                }
            }
            final String configurationPath = fields.get("configuration.filePath");
            final String configurationSha = fields.get("configuration.fileSha256");
            if (configurationPath == null || configurationSha == null) {
                findings.add("mandatory configuration artifact binding is missing");
            } else {
                verifyMandatoryArtifact(root, fields, "configuration.filePath",
                        "configuration.fileSha256", inventory, findings, "configuration");
            }
            final Map<String, String> environment = new TreeMap<>();
            fields.forEach((key, value) -> {
                if (key.startsWith("environment.")
                        && !key.equals("environment.identitySha256")
                        && !key.equals("environment.bound")) {
                    environment.put(key.substring("environment.".length()), value);
                }
            });
            if (environment.isEmpty() || !Objects.equals(
                    GaPerformanceEnvironment.identity(environment),
                    fields.get("environment.identitySha256"))) {
                findings.add("environment identity is not recomputable from authoritative evidence");
            }
            verifyStorageOwnership(root, findings);
        } catch (IOException | RuntimeException failure) {
            findings.add("mandatory runtime evidence reconstruction failed: "
                    + failure.getMessage());
        }
    }

    private static Path verifyMandatoryArtifact(
            final Path root,
            final Map<String, String> fields,
            final String pathKey,
            final String shaKey,
            final Map<String, String> inventory,
            final List<String> findings,
            final String description) throws IOException {
        final String relative = fields.get(pathKey);
        final String declared = fields.get(shaKey);
        if (relative == null || declared == null || relative.isBlank()
                || !declared.matches("[0-9a-f]{64}")) {
            findings.add("mandatory " + description + " binding is missing or malformed");
            return null;
        }
        final Path payload = resolvePayload(root, relative);
        if (!Files.isRegularFile(payload) || Files.isSymbolicLink(payload)
                || !Files.isReadable(payload) || Files.size(payload) <= 0L) {
            findings.add("mandatory " + description + " is not a readable regular file");
            return null;
        }
        final String actual = QualificationArtifactHasher.sha256(payload);
        if (!declared.equals(actual)) {
            findings.add("mandatory " + description + " hash does not match its owner");
        }
        final String listed = inventory.get(root.relativize(payload).toString().replace('\\', '/'));
        if (!actual.equals(listed)) {
            findings.add("mandatory " + description + " is not bound by its authoritative inventory");
        }
        verifySidecar(payload.resolveSibling(payload.getFileName() + ".sha256"), payload, findings);
        return payload;
    }

    private static void verifyLifecycleDirectory(
            final Path root,
            final Map<String, String> campaign,
            final Map<String, String> campaignEvidence,
            final List<String> findings) {
        final Path summary = root.resolve("lifecycle-summary-v2.txt");
        final Path inventory = root.resolve("SHA256SUMS");
        try {
            if (!Files.isRegularFile(summary) || !Files.isRegularFile(inventory)) {
                findings.add("PASS campaign is missing lifecycle summary/inventory");
                return;
            }
            verifyDirectoryInventory(inventory, findings);
            final Map<String, String> inventoryEntries = readCanonicalInventory(inventory);
            final Map<String, String> fields = readKeyValue(summary);
            if (!"ga-g4-lifecycle-summary-v2".equals(fields.get("schema"))
                    || !"true".equals(fields.get("formal"))
                    || !"FORMAL_G4".equals(fields.get("lane"))
                    || !Objects.equals(fields.get("campaign.id"), campaign.get("campaign.id"))
                    || !"G4".equals(fields.get("gate.id"))
                    || !GaFormalPerformanceContract.CAMPAIGN.equals(fields.get("gate.version"))
                    || !GaPerformanceMatrix.APPROVED_VERSION.equals(fields.get("matrix.version"))
                    || !"60".equals(fields.get("matrix.cycles"))
                    || !"60".equals(fields.get("sample.count"))
                    || !"60".equals(fields.get("startup.sampleCount"))
                    || !"60".equals(fields.get("shutdown.sampleCount"))
                    || !"true".equals(fields.get("complete"))
                    || !"true".equals(fields.get("passed"))
                    || !"NONE".equals(fields.get("blocker"))) {
                findings.add("PASS campaign lifecycle summary is incomplete");
            }
            verifyChildIdentity(fields, campaignEvidence, "lifecycle summary", findings);
            if (!Objects.equals(fields.get("campaign.id"), campaignEvidence.get("campaign.id"))) {
                findings.add("lifecycle summary campaign identity mismatch");
            }
            final long[] startup = new long[60];
            final long[] shutdown = new long[60];
            final Set<String> physicalExecutions = new HashSet<>();
            String configurationIdentity = null;
            for (int index = 1; index <= 60; index++) {
                final String prefix = "cycle." + index + ".";
                final String relative = fields.get(prefix + "rawPath");
                if (relative == null || relative.isBlank()) {
                    findings.add("lifecycle raw path is missing: cycle " + index);
                    continue;
                }
                final Path raw = resolvePayload(root, relative);
                final String declared = fields.get(prefix + "rawSha256");
                if (!Files.isRegularFile(raw) || declared == null
                        || !declared.equals(QualificationArtifactHasher.sha256(raw))) {
                    findings.add("lifecycle raw evidence binding mismatch: cycle " + index);
                    continue;
                }
                verifySidecar(raw.resolveSibling(raw.getFileName() + ".sha256"), raw, findings);
                final Map<String, String> cycle = readKeyValue(raw);
                verifyChildIdentity(cycle, campaignEvidence, "lifecycle cycle " + index, findings);
                if (!"ga-g4-lifecycle-cycle-v2".equals(cycle.get("schema"))
                        || !"true".equals(cycle.get("formal"))
                        || !"FORMAL_G4".equals(cycle.get("lane"))
                        || !Objects.equals(cycle.get("campaign.id"), campaign.get("campaign.id"))
                        || !"G4".equals(cycle.get("gate.id"))
                        || !GaFormalPerformanceContract.CAMPAIGN.equals(cycle.get("gate.version"))
                        || !GaPerformanceMatrix.APPROVED_VERSION.equals(cycle.get("matrix.version"))
                        || !Integer.toString(index).equals(cycle.get("cycle"))
                        || !Objects.equals(fields.get(prefix + "physicalExecutionId"),
                        cycle.get("physicalExecutionId"))
                        || !physicalExecutionIdValid(cycle.get("physicalExecutionId"),
                        physicalExecutions)) {
                    findings.add("lifecycle cycle canonical identity mismatch: " + index);
                }
                verifyMandatoryRuntimeEvidence(raw.getParent(), cycle, findings,
                        rebaseInventory(inventory.getParent(), raw.getParent(), inventoryEntries));
                if (!"PASS".equals(cycle.get("outcome"))
                        || !"NONE".equals(cycle.get("blocker"))
                        || !"true".equals(cycle.get("candidate.ready"))
                        || !"NONE".equals(cycle.get("candidate.failureCode"))
                        || !"0".equals(cycle.get("candidate.terminalFailures"))
                        || !"0".equals(cycle.get("shutdown.exitCode"))
                        || !"true".equals(cycle.get("shutdown.completed"))
                        || !"true".equals(cycle.get("configuration.bound"))
                        || !"true".equals(cycle.get("environment.bound"))
                        || !"true".equals(cycle.get("candidate.bound"))
                        || !"true".equals(cycle.get("controller.bound"))) {
                    findings.add("lifecycle cycle is not a trustworthy PASS: " + index);
                }
                try {
                    startup[index - 1] = Long.parseLong(cycle.get("startupNanos"));
                    shutdown[index - 1] = Long.parseLong(cycle.get("shutdownNanos"));
                    if (startup[index - 1] < 0L || shutdown[index - 1] < 0L) {
                        findings.add("lifecycle cycle has negative timing: " + index);
                    }
                } catch (NumberFormatException | NullPointerException failure) {
                    findings.add("lifecycle cycle timing is invalid: " + index);
                }
                final String cycleConfiguration = cycle.get("configuration.identitySha256");
                if (configurationIdentity == null) {
                    configurationIdentity = cycleConfiguration;
                } else if (!Objects.equals(configurationIdentity, cycleConfiguration)) {
                    findings.add("lifecycle configuration identity is not stable");
                }
            }
            final QualificationPercentiles.Summary startupSummary =
                    QualificationPercentiles.summarize(startup);
            final QualificationPercentiles.Summary shutdownSummary =
                    QualificationPercentiles.summarize(shutdown);
            if (!Long.toString(startupSummary.p99Nanos()).equals(fields.get("startup.p99Nanos"))
                    || !Long.toString(shutdownSummary.p99Nanos())
                    .equals(fields.get("shutdown.p99Nanos"))) {
                findings.add("lifecycle summary percentiles are not recomputable");
            }
            if (!independentLifecyclePass(startup, shutdown)) {
                findings.add("lifecycle P99 exceeds the frozen 1.25s threshold");
            }
        } catch (IOException | RuntimeException failure) {
            findings.add("lifecycle evidence reconstruction failed: " + failure.getMessage());
        }
    }

    /** Independently applies the frozen startup and shutdown P99 lifecycle predicates. */
    static boolean independentLifecyclePass(
            final long[] startupNanos, final long[] shutdownNanos) {
        Objects.requireNonNull(startupNanos, "startupNanos");
        Objects.requireNonNull(shutdownNanos, "shutdownNanos");
        if (startupNanos.length != GaFormalPerformanceContract.LIFECYCLE_CYCLES
                || shutdownNanos.length != GaFormalPerformanceContract.LIFECYCLE_CYCLES) {
            return false;
        }
        for (long value : startupNanos) {
            if (value < 0L) {
                return false;
            }
        }
        for (long value : shutdownNanos) {
            if (value < 0L) {
                return false;
            }
        }
        final QualificationPercentiles.Summary startup =
                QualificationPercentiles.summarize(startupNanos);
        final QualificationPercentiles.Summary shutdown =
                QualificationPercentiles.summarize(shutdownNanos);
        return startup.p99Nanos() <= GaPerformanceEvaluator.MAX_LIFECYCLE_P99_NANOS
                && shutdown.p99Nanos() <= GaPerformanceEvaluator.MAX_LIFECYCLE_P99_NANOS;
    }

    /** Independently verifies that all performance runs share material identities. */
    static boolean independentPerformanceComparable(
            final List<String> configurationIdentities,
            final List<String> comparabilityIdentities) {
        Objects.requireNonNull(configurationIdentities, "configurationIdentities");
        Objects.requireNonNull(comparabilityIdentities, "comparabilityIdentities");
        if (configurationIdentities.size() != GaFormalPerformanceContract.RUN_COUNT
                || comparabilityIdentities.size() != GaFormalPerformanceContract.RUN_COUNT
                || configurationIdentities.stream().anyMatch(value -> value == null || value.isBlank())
                || comparabilityIdentities.stream()
                .anyMatch(value -> value == null || value.isBlank())) {
            return false;
        }
        final String configuration = configurationIdentities.get(0);
        final String comparability = comparabilityIdentities.get(0);
        return configurationIdentities.stream().allMatch(configuration::equals)
                && comparabilityIdentities.stream().allMatch(comparability::equals);
    }

    private static void verifyPerformanceComparability(
            final List<Map<String, String>> runManifests,
            final Map<String, String> campaign,
            final List<String> findings) {
        final List<String> configurations = runManifests.stream()
                .map(fields -> fields.get("configuration.identitySha256"))
                .toList();
        final List<String> comparability = runManifests.stream()
                .map(fields -> fields.get("comparability.identitySha256"))
                .toList();
        if (!independentPerformanceComparable(configurations, comparability)) {
            findings.add("performance run material configuration is not independently comparable");
        }
        final boolean declaredConfiguration = Boolean.parseBoolean(
                campaign.getOrDefault("campaign.configurationIdentityEqual", "false"));
        if (!declaredConfiguration) {
            findings.add("campaign configuration comparability declaration is not PASS");
        }
    }

    private static void verifyManagementDirectory(
            final Path root,
            final Map<String, String> campaign,
            final Map<String, String> campaignEvidence,
            final List<String> findings) {
        final Path summary = root.resolve("management-summary-v2.txt");
        final Path inventory = root.resolve("SHA256SUMS");
        try {
            if (!Files.isRegularFile(summary) || !Files.isRegularFile(inventory)) {
                findings.add("PASS campaign is missing management summary/inventory");
                return;
            }
            verifyDirectoryInventory(inventory, findings);
            final Map<String, String> inventoryEntries = readCanonicalInventory(inventory);
            final Map<String, String> fields = readKeyValue(summary);
            if (!"ga-g4-management-summary-v2".equals(fields.get("schema"))
                    || !"true".equals(fields.get("formal"))
                    || !"FORMAL_G4".equals(fields.get("lane"))
                    || !Objects.equals(fields.get("campaign.id"), campaign.get("campaign.id"))
                    || !"G4".equals(fields.get("gate.id"))
                    || !GaFormalPerformanceContract.CAMPAIGN.equals(fields.get("gate.version"))
                    || !GaPerformanceMatrix.APPROVED_VERSION.equals(fields.get("matrix.version"))
                    || !"4".equals(fields.get("trial.count"))
                    || !"true".equals(fields.get("pairA.passed"))
                    || !"true".equals(fields.get("pairB.passed"))
                    || !"NONE".equals(fields.get("blocker"))) {
                findings.add("PASS campaign management summary is incomplete");
            }
            verifyChildIdentity(fields, campaignEvidence, "management summary", findings);
            final Set<String> physicalExecutions = new HashSet<>();
            final Set<String> trialIds = new HashSet<>();
            final List<ManagementEvidenceRow> trials = new ArrayList<>();
            String configurationIdentity = null;
            final boolean[] expectedStatusModes = {false, true, true, false};
            for (int index = 1; index <= 4; index++) {
                final String prefix = "trial." + index + ".";
                final String relative = fields.get(prefix + "rawPath");
                if (relative == null) {
                    findings.add("management trial raw path is missing: " + index);
                    continue;
                }
                final Path raw = resolvePayload(root, relative);
                final String declared = fields.get(prefix + "rawSha256");
                if (!Files.isRegularFile(raw) || declared == null
                        || !declared.equals(QualificationArtifactHasher.sha256(raw))) {
                    findings.add("management raw evidence binding mismatch: trial " + index);
                    continue;
                }
                verifySidecar(raw.resolveSibling(raw.getFileName() + ".sha256"), raw, findings);
                final Map<String, String> trial = readKeyValue(raw);
                verifyChildIdentity(trial, campaignEvidence, "management trial " + index, findings);
                final String expectedTrialId = fields.get(prefix + "id");
                final String expectedPairId = fields.get(prefix + "pairId");
                final String expectedPhysicalId = fields.get(prefix + "physicalExecutionId");
                final String trialPhysicalId = trial.get("physicalExecutionId");
                if (!managementIdentityComplete(fields, trial, campaignEvidence, index)
                        || !"ga-g4-management-trial-v2".equals(trial.get("schema"))
                        || !"true".equals(trial.get("formal"))
                        || !"FORMAL_G4".equals(trial.get("lane"))
                        || !Objects.equals(trial.get("campaign.id"), campaign.get("campaign.id"))
                        || !"G4".equals(trial.get("gate.id"))
                        || !GaFormalPerformanceContract.CAMPAIGN.equals(trial.get("gate.version"))
                        || !physicalExecutionIdValid(expectedPhysicalId, physicalExecutions)
                        || !Objects.equals(expectedPhysicalId, trialPhysicalId)
                        || !trialIds.add(expectedTrialId)) {
                    findings.add("management trial canonical ownership mismatch: " + index);
                }
                verifyMandatoryRuntimeEvidence(raw.getParent(), trial, findings,
                        rebaseInventory(inventory.getParent(), raw.getParent(), inventoryEntries));
                if (!managementBindingComplete(trial)) {
                    findings.add("management evidence canonical binding is incomplete: trial "
                            + index);
                }
                final String expectedPathPart = index == 1 || index == 4
                        ? "pair-" + (index == 1 ? "a" : "b") + "-idle"
                        : "pair-" + (index == 2 ? "a" : "b") + "-status";
                if (!relative.contains(expectedPathPart)) {
                    findings.add("management trial mode/order mismatch: " + index);
                }
                final String statusValue = trial.get("pollStatus");
                final boolean status = "true".equals(statusValue);
                if (!("true".equals(statusValue) || "false".equals(statusValue))
                        || status != expectedStatusModes[index - 1]) {
                    findings.add("management trial status mode mismatch: " + index);
                }
                final int polls = integerValue(trial.get("status.pollCount"), -1);
                final long offered = longValueOrDefault(trial.get("measurement.offeredCommands"), -1L);
                final long accepted = longValueOrDefault(trial.get("measurement.acceptedCommands"), -1L);
                final long completed = longValueOrDefault(trial.get("measurement.completedCommands"), -1L);
                final long post = longValueOrDefault(
                        trial.get("measurement.postMeasurementDrainCommands"), -1L);
                final long cross = longValueOrDefault(trial.get("measurement.crossBoundaryCommands"), -1L);
                final long unfinished = longValueOrDefault(trial.get("measurement.unfinishedCommands"), -1L);
                final long duration = longValueOrDefault(trial.get("measurementDurationNanos"), -1L);
                final String expectedMode = expectedStatusModes[index - 1] ? "STATUS" : "IDLE";
                if (!"PASS".equals(trial.get("outcome")) || !"NONE".equals(trial.get("blocker"))
                        || !statusHealthPass(trial) || polls != (status ? 300 : 0)
                        || !expectedMode.equals(trial.get("trial.mode"))
                        || offered <= 0L || accepted != offered
                        || completed + post + cross + unfinished != accepted
                        || unfinished != 0L || duration != 300_000_000_000L
                        || longValueOrDefault(trial.get("warmupDurationNanos"), -1L)
                        != GaFormalPerformanceContract.MANAGEMENT_WARMUP.toNanos()
                        || !"0".equals(trial.get("shutdown.exitCode"))
                        || !"true".equals(trial.get("shutdown.completed"))) {
                    findings.add("management trial is not a trustworthy PASS: " + index);
                }
                final long[] requestLatencies = readRequestLatencies(trial);
                if (!accountingMatchesRaw(trial)) {
                    findings.add("management accounting is not backed by request-level raw evidence: "
                            + index);
                }
                final QualificationPercentiles.Summary requestSummary =
                        QualificationPercentiles.summarize(requestLatencies);
                final long p99 = requestSummary.count() == 0L
                        ? -1L : requestSummary.p99Nanos();
                final long declaredRawP99 = longValueOrDefault(trial.get("latency.p99Nanos"), -1L);
                final long sampleCount = longValueOrDefault(trial.get("latency.count"), -1L);
                final long[] statusLatencies = readStatusLatencies(trial, status, polls);
                final double throughput = accepted * 1_000_000_000.0 / Math.max(1L, duration);
                final double declaredThroughput = doubleValue(fields.get(prefix + "throughput"));
                final long declaredP99 = longValueOrDefault(
                        fields.get(prefix + "responseP99Nanos"), -1L);
                if (p99 < 0L || sampleCount != completed || declaredRawP99 != p99
                        || status && statusLatencies.length != polls
                        || Double.compare(throughput, declaredThroughput) != 0) {
                    findings.add("management trial metrics are not recomputable: " + index);
                }
                final String trialConfiguration = trial.get("configuration.identitySha256");
                if (configurationIdentity == null) {
                    configurationIdentity = trialConfiguration;
                } else if (!Objects.equals(configurationIdentity, trialConfiguration)) {
                    findings.add("management configuration identity is not stable");
                }
                trials.add(new ManagementEvidenceRow(status, declaredThroughput, p99));
            }
            if (trials.size() == 4) {
                verifyManagementPair(trials.get(0), trials.get(1), "A", findings);
                verifyManagementPair(trials.get(3), trials.get(2), "B", findings);
            }
        } catch (IOException | RuntimeException failure) {
            findings.add("management evidence reconstruction failed: " + failure.getMessage());
        }
    }

    static boolean managementBindingComplete(final Map<String, String> trial) {
        return "true".equals(trial.get("configuration.bound"))
                && "true".equals(trial.get("environment.bound"))
                && "true".equals(trial.get("candidate.bound"))
                && "true".equals(trial.get("controller.bound"))
                && "true".equals(trial.get("evidence.mandatoryComplete"));
    }

    /** Returns whether the canonical campaign/trial identity tuple is complete and owned. */
    static boolean managementIdentityComplete(
            final Map<String, String> summary,
            final Map<String, String> trial,
            final Map<String, String> campaignEvidence,
            final int ordinal) {
        final String prefix = "trial." + ordinal + ".";
        return nonBlankEqual(campaignEvidence, trial, "candidate.tag")
                && nonBlankEqual(campaignEvidence, trial, "candidate.tagObjectSha")
                && nonBlankEqual(campaignEvidence, trial, "candidate.productionSha")
                && nonBlankEqual(campaignEvidence, trial, "candidate.applicationJarSha256")
                && nonBlankEqual(campaignEvidence, trial, "qualification.jarSha256")
                && nonBlankEqual(campaignEvidence, trial, "controller.gitSha")
                && Objects.equals(summary.get("campaign.id"), trial.get("campaign.id"))
                && Objects.equals(summary.get("gate.id"), trial.get("gate.id"))
                && Objects.equals(summary.get("gate.version"), trial.get("gate.version"))
                && Objects.equals(summary.get(prefix + "id"), trial.get("trial.id"))
                && Objects.equals(summary.get(prefix + "ordinal"), trial.get("trial.ordinal"))
                && Objects.equals(summary.get(prefix + "pairId"), trial.get("pair.id"))
                && Objects.equals(summary.get(prefix + "physicalExecutionId"),
                        trial.get("physicalExecutionId"))
                && Objects.equals(summary.get(prefix + "configuration.identitySha256"),
                        trial.get("configuration.identitySha256"))
                && Objects.equals(summary.get(prefix + "environment.identitySha256"),
                        trial.get("environment.identitySha256"))
                && nonBlank(summary.get("campaign.id"))
                && nonBlank(summary.get(prefix + "id"))
                && nonBlank(summary.get(prefix + "pairId"))
                && physicalExecutionIdWellFormed(summary.get(prefix + "physicalExecutionId"));
    }

    /** Returns whether a formal run carries every frozen semantic identity. */
    static boolean formalRunSemanticsComplete(
            final Map<String, String> raw,
            final Map<String, String> manifest) {
        final List<String> findings = new ArrayList<>();
        verifyFormalRunSemantics(raw, manifest, findings);
        return findings.isEmpty();
    }

    /** Returns whether STATUS samples satisfy the persisted absolute-clock contract. */
    static boolean statusEvidenceComplete(final Map<String, String> trial) {
        try {
            final boolean status = "true".equals(trial.get("pollStatus"));
            readStatusLatencies(trial, status, integerValue(trial.get("status.pollCount"), -1));
            return true;
        } catch (IOException | RuntimeException failure) {
            return false;
        }
    }

    /** Returns whether all mandatory ownership identity fields are present and usable. */
    static boolean mandatoryIdentityFieldsComplete(final Map<String, String> fields) {
        return physicalExecutionIdWellFormed(fields.get("physicalExecutionId"))
                && digest(fields.get("candidate.tagObjectSha"), 40)
                && digest(fields.get("controller.gitSha"), 40)
                && digest(fields.get("configuration.identitySha256"), 64)
                && digest(fields.get("environment.identitySha256"), 64);
    }

    private static boolean nonBlank(final String value) {
        return value != null && !value.isBlank();
    }

    private static boolean nonBlankEqual(
            final Map<String, String> first,
            final Map<String, String> second,
            final String key) {
        return nonBlank(first.get(key)) && Objects.equals(first.get(key), second.get(key));
    }

    private static boolean physicalExecutionIdWellFormed(final String value) {
        try {
            return value != null && UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException failure) {
            return false;
        }
    }

    private static boolean digest(final String value, final int length) {
        return value != null && value.matches("[0-9a-f]{" + length + "}");
    }

    private static boolean statusHealthPass(final Map<String, String> trial) {
        return "true".equals(trial.get("candidate.ready"))
                && "true".equals(trial.get("status.healthy"))
                && "true".equals(trial.get("metrics.complete"))
                && "NONE".equals(trial.get("candidate.failureCode"))
                && "0".equals(trial.get("candidate.terminalFailures"))
                && "true".equals(trial.get("candidate.healthEvidenceComplete"))
                && "true".equals(trial.get("shutdown.completed"))
                && "true".equals(trial.get("status.boundaryComplete"))
                && managementBindingComplete(trial);
    }

    private static long[] readRequestLatencies(final Map<String, String> trial)
            throws IOException {
        final Map<Long, Long> values = new TreeMap<>();
        for (Map.Entry<String, String> entry : trial.entrySet()) {
            final String key = entry.getKey();
            if (!key.startsWith("request.") || !key.endsWith(".latencyNanos")) {
                continue;
            }
            final String ordinal = key.substring("request.".length(),
                    key.length() - ".latencyNanos".length());
            try {
                final long requestId = Long.parseLong(ordinal);
                final long latency = Long.parseLong(entry.getValue());
                if (requestId <= 0L || latency < 1L || values.put(requestId, latency) != null) {
                    throw new IOException("invalid management request latency identity");
                }
            } catch (NumberFormatException failure) {
                throw new IOException("invalid management request latency", failure);
            }
        }
        final long declared = longValueOrDefault(trial.get("latency.count"), -1L);
        if (declared < 0L || declared != values.size()) {
            throw new IOException("management request latency count is not recomputable");
        }
        final long[] result = new long[values.size()];
        int index = 0;
        for (long value : values.values()) {
            result[index++] = value;
        }
        return result;
    }

    private static long[] readStatusLatencies(
            final Map<String, String> trial, final boolean status, final int polls)
            throws IOException {
        final String operation = trial.get("status.operation");
        if (!Objects.equals(operation, status ? "STATUS" : "NONE")
                || !"METRICS".equals(trial.get("metrics.operation"))) {
            throw new IOException("management operation ownership is invalid");
        }
        final int declared = integerValue(trial.get("status.sampleCount"), -1);
        if (declared < 0 || declared != polls || declared != (status
                ? GaFormalPerformanceContract.MANAGEMENT_STATUS_REQUESTS : 0)) {
            throw new IOException("STATUS evidence count is not exact");
        }
        final long[] result = new long[declared];
        final long interval = GaFormalPerformanceContract.MANAGEMENT_INTERVAL.toNanos();
        final long measurementStart = longValue(trial, "measurementStartNanos");
        final long measurementEnd = longValue(trial, "measurementEndNanos");
        long previousCompleted = Long.MIN_VALUE;
        for (int index = 1; index <= declared; index++) {
            final String prefix = "status.sample." + index + ".";
            if (!"STATUS".equals(trial.get(prefix + "operation"))
                    || integerValue(trial.get(prefix + "ordinal"), -1) != index) {
                throw new IOException("STATUS operation or ordinal is not persisted exactly");
            }
            final long deadline = longValue(trial, prefix + "deadlineNanos");
            final long started = longValue(trial, prefix + "startedNanos");
            final long completed = longValue(trial, prefix + "completedNanos");
            final long latency = longValue(trial, prefix + "latencyNanos");
            final long expectedDeadline;
            try {
                expectedDeadline = Math.addExact(measurementStart,
                        Math.multiplyExact((long) (index - 1), interval));
            } catch (ArithmeticException failure) {
                throw new IOException("STATUS deadline arithmetic overflow", failure);
            }
            final long nextDeadline = index == declared ? Long.MAX_VALUE
                    : Math.addExact(expectedDeadline, interval);
            if (deadline != expectedDeadline || started < deadline || completed < started
                    || deadline < measurementStart || deadline >= measurementEnd
                    || index > 1 && started < previousCompleted
                    || completed >= nextDeadline
                    || !GaFormalPerformanceRunner.statusSampleWithinMeasurement(
                            measurementStart, measurementEnd, started, completed)
                    || latency != completed - started || latency < 1L) {
                throw new IOException("STATUS timing evidence is not absolute or ordered");
            }
            previousCompleted = completed;
            result[index - 1] = latency;
        }
        return result;
    }

    private static void verifyManagementPair(
            final ManagementEvidenceRow idle,
            final ManagementEvidenceRow status,
            final String pair,
            final List<String> findings) {
        if (status.throughput() < idle.throughput() * 0.90d
                || status.p99Nanos() > idle.p99Nanos() * 1.10d) {
            findings.add("management pair " + pair + " regression predicate failed");
        }
    }

    private static void verifyChildIdentity(
            final Map<String, String> child,
            final Map<String, String> campaign,
            final String description,
            final List<String> findings) {
        final Map<String, String> pairs = new LinkedHashMap<>();
        pairs.put("candidate.tag", campaign.get("candidate.tag"));
        pairs.put("candidate.tagObjectSha", campaign.get("candidate.tagObjectSha"));
        pairs.put("candidate.productionSha", campaign.get("candidate.productionSha"));
        pairs.put("candidate.applicationJarSha256",
                campaign.get("candidate.applicationJarSha256"));
        pairs.put("campaign.id", campaign.get("campaign.id"));
        pairs.put("gate.id", "G4");
        pairs.put("gate.version", GaFormalPerformanceContract.CAMPAIGN);
        pairs.put("lane", "FORMAL_G4");
        pairs.put("qualification.jarSha256", campaign.get("qualification.jarSha256"));
        pairs.put("controller.gitSha", campaign.get("qualification.controllerSha"));
        pairs.put("protocol", "v2");
        pairs.put("window", "8");
        pairs.put("walMode", "SYNC_EACH_APPEND");
        pairs.put("loadModel", GaPerformanceMatrix.APPROVED_LOAD_MODEL);
        pairs.put("matrix.version", GaPerformanceMatrix.APPROVED_VERSION);
        pairs.put("profile", GaPerformanceMatrix.APPROVED_PROFILE);
        pairs.put("seed", Long.toString(GaPerformanceMatrix.APPROVED_SEED));
        for (Map.Entry<String, String> entry : pairs.entrySet()) {
            if (entry.getValue() != null
                    && !Objects.equals(entry.getValue(), child.get(entry.getKey()))) {
                findings.add(description + " identity mismatch: " + entry.getKey());
            }
        }
    }

    private static void verifyStorageOwnership(
            final Path root,
            final List<String> findings) throws IOException {
        final Path storage = root.resolve("storage");
        if (!Files.isDirectory(storage)) {
            findings.add("PASS run is missing storage evidence root");
            return;
        }
        try (var paths = Files.walk(storage)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                final Path relative = storage.relativize(path);
                if (relative.getNameCount() == 0) {
                    findings.add("storage file has no owner: " + relative);
                    return;
                }
                final String owner = relative.getName(0).toString();
                if ("wal".equals(owner)) {
                    return;
                }
                if ("snapshots".equals(owner) && relative.getNameCount() == 2) {
                    final String name = relative.getFileName().toString();
                    if (name.startsWith("snapshot-")
                            && (name.endsWith(".bin") || name.endsWith(".tmp"))) {
                        return;
                    }
                }
                findings.add("unknown candidate-owned storage file: "
                        + relative.toString().replace('\\', '/'));
            });
        }
    }

    private static void compareRawManifest(
            final Map<String, String> raw,
            final Map<String, String> manifest,
            final List<String> findings) {
        final Map<String, String> pairs = Map.ofEntries(
                Map.entry("candidate.tag", "candidate.tag"),
                Map.entry("candidate.tagObjectSha", "candidate.tagObjectSha"),
                Map.entry("candidate.productionSha", "candidate.productionSha"),
                Map.entry("candidate.productionTreeSha256", "candidate.productionTreeSha256"),
                Map.entry("candidate.applicationJarSha256", "candidate.applicationJarSha256"),
                Map.entry("qualification.jarSha256", "qualification.jarSha256"),
                Map.entry("controller.gitSha", "controller.gitSha"),
                Map.entry("configuration.identitySha256", "configuration.identitySha256"),
                Map.entry("environment.identitySha256", "comparability.identitySha256"),
                Map.entry("physicalExecutionId", "physicalExecution.id"),
                Map.entry("offeredCommands", "run.commandCount"),
                Map.entry("profile", "run.profile"),
                Map.entry("seed", "run.seed"),
                Map.entry("window", "run.protocolV2Window"));
        for (Map.Entry<String, String> pair : pairs.entrySet()) {
            if (!Objects.equals(raw.get(pair.getKey()), manifest.get(pair.getValue()))) {
                findings.add("raw/manifest mismatch: " + pair.getKey());
            }
        }
        if (manifest.containsKey("campaign.id")
                && !Objects.equals(raw.get("campaign.id"), manifest.get("campaign.id"))) {
            findings.add("raw/manifest mismatch: campaign.id");
        }
        if ("PASS".equals(manifest.get("evidence.outcome"))) {
            verifyFixedRunIdentity(raw, manifest, findings);
        }
    }

    private static void verifyFixedRunIdentity(
            final Map<String, String> raw,
            final Map<String, String> manifest,
            final List<String> findings) {
        final Map<String, String> expected = Map.ofEntries(
                Map.entry("candidate.tag", "v0.9.0-rc.2"),
                Map.entry("candidate.tagObjectSha",
                        "9e2a67ada0e3b6220b730131d0bae79dc03073ed"),
                Map.entry("candidate.productionSha",
                        "740e8a3dea0a759c707c597778c26c41e9bb3e47"),
                Map.entry("candidate.productionTreeSha256",
                        "ef1d9f4cb64a9d6e331fb326ebe8f3b0abb29a53bf6045a5d4999a53e73b4bbc"),
                Map.entry("candidate.applicationJarSha256",
                        "0b77d37985b9124ac4fd1b90d669db550efd0cf00c23af65fdc29b35071703c4"),
                Map.entry("protocol", "v2"),
                Map.entry("window", "8"),
                Map.entry("walMode", "SYNC_EACH_APPEND"),
                Map.entry("loadModel", "BOUNDED_CLOSED_LOOP_CONTINUOUS_REFILL"),
                Map.entry("profile", "MEMORY_STEADY_STATE_V1"),
                Map.entry("seed", "20260823"));
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            if (!Objects.equals(entry.getValue(), raw.get(entry.getKey()))) {
                findings.add("formal RC2 identity mismatch: " + entry.getKey());
            }
        }
        if (!Objects.equals(raw.get("candidate.tag"), manifest.get("candidate.tag"))
                || !Objects.equals(raw.get("candidate.tagObjectSha"),
                        manifest.get("candidate.tagObjectSha"))
                || !Objects.equals(raw.get("candidate.productionSha"),
                        manifest.get("candidate.productionSha"))) {
            findings.add("formal RC2 candidate identity is not manifest-bound");
        }
        if (!Objects.equals(raw.get("window"), manifest.get("run.protocolV2Window"))) {
            findings.add("formal RC2 protocol identity is not manifest-bound");
        }
    }

    private static void verifyAccounting(
            final Map<String, String> raw,
            final Map<String, String> manifest,
            final List<LatencyRow> latency,
            final List<String> findings) {
        try {
            final long offered = longValue(raw, "measurement.offeredCommands");
            final long accepted = longValue(raw, "measurement.acceptedCommands");
            final long completed = longValue(raw, "measurement.completedCommands");
            final long post = longValue(raw, "measurement.postMeasurementDrainCommands");
            final long cross = longValue(raw, "measurement.crossBoundaryCommands");
            final long unfinished = longValue(raw, "measurement.unfinishedCommands");
            if (offered < 0L || accepted < 0L || completed < 0L || post < 0L || cross < 0L
                    || unfinished < 0L || accepted > offered
                    || completed + post + cross + unfinished != accepted
                    || latency.size() != completed) {
                findings.add("measurement accounting is not partitioned by raw evidence");
            }
            final long start = longValue(raw, "measurementStartNanos");
            final long end = longValue(raw, "measurementEndNanos");
            final long duration = longValue(raw, "measurementDurationNanos");
            if (start < 0L || end < start || duration != end - start
                    || (manifest.containsKey("evidence.measurementDurationNanos")
                    && duration != longValue(manifest, "evidence.measurementDurationNanos"))) {
                findings.add("measurement boundary arithmetic is invalid");
            }
            if (!accountingMatchesRaw(raw)) {
                findings.add("measurement accounting is not recomputable from request-level raw evidence");
            }
        } catch (IOException | ArithmeticException failure) {
            findings.add("measurement accounting field is invalid: " + failure.getMessage());
        }
    }

    /** Recomputes bounded request accounting without trusting summary counters. */
    static boolean accountingMatchesRaw(final Map<String, String> raw) {
        Objects.requireNonNull(raw, "raw");
        try {
            final long start = longValue(raw, "measurementStartNanos");
            final long end = longValue(raw, "measurementEndNanos");
            if (start < 0L || end <= start) {
                return false;
            }
            final RequestAccounting accounting = recomputeRequestAccounting(raw, start, end);
            return accounting.summaryMatches(raw);
        } catch (IOException | RuntimeException failure) {
            return false;
        }
    }

    private static RequestAccounting recomputeRequestAccounting(
            final Map<String, String> raw,
            final long measurementStart,
            final long measurementEnd) throws IOException {
        final Map<Long, RequestEvidence> requests = readRequestLedger(raw);
        final Set<Long> sequences = new HashSet<>();
        long offered = 0L;
        long accepted = 0L;
        long completed = 0L;
        long post = 0L;
        long cross = 0L;
        long unfinished = 0L;
        for (RequestEvidence request : requests.values()) {
            if (request.commandSequence == null || request.offeredNanos == null
                    || request.inMeasurement == null || request.completedNanos == null
                    || request.capacityReleaseNanos == null || request.outcomeCode == null) {
                throw new IOException("request-level accounting row is incomplete");
            }
            if (request.commandSequence <= 0L || !sequences.add(request.commandSequence)
                    || request.offeredNanos < 0L
                    || request.completedNanos < 0L || request.capacityReleaseNanos < 0L) {
                throw new IOException("request-level accounting chronology is invalid");
            }
            if (request.outcomeCode < -1L || request.outcomeCode == 0L
                    || request.outcomeCode > 3L) {
                throw new IOException("request-level accounting outcome code is unknown");
            }
            if (request.completedNanos == 0L
                    && (request.outcomeCode != -1L || request.capacityReleaseNanos != 0L)) {
                throw new IOException("unfinished request has a terminal outcome or release");
            }
            if (request.completedNanos > 0L
                    && (request.completedNanos < request.offeredNanos
                    || request.capacityReleaseNanos <= request.completedNanos
                    || request.outcomeCode < 1L || request.outcomeCode > 3L)) {
                throw new IOException("request-level accounting completion is invalid");
            }
            if (request.latencyNanos != null
                    && request.completedNanos > 0L
                    && request.latencyNanos != Math.max(1L,
                            request.completedNanos - request.offeredNanos)) {
                throw new IOException("request-level derived latency is not authoritative");
            }
            if (!request.inMeasurement) {
                continue;
            }
            if (request.offeredNanos < measurementStart
                    || request.offeredNanos >= measurementEnd) {
                throw new IOException("measurement request offer is outside its interval");
            }
            offered++;
            if (request.completedNanos == 0L) {
                unfinished++;
            } else {
                accepted++;
                if (request.completedNanos < measurementStart) {
                    cross++;
                } else if (request.completedNanos < measurementEnd) {
                    completed++;
                } else {
                    post++;
                }
            }
        }
        return new RequestAccounting(offered, accepted, completed, post, cross, unfinished);
    }

    private static Map<Long, RequestEvidence> readRequestLedger(
            final Map<String, String> raw) throws IOException {
        final Map<Long, RequestEvidence> requests = new TreeMap<>();
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            final String key = entry.getKey();
            if (!key.startsWith("request.")) {
                continue;
            }
            final String remainder = key.substring("request.".length());
            final int separator = remainder.indexOf('.');
            if (separator <= 0 || separator == remainder.length() - 1) {
                throw new IOException("malformed request-level accounting key");
            }
            final long requestId;
            try {
                requestId = Long.parseLong(remainder.substring(0, separator));
            } catch (NumberFormatException failure) {
                throw new IOException("request-level accounting id is invalid", failure);
            }
            if (requestId <= 0L) {
                throw new IOException("request-level accounting id is outside bounds");
            }
            final RequestEvidence request = requests.computeIfAbsent(
                    requestId, ignored -> new RequestEvidence());
            final String field = remainder.substring(separator + 1);
            switch (field) {
                case "commandSequence" -> request.commandSequence = parseLong(entry.getValue(), field);
                case "offeredNanos" -> request.offeredNanos = parseLong(entry.getValue(), field);
                case "inMeasurement" -> request.inMeasurement = parseBoolean(entry.getValue(), field);
                case "completedNanos" -> request.completedNanos = parseLong(entry.getValue(), field);
                case "capacityReleaseNanos" -> request.capacityReleaseNanos = parseLong(
                        entry.getValue(), field);
                case "outcomeCode" -> request.outcomeCode = parseLong(entry.getValue(), field);
                case "latencyNanos" -> request.latencyNanos = parseLong(entry.getValue(), field);
                default -> throw new IOException("unknown request-level accounting field: " + field);
            }
        }
        if (requests.isEmpty()) {
            throw new IOException("request-level accounting evidence is missing");
        }
        return requests;
    }

    private static long parseLong(final String value, final String field) throws IOException {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException failure) {
            throw new IOException("request-level accounting field is not a long: " + field,
                    failure);
        }
    }

    private static boolean parseBoolean(final String value, final String field) throws IOException {
        if (!"true".equals(value) && !"false".equals(value)) {
            throw new IOException("request-level accounting field is not boolean: " + field);
        }
        return Boolean.parseBoolean(value);
    }

    private static void verifyLatency(
            final Map<String, String> raw,
            final Map<String, String> manifest,
            final List<LatencyRow> rows,
            final List<String> findings) {
        final long[] values = rows.stream().mapToLong(LatencyRow::latencyNanos).toArray();
        final QualificationPercentiles.Summary summary =
                QualificationPercentiles.summarize(values);
        try {
            if (summary.count() != longValue(raw, "latency.count")
                    || summary.p50Nanos() != longValue(raw, "latency.p50Nanos")
                    || summary.p99Nanos() != longValue(raw, "latency.p99Nanos")
                    || summary.p999Nanos() != longValue(raw, "latency.p999Nanos")) {
                findings.add("latency percentiles are not recomputable from raw samples");
            }
        } catch (IOException | ArithmeticException failure) {
            findings.add("latency summary field is invalid: " + failure.getMessage());
        }
    }

    private static void verifyCapacity(
            final Map<String, String> raw,
            final Map<String, String> manifest,
            final List<LatencyRow> latency,
            final Path capacityPath,
            final List<String> findings) throws IOException {
        final Map<String, String> capacity = readKeyValue(capacityPath);
        final long declaredReleaseSamples;
        try {
            declaredReleaseSamples = longValue(capacity, "releaseSampleCount");
        } catch (IOException failure) {
            throw new IOException("capacity release sample count is missing", failure);
        }
        final boolean detailedRows = declaredReleaseSamples > 0L
                && capacity.containsKey("releaseSample.000001.requestId");
        final List<CapacityRow> rows = detailedRows ? readCapacityRows(capacity) : List.of();
        try {
            final long releaseCount = longValue(capacity, "capacityReleaseCount");
            final long releaseDelayCount = longValue(capacity, "releaseDelayCount");
            if (releaseCount != releaseDelayCount || releaseCount != declaredReleaseSamples
                    || releaseCount != longValue(manifest, "evidence.capacity.releaseCount")
                    || rows.size() != releaseCount) {
                if (!detailedRows && !"PASS".equals(manifest.get("evidence.outcome"))
                        && releaseCount == 0L && declaredReleaseSamples == 0L) {
                    // Older failed fixtures may legitimately have no response rows.  Keep the
                    // compatibility path for those artifacts while requiring detailed rows for
                    // every formal PASS.
                } else {
                    findings.add("capacity release count is not backed by raw samples");
                }
            }
            if (detailedRows) {
                final long[] delays = rows.stream().mapToLong(CapacityRow::releaseDelayNanos)
                        .toArray();
                final QualificationPercentiles.Summary summary =
                        QualificationPercentiles.summarize(delays);
                if (summary.p50Nanos() != longValue(manifest,
                        "evidence.capacity.releaseDelayP50Nanos")
                        || percentile(delays, 0.90d) != longValue(manifest,
                                "evidence.capacity.releaseDelayP90Nanos")
                        || summary.p99Nanos() != longValue(manifest,
                                "evidence.capacity.releaseDelayP99Nanos")
                        || summary.maxNanos() != longValue(manifest,
                                "evidence.capacity.releaseDelayMaxNanos")) {
                    findings.add("capacity release-delay percentiles are not recomputable");
                }
            } else if ("PASS".equals(manifest.get("evidence.outcome"))) {
                findings.add("PASS run has no detailed capacity release rows");
            }
            verifyRequestCorrelations(raw, latency, rows, findings);
            verifyCapacityBounds(capacity, manifest, rows, findings);
            verifyLatencyCapacityCrossCheck(latency, rows, findings);
            if ("PASS".equals(manifest.get("evidence.outcome"))
                    && longValue(raw, "measurement.acceptedCommands") > 0L && rows.isEmpty()) {
                findings.add("accepted requests have no capacity-release samples");
            }
        } catch (IOException | ArithmeticException failure) {
            findings.add("capacity evidence field is invalid: " + failure.getMessage());
        }
    }

    private static void verifyCapacityBounds(
            final Map<String, String> capacity,
            final Map<String, String> manifest,
            final List<CapacityRow> rows,
            final List<String> findings) throws IOException {
        final long maxInFlight = longValue(capacity, "maxInFlight");
        final long maxPending = longValue(capacity, "maxPendingWire");
        final long maxCompleted = longValue(capacity, "maxCompletedUndrained");
        if (maxInFlight != longValue(manifest, "evidence.capacity.maxInFlight")
                || maxPending != longValue(manifest, "evidence.capacity.maxPendingWire")
                || maxCompleted != longValue(manifest,
                        "evidence.capacity.maxCompletedUndrained")) {
            findings.add("capacity maxima do not match manifest");
        }
        final int window = Math.toIntExact(longValue(manifest, "run.protocolV2Window"));
        if (maxInFlight > window || maxPending > window || maxCompleted > window) {
            findings.add("capacity maxima exceed the configured protocol window");
        }
        final Set<Long> requests = new HashSet<>();
        final Set<Long> sequences = new HashSet<>();
        for (CapacityRow row : rows) {
            if (!requests.add(row.requestId()) || !sequences.add(row.commandSequence())) {
                findings.add("duplicate capacity release identity");
            }
        }
    }

    private static void verifyLatencyCapacityCrossCheck(
            final List<LatencyRow> latency,
            final List<CapacityRow> capacity,
            final List<String> findings) {
        final Map<Long, CapacityRow> byRequest = new TreeMap<>();
        for (CapacityRow row : capacity) {
            byRequest.put(row.requestId(), row);
        }
        for (LatencyRow row : latency) {
            final CapacityRow release = byRequest.get(row.requestId());
            if (release == null || release.responseCompleteNanos() != row.completedNanos()
                    || release.capacityReleaseNanos() != row.capacityReleaseNanos()
                    || release.offeredNanos() != row.offeredNanos()) {
                findings.add("latency/capacity request timeline mismatch: " + row.requestId());
            }
        }
    }

    private static void verifyRequestCorrelations(
            final Map<String, String> raw,
            final List<LatencyRow> latency,
            final List<CapacityRow> capacity,
            final List<String> findings) {
        try {
            final Map<Long, RequestEvidence> ledger = readRequestLedger(raw);
            final long start = longValue(raw, "measurementStartNanos");
            final long end = longValue(raw, "measurementEndNanos");
            final Set<Long> latencyRequests = new HashSet<>();
            for (LatencyRow row : latency) {
                final RequestEvidence request = ledger.get(row.requestId());
                if (request == null || !latencyRequests.add(row.requestId())
                        || !Objects.equals(request.commandSequence, row.commandSequence())
                        || !Objects.equals(request.offeredNanos, row.offeredNanos())
                        || !Objects.equals(request.completedNanos, row.completedNanos())
                        || !Objects.equals(request.capacityReleaseNanos,
                                row.capacityReleaseNanos())
                        || !Boolean.TRUE.equals(request.inMeasurement)
                        || request.completedNanos < start || request.completedNanos >= end
                        || request.latencyNanos == null
                        || request.latencyNanos != row.latencyNanos()) {
                    findings.add("latency sample is orphaned or mismatched with raw request: "
                            + row.requestId());
                }
            }
            for (Map.Entry<Long, RequestEvidence> entry : ledger.entrySet()) {
                final RequestEvidence request = entry.getValue();
                if (Boolean.TRUE.equals(request.inMeasurement)
                        && request.completedNanos != null && request.completedNanos > 0L
                        && request.completedNanos >= start && request.completedNanos < end
                        && !latencyRequests.contains(entry.getKey())) {
                    findings.add("measured completed request has no latency sample: "
                            + entry.getKey());
                }
            }
            final Set<Long> capacityRequests = new HashSet<>();
            for (CapacityRow row : capacity) {
                final RequestEvidence request = ledger.get(row.requestId());
                if (request == null || !capacityRequests.add(row.requestId())
                        || !Objects.equals(request.commandSequence, row.commandSequence())
                        || !Objects.equals(request.offeredNanos, row.offeredNanos())
                        || !Objects.equals(request.completedNanos, row.responseCompleteNanos())
                        || !Objects.equals(request.capacityReleaseNanos,
                                row.capacityReleaseNanos())) {
                    findings.add("capacity sample is orphaned or mismatched with raw request: "
                            + row.requestId());
                }
            }
            if (!capacity.isEmpty()) {
                for (Map.Entry<Long, RequestEvidence> entry : ledger.entrySet()) {
                    final RequestEvidence request = entry.getValue();
                    if (request.completedNanos != null && request.completedNanos > 0L
                            && !capacityRequests.contains(entry.getKey())) {
                        findings.add("completed request has no capacity sample: " + entry.getKey());
                    }
                }
            }
        } catch (IOException | RuntimeException failure) {
            findings.add("request correlation reconstruction failed: " + failure.getMessage());
        }
    }

    private static List<LatencyRow> readLatencySamples(final Path path) throws IOException {
        final List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !LATENCY_HEADER.equals(lines.get(0))) {
            throw new IOException("latency evidence header is invalid");
        }
        final List<LatencyRow> rows = new ArrayList<>();
        final Set<Long> requests = new HashSet<>();
        final Set<Long> sequences = new HashSet<>();
        for (int index = 1; index < lines.size(); index++) {
            final String[] fields = lines.get(index).split(",", -1);
            if (fields.length != 6) {
                throw new IOException("latency evidence row is malformed");
            }
            final LatencyRow row;
            try {
                row = new LatencyRow(Long.parseLong(fields[0]), Long.parseLong(fields[1]),
                        Long.parseLong(fields[2]), Long.parseLong(fields[3]),
                        Long.parseLong(fields[4]), Long.parseLong(fields[5]));
            } catch (NumberFormatException failure) {
                throw new IOException("latency evidence row contains a non-integer", failure);
            }
            if (!requests.add(row.requestId()) || !sequences.add(row.commandSequence())
                    || row.requestId() <= 0L || row.commandSequence() <= 0L
                    || row.offeredNanos() < 0L || row.completedNanos() < row.offeredNanos()
                    || row.capacityReleaseNanos() < row.completedNanos()
                    || row.latencyNanos() != Math.max(1L,
                            row.completedNanos() - row.offeredNanos())) {
                throw new IOException("latency evidence chronology or identity is invalid");
            }
            rows.add(row);
        }
        return List.copyOf(rows);
    }

    private static List<CapacityRow> readCapacityRows(final Map<String, String> fields)
            throws IOException {
        final int count = Math.toIntExact(longValue(fields, "releaseSampleCount"));
        if (count < 0 || count > 10_000_000) {
            throw new IOException("capacity release sample count is outside bounds");
        }
        final List<CapacityRow> rows = new ArrayList<>();
        final Set<Long> requests = new HashSet<>();
        final Set<Long> sequences = new HashSet<>();
        for (int index = 1; index <= count; index++) {
            final String prefix = String.format("releaseSample.%06d.", index);
            final CapacityRow row = new CapacityRow(
                    longValue(fields, prefix + "requestId"),
                    longValue(fields, prefix + "commandSequence"),
                    longValue(fields, prefix + "offeredNanos"),
                    longValue(fields, prefix + "responseCompleteNanos"),
                    longValue(fields, prefix + "capacityReleaseNanos"),
                    longValue(fields, prefix + "schedulerConsumedNanos"),
                    longValue(fields, prefix + "releaseDelayNanos"));
            if (!requests.add(row.requestId()) || !sequences.add(row.commandSequence())
                    || row.requestId() <= 0L || row.commandSequence() <= 0L
                    || row.offeredNanos() < 0L || row.responseCompleteNanos() < row.offeredNanos()
                    || row.capacityReleaseNanos() < row.responseCompleteNanos()
                    || row.schedulerConsumedNanos() < row.capacityReleaseNanos()
                    || row.releaseDelayNanos() != row.capacityReleaseNanos()
                            - row.responseCompleteNanos()) {
                throw new IOException("capacity release row chronology or identity is invalid");
            }
            rows.add(row);
        }
        return List.copyOf(rows);
    }

    private static Map<String, String> readKeyValue(final Path path) throws IOException {
        final Map<String, String> fields = new TreeMap<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            final int separator = line.indexOf('=');
            if (separator <= 0 || fields.put(line.substring(0, separator),
                    line.substring(separator + 1)) != null) {
                throw new IOException("duplicate or malformed key/value evidence: " + path);
            }
        }
        return Map.copyOf(fields);
    }

    private static long longValue(final Map<String, String> fields, final String key)
            throws IOException {
        final String value = fields.get(key);
        if (value == null || value.isBlank()) {
            throw new IOException("missing evidence field: " + key);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException failure) {
            throw new IOException("evidence field is not a long: " + key, failure);
        }
    }

    private static int integer(final Map<String, String> fields, final String key)
            throws IOException {
        final long value = longValue(fields, key);
        try {
            return Math.toIntExact(value);
        } catch (ArithmeticException failure) {
            throw new IOException("evidence field is outside integer range: " + key, failure);
        }
    }

    private static long longValueOrDefault(final String value, final long fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException failure) {
            return fallback;
        }
    }

    private static int integerValue(final String value, final int fallback) {
        final long parsed = longValueOrDefault(value, fallback);
        try {
            return Math.toIntExact(parsed);
        } catch (ArithmeticException failure) {
            return fallback;
        }
    }

    private static double doubleValue(final String value) {
        if (value == null || value.isBlank()) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException failure) {
            return Double.NaN;
        }
    }

    private static long percentile(final long[] values, final double percentile) {
        if (values.length == 0) {
            return 0L;
        }
        final long[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        final int rank = Math.max(1, (int) Math.ceil(percentile * sorted.length));
        return sorted[rank - 1];
    }

    private static List<InventoryEntry> readInventory(
            final Path root, final Path inventory) throws IOException {
        if (!Files.isRegularFile(inventory)) {
            throw new IOException("missing SHA256SUMS");
        }
        final List<InventoryEntry> entries = new ArrayList<>();
        String previous = "";
        for (String line : Files.readAllLines(inventory, StandardCharsets.US_ASCII)) {
            if (line.length() < 68 || line.charAt(64) != ' ' || line.charAt(65) != ' ') {
                throw new IOException("malformed SHA256SUMS line");
            }
            final String digest = line.substring(0, 64);
            final String path = line.substring(66);
            if (!digest.matches("[0-9a-f]{64}") || path.isBlank()
                    || path.compareTo(previous) <= 0) {
                throw new IOException("non-canonical SHA256SUMS entry");
            }
            resolvePayload(root, path);
            entries.add(new InventoryEntry(path, digest));
            previous = path;
        }
        if (entries.isEmpty()) {
            throw new IOException("empty SHA256SUMS");
        }
        return List.copyOf(entries);
    }

    private static Map<String, String> readInventoryMap(final Path root) throws IOException {
        final Map<String, String> result = new TreeMap<>();
        for (InventoryEntry entry : readInventory(root, root.resolve("SHA256SUMS"))) {
            if (result.put(entry.path(), entry.sha256()) != null) {
                throw new IOException("duplicate SHA256SUMS entry");
            }
        }
        return Map.copyOf(result);
    }

    private static Map<String, String> rebaseInventory(
            final Path inventoryRoot,
            final Path payloadRoot,
            final Map<String, String> inventory) throws IOException {
        final Path normalizedInventoryRoot = inventoryRoot.toAbsolutePath().normalize();
        final Path normalizedPayloadRoot = payloadRoot.toAbsolutePath().normalize();
        if (normalizedInventoryRoot.equals(normalizedPayloadRoot)) {
            return inventory;
        }
        final String prefix = normalizedInventoryRoot.relativize(normalizedPayloadRoot)
                .toString().replace('\\', '/') + "/";
        final Map<String, String> rebased = new TreeMap<>();
        for (Map.Entry<String, String> entry : inventory.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                rebased.put(entry.getKey().substring(prefix.length()), entry.getValue());
            }
        }
        return Map.copyOf(rebased);
    }

    private static Path resolvePayload(final Path root, final String relative)
            throws IOException {
        final Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root) || relative.contains("\\") || relative.startsWith("/")) {
            throw new IOException("payload escapes evidence root: " + relative);
        }
        return resolved;
    }

    private static void verifySidecar(
            final Path sidecar, final Path payload, final List<String> findings) {
        try {
            final var values = GaEvidenceStore.readArtifactSidecar(sidecar);
            final String name = payload.getFileName().toString();
            final String expected = QualificationArtifactHasher.sha256(payload);
            if (values.size() != 1 || !expected.equals(values.get(name))) {
                findings.add("sidecar hash mismatch: " + sidecar);
            }
        } catch (final IOException | RuntimeException failure) {
            findings.add("invalid sidecar: " + sidecar);
        }
    }

    private record InventoryEntry(String path, String sha256) {
    }

    private record LatencyRow(
            long requestId,
            long commandSequence,
            long offeredNanos,
            long completedNanos,
            long capacityReleaseNanos,
            long latencyNanos) {
    }

    private record CapacityRow(
            long requestId,
            long commandSequence,
            long offeredNanos,
            long responseCompleteNanos,
            long capacityReleaseNanos,
            long schedulerConsumedNanos,
            long releaseDelayNanos) {
    }

    private record ManagementEvidenceRow(
            boolean status,
            double throughput,
            long p99Nanos) {
    }

    private record RequestAccounting(
            long offered,
            long accepted,
            long completed,
            long postMeasurementDrain,
            long crossBoundary,
            long unfinished) {

        private boolean summaryMatches(final Map<String, String> raw) {
            return longValueOrDefault(raw.get("measurement.offeredCommands"), -1L) == offered
                    && longValueOrDefault(raw.get("measurement.acceptedCommands"), -1L)
                    == accepted
                    && longValueOrDefault(raw.get("measurement.completedCommands"), -1L)
                    == completed
                    && longValueOrDefault(raw.get("measurement.postMeasurementDrainCommands"), -1L)
                    == postMeasurementDrain
                    && longValueOrDefault(raw.get("measurement.crossBoundaryCommands"), -1L)
                    == crossBoundary
                    && longValueOrDefault(raw.get("measurement.unfinishedCommands"), -1L)
                    == unfinished
                    && optionalSummaryMatches(raw, "offeredCommands", offered)
                    && optionalSummaryMatches(raw, "acceptedCommands", accepted)
                    && optionalSummaryMatches(raw, "responseCount", completed);
        }

        private static boolean optionalSummaryMatches(
                final Map<String, String> raw, final String key, final long expected) {
            return !raw.containsKey(key) || longValueOrDefault(raw.get(key), Long.MIN_VALUE) == expected;
        }
    }

    private static final class RequestEvidence {
        private Long commandSequence;
        private Long offeredNanos;
        private Boolean inMeasurement;
        private Long completedNanos;
        private Long capacityReleaseNanos;
        private Long outcomeCode;
        private Long latencyNanos;
    }
}
