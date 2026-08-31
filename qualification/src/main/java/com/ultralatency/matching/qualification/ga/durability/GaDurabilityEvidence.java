package com.ultralatency.matching.qualification.ga.durability;

import com.ultralatency.matching.qualification.QualificationArtifactHasher;
import com.ultralatency.matching.qualification.QualificationEvidencePublication;
import com.ultralatency.matching.qualification.QualificationIdentity;
import com.ultralatency.matching.qualification.ga.GaCandidateVerifier;
import com.ultralatency.matching.qualification.ga.GaEvidenceCodec;
import com.ultralatency.matching.qualification.ga.GaEvidenceStore;
import com.ultralatency.matching.qualification.ga.GaGateEvaluator;
import com.ultralatency.matching.qualification.ga.correctness.GaCorrectnessCanonicalContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/** Shared immutable evidence publication for the qualification-only G3/G7 runners. */
public final class GaDurabilityEvidence {

    private static final int MAX_ARTIFACTS = 1_000;
    private static final String PROFILE = "LIFECYCLE_MIX";
    private static final String LIMITATION_DURABILITY =
            "G3 uses the approved WAL fault model and does not claim hardware power-loss safety.";
    private static final String LIMITATION_EXACTLY_ONCE =
            "Completed-response termination does not claim arbitrary in-flight exactly-once.";
    private static final String LIMITATION_OVERLOAD =
            "G7 proves only the approved single-session bounded overload envelope.";

    private GaDurabilityEvidence() {
    }

    /** One canonical run-manifest reference. */
    public record RunReference(
            String runId,
            String gate,
            Path manifestPath,
            String manifestSha256,
            String configurationIdentitySha256,
            String comparabilityIdentitySha256,
            boolean passed,
            String outcome) {

        public RunReference {
            requireUuid(runId, "runId");
            requireText(gate, "gate");
            Objects.requireNonNull(manifestPath, "manifestPath");
            requireSha256(manifestSha256, "manifestSha256");
            requireSha256(configurationIdentitySha256, "configurationIdentitySha256");
            requireSha256(comparabilityIdentitySha256, "comparabilityIdentitySha256");
            requireOutcome(outcome, "outcome");
            if (passed != "PASS".equals(outcome)) {
                throw new IllegalArgumentException("run reference outcome does not match passed");
            }
        }

        /** Backward-compatible PASS/FAIL reference constructor. */
        public RunReference(
                final String runId,
                final String gate,
                final Path manifestPath,
                final String manifestSha256,
                final String configurationIdentitySha256,
                final String comparabilityIdentitySha256,
                final boolean passed) {
            this(runId, gate, manifestPath, manifestSha256, configurationIdentitySha256,
                    comparabilityIdentitySha256, passed, passed ? "PASS" : "FAIL");
        }

        /** Returns whether this run was explicitly aborted before qualification completed. */
        public boolean aborted() {
            return "ABORTED".equals(outcome);
        }
    }

    /** One measured criterion for a canonical gate result. */
    public record Criterion(String id, String actual, String operator, String required,
            boolean passed) {

        public Criterion {
            requireToken(id, "criterion id");
            requireText(actual, "criterion actual");
            requireText(operator, "criterion operator");
            requireText(required, "criterion required");
        }
    }

    /** Publishes an immutable raw inventory and one canonical run manifest. */
    static RunReference publishRun(
            final Path runDirectory,
            final String gate,
            final String gateVersion,
            final long seed,
            final int commandCount,
            final int segmentSize,
            final String workloadVersion,
            final GaCorrectnessCanonicalContext context,
            final Instant started,
            final Instant completed,
            final boolean passed,
            final String failureCode,
            final String rawEvidence) throws IOException {
        return publishRun(runDirectory, gate, gateVersion, seed, commandCount, segmentSize,
                workloadVersion, context, started, completed, passed ? "PASS" : "FAIL",
                failureCode, rawEvidence, null);
    }

    /** Publishes a run while binding it to a matrix-wide configuration identity. */
    static RunReference publishRun(
            final Path runDirectory,
            final String gate,
            final String gateVersion,
            final long seed,
            final int commandCount,
            final int segmentSize,
            final String workloadVersion,
            final GaCorrectnessCanonicalContext context,
            final Instant started,
            final Instant completed,
            final boolean passed,
            final String failureCode,
            final String rawEvidence,
            final String configurationIdentityOverride) throws IOException {
        return publishRun(runDirectory, gate, gateVersion, seed, commandCount, segmentSize,
                workloadVersion, context, started, completed, passed ? "PASS" : "FAIL",
                failureCode, rawEvidence, configurationIdentityOverride);
    }

    /** Publishes an explicit aborted run without converting it to a failed PASS/FAIL result. */
    static RunReference publishAbortedRun(
            final Path runDirectory,
            final String gate,
            final String gateVersion,
            final long seed,
            final int commandCount,
            final int segmentSize,
            final String workloadVersion,
            final GaCorrectnessCanonicalContext context,
            final Instant started,
            final Instant completed,
            final String failureCode,
            final String rawEvidence,
            final String configurationIdentityOverride) throws IOException {
        return publishRun(runDirectory, gate, gateVersion, seed, commandCount, segmentSize,
                workloadVersion, context, started, completed, "ABORTED", failureCode,
                rawEvidence, configurationIdentityOverride);
    }

    /** Publishes a run with an explicit PASS, FAIL or ABORTED lifecycle outcome. */
    static RunReference publishRun(
            final Path runDirectory,
            final String gate,
            final String gateVersion,
            final long seed,
            final int commandCount,
            final int segmentSize,
            final String workloadVersion,
            final GaCorrectnessCanonicalContext context,
            final Instant started,
            final Instant completed,
            final String outcome,
            final String failureCode,
            final String rawEvidence,
            final String configurationIdentityOverride) throws IOException {
        Objects.requireNonNull(runDirectory, "runDirectory");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(started, "started");
        Objects.requireNonNull(completed, "completed");
        requireOutcome(outcome, "outcome");
        final boolean passed = "PASS".equals(outcome);
        if (completed.isBefore(started) || seed < 0 || commandCount < 0 || segmentSize <= 0) {
            throw new IllegalArgumentException("invalid run evidence values");
        }
        if (passed && !"NONE".equals(failureCode)) {
            throw new IllegalArgumentException("passed run must use failure code NONE");
        }
        if (!passed && "NONE".equals(failureCode)) {
            throw new IllegalArgumentException("non-passing run needs a failure code");
        }
        final Path root = runDirectory.toAbsolutePath().normalize();
        Files.createDirectories(root);
        final Path raw = root.resolve("raw-evidence-v1.txt");
        QualificationEvidencePublication.text(raw, Objects.requireNonNull(rawEvidence, "rawEvidence"));
        final Inventory inventory = publishInventory(root);
        final Map<String, String> runtime = runtimeProvenance(root);
        final String comparability = QualificationIdentity.digest(runtime);
        final String configuration = configurationIdentityOverride == null
                ? QualificationIdentity.digest(configurationFields(
                        gate, gateVersion, seed, commandCount, segmentSize, workloadVersion))
                : requireSha256Value(configurationIdentityOverride,
                        "configurationIdentityOverride");
        final String runId = UUID.randomUUID().toString();
        final Map<String, String> fields = new TreeMap<>();
        final GaCandidateVerifier.Verified candidate = context.candidate();
        fields.put("artifact.inventory.path", inventory.path());
        fields.put("artifact.inventory.sha256", inventory.sha256());
        fields.put("artifact.inventory.size", Long.toString(inventory.size()));
        final List<InventoryArtifact> artifacts = orderedManifestArtifacts(inventory.artifacts());
        if (artifacts.isEmpty() || artifacts.size() > MAX_ARTIFACTS) {
            throw new IOException("manifest artifact inventory is outside its bounds");
        }
        for (int index = 0; index < artifacts.size(); index++) {
            final InventoryArtifact artifact = artifacts.get(index);
            final String prefix = String.format("artifact.%04d", index + 1);
            fields.put(prefix + ".path", artifact.path());
            fields.put(prefix + ".sha256", artifact.sha256());
            fields.put(prefix + ".size", Long.toString(artifact.size()));
        }
        fields.put("candidate.applicationJarSha256", candidate.applicationJarSha256());
        fields.put("candidate.productionSha", candidate.productionSha());
        fields.put("candidate.productionTreeSha256", candidate.productionTreeSha256());
        fields.put("candidate.tag", candidate.tag());
        fields.put("candidate.tagObjectSha", candidate.tagObjectSha());
        fields.put("comparability.identitySha256", comparability);
        fields.put("configuration.identitySha256", configuration);
        fields.put("controller.gitSha", context.controllerGitSha());
        fields.put("evidence.completedAtUtc", completed.toString());
        fields.put("evidence.elapsedMillis", Long.toString(
                Math.max(0L, java.time.Duration.between(started, completed).toMillis())));
        fields.put("evidence.failureCode", failureCode);
        fields.put("evidence.failureDigestSha256", digest(failureCode));
        fields.put("evidence.outcome", outcome);
        fields.put("evidence.startedAtUtc", started.toString());
        fields.put("gate.id", gate);
        fields.put("gate.version", gateVersion);
        fields.put("run.commandCount", Integer.toString(commandCount));
        fields.put("run.id", runId);
        fields.put("run.profile", PROFILE);
        fields.put("run.seed", Long.toString(seed));
        fields.putAll(runtime);
        fields.put("schema.version", GaEvidenceCodec.Schema.RUN.version());
        fields.put("workload.version", workloadVersion);
        final Path manifest = root.resolve(gate.toLowerCase(java.util.Locale.ROOT)
                + "-run-manifest-v1.txt");
        final String manifestSha = GaEvidenceStore.publish(
                manifest, GaEvidenceCodec.Schema.RUN, fields);
        publishSidecar(manifest);
        return new RunReference(runId, gate, manifest, manifestSha, configuration,
                comparability, passed, outcome);
    }

    /** Publishes the canonical immutable multi-run campaign summary. */
    static Path publishCampaign(
            final Path root,
            final String gate,
            final List<RunReference> references,
            final GaCorrectnessCanonicalContext context,
            final Instant started,
            final Instant completed,
            final int requiredRunCount,
            final String configurationIdentity,
            final boolean passed) throws IOException {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(gate, "gate");
        Objects.requireNonNull(references, "references");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(started, "started");
        Objects.requireNonNull(completed, "completed");
        requireSha256Value(configurationIdentity, "configurationIdentity");
        if (references.isEmpty() || requiredRunCount < 1
                || completed.isBefore(started)) {
            throw new IllegalArgumentException("campaign evidence is outside its bounds");
        }
        validateReferences(root, gate, references, configurationIdentity, context);
        final boolean allConfigurationsEqual = references.stream()
                .allMatch(reference -> configurationIdentity.equals(
                        reference.configurationIdentitySha256()));
        final boolean uniqueRunIds = references.stream().map(RunReference::runId).distinct()
                .count() == references.size();
        final boolean uniqueManifests = references.stream().map(RunReference::manifestPath)
                .map(path -> path.toAbsolutePath().normalize()).distinct().count()
                == references.size();
        final int validRunCount = (int) references.stream()
                .filter(RunReference::passed)
                .count();
        final boolean aborted = references.stream().anyMatch(RunReference::aborted);
        final boolean evaluatedPass = passed
                && references.size() == requiredRunCount
                && validRunCount == requiredRunCount
                && allConfigurationsEqual
                && uniqueRunIds
                && uniqueManifests;
        final String evaluatedOutcome = aborted ? "ABORTED" : evaluatedPass ? "PASS" : "FAIL";
        final GaCandidateVerifier.Verified candidate = context.candidate();
        final Map<String, String> fields = new TreeMap<>();
        fields.put("campaign.completedAtUtc", completed.toString());
        fields.put("campaign.configurationIdentityEqual", Boolean.toString(allConfigurationsEqual));
        fields.put("campaign.id", UUID.randomUUID().toString());
        fields.put("campaign.outcome", evaluatedOutcome);
        fields.put("campaign.requiredRunCount", Integer.toString(requiredRunCount));
        fields.put("campaign.startedAtUtc", started.toString());
        fields.put("campaign.validRunCount", Integer.toString(validRunCount));
        fields.put("candidate.applicationJarSha256", candidate.applicationJarSha256());
        fields.put("candidate.productionSha", candidate.productionSha());
        fields.put("candidate.tag", candidate.tag());
        fields.put("candidate.tagObjectSha", candidate.tagObjectSha());
        fields.put("comparability.policy", "runtime-provenance-v1");
        fields.put("controller.gitSha", context.controllerGitSha());
        fields.put("gate.id", gate);
        fields.put("run.count", Integer.toString(references.size()));
        for (int index = 0; index < references.size(); index++) {
            final RunReference reference = references.get(index);
            final String prefix = String.format("run.%04d", index + 1);
            fields.put(prefix + ".comparabilityIdentitySha256",
                    reference.comparabilityIdentitySha256());
            fields.put(prefix + ".configurationIdentitySha256",
                    reference.configurationIdentitySha256());
            fields.put(prefix + ".id", reference.runId());
            fields.put(prefix + ".manifestPath", relative(root, reference.manifestPath()));
            fields.put(prefix + ".manifestSha256", reference.manifestSha256());
            fields.put(prefix + ".outcome", reference.outcome());
        }
        fields.put("schema.version", GaEvidenceCodec.Schema.CAMPAIGN.version());
        final Path campaign = root.resolve(gate.toLowerCase(java.util.Locale.ROOT)
                + "-campaign-summary-v1.txt");
        final String digest = GaEvidenceStore.publish(
                campaign, GaEvidenceCodec.Schema.CAMPAIGN, fields);
        publishSidecar(campaign);
        final GaGateEvaluator.CampaignDecision decision = GaGateEvaluator.evaluateCampaign(fields);
        if (decision.passed() != evaluatedPass || digest.isBlank()) {
            throw new IOException("canonical campaign result semantic evaluation mismatch");
        }
        return campaign;
    }

    /** Publishes one canonical gate result and validates its semantics before returning. */
    static Path publishGate(
            final Path campaignRoot,
            final String gate,
            final String gateVersion,
            final List<RunReference> references,
            final GaCorrectnessCanonicalContext context,
            final Instant started,
            final Instant completed,
            final List<Criterion> criteria,
            final List<String> limitationStatements) throws IOException {
        Objects.requireNonNull(campaignRoot, "campaignRoot");
        Objects.requireNonNull(references, "references");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(criteria, "criteria");
        Objects.requireNonNull(limitationStatements, "limitationStatements");
        if (references.isEmpty() || criteria.isEmpty() || completed.isBefore(started)) {
            throw new IllegalArgumentException("gate evidence cannot be empty or inverted");
        }
        validateReferences(campaignRoot, gate, references,
                references.get(0).configurationIdentitySha256(), context);
        final boolean allConfigurationsEqual = references.stream()
                .map(RunReference::configurationIdentitySha256).distinct().count() == 1;
        final boolean uniqueRunIds = references.stream().map(RunReference::runId).distinct()
                .count() == references.size();
        final boolean uniqueManifests = references.stream().map(RunReference::manifestPath)
                .map(path -> path.toAbsolutePath().normalize()).distinct().count()
                == references.size();
        final boolean passed = criteria.stream().allMatch(Criterion::passed)
                && references.stream().allMatch(RunReference::passed)
                && allConfigurationsEqual
                && uniqueRunIds
                && uniqueManifests;
        final boolean aborted = references.stream().anyMatch(RunReference::aborted);
        final String outcome = aborted ? "ABORTED" : passed ? "PASS" : "FAIL";
        final Map<String, String> fields = new TreeMap<>();
        final GaCandidateVerifier.Verified candidate = context.candidate();
        fields.put("blocker.classification", passed
                ? "NONE"
                : blockerClassification(references, allConfigurationsEqual,
                        uniqueRunIds, uniqueManifests));
        fields.put("candidate.applicationJarSha256", candidate.applicationJarSha256());
        fields.put("candidate.productionSha", candidate.productionSha());
        fields.put("candidate.productionTreeSha256", candidate.productionTreeSha256());
        fields.put("candidate.tag", candidate.tag());
        fields.put("candidate.tagObjectSha", candidate.tagObjectSha());
        fields.put("comparability.identitySha256", references.get(0).comparabilityIdentitySha256());
        fields.put("configuration.identitySha256", references.get(0).configurationIdentitySha256());
        fields.put("controller.gitSha", context.controllerGitSha());
        fields.put("criterion.count", Integer.toString(criteria.size()));
        for (int index = 0; index < criteria.size(); index++) {
            final Criterion criterion = criteria.get(index);
            final String prefix = String.format("criterion.%04d", index + 1);
            fields.put(prefix + ".actual", criterion.actual());
            fields.put(prefix + ".id", criterion.id());
            fields.put(prefix + ".operator", criterion.operator());
            fields.put(prefix + ".required", criterion.required());
            fields.put(prefix + ".result", criterion.passed() ? "PASS" : "FAIL");
        }
        fields.put("evidence.completedAtUtc", completed.toString());
        fields.put("evidence.outcome", outcome);
        fields.put("evidence.startedAtUtc", started.toString());
        fields.put("gate.id", gate);
        fields.put("gate.version", gateVersion);
        fields.put("limitation.count", Integer.toString(limitationStatements.size()));
        for (int index = 0; index < limitationStatements.size(); index++) {
            final String prefix = String.format("limitation.%04d", index + 1);
            fields.put(prefix + ".code", "L" + (index + 1));
            fields.put(prefix + ".statementDigestSha256",
                    digest(limitationStatements.get(index)));
        }
        fields.put("manifest.count", Integer.toString(references.size()));
        for (int index = 0; index < references.size(); index++) {
            final RunReference reference = references.get(index);
            final String prefix = String.format("manifest.%04d", index + 1);
            fields.put(prefix + ".path", relative(campaignRoot, reference.manifestPath()));
            fields.put(prefix + ".sha256", reference.manifestSha256());
        }
        fields.put("schema.version", GaEvidenceCodec.Schema.GATE.version());
        final Path result = campaignRoot.resolve(gate.toLowerCase(java.util.Locale.ROOT)
                + "-gate-result-v1.txt");
        final String resultSha = GaEvidenceStore.publish(result, GaEvidenceCodec.Schema.GATE, fields);
        publishSidecar(result);
        if (GaGateEvaluator.evaluateGate(fields).passed() != passed) {
            throw new IOException("canonical gate result semantic evaluation mismatch");
        }
        if (resultSha.isBlank()) {
            throw new IOException("canonical gate result digest is empty");
        }
        return result;
    }

    private static String blockerClassification(
            final List<RunReference> references,
            final boolean allConfigurationsEqual,
            final boolean uniqueRunIds,
            final boolean uniqueManifests) throws IOException {
        if (!allConfigurationsEqual || !uniqueRunIds || !uniqueManifests) {
            return "B0";
        }
        String selected = "B2";
        int selectedPriority = 2;
        for (RunReference reference : references) {
            final Map<String, String> fields = GaEvidenceStore.read(
                    reference.manifestPath(), GaEvidenceCodec.Schema.RUN);
            final String code = fields.get("evidence.failureCode");
            final int priority = failurePriority(code);
            if (priority > selectedPriority) {
                selectedPriority = priority;
                selected = code;
            }
        }
        return selected;
    }

    private static int failurePriority(final String code) {
        return switch (code) {
            case "B0" -> 5;
            case "B1" -> 4;
            case "B3" -> 3;
            case "B4" -> 1;
            default -> 2;
        };
    }

    /** Publishes a deterministic campaign summary for audit tooling. */
    static Path publishSummary(
            final Path root,
            final String matrixVersion,
            final List<RunReference> references,
            final Path g3Result,
            final boolean passed) throws IOException {
        final StringBuilder text = new StringBuilder();
        text.append("schemaVersion=ga-g3-g7-summary-v1\n");
        text.append("matrixVersion=").append(matrixVersion).append('\n');
        text.append("runCount=").append(references.size()).append('\n');
        text.append("g3GateResult=").append(relative(root, g3Result)).append('\n');
        text.append("gateResultCount=1\n");
        text.append("passed=").append(passed).append('\n');
        text.append("outcome=").append(summaryOutcome(references, passed)).append('\n');
        for (int index = 0; index < references.size(); index++) {
            final RunReference reference = references.get(index);
            text.append(String.format("run.%04d.id=%s\n", index + 1, reference.runId()));
            text.append(String.format("run.%04d.gate=%s\n", index + 1, reference.gate()));
            text.append(String.format("run.%04d.manifestSha256=%s\n", index + 1,
                    reference.manifestSha256()));
            text.append(String.format("run.%04d.outcome=%s\n", index + 1,
                    reference.outcome()));
        }
        final Path summary = root.resolve("ga-g3-g7-summary-v1.txt");
        QualificationEvidencePublication.text(summary, text.toString());
        publishSidecar(summary);
        return summary;
    }

    /** Publishes a deterministic G7 campaign summary for audit tooling. */
    static Path publishOverloadSummary(
            final Path root,
            final String matrixVersion,
            final List<RunReference> references,
            final Path gate,
            final boolean passed) throws IOException {
        final StringBuilder text = new StringBuilder();
        text.append("schemaVersion=ga-g7-summary-v1\n");
        text.append("matrixVersion=").append(matrixVersion).append('\n');
        text.append("runCount=").append(references.size()).append('\n');
        text.append("g7GateResult=").append(relative(root, gate)).append('\n');
        text.append("passed=").append(passed).append('\n');
        text.append("outcome=").append(summaryOutcome(references, passed)).append('\n');
        for (int index = 0; index < references.size(); index++) {
            final RunReference reference = references.get(index);
            text.append(String.format("run.%04d.id=%s\n", index + 1, reference.runId()));
            text.append(String.format("run.%04d.gate=%s\n", index + 1, reference.gate()));
            text.append(String.format("run.%04d.manifestSha256=%s\n", index + 1,
                    reference.manifestSha256()));
            text.append(String.format("run.%04d.outcome=%s\n", index + 1,
                    reference.outcome()));
        }
        final Path summary = root.resolve("ga-g7-summary-v1.txt");
        QualificationEvidencePublication.text(summary, text.toString());
        publishSidecar(summary);
        return summary;
    }

    static String limitationDurability() {
        return LIMITATION_DURABILITY;
    }

    static String limitationExactlyOnce() {
        return LIMITATION_EXACTLY_ONCE;
    }

    static String limitationOverload() {
        return LIMITATION_OVERLOAD;
    }

    /** Returns the immutable evidence inventory bound used by the G3/G7 publisher. */
    static int maxArtifactCount() {
        return MAX_ARTIFACTS;
    }

    private static String summaryOutcome(
            final List<RunReference> references,
            final boolean passed) {
        return references.stream().anyMatch(RunReference::aborted)
                ? "ABORTED" : passed ? "PASS" : "FAIL";
    }

    private static Inventory publishInventory(final Path root) throws IOException {
        final Path inventory = root.resolve("SHA256SUMS");
        final List<Path> files = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            final var iterator = paths.iterator();
            while (iterator.hasNext()) {
                final Path path = iterator.next();
                if (path.equals(root)) {
                    continue;
                }
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("symbolic links are not allowed in evidence inventory");
                }
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("unsupported evidence filesystem entry: " + path);
                }
                if (!path.equals(inventory)
                        && !path.getFileName().toString().endsWith(".sha256")) {
                    files.add(path);
                }
            }
        }
        files.sort(Comparator.comparing(path -> relativeUnchecked(root, path)));
        if (files.isEmpty() || files.size() > MAX_ARTIFACTS) {
            throw new IOException("invalid evidence inventory size");
        }
        final StringBuilder text = new StringBuilder(files.size() * 100);
        final List<InventoryArtifact> artifacts = new ArrayList<>(files.size());
        for (Path file : files) {
            final String path = relative(root, file);
            final long size = Files.size(file);
            final String sha256 = QualificationArtifactHasher.sha256(file);
            artifacts.add(new InventoryArtifact(path, size, sha256));
            text.append(sha256).append("  ").append(path).append('\n');
        }
        final byte[] bytes = text.toString().getBytes(StandardCharsets.US_ASCII);
        QualificationEvidencePublication.bytes(inventory, bytes);
        for (InventoryArtifact artifact : artifacts) {
            publishSidecar(root.resolve(artifact.path()));
        }
        publishSidecar(inventory);
        return new Inventory(artifacts, relative(root, inventory), bytes.length, digest(bytes));
    }

    private static void validateReferences(
            final Path root,
            final String gate,
            final List<RunReference> references,
            final String configurationIdentity,
            final GaCorrectnessCanonicalContext context) throws IOException {
        final Path normalizedRoot = root.toAbsolutePath().normalize();
        final Path realRoot = normalizedRoot.toRealPath();
        Objects.requireNonNull(context, "context");
        for (RunReference reference : references) {
            final Path manifest = reference.manifestPath().toAbsolutePath().normalize();
            if (!manifest.startsWith(normalizedRoot) || !Files.isRegularFile(manifest,
                    LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("campaign references a missing or foreign manifest");
            }
            if (!manifest.toRealPath().startsWith(realRoot)) {
                throw new IOException("campaign manifest escapes the evidence root");
            }
            if (!reference.manifestSha256().equals(QualificationArtifactHasher.sha256(manifest))) {
                throw new IOException("campaign manifest digest mismatch");
            }
            final Map<String, String> fields = GaEvidenceStore.read(
                    manifest, GaEvidenceCodec.Schema.RUN);
            if (!gate.equals(fields.get("gate.id"))
                    || !reference.gate().equals(fields.get("gate.id"))
                    || !reference.runId().equals(fields.get("run.id"))
                    || !configurationIdentity.equals(fields.get("configuration.identitySha256"))
                    || !reference.outcome().equals(fields.get("evidence.outcome"))) {
                throw new IOException("campaign manifest identity or outcome mismatch");
            }
            validateCandidateIdentity(fields, context);
            validateManifestArtifacts(manifest, normalizedRoot, realRoot, fields);
            final Path sidecar = manifest.resolveSibling(manifest.getFileName() + ".sha256");
            if (!Files.isRegularFile(sidecar, LinkOption.NOFOLLOW_LINKS)
                    || !sidecar.toRealPath().startsWith(realRoot)) {
                throw new IOException("campaign manifest sidecar is missing or foreign");
            }
            final Map<String, String> sidecarEntries = GaEvidenceStore.readArtifactSidecar(sidecar);
            if (sidecarEntries.size() != 1
                    || !reference.manifestSha256().equals(sidecarEntries.get(
                    manifest.getFileName().toString()))) {
                throw new IOException("campaign manifest sidecar mismatch");
            }
        }
    }

    private static void validateCandidateIdentity(
            final Map<String, String> fields,
            final GaCorrectnessCanonicalContext context) throws IOException {
        final GaCandidateVerifier.Verified candidate = context.candidate();
        if (!candidate.applicationJarSha256().equals(fields.get("candidate.applicationJarSha256"))
                || !candidate.productionSha().equals(fields.get("candidate.productionSha"))
                || !candidate.productionTreeSha256().equals(
                fields.get("candidate.productionTreeSha256"))
                || !candidate.tag().equals(fields.get("candidate.tag"))
                || !candidate.tagObjectSha().equals(fields.get("candidate.tagObjectSha"))
                || !context.controllerGitSha().equals(fields.get("controller.gitSha"))) {
            throw new IOException("campaign manifest candidate or controller identity mismatch");
        }
    }

    private static void validateManifestArtifacts(
            final Path manifest,
            final Path evidenceRoot,
            final Path realEvidenceRoot,
            final Map<String, String> fields) throws IOException {
        final Path runRoot = manifest.getParent();
        if (runRoot == null || !runRoot.startsWith(evidenceRoot)
                || !runRoot.toRealPath().startsWith(realEvidenceRoot)) {
            throw new IOException("manifest evidence root is foreign");
        }
        final Path inventory = resolveContained(runRoot, fields.get("artifact.inventory.path"));
        if (!inventory.getParent().equals(runRoot)
                || !"SHA256SUMS".equals(inventory.getFileName().toString())
                || !Files.isRegularFile(inventory, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(inventory)
                || !inventory.toRealPath().startsWith(runRoot.toRealPath())) {
            throw new IOException("manifest inventory is missing or foreign");
        }
        if (Files.size(inventory) != parseSize(fields, "artifact.inventory.size")
                || !fields.get("artifact.inventory.sha256").equals(
                QualificationArtifactHasher.sha256(inventory))) {
            throw new IOException("manifest inventory digest or size mismatch");
        }
        verifyAdjacentSidecar(inventory, QualificationArtifactHasher.sha256(inventory));
        final Map<String, InventoryArtifact> inventoryEntries = readInventory(inventory, runRoot);
        final Map<String, InventoryArtifact> manifestEntries = new TreeMap<>();
        int index = 1;
        while (fields.containsKey(String.format("artifact.%04d.path", index))) {
            final String prefix = String.format("artifact.%04d", index++);
            final String path = fields.get(prefix + ".path");
            final Path artifact = resolveContained(runRoot, path);
            final String digest = fields.get(prefix + ".sha256");
            final long size = parseSize(fields, prefix + ".size");
            if (!Files.isRegularFile(artifact, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(artifact)
                    || Files.size(artifact) != size
                    || !digest.equals(QualificationArtifactHasher.sha256(artifact))) {
                throw new IOException("manifest artifact digest or size mismatch: " + path);
            }
            verifyAdjacentSidecar(artifact, digest);
            if (manifestEntries.put(path, new InventoryArtifact(path, size, digest)) != null) {
                throw new IOException("duplicate manifest artifact path: " + path);
            }
        }
        if (manifestEntries.isEmpty() || !manifestEntries.equals(inventoryEntries)) {
            throw new IOException("manifest artifact inventory does not match SHA256SUMS");
        }
        rejectUnlistedFiles(runRoot, manifest, inventory, manifestEntries.keySet());
    }

    private static Map<String, InventoryArtifact> readInventory(
            final Path inventory,
            final Path runRoot) throws IOException {
        final byte[] bytes = Files.readAllBytes(inventory);
        final String text = new String(bytes, StandardCharsets.US_ASCII);
        if (!java.util.Arrays.equals(bytes, text.getBytes(StandardCharsets.US_ASCII))
                || text.indexOf('\r') >= 0 || !text.endsWith("\n")) {
            throw new IOException("evidence inventory is not canonical ASCII");
        }
        final TreeMap<String, InventoryArtifact> entries = new TreeMap<>();
        final String[] lines = text.split("\n", -1);
        String previous = null;
        for (int index = 0; index < lines.length - 1; index++) {
            final String line = lines[index];
            if (line.length() < 68 || line.charAt(64) != ' '
                    || line.charAt(65) != ' ') {
                throw new IOException("malformed evidence inventory line");
            }
            final String digest = line.substring(0, 64);
            final String path = line.substring(66);
            if (!digest.matches("[0-9a-f]{64}") || path.isBlank()
                    || "SHA256SUMS".equals(path) || path.endsWith(".sha256")) {
                throw new IOException("invalid evidence inventory entry");
            }
            if (previous != null && previous.compareTo(path) >= 0) {
                throw new IOException("evidence inventory is not sorted");
            }
            final Path artifact = resolveContained(runRoot, path);
            if (!Files.isRegularFile(artifact, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(artifact)
                    || !artifact.toRealPath().startsWith(runRoot.toRealPath())) {
                throw new IOException("evidence inventory references a foreign artifact");
            }
            final long size = Files.size(artifact);
            if (!digest.equals(QualificationArtifactHasher.sha256(artifact))) {
                throw new IOException("evidence inventory digest mismatch: " + path);
            }
            if (entries.put(path, new InventoryArtifact(path, size, digest)) != null) {
                throw new IOException("duplicate evidence inventory entry");
            }
            previous = path;
        }
        if (entries.isEmpty()) {
            throw new IOException("evidence inventory is empty");
        }
        return Map.copyOf(entries);
    }

    private static void rejectUnlistedFiles(
            final Path runRoot,
            final Path manifest,
            final Path inventory,
            final java.util.Set<String> inventoryEntries) throws IOException {
        final java.util.Set<String> allowed = new java.util.HashSet<>(inventoryEntries);
        allowed.add(relative(runRoot, inventory));
        allowed.add(relative(runRoot, inventory.resolveSibling(
                inventory.getFileName() + ".sha256")));
        allowed.add(relative(runRoot, manifest));
        allowed.add(relative(runRoot, manifest.resolveSibling(
                manifest.getFileName() + ".sha256")));
        for (String entry : inventoryEntries) {
            final Path artifact = resolveContained(runRoot, entry);
            allowed.add(relative(runRoot, artifact.resolveSibling(
                    artifact.getFileName() + ".sha256")));
        }
        try (var paths = Files.walk(runRoot)) {
            for (Path path : paths.toList()) {
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("evidence root contains a symbolic link");
                }
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        || !allowed.contains(relative(runRoot, path))) {
                    throw new IOException("evidence root contains an unlisted artifact");
                }
            }
        }
    }

    private static long parseSize(final Map<String, String> fields, final String key)
            throws IOException {
        try {
            final long size = Long.parseLong(fields.get(key));
            if (size < 0) {
                throw new NumberFormatException("negative");
            }
            return size;
        } catch (final RuntimeException exception) {
            throw new IOException("invalid evidence size: " + key, exception);
        }
    }

    private static Path resolveContained(final Path root, final String value) throws IOException {
        if (value == null || value.isBlank() || value.startsWith("/")
                || value.startsWith("\\") || value.contains("\\")) {
            throw new IOException("invalid evidence relative path");
        }
        for (String segment : value.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IOException("non-canonical evidence relative path");
            }
        }
        final Path normalizedRoot = root.toAbsolutePath().normalize();
        final Path resolved = normalizedRoot.resolve(value).normalize();
        if (!resolved.startsWith(normalizedRoot) || resolved.equals(normalizedRoot)) {
            throw new IOException("evidence path escaped root");
        }
        return resolved;
    }

    private static void verifyAdjacentSidecar(
            final Path artifact,
            final String expectedDigest) throws IOException {
        final Path normalized = artifact.toAbsolutePath().normalize();
        final Path parent = normalized.getParent();
        if (parent == null || !Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(normalized)) {
            throw new IOException("evidence artifact is not a regular file");
        }
        final Path sidecar = parent.resolve(normalized.getFileName() + ".sha256");
        if (!Files.isRegularFile(sidecar, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(sidecar)) {
            throw new IOException("evidence artifact sidecar is missing: "
                    + normalized.getFileName());
        }
        final Map<String, String> values = GaEvidenceStore.readArtifactSidecar(sidecar);
        if (values.size() != 1 || !expectedDigest.equals(values.get(
                normalized.getFileName().toString()))) {
            throw new IOException("evidence artifact sidecar mismatch: "
                    + normalized.getFileName());
        }
    }

    private static Map<String, String> runtimeProvenance(final Path root) throws IOException {
        final Map<String, String> fields = new LinkedHashMap<>();
        final java.nio.file.FileStore store = Files.getFileStore(root);
        fields.put("runtime.cpuModel", value(System.getenv("PROCESSOR_IDENTIFIER"),
                System.getenv("HOSTTYPE"), System.getProperty("os.arch")));
        fields.put("runtime.filesystem", value(store.type()));
        fields.put("runtime.gcCollectors", java.lang.management.ManagementFactory
                .getGarbageCollectorMXBeans().stream()
                .map(java.lang.management.GarbageCollectorMXBean::getName).sorted()
                .collect(java.util.stream.Collectors.joining(",")));
        fields.put("runtime.heapMaxBytes", Long.toString(Math.max(0L,
                java.lang.management.ManagementFactory.getMemoryMXBean()
                        .getHeapMemoryUsage().getMax())));
        fields.put("runtime.javaRuntimeVersion", value(System.getProperty("java.runtime.version")));
        fields.put("runtime.javaVendor", value(System.getProperty("java.vendor")));
        fields.put("runtime.javaVmArguments", value(String.join(" ",
                java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments())));
        fields.put("runtime.javaVmName", value(System.getProperty("java.vm.name")));
        fields.put("runtime.javaVmVersion", value(System.getProperty("java.vm.version")));
        fields.put("runtime.logicalProcessors", Integer.toString(Runtime.getRuntime()
                .availableProcessors()));
        fields.put("runtime.nettyAllocator", io.netty.buffer.PooledByteBufAllocator.DEFAULT
                .getClass().getName());
        fields.put("runtime.osArch", value(System.getProperty("os.arch")));
        fields.put("runtime.osName", value(System.getProperty("os.name")));
        fields.put("runtime.osVersion", value(System.getProperty("os.version")));
        fields.put("runtime.storageIdentity", value(store.name() + ":" + store.type()));
        return Map.copyOf(fields);
    }

    private static Map<String, String> configurationFields(
            final String gate,
            final String gateVersion,
            final long seed,
            final int commandCount,
            final int segmentSize,
            final String workloadVersion) {
        final Map<String, String> fields = new TreeMap<>();
        fields.put("gate.id", gate);
        fields.put("gate.version", gateVersion);
        fields.put("matrix.commandCount", Integer.toString(commandCount));
        fields.put("matrix.segmentSizeBytes", Integer.toString(segmentSize));
        fields.put("matrix.seed", Long.toString(seed));
        fields.put("matrix.version", GaDurabilityMatrix.APPROVED_VERSION);
        fields.put("workload.profile", PROFILE);
        fields.put("workload.version", workloadVersion);
        return fields;
    }

    private static void publishSidecar(final Path file) throws IOException {
        final Path sidecar = file.resolveSibling(file.getFileName() + ".sha256");
        final String line = QualificationArtifactHasher.sha256(file) + "  "
                + file.getFileName() + "\n";
        QualificationEvidencePublication.text(sidecar, line);
    }

    private static String relative(final Path root, final Path file) throws IOException {
        final String value = relativeUnchecked(root, file);
        if (value.isBlank() || value.startsWith("/") || value.contains("\\")
                || value.contains("//") || value.contains("../")
                || !StandardCharsets.US_ASCII.newEncoder().canEncode(value)) {
            throw new IOException("evidence path escaped campaign root");
        }
        for (String segment : value.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IOException("evidence path is not canonical");
            }
        }
        return value;
    }

    private static String relativeUnchecked(final Path root, final Path file) {
        final Path normalizedRoot = root.toAbsolutePath().normalize();
        final Path normalized = file.toAbsolutePath().normalize();
        if (!normalized.startsWith(normalizedRoot) || normalized.equals(normalizedRoot)) {
            throw new IllegalArgumentException("evidence path escaped campaign root");
        }
        return normalizedRoot.relativize(normalized).toString().replace('\\', '/');
    }

    private static List<InventoryArtifact> orderedManifestArtifacts(
            final List<InventoryArtifact> inventoryArtifacts) throws IOException {
        final List<InventoryArtifact> ordered = new ArrayList<>(inventoryArtifacts.size());
        InventoryArtifact raw = null;
        for (InventoryArtifact artifact : inventoryArtifacts) {
            if ("raw-evidence-v1.txt".equals(artifact.path())) {
                raw = artifact;
                break;
            }
        }
        if (raw == null) {
            throw new IOException("evidence inventory is missing raw evidence payload");
        }
        ordered.add(raw);
        final String rawPath = raw.path();
        inventoryArtifacts.stream()
                .filter(artifact -> !artifact.path().equals(rawPath))
                .sorted(Comparator.comparing(InventoryArtifact::path))
                .forEach(ordered::add);
        return List.copyOf(ordered);
    }

    private static String value(final String... values) {
        for (String item : values) {
            if (item != null && !item.isBlank()) {
                return item;
            }
        }
        return "unknown";
    }

    private static String digest(final String value) {
        return digest(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String digest(final byte[] value) {
        try {
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value));
        } catch (final java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JDK", exception);
        }
    }

    private static String requireSha256Value(final String value, final String field) {
        requireSha256(value, field);
        return value;
    }

    private static void requireUuid(final String value, final String field) {
        if (value == null || !value.matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                || !UUID.fromString(value).toString().equals(value)) {
            throw new IllegalArgumentException(field + " must be a lowercase UUID");
        }
    }

    private static void requireSha256(final String value, final String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be lowercase SHA-256");
        }
    }

    private static void requireText(final String value, final String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static void requireToken(final String value, final String field) {
        if (value == null || !value.matches("[a-z0-9][a-z0-9.-]{0,63}")) {
            throw new IllegalArgumentException(field + " must be a lowercase token");
        }
    }

    private static void requireOutcome(final String value, final String field) {
        if (!"PASS".equals(value) && !"FAIL".equals(value) && !"ABORTED".equals(value)) {
            throw new IllegalArgumentException(field + " must be PASS, FAIL or ABORTED");
        }
    }

    private record InventoryArtifact(String path, long size, String sha256) {
    }

    private record Inventory(
            List<InventoryArtifact> artifacts,
            String path,
            long size,
            String sha256) {

        private Inventory {
            artifacts = List.copyOf(Objects.requireNonNull(artifacts, "artifacts"));
        }
    }
}
