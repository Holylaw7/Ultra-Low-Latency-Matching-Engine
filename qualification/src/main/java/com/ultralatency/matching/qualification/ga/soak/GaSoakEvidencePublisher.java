package com.ultralatency.matching.qualification.ga.soak;

import com.ultralatency.matching.qualification.QualificationArtifactHasher;
import com.ultralatency.matching.qualification.QualificationEvidencePublication;
import com.ultralatency.matching.qualification.QualificationIdentity;
import com.ultralatency.matching.qualification.ga.GaCandidateVerifier;
import com.ultralatency.matching.qualification.ga.GaEvidenceCodec;
import com.ultralatency.matching.qualification.ga.GaEvidenceStore;
import com.ultralatency.matching.qualification.ga.correctness.GaCorrectnessCanonicalContext;
import com.ultralatency.matching.qualification.ga.observability.GaObservabilityEvaluator;
import com.ultralatency.matching.qualification.ga.observability.GaObservabilityObservation;
import com.ultralatency.matching.qualification.ga.soak.GaSoakEvaluator.Criterion;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/** Publishes one shared physical Quick run through two independent GA evidence chains. */
public final class GaSoakEvidencePublisher {

    /** Canonical G6 Quick gate version. */
    public static final String G6_QUICK_VERSION = "g6-soak-quick-v1";
    /** Canonical G8 Quick gate version. */
    public static final String G8_QUICK_VERSION = "g8-observability-quick-v1";
    /** Non-formal limitation attached to every Quick gate result. */
    public static final String QUICK_LIMITATION_CODE = "QUICK_READINESS_ONLY";
    private static final String QUICK_LIMITATION =
            "Quick evidence demonstrates packaged-path readiness only; it is not formal G6/G8. ";

    private GaSoakEvidencePublisher() {
    }

    /** One immutable published canonical run manifest. */
    public record PublishedRun(
            String runId,
            String gate,
            Path manifestPath,
            String manifestSha256,
            String configurationIdentitySha256,
            String comparabilityIdentitySha256,
            String outcome) {
        public PublishedRun {
            requireText(runId, "runId");
            requireText(gate, "gate");
            Objects.requireNonNull(manifestPath, "manifestPath");
            requireDigest(manifestSha256, "manifestSha256");
            requireDigest(configurationIdentitySha256, "configurationIdentitySha256");
            requireDigest(comparabilityIdentitySha256, "comparabilityIdentitySha256");
            requireOutcome(outcome);
        }
    }

    /** The complete shared physical execution publication result. */
    public record PublishedQuick(
            String physicalExecutionId,
            Path evidenceRoot,
            PublishedRun g6,
            PublishedRun g8,
            Path bindingPath,
            String bindingSha256,
            Path g6GatePath,
            Path g8GatePath,
            Path inventoryPath) {
        public PublishedQuick {
            requireUuid(physicalExecutionId, "physicalExecutionId");
            Objects.requireNonNull(evidenceRoot, "evidenceRoot");
            Objects.requireNonNull(g6, "g6");
            Objects.requireNonNull(g8, "g8");
            Objects.requireNonNull(bindingPath, "bindingPath");
            requireDigest(bindingSha256, "bindingSha256");
            Objects.requireNonNull(g6GatePath, "g6GatePath");
            Objects.requireNonNull(g8GatePath, "g8GatePath");
            Objects.requireNonNull(inventoryPath, "inventoryPath");
            if (!"G6".equals(g6.gate()) || !"G8".equals(g8.gate())
                    || g6.runId().equals(g8.runId())) {
                throw new IllegalArgumentException("G6/G8 publication identities are not distinct");
            }
        }
    }

    /** Publishes one Quick physical run and separate G6/G8 canonical chains. */
    public static PublishedQuick publishQuick(
            final Path evidenceRoot,
            final GaSoakMatrix matrix,
            final GaSoakObservation g6Observation,
            final GaObservabilityObservation g8Observation,
            final GaSoakEvaluator.Evaluation g6Evaluation,
            final GaObservabilityEvaluator.Evaluation g8Evaluation,
            final GaCorrectnessCanonicalContext context,
            final Instant started,
            final Instant completed,
            final Map<String, Path> rawArtifacts) throws IOException {
        return publishQuick(evidenceRoot, matrix, g6Observation, g8Observation, g6Evaluation,
                g8Evaluation, context, started, completed, rawArtifacts, null);
    }

    /** Publishes one Quick run with canonical paced identity and runtime evidence. */
    public static PublishedQuick publishQuick(
            final Path evidenceRoot,
            final GaSoakMatrix matrix,
            final GaSoakObservation g6Observation,
            final GaObservabilityObservation g8Observation,
            final GaSoakEvaluator.Evaluation g6Evaluation,
            final GaObservabilityEvaluator.Evaluation g8Evaluation,
            final GaCorrectnessCanonicalContext context,
            final Instant started,
            final Instant completed,
            final Map<String, Path> rawArtifacts,
            final GaPacedRuntimeEvidence pacedEvidence) throws IOException {
        Objects.requireNonNull(evidenceRoot, "evidenceRoot");
        Objects.requireNonNull(matrix, "matrix");
        Objects.requireNonNull(g6Observation, "g6Observation");
        Objects.requireNonNull(g8Observation, "g8Observation");
        Objects.requireNonNull(g6Evaluation, "g6Evaluation");
        Objects.requireNonNull(g8Evaluation, "g8Evaluation");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(started, "started");
        Objects.requireNonNull(completed, "completed");
        Objects.requireNonNull(rawArtifacts, "rawArtifacts");
        if (!matrix.isQuick() || completed.isBefore(started)
                || !g6Observation.physicalExecutionId().equals(g8Observation.physicalExecutionId())
                || g6Observation.stage() != GaSoakMatrix.Stage.QUICK
                || g8Observation.stage() != GaSoakMatrix.Stage.QUICK) {
            throw new IllegalArgumentException("invalid shared Quick publication input");
        }
        final String physicalId = g6Observation.physicalExecutionId();
        if (!physicalId.equals(g8Observation.physicalExecutionId())) {
            throw new IllegalArgumentException("G6/G8 physical IDs differ");
        }
        final Path root = evidenceRoot.toAbsolutePath().normalize();
        Files.createDirectories(root);
        final Map<String, Path> artifacts = normalizeArtifacts(root, rawArtifacts);
        if (artifacts.isEmpty()) {
            throw new IOException("Quick evidence requires raw artifacts");
        }
        if (pacedEvidence != null) {
            for (String required : List.of("capacity-evidence-v1.csv",
                    "reader-wake-evidence-v1.csv", "measurement-boundary-v1.txt",
                    "invocation-v1.properties")) {
                if (!artifacts.containsKey(required)) {
                    throw new IOException("paced Quick evidence is missing " + required);
                }
            }
            final Map<String, String> publishedInvocation = GaQuickInvocation.read(
                    artifacts.get("invocation-v1.properties"));
            if (!publishedInvocation.equals(pacedEvidence.invocationFields())
                    || !pacedEvidence.invocationIdentitySha256().equals(
                    GaQuickInvocation.identity(publishedInvocation))) {
                throw new IOException("invocation artifact does not match runtime identity");
            }
        }
        for (Path artifact : artifacts.values()) {
            publishSidecar(artifact);
        }
        final Path inventory = root.resolve("SHA256SUMS");
        publishInventory(inventory, artifacts, root);
        publishSidecar(inventory);
        final Map<String, String> runtime = runtimeFields(root);
        final String comparability = QualificationIdentity.digest(runtime);
        final Map<String, String> configurationFields = configurationFields(matrix, pacedEvidence);
        final String configuration = QualificationIdentity.digest(configurationFields);
        final String g6RunId = UUID.randomUUID().toString();
        final String g8RunId = UUID.randomUUID().toString();
        // G6 and G8 are two views of one physical workload.  Both manifests
        // therefore carry the same observed accepted-command count; using the
        // matrix floor for G8 would hide a count mismatch between the two
        // canonical chains.
        final long sharedAcceptedCommands = g6Observation.acceptedCommands();
        final Map<String, String> g6Fields = manifestFields(
                "G6", G6_QUICK_VERSION, g6RunId, matrix, physicalId, context,
                runtime, configuration, comparability, started, completed, inventory, artifacts,
                g6Evaluation.outcome(), g6Evaluation.failureCode(), sharedAcceptedCommands,
                pacedEvidence);
        final Map<String, String> g8Fields = manifestFields(
                "G8", G8_QUICK_VERSION, g8RunId, matrix, physicalId, context,
                runtime, configuration, comparability, started, completed, inventory, artifacts,
                g8Evaluation.outcome(), g8Evaluation.failureCode(), sharedAcceptedCommands,
                pacedEvidence);
        final Path g6Manifest = root.resolve("g6-run-manifest-v1.txt");
        final Path g8Manifest = root.resolve("g8-run-manifest-v1.txt");
        final String g6Digest = GaEvidenceStore.publish(
                g6Manifest, GaEvidenceCodec.Schema.RUN, g6Fields);
        final String g8Digest = GaEvidenceStore.publish(
                g8Manifest, GaEvidenceCodec.Schema.RUN, g8Fields);
        publishSidecar(g6Manifest);
        publishSidecar(g8Manifest);
        final GaCandidateVerifier.Verified candidate = context.candidate();
        final GaG6G8PhysicalRunBinding.Fields bindingFields = new GaG6G8PhysicalRunBinding.Fields(
                physicalId, GaSoakMatrix.Stage.QUICK, g6RunId, relative(root, g6Manifest), g6Digest,
                g8RunId, relative(root, g8Manifest), g8Digest, context.controllerGitSha(),
                candidate.tag(), candidate.tagObjectSha(), candidate.productionSha(),
                candidate.applicationJarSha256(), candidate.productionTreeSha256(), configuration,
                QualificationArtifactHasher.sha256(inventory));
        final GaG6G8PhysicalRunBinding.Published binding = GaG6G8PhysicalRunBinding.publish(
                root.resolve("ga-g6-g8-physical-run-binding-v1.txt"), bindingFields);
        final Path g6Gate = publishGate(root, "G6", G6_QUICK_VERSION, g6RunId, g6Manifest,
                g6Digest, g6Evaluation.criteria(), g6Evaluation.outcome(),
                g6Evaluation.failureCode(), context, configuration, comparability, started, completed);
        final Path g8Gate = publishGate(root, "G8", G8_QUICK_VERSION, g8RunId, g8Manifest,
                g8Digest, convert(g8Evaluation.criteria()), g8Evaluation.outcome(),
                g8Evaluation.failureCode(), context, configuration, comparability, started, completed);
        return new PublishedQuick(physicalId, root,
                new PublishedRun(g6RunId, "G6", g6Manifest, g6Digest, configuration,
                        comparability, g6Evaluation.outcome()),
                new PublishedRun(g8RunId, "G8", g8Manifest, g8Digest, configuration,
                        comparability, g8Evaluation.outcome()),
                binding.path(), binding.sha256(), g6Gate, g8Gate, inventory);
    }

    private static Map<String, Path> normalizeArtifacts(
            final Path root, final Map<String, Path> input) throws IOException {
        final Map<String, Path> result = new TreeMap<>();
        for (Map.Entry<String, Path> entry : input.entrySet()) {
            final String name = Objects.requireNonNull(entry.getKey(), "artifact name");
            if (name.isBlank() || name.contains("/") || name.contains("\\")
                    || name.equals(".") || name.equals("..")) {
                throw new IllegalArgumentException("raw artifact names must be basenames");
            }
            final Path artifact = Objects.requireNonNull(entry.getValue(), name)
                    .toAbsolutePath().normalize();
            if (!artifact.startsWith(root) || !Files.isRegularFile(artifact)) {
                throw new IOException("raw artifact is not contained in Quick root: " + artifact);
            }
            if (result.put(name, artifact) != null) {
                throw new IOException("duplicate raw artifact name: " + name);
            }
        }
        return Map.copyOf(result);
    }

    private static Map<String, String> manifestFields(
            final String gate,
            final String gateVersion,
            final String runId,
            final GaSoakMatrix matrix,
            final String physicalId,
            final GaCorrectnessCanonicalContext context,
            final Map<String, String> runtime,
            final String configuration,
            final String comparability,
            final Instant started,
            final Instant completed,
            final Path inventory,
            final Map<String, Path> artifacts,
            final String outcome,
            final String failureCode,
            final long acceptedCommands,
            final GaPacedRuntimeEvidence pacedEvidence) throws IOException {
        if (acceptedCommands < 0L) {
            throw new IllegalArgumentException("accepted command count must be non-negative");
        }
        final Map<String, String> fields = new TreeMap<>();
        fields.put("artifact.inventory.path", relative(inventory.getParent(), inventory));
        fields.put("artifact.inventory.sha256", QualificationArtifactHasher.sha256(inventory));
        fields.put("artifact.inventory.size", Long.toString(Files.size(inventory)));
        int index = 1;
        for (Map.Entry<String, Path> artifact : artifacts.entrySet()) {
            final String prefix = String.format("artifact.%04d", index++);
            fields.put(prefix + ".path", relative(inventory.getParent(), artifact.getValue()));
            fields.put(prefix + ".sha256", QualificationArtifactHasher.sha256(artifact.getValue()));
            fields.put(prefix + ".size", Long.toString(Files.size(artifact.getValue())));
        }
        final GaCandidateVerifier.Verified candidate = context.candidate();
        fields.put("candidate.applicationJarSha256", candidate.applicationJarSha256());
        fields.put("candidate.productionSha", candidate.productionSha());
        fields.put("candidate.productionTreeSha256", candidate.productionTreeSha256());
        fields.put("candidate.tag", candidate.tag());
        fields.put("candidate.tagObjectSha", candidate.tagObjectSha());
        fields.put("physicalExecution.id", physicalId);
        fields.put("comparability.identitySha256", comparability);
        fields.put("configuration.identitySha256", configuration);
        fields.put("controller.gitSha", context.controllerGitSha());
        if (pacedEvidence != null) {
            fields.put("qualification.jarSha256", pacedEvidence.qualificationJarSha256());
            fields.put("invocation.identitySha256", pacedEvidence.invocationIdentitySha256());
            fields.put("run.protocolV2Window", Integer.toString(pacedEvidence.configuredWindow()));
            fields.putAll(pacedEvidence.manifestFields());
        }
        fields.put("evidence.completedAtUtc", completed.toString());
        fields.put("evidence.elapsedMillis", Long.toString(Math.max(0L,
                Duration.between(started, completed).toMillis())));
        fields.put("evidence.failureCode", failureCode);
        fields.put("evidence.failureDigestSha256", digest(failureCode));
        fields.put("evidence.outcome", outcome);
        fields.put("evidence.startedAtUtc", started.toString());
        fields.put("gate.id", gate);
        fields.put("gate.version", gateVersion);
        fields.put("run.commandCount", Long.toString(acceptedCommands));
        fields.put("run.id", runId);
        fields.put("run.profile", matrix.profile());
        fields.put("run.seed", Long.toString(matrix.seed()));
        fields.putAll(runtime);
        fields.put("schema.version", GaEvidenceCodec.Schema.RUN.version());
        fields.put("workload.version", "qualification-memory-steady-state-v1");
        return fields;
    }

    private static Path publishGate(
            final Path root,
            final String gate,
            final String gateVersion,
            final String runId,
            final Path manifest,
            final String manifestDigest,
            final List<GaSoakEvaluator.Criterion> criteria,
            final String outcome,
            final String failureCode,
            final GaCorrectnessCanonicalContext context,
            final String configuration,
            final String comparability,
            final Instant started,
            final Instant completed) throws IOException {
        final Map<String, String> fields = gateFields(gate, gateVersion, runId, manifest,
                manifestDigest, criteria, outcome, failureCode, context, configuration,
                comparability, started, completed);
        final Path target = root.resolve(gate.toLowerCase(java.util.Locale.ROOT)
                + "-gate-result-v1.txt");
        GaEvidenceStore.publish(target, GaEvidenceCodec.Schema.GATE, fields);
        publishSidecar(target);
        return target;
    }

    private static Map<String, String> gateFields(
            final String gate,
            final String gateVersion,
            final String runId,
            final Path manifest,
            final String manifestDigest,
            final List<GaSoakEvaluator.Criterion> criteria,
            final String outcome,
            final String failureCode,
            final GaCorrectnessCanonicalContext context,
            final String configuration,
            final String comparability,
            final Instant started,
            final Instant completed) throws IOException {
        final GaCandidateVerifier.Verified candidate = context.candidate();
        final Map<String, String> fields = new TreeMap<>();
        fields.put("blocker.classification", "PASS".equals(outcome) ? "NONE" : failureCode);
        fields.put("candidate.applicationJarSha256", candidate.applicationJarSha256());
        fields.put("candidate.productionSha", candidate.productionSha());
        fields.put("candidate.productionTreeSha256", candidate.productionTreeSha256());
        fields.put("candidate.tag", candidate.tag());
        fields.put("candidate.tagObjectSha", candidate.tagObjectSha());
        fields.put("comparability.identitySha256", comparability);
        fields.put("configuration.identitySha256", configuration);
        fields.put("controller.gitSha", context.controllerGitSha());
        fields.put("criterion.count", Integer.toString(criteria.size()));
        fields.put("evidence.completedAtUtc", completed.toString());
        fields.put("evidence.outcome", outcome);
        fields.put("evidence.startedAtUtc", started.toString());
        fields.put("gate.id", gate);
        fields.put("gate.version", gateVersion);
        fields.put("limitation.count", "1");
        fields.put("limitation.0001.code", QUICK_LIMITATION_CODE);
        fields.put("limitation.0001.statementDigestSha256", digest(QUICK_LIMITATION));
        fields.put("manifest.count", "1");
        fields.put("manifest.0001.path", relative(manifest.getParent(), manifest));
        fields.put("manifest.0001.sha256", manifestDigest);
        fields.put("schema.version", GaEvidenceCodec.Schema.GATE.version());
        for (int index = 0; index < criteria.size(); index++) {
            final Criterion criterion = criteria.get(index);
            final String prefix = String.format("criterion.%04d", index + 1);
            fields.put(prefix + ".id", criterion.id());
            fields.put(prefix + ".actual", criterion.actual());
            fields.put(prefix + ".operator", criterion.operator());
            fields.put(prefix + ".required", criterion.required());
            fields.put(prefix + ".result", criterion.passed() ? "PASS" : "FAIL");
        }
        return fields;
    }

    private static List<GaSoakEvaluator.Criterion> convert(
            final List<GaObservabilityEvaluator.Criterion> criteria) {
        final List<GaSoakEvaluator.Criterion> result = new ArrayList<>(criteria.size());
        for (GaObservabilityEvaluator.Criterion criterion : criteria) {
            result.add(new GaSoakEvaluator.Criterion(criterion.id(), criterion.actual(),
                    criterion.operator(), criterion.required(), criterion.passed()));
        }
        return List.copyOf(result);
    }

    private static Map<String, String> configurationFields(
            final GaSoakMatrix matrix,
            final GaPacedRuntimeEvidence pacedEvidence) {
        final Map<String, String> values = new TreeMap<>();
        values.put("acceptedFloor", Long.toString(matrix.acceptedFloor()));
        values.put("duration", matrix.duration().toString());
        values.put("offeredRatePerSecond", Integer.toString(matrix.offeredRatePerSecond()));
        values.put("profile", matrix.profile());
        values.put("sampleRateHz", Integer.toString(matrix.sampleRateHz()));
        values.put("seed", Long.toString(matrix.seed()));
        values.put("stage", matrix.stage().name());
        values.put("version", matrix.version());
        if (pacedEvidence != null) {
            values.put("protocolV2.window", Integer.toString(pacedEvidence.configuredWindow()));
        }
        return Map.copyOf(values);
    }

    private static Map<String, String> runtimeFields(final Path root) throws IOException {
        final Map<String, String> observed =
                com.ultralatency.matching.qualification.ga.performance.GaPerformanceEnvironment
                        .capture(root);
        final Map<String, String> runtime = new LinkedHashMap<>();
        runtime.put("runtime.cpuModel", observed.getOrDefault("cpu.model", "UNAVAILABLE"));
        runtime.put("runtime.filesystem", observed.getOrDefault("filesystem", "UNAVAILABLE"));
        runtime.put("runtime.gcCollectors", observed.getOrDefault("gc.collectors", "UNAVAILABLE"));
        runtime.put("runtime.heapMaxBytes", observed.getOrDefault("heap.max.bytes", "0"));
        runtime.put("runtime.javaRuntimeVersion",
                observed.getOrDefault("java.runtime.version", "UNAVAILABLE"));
        runtime.put("runtime.javaVendor", observed.getOrDefault("java.vendor", "UNAVAILABLE"));
        runtime.put("runtime.javaVmArguments",
                stableRuntimeArguments(observed.getOrDefault("java.vm.arguments", "<none>")));
        runtime.put("runtime.javaVmName", observed.getOrDefault("java.vm.name", "UNAVAILABLE"));
        runtime.put("runtime.javaVmVersion",
                observed.getOrDefault("java.vm.version", "UNAVAILABLE"));
        runtime.put("runtime.logicalProcessors",
                observed.getOrDefault("logical.processors", "0"));
        runtime.put("runtime.nettyAllocator", "io.netty.buffer.PooledByteBufAllocator");
        runtime.put("runtime.osArch", observed.getOrDefault("os.arch", "UNAVAILABLE"));
        runtime.put("runtime.osName", observed.getOrDefault("os.name", "UNAVAILABLE"));
        runtime.put("runtime.osVersion", observed.getOrDefault("os.version", "UNAVAILABLE"));
        runtime.put("runtime.storageIdentity",
                observed.getOrDefault("storage.identity", "UNAVAILABLE"));
        return Map.copyOf(runtime);
    }

    /** Removes the separately bound matrix window from the comparability identity. */
    private static String stableRuntimeArguments(final String arguments) {
        final String value = Objects.requireNonNull(arguments, "arguments");
        final String stable = java.util.Arrays.stream(value.split(" "))
                .filter(argument -> !argument.startsWith("-Dqualification.paced.maxInFlight="))
                .filter(argument -> !argument.isBlank())
                .collect(java.util.stream.Collectors.joining(" "));
        return stable.isBlank() ? "<none>" : stable;
    }

    private static void publishInventory(
            final Path inventory,
            final Map<String, Path> artifacts,
            final Path root) throws IOException {
        final StringBuilder text = new StringBuilder();
        final List<Map.Entry<String, Path>> ordered = new ArrayList<>(artifacts.entrySet());
        ordered.sort((left, right) -> relativeUnchecked(root, left.getValue())
                .compareTo(relativeUnchecked(root, right.getValue())));
        for (Map.Entry<String, Path> entry : ordered) {
            text.append(QualificationArtifactHasher.sha256(entry.getValue())).append("  ")
                    .append(relativeUnchecked(root, entry.getValue())).append('\n');
        }
        QualificationEvidencePublication.text(inventory, text.toString());
    }

    private static void publishSidecar(final Path artifact) throws IOException {
        final Path sidecar = artifact.resolveSibling(artifact.getFileName() + ".sha256");
        GaEvidenceStore.publishArtifactSidecar(sidecar,
                Map.of(artifact.getFileName().toString(), artifact));
    }

    private static String relative(final Path root, final Path path) throws IOException {
        final String value = relativeUnchecked(root, path);
        if (value.isBlank() || value.startsWith("/") || value.startsWith("../")
                || value.contains("\\") || value.contains("//")) {
            throw new IOException("evidence path is not relative POSIX text");
        }
        return value;
    }

    private static String relativeUnchecked(final Path root, final Path path) {
        return root.toAbsolutePath().normalize().relativize(
                path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private static String digest(final String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JDK", exception);
        }
    }

    private static void requireUuid(final String value, final String name) {
        requireText(value, name);
        try {
            UUID.fromString(value);
        } catch (final IllegalArgumentException exception) {
            throw new IllegalArgumentException(name + " must be a UUID", exception);
        }
    }

    private static void requireDigest(final String value, final String name) {
        requireText(value, name);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must be lowercase SHA-256");
        }
    }

    private static void requireOutcome(final String value) {
        if (!List.of("PASS", "FAIL", "ABORTED").contains(value)) {
            throw new IllegalArgumentException("unsupported outcome");
        }
    }

    private static void requireText(final String value, final String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
