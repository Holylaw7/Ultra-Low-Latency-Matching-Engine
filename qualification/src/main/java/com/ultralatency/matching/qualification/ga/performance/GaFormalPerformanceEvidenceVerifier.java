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
            verifyCampaignEvidence(root, fields, gateFields, findings);
            final int runCount = Math.toIntExact(longValue(fields, "run.count"));
            final List<Map<String, String>> runManifests = new ArrayList<>();
            for (int index = 1; index <= runCount; index++) {
                final String prefix = String.format("run.%04d.", index);
                final Path runManifest = resolvePayload(root, fields.get(prefix + "manifestPath"));
                final String declared = fields.get(prefix + "manifestSha256");
                if (!declared.equals(QualificationArtifactHasher.sha256(runManifest))) {
                    findings.add("campaign run manifest hash mismatch: " + runManifest);
                }
                try {
                    runManifests.add(GaEvidenceStore.read(
                            runManifest, GaEvidenceCodec.Schema.RUN));
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
                verifyLifecycleDirectory(root.resolve("lifecycle"), fields, findings);
                verifyManagementDirectory(root.resolve("management"), fields, findings);
            }
        } catch (final IOException | RuntimeException failure) {
            findings.add("campaign reconstruction failed: " + failure.getMessage());
        }
        return new Verification(findings.isEmpty(), findings.isEmpty() ? "NONE"
                : classifyFindings(findings), findings);
    }

    private static void verifyCampaignEvidence(
            final Path root,
            final Map<String, String> fields,
            final Map<String, String> gate,
            final List<String> findings) throws IOException {
        final Path evidence = root.resolve("campaign-evidence-v2.txt");
        if (!Files.isRegularFile(evidence)) {
            findings.add("missing campaign evidence payload");
            return;
        }
        if (!isGatePayloadBound(root, gate, evidence)) {
            findings.add("campaign evidence hash or size mismatch");
        }
        verifySidecar(evidence.resolveSibling(evidence.getFileName() + ".sha256"),
                evidence, findings);
        final Map<String, String> campaignEvidence = readKeyValue(evidence);
        verifyCampaignIdentity(fields, campaignEvidence, findings);
        if (!"PASS".equals(fields.get("campaign.outcome"))) {
            return;
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
        final List<LatencyRow> latency = readLatencySamples(samplesPath);
        verifyAccounting(raw, manifest, latency, findings);
        verifyLatency(raw, manifest, latency, findings);
        verifyCapacity(raw, manifest, latency, capacityPath, findings);
        if ("PASS".equals(manifest.get("evidence.outcome"))) {
            verifyMandatoryRuntimeEvidence(root, findings);
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
        if (!Objects.equals(manifest.get("candidate.tag"), fields.get("candidate.tag"))
                || !Objects.equals(manifest.get("candidate.tagObjectSha"),
                        fields.get("candidate.tagObjectSha"))
                || !Objects.equals(manifest.get("candidate.applicationJarSha256"),
                        fields.get("candidate.applicationJarSha256"))
                || !Objects.equals(manifest.get("controller.gitSha"),
                        fields.get("controller.gitSha"))) {
            findings.add("run gate identity is not bound to the run manifest");
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
            final List<String> findings) {
        final Path evidenceDirectory = root.resolve("process-evidence");
        final Path resource = evidenceDirectory.resolve("resource-evidence.csv");
        final Path jfr = evidenceDirectory.resolve("qualification.jfr");
        try {
            if (!Files.isRegularFile(resource) || Files.isSymbolicLink(resource)
                    || !Files.isReadable(resource) || Files.size(resource) <= 0L) {
                findings.add("PASS run is missing readable resource evidence");
            } else {
                final QualificationResourceEvidence parsed =
                        QualificationResourceEvidenceReader.read(resource, 0);
                if (parsed.samples().isEmpty()) {
                    findings.add("PASS run has no resource evidence samples");
                }
            }
            if (!Files.isRegularFile(jfr) || Files.isSymbolicLink(jfr)
                    || !Files.isReadable(jfr) || Files.size(jfr) <= 0L) {
                findings.add("PASS run is missing readable JFR evidence");
            } else {
                final GaJfrEvidence parsed = GaJfrEvidence.inspect(jfr, true, true, true);
                if (!parsed.complete()) {
                    findings.add("PASS run JFR evidence is not complete for the formal contract");
                }
            }
            verifyStorageOwnership(root, findings);
        } catch (IOException | RuntimeException failure) {
            findings.add("mandatory runtime evidence reconstruction failed: "
                    + failure.getMessage());
        }
    }

    private static void verifyLifecycleDirectory(
            final Path root,
            final Map<String, String> campaign,
            final List<String> findings) {
        final Path summary = root.resolve("lifecycle-summary-v2.txt");
        final Path inventory = root.resolve("SHA256SUMS");
        try {
            if (!Files.isRegularFile(summary) || !Files.isRegularFile(inventory)) {
                findings.add("PASS campaign is missing lifecycle summary/inventory");
                return;
            }
            verifyDirectoryInventory(inventory, findings);
            final Map<String, String> fields = readKeyValue(summary);
            if (!"true".equals(fields.get("formal"))
                    || !"60".equals(fields.get("matrix.cycles"))
                    || !"60".equals(fields.get("sample.count"))
                    || !"60".equals(fields.get("startup.sampleCount"))
                    || !"60".equals(fields.get("shutdown.sampleCount"))
                    || !"true".equals(fields.get("complete"))
                    || !"true".equals(fields.get("passed"))
                    || !"NONE".equals(fields.get("blocker"))) {
                findings.add("PASS campaign lifecycle summary is incomplete");
            }
            verifyChildIdentity(fields, campaign, "lifecycle summary", findings);
            final long[] startup = new long[60];
            final long[] shutdown = new long[60];
            String configurationIdentity = null;
            for (int index = 1; index <= 60; index++) {
                final String prefix = "cycle." + index + ".";
                final String relative = fields.get(prefix + "rawPath");
                final Path raw = relative == null
                        ? root.resolve(String.format("cycle-%02d/lifecycle-raw-evidence-v2.txt",
                                index))
                        : resolvePayload(root, relative);
                final String declared = fields.get(prefix + "rawSha256");
                if (!Files.isRegularFile(raw) || declared == null
                        || !declared.equals(QualificationArtifactHasher.sha256(raw))) {
                    findings.add("lifecycle raw evidence binding mismatch: cycle " + index);
                    continue;
                }
                verifySidecar(raw.resolveSibling(raw.getFileName() + ".sha256"), raw, findings);
                final Map<String, String> cycle = readKeyValue(raw);
                verifyChildIdentity(cycle, campaign, "lifecycle cycle " + index, findings);
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
            final List<String> findings) {
        final Path summary = root.resolve("management-summary-v2.txt");
        final Path inventory = root.resolve("SHA256SUMS");
        try {
            if (!Files.isRegularFile(summary) || !Files.isRegularFile(inventory)) {
                findings.add("PASS campaign is missing management summary/inventory");
                return;
            }
            verifyDirectoryInventory(inventory, findings);
            final Map<String, String> fields = readKeyValue(summary);
            if (!"true".equals(fields.get("formal"))
                    || !"4".equals(fields.get("trial.count"))
                    || !"true".equals(fields.get("pairA.passed"))
                    || !"true".equals(fields.get("pairB.passed"))
                    || !"NONE".equals(fields.get("blocker"))) {
                findings.add("PASS campaign management summary is incomplete");
            }
            verifyChildIdentity(fields, campaign, "management summary", findings);
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
                verifyChildIdentity(trial, campaign, "management trial " + index, findings);
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
                if (!"PASS".equals(trial.get("outcome")) || !"NONE".equals(trial.get("blocker"))
                        || !statusHealthPass(trial) || polls != (status ? 300 : 0)
                        || offered <= 0L || accepted != offered
                        || completed + post + cross + unfinished != accepted
                        || unfinished != 0L || duration != 300_000_000_000L
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
        final int declared = integerValue(trial.get("status.sampleCount"), -1);
        if (declared < 0 || declared != polls || declared != (status
                ? GaFormalPerformanceContract.MANAGEMENT_STATUS_REQUESTS : 0)) {
            throw new IOException("STATUS evidence count is not exact");
        }
        final long[] result = new long[declared];
        final long interval = GaFormalPerformanceContract.MANAGEMENT_INTERVAL.toNanos();
        final long measurementStart = longValue(trial, "measurementStartNanos");
        final long measurementEnd = longValue(trial, "measurementEndNanos");
        for (int index = 1; index <= declared; index++) {
            final String prefix = "status.sample." + index + ".";
            final long deadline = longValue(trial, prefix + "deadlineNanos");
            final long started = longValue(trial, prefix + "startedNanos");
            final long completed = longValue(trial, prefix + "completedNanos");
            final long latency = longValue(trial, prefix + "latencyNanos");
            final long expectedDeadline = measurementStart + (long) (index - 1) * interval;
            if (deadline != expectedDeadline || started < deadline || completed < started
                    || !GaFormalPerformanceRunner.statusSampleWithinMeasurement(
                            measurementStart, measurementEnd, started, completed)
                    || latency != completed - started || latency < 1L) {
                throw new IOException("STATUS timing evidence is not absolute or ordered");
            }
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
        pairs.put("qualification.jarSha256", campaign.get("qualification.jarSha256"));
        pairs.put("controller.gitSha", campaign.get("qualification.controllerSha"));
        pairs.put("protocol", "v2");
        pairs.put("window", "8");
        pairs.put("walMode", "SYNC_EACH_APPEND");
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
                case "latencyNanos" -> {
                    // The management raw payload may include this derived field.  The
                    // authoritative accounting boundaries are the request state timestamps.
                }
                default -> throw new IOException("unknown request-level accounting field: " + field);
            }
        }
        if (requests.isEmpty()) {
            throw new IOException("request-level accounting evidence is missing");
        }
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
            if (request.commandSequence <= 0L || request.offeredNanos < 0L
                    || request.completedNanos < 0L || request.capacityReleaseNanos < 0L) {
                throw new IOException("request-level accounting chronology is invalid");
            }
            if (request.completedNanos > 0L
                    && (request.completedNanos < request.offeredNanos
                    || request.capacityReleaseNanos < request.completedNanos
                    || request.outcomeCode < 0L)) {
                throw new IOException("request-level accounting completion is invalid");
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
    }
}
