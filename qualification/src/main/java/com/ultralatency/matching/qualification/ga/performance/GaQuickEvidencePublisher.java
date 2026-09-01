package com.ultralatency.matching.qualification.ga.performance;

import com.ultralatency.matching.qualification.QualificationArtifactHasher;
import com.ultralatency.matching.qualification.QualificationEvidencePublication;
import com.ultralatency.matching.qualification.QualificationIdentity;
import com.ultralatency.matching.qualification.ga.GaCandidateVerifier;
import com.ultralatency.matching.qualification.ga.GaEvidenceCodec;
import com.ultralatency.matching.qualification.ga.GaEvidenceStore;
import com.ultralatency.matching.qualification.ga.correctness.GaCorrectnessCanonicalContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/** Publishes bounded Quick readiness evidence using the frozen GA schemas. */
public final class GaQuickEvidencePublisher {

    private GaQuickEvidencePublisher() {
    }

    /** Immutable input for one non-formal Quick run. */
    public record RunInput(
            String gate,
            String gateVersion,
            String profile,
            long seed,
            int commandCount,
            String workloadVersion,
            Instant started,
            Instant completed,
            String outcome,
            String failureCode,
            long acceptedCommands,
            long responseCount,
            long tradeCount,
            String rawEvidence,
            long[] latencySamplesNanos,
            Map<String, String> configurationFields,
            GaCorrectnessCanonicalContext context) {

        public RunInput {
            requireText(gate, "gate");
            requireText(gateVersion, "gateVersion");
            requireText(profile, "profile");
            requireText(workloadVersion, "workloadVersion");
            Objects.requireNonNull(started, "started");
            Objects.requireNonNull(completed, "completed");
            requireOutcome(outcome);
            requireFailureCode(failureCode, outcome);
            if (seed < 0 || commandCount <= 0 || acceptedCommands < 0
                    || responseCount < 0 || tradeCount < 0 || completed.isBefore(started)) {
                throw new IllegalArgumentException("Quick run values are outside their bounds");
            }
            requireText(rawEvidence, "rawEvidence");
            latencySamplesNanos = copySamples(latencySamplesNanos);
            configurationFields = Map.copyOf(Objects.requireNonNull(
                    configurationFields, "configurationFields"));
            Objects.requireNonNull(context, "context");
        }

        @Override
        public long[] latencySamplesNanos() {
            return latencySamplesNanos.clone();
        }
    }

    /** Immutable reference to a published canonical run manifest. */
    public record PublishedRun(
            String runId,
            String gate,
            Path manifestPath,
            String manifestSha256,
            String configurationIdentitySha256,
            String comparabilityIdentitySha256,
            String outcome,
            boolean passed) {
        public PublishedRun {
            requireText(runId, "runId");
            requireText(gate, "gate");
            Objects.requireNonNull(manifestPath, "manifestPath");
            requireDigest(manifestSha256, "manifestSha256");
            requireDigest(configurationIdentitySha256, "configurationIdentitySha256");
            requireDigest(comparabilityIdentitySha256, "comparabilityIdentitySha256");
            requireOutcome(outcome);
            if (passed != "PASS".equals(outcome)) {
                throw new IllegalArgumentException("published run outcome does not match passed");
            }
        }
    }

    /** Publishes one immutable run manifest and its complete local artifact chain. */
    public static PublishedRun publishRun(final Path runDirectory, final RunInput input)
            throws IOException {
        Objects.requireNonNull(runDirectory, "runDirectory");
        Objects.requireNonNull(input, "input");
        final Path root = runDirectory.toAbsolutePath().normalize();
        Files.createDirectories(root);
        final Path raw = root.resolve("raw-evidence-v1.txt");
        final Path samples = root.resolve("latency-samples-v1.csv");
        QualificationEvidencePublication.text(raw, input.rawEvidence());
        QualificationEvidencePublication.samples(samples, input.latencySamplesNanos());
        publishArtifactSidecar(raw);
        publishArtifactSidecar(samples);

        final Path inventory = root.resolve("SHA256SUMS");
        publishInventory(inventory, List.of(raw, samples), root);
        publishArtifactSidecar(inventory);

        final Map<String, String> runtime = runtimeFields(root);
        final String comparability = QualificationIdentity.digest(runtime);
        final String configuration = QualificationIdentity.digest(input.configurationFields());
        final GaCandidateVerifier.Verified candidate = input.context().candidate();
        final String runId = UUID.randomUUID().toString();
        final Map<String, String> fields = new TreeMap<>();
        fields.put("artifact.inventory.path", "SHA256SUMS");
        fields.put("artifact.inventory.sha256", QualificationArtifactHasher.sha256(inventory));
        fields.put("artifact.inventory.size", Long.toString(Files.size(inventory)));
        final List<Path> artifacts = List.of(raw, samples);
        for (int index = 0; index < artifacts.size(); index++) {
            final Path artifact = artifacts.get(index);
            final String prefix = String.format("artifact.%04d", index + 1);
            fields.put(prefix + ".path", relative(root, artifact));
            fields.put(prefix + ".sha256", QualificationArtifactHasher.sha256(artifact));
            fields.put(prefix + ".size", Long.toString(Files.size(artifact)));
        }
        fields.put("candidate.applicationJarSha256", candidate.applicationJarSha256());
        fields.put("candidate.productionSha", candidate.productionSha());
        fields.put("candidate.productionTreeSha256", candidate.productionTreeSha256());
        fields.put("candidate.tag", candidate.tag());
        fields.put("candidate.tagObjectSha", candidate.tagObjectSha());
        fields.put("comparability.identitySha256", comparability);
        fields.put("configuration.identitySha256", configuration);
        fields.put("controller.gitSha", input.context().controllerGitSha());
        fields.put("evidence.completedAtUtc", input.completed().toString());
        fields.put("evidence.elapsedMillis", Long.toString(Duration.between(
                input.started(), input.completed()).toMillis()));
        fields.put("evidence.failureCode", input.failureCode());
        fields.put("evidence.failureDigestSha256", digest(input.failureCode()));
        fields.put("evidence.outcome", input.outcome());
        fields.put("evidence.startedAtUtc", input.started().toString());
        fields.put("gate.id", input.gate());
        fields.put("gate.version", input.gateVersion());
        fields.put("run.commandCount", Integer.toString(input.commandCount()));
        fields.put("run.id", runId);
        fields.put("run.profile", input.profile());
        fields.put("run.seed", Long.toString(input.seed()));
        fields.putAll(runtime);
        fields.put("schema.version", GaEvidenceCodec.Schema.RUN.version());
        fields.put("workload.version", input.workloadVersion());
        final Path manifest = root.resolve("ga-run-manifest-v1.txt");
        final String manifestSha = GaEvidenceStore.publish(
                manifest, GaEvidenceCodec.Schema.RUN, fields);
        publishArtifactSidecar(manifest);
        return new PublishedRun(runId, input.gate(), manifest, manifestSha,
                configuration, comparability, input.outcome(), "PASS".equals(input.outcome()));
    }

    /** Publishes one immutable readiness gate result referencing the run manifest. */
    public static Path publishGate(
            final Path evidenceRoot,
            final String gate,
            final String gateVersion,
            final PublishedRun run,
            final List<? extends GaPerformanceEvaluator.Criterion> criteria,
            final GaCorrectnessCanonicalContext context,
            final Instant started,
            final Instant completed,
            final String limitationCode,
            final String limitationText) throws IOException {
        Objects.requireNonNull(evidenceRoot, "evidenceRoot");
        requireText(gate, "gate");
        requireText(gateVersion, "gateVersion");
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(criteria, "criteria");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(started, "started");
        Objects.requireNonNull(completed, "completed");
        requireText(limitationCode, "limitationCode");
        requireText(limitationText, "limitationText");
        if (criteria.isEmpty() || completed.isBefore(started) || !gate.equals(run.gate())) {
            throw new IllegalArgumentException("readiness gate values are outside their bounds");
        }
        final Path root = evidenceRoot.toAbsolutePath().normalize();
        Files.createDirectories(root);
        final Map<String, String> fields = new TreeMap<>();
        final GaCandidateVerifier.Verified candidate = context.candidate();
        fields.put("blocker.classification", blockerFor(run.outcome()));
        fields.put("candidate.applicationJarSha256", candidate.applicationJarSha256());
        fields.put("candidate.productionSha", candidate.productionSha());
        fields.put("candidate.productionTreeSha256", candidate.productionTreeSha256());
        fields.put("candidate.tag", candidate.tag());
        fields.put("candidate.tagObjectSha", candidate.tagObjectSha());
        fields.put("comparability.identitySha256", run.comparabilityIdentitySha256());
        fields.put("configuration.identitySha256", run.configurationIdentitySha256());
        fields.put("controller.gitSha", context.controllerGitSha());
        fields.put("criterion.count", Integer.toString(criteria.size()));
        fields.put("evidence.completedAtUtc", completed.toString());
        fields.put("evidence.outcome", run.outcome());
        fields.put("evidence.startedAtUtc", started.toString());
        fields.put("gate.id", gate);
        fields.put("gate.version", gateVersion);
        fields.put("limitation.count", "1");
        fields.put("limitation.0001.code", limitationCode);
        fields.put("limitation.0001.statementDigestSha256", digest(limitationText));
        fields.put("manifest.count", "1");
        fields.put("manifest.0001.path", relative(root, run.manifestPath()));
        fields.put("manifest.0001.sha256", run.manifestSha256());
        fields.put("schema.version", GaEvidenceCodec.Schema.GATE.version());
        for (int index = 0; index < criteria.size(); index++) {
            final GaPerformanceEvaluator.Criterion criterion = criteria.get(index);
            final String prefix = String.format("criterion.%04d", index + 1);
            fields.put(prefix + ".id", criterion.id());
            fields.put(prefix + ".actual", criterion.actual());
            fields.put(prefix + ".operator", criterion.operator());
            fields.put(prefix + ".required", criterion.required());
            fields.put(prefix + ".result", criterion.passed() ? "PASS" : "FAIL");
        }
        final Path gatePath = root.resolve(gate.toLowerCase(java.util.Locale.ROOT)
                + "-gate-result-v1.txt");
        final String ignored = GaEvidenceStore.publish(
                gatePath, GaEvidenceCodec.Schema.GATE, fields);
        if (ignored.isBlank()) {
            throw new IOException("gate digest was not produced");
        }
        publishArtifactSidecar(gatePath);
        return gatePath;
    }

    /** Publishes a sidecar for one payload artifact. */
    public static void publishArtifactSidecar(final Path artifact) throws IOException {
        final Path sidecar = artifact.resolveSibling(artifact.getFileName() + ".sha256");
        GaEvidenceStore.publishArtifactSidecar(sidecar,
                Map.of(artifact.getFileName().toString(), artifact));
    }

    private static void publishInventory(
            final Path inventory, final List<Path> artifacts, final Path root) throws IOException {
        final List<Path> ordered = new ArrayList<>(artifacts);
        ordered.sort(Comparator.comparing(path -> relativeUnchecked(root, path)));
        final StringBuilder text = new StringBuilder();
        for (Path artifact : ordered) {
            text.append(QualificationArtifactHasher.sha256(artifact)).append("  ")
                    .append(relativeUnchecked(root, artifact)).append('\n');
        }
        QualificationEvidencePublication.text(inventory, text.toString());
    }

    private static Map<String, String> runtimeFields(final Path root) throws IOException {
        final Map<String, String> observed = GaPerformanceEnvironment.capture(root);
        final Map<String, String> runtime = new LinkedHashMap<>();
        runtime.put("runtime.cpuModel", observed.getOrDefault("cpu.model", "UNAVAILABLE"));
        runtime.put("runtime.filesystem", observed.getOrDefault("filesystem", "UNAVAILABLE"));
        runtime.put("runtime.gcCollectors", observed.getOrDefault("gc.collectors", "UNAVAILABLE"));
        runtime.put("runtime.heapMaxBytes", observed.getOrDefault("heap.max.bytes", "0"));
        runtime.put("runtime.javaRuntimeVersion",
                observed.getOrDefault("java.runtime.version", "UNAVAILABLE"));
        runtime.put("runtime.javaVendor", observed.getOrDefault("java.vendor", "UNAVAILABLE"));
        runtime.put("runtime.javaVmArguments",
                observed.getOrDefault("java.vm.arguments", "<none>"));
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

    private static String relative(final Path root, final Path file) {
        final String value = relativeUnchecked(root, file);
        if (value.isBlank() || value.contains("\\") || value.startsWith("/")) {
            throw new IllegalArgumentException("evidence path must be relative POSIX text");
        }
        return value;
    }

    private static String relativeUnchecked(final Path root, final Path file) {
        return root.toAbsolutePath().normalize().relativize(
                file.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private static long[] copySamples(final long[] values) {
        Objects.requireNonNull(values, "latencySamplesNanos");
        final long[] copy = values.clone();
        for (long value : copy) {
            if (value < 0) {
                throw new IllegalArgumentException("latency samples must be non-negative");
            }
        }
        return copy;
    }

    private static String digest(final String value) {
        return QualificationIdentity.digest(Map.of("value", value));
    }

    private static void requireOutcome(final String value) {
        if (!"PASS".equals(value) && !"FAIL".equals(value) && !"ABORTED".equals(value)) {
            throw new IllegalArgumentException("outcome must be PASS, FAIL or ABORTED");
        }
    }

    private static String blockerFor(final String outcome) {
        return switch (outcome) {
            case "PASS" -> "NONE";
            case "FAIL" -> "B2";
            case "ABORTED" -> "B3";
            default -> throw new IllegalArgumentException("unsupported run outcome");
        };
    }

    private static void requireFailureCode(final String value, final String outcome) {
        requireText(value, "failureCode");
        if ("PASS".equals(outcome) && !"NONE".equals(value)) {
            throw new IllegalArgumentException("PASS requires failureCode NONE");
        }
        if (!"PASS".equals(outcome) && "NONE".equals(value)) {
            throw new IllegalArgumentException("non-PASS requires a failure code");
        }
    }

    private static void requireDigest(final String value, final String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be lowercase SHA-256");
        }
    }

    private static void requireText(final String value, final String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
