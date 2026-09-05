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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/** Publishes one fully bound, per-request RC2 formal G4 performance run. */
public final class GaFormalPerformanceEvidencePublisher {

    /** Bounded-window counters retained as raw formal evidence. */
    public record CapacityMetrics(
            int maximumObservedInFlight,
            int maximumObservedPendingWire,
            int maximumObservedCompletedUndrained,
            long readerWakeCount,
            long capacityReleaseCount,
            long[] releaseDelayNanos,
            List<CapacityReleaseSample> releaseSamples) {
        public CapacityMetrics {
            if (maximumObservedInFlight < 0 || maximumObservedPendingWire < 0
                    || maximumObservedCompletedUndrained < 0 || readerWakeCount < 0
                    || capacityReleaseCount < 0) {
                throw new IllegalArgumentException("capacity metrics must be non-negative");
            }
            releaseDelayNanos = copyNonNegative(releaseDelayNanos, "releaseDelayNanos");
            releaseSamples = List.copyOf(Objects.requireNonNull(releaseSamples,
                    "releaseSamples"));
            for (CapacityReleaseSample sample : releaseSamples) {
                Objects.requireNonNull(sample, "releaseSamples member");
            }
            if (!releaseSamples.isEmpty()
                    && releaseSamples.size() != releaseDelayNanos.length) {
                throw new IllegalArgumentException(
                        "release samples and release delays must have equal size");
            }
            if (!releaseSamples.isEmpty()
                    && capacityReleaseCount != releaseSamples.size()) {
                throw new IllegalArgumentException(
                        "capacity release count must match release samples");
            }
        }

        /** Compatibility constructor for older synthetic qualification fixtures. */
        public CapacityMetrics(
                final int maximumObservedInFlight,
                final int maximumObservedPendingWire,
                final int maximumObservedCompletedUndrained,
                final long readerWakeCount,
                final long capacityReleaseCount,
                final long[] releaseDelayNanos) {
            this(maximumObservedInFlight, maximumObservedPendingWire,
                    maximumObservedCompletedUndrained, readerWakeCount, capacityReleaseCount,
                    releaseDelayNanos, List.of());
        }

        @Override
        public long[] releaseDelayNanos() {
            return releaseDelayNanos.clone();
        }
    }

    /** One complete response-to-capacity-release timeline row. */
    public record CapacityReleaseSample(
            long requestId,
            long commandSequence,
            long offeredNanos,
            long responseCompleteNanos,
            long capacityReleaseNanos,
            long schedulerConsumedNanos) {
        public CapacityReleaseSample {
            if (requestId <= 0L || commandSequence <= 0L || offeredNanos < 0L
                    || responseCompleteNanos < offeredNanos
                    || capacityReleaseNanos < responseCompleteNanos
                    || schedulerConsumedNanos < capacityReleaseNanos) {
                throw new IllegalArgumentException("capacity release chronology is invalid");
            }
        }

        /** Returns the response-complete to capacity-release delay. */
        public long releaseDelayNanos() {
            return capacityReleaseNanos - responseCompleteNanos;
        }
    }

    /** One raw per-request timing row retained for independent percentile recomputation. */
    public record LatencySample(
            long requestId,
            long commandSequence,
            long offeredNanos,
            long completedNanos,
            long capacityReleaseNanos) {
        public LatencySample {
            if (requestId <= 0L || commandSequence <= 0L || offeredNanos < 0L
                    || completedNanos < offeredNanos || capacityReleaseNanos < completedNanos) {
                throw new IllegalArgumentException("formal latency sample chronology is invalid");
            }
        }

        /** Returns the request's offer-to-validated-response duration. */
        public long latencyNanos() {
            return Math.max(1L, completedNanos - offeredNanos);
        }
    }

    /** Immutable result of publishing one formal G4 run. */
    public record PublishedRun(
            String runId,
            String physicalExecutionId,
            Path evidenceDirectory,
            Path manifestPath,
            String manifestSha256,
            Path gateResultPath,
            String configurationIdentitySha256,
            String comparabilityIdentitySha256,
            String outcome,
            boolean passed) {
        public PublishedRun {
            requireText(runId, "runId");
            requireUuid(physicalExecutionId, "physicalExecutionId");
            Objects.requireNonNull(evidenceDirectory, "evidenceDirectory");
            Objects.requireNonNull(manifestPath, "manifestPath");
            Objects.requireNonNull(gateResultPath, "gateResultPath");
            requireDigest(manifestSha256, "manifestSha256");
            requireDigest(configurationIdentitySha256, "configurationIdentitySha256");
            requireDigest(comparabilityIdentitySha256, "comparabilityIdentitySha256");
            requireOutcome(outcome);
            if (passed != "PASS".equals(outcome)) {
                throw new IllegalArgumentException("formal run outcome does not match passed");
            }
        }
    }

    /** Input for one immutable formal run publication. */
    public record RunInput(
            GaPerformanceMatrix matrix,
            GaCorrectnessCanonicalContext context,
            String physicalExecutionId,
            Instant started,
            Instant completed,
            long measurementStartNanos,
            long measurementEndNanos,
            GaPerformanceObservation observation,
            long offeredCommands,
            long tradeCount,
            List<LatencySample> latencySamples,
            CapacityMetrics capacityMetrics,
            Map<String, String> configurationFields,
            Map<String, String> environment,
            String rawEvidence,
            String outcome,
            String failureCode,
            List<? extends GaPerformanceEvaluator.Criterion> criteria,
            String campaignId) {
        /** Compatibility constructor for direct qualification fixtures without a campaign. */
        public RunInput(
                final GaPerformanceMatrix matrix,
                final GaCorrectnessCanonicalContext context,
                final String physicalExecutionId,
                final Instant started,
                final Instant completed,
                final long measurementStartNanos,
                final long measurementEndNanos,
                final GaPerformanceObservation observation,
                final long offeredCommands,
                final long tradeCount,
                final List<LatencySample> latencySamples,
                final CapacityMetrics capacityMetrics,
                final Map<String, String> configurationFields,
                final Map<String, String> environment,
                final String rawEvidence,
                final String outcome,
                final String failureCode,
                final List<? extends GaPerformanceEvaluator.Criterion> criteria) {
            this(matrix, context, physicalExecutionId, started, completed, measurementStartNanos,
                    measurementEndNanos, observation, offeredCommands, tradeCount, latencySamples,
                    capacityMetrics, configurationFields, environment, rawEvidence, outcome,
                    failureCode, criteria, "");
        }

        public RunInput {
            Objects.requireNonNull(matrix, "matrix");
            Objects.requireNonNull(context, "context");
            requireUuid(physicalExecutionId, "physicalExecutionId");
            Objects.requireNonNull(started, "started");
            Objects.requireNonNull(completed, "completed");
            Objects.requireNonNull(observation, "observation");
            latencySamples = List.copyOf(Objects.requireNonNull(latencySamples,
                    "latencySamples"));
            Objects.requireNonNull(capacityMetrics, "capacityMetrics");
            configurationFields = Map.copyOf(Objects.requireNonNull(configurationFields,
                    "configurationFields"));
            environment = Map.copyOf(Objects.requireNonNull(environment, "environment"));
            requireText(rawEvidence, "rawEvidence");
            requireOutcome(outcome);
            requireFailureCode(failureCode, outcome);
            criteria = List.copyOf(Objects.requireNonNull(criteria, "criteria"));
            campaignId = campaignId == null ? "" : campaignId;
            if (!campaignId.isBlank()) {
                requireUuid(campaignId, "campaignId");
            }
            if (completed.isBefore(started) || measurementStartNanos < 0L
                    || measurementEndNanos < measurementStartNanos || offeredCommands < 0L
                    || tradeCount < 0L || criteria.isEmpty()) {
                throw new IllegalArgumentException("formal run values are outside their bounds");
            }
            for (LatencySample sample : latencySamples) {
                Objects.requireNonNull(sample, "latencySamples member");
            }
        }
    }

    private GaFormalPerformanceEvidencePublisher() {
    }

    /** Publishes one immutable formal run and its gate result. */
    public static PublishedRun publishRun(
            final Path runDirectory,
            final RunInput input) throws IOException {
        Objects.requireNonNull(runDirectory, "runDirectory");
        Objects.requireNonNull(input, "input");
        final Path root = runDirectory.toAbsolutePath().normalize();
        Files.createDirectories(root);
        final Path raw = root.resolve("raw-evidence-v2.txt");
        final Path samples = root.resolve("latency-samples-v2.csv");
        final Path config = root.resolve("formal-configuration-v2.txt");
        final Path environment = root.resolve("formal-environment-v2.txt");
        final Path capacity = root.resolve("capacity-evidence-v2.txt");
        QualificationEvidencePublication.text(raw, input.rawEvidence());
        QualificationEvidencePublication.text(samples, samplesText(input.latencySamples()));
        QualificationEvidencePublication.text(config, keyValueText(input.configurationFields()));
        QualificationEvidencePublication.text(environment, keyValueText(input.environment()));
        QualificationEvidencePublication.text(capacity, capacityText(input));

        final Path inventory = root.resolve("SHA256SUMS");
        final List<Path> artifacts = payloadArtifacts(root);
        for (Path artifact : artifacts) {
            publishArtifactSidecar(artifact);
        }
        publishInventory(inventory, artifacts, root);
        publishArtifactSidecar(inventory);
        final Map<String, String> runtime = runtimeFields(input.environment());
        final String comparability = GaPerformanceEnvironment.identity(input.environment());
        final String configuration = GaFormalPerformanceContract.configurationIdentity(
                input.configurationFields());
        final GaCandidateVerifier.Verified candidate = input.context().candidate();
        final String runId = UUID.randomUUID().toString();
        final Map<String, String> fields = new TreeMap<>();
        fields.put("artifact.inventory.path", "SHA256SUMS");
        fields.put("artifact.inventory.sha256", QualificationArtifactHasher.sha256(inventory));
        fields.put("artifact.inventory.size", Long.toString(Files.size(inventory)));
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
        if (!input.campaignId().isBlank()) {
            fields.put("campaign.id", input.campaignId());
        }
        fields.put("evidence.completedAtUtc", input.completed().toString());
        fields.put("evidence.elapsedMillis", Long.toString(Duration.between(
                input.started(), input.completed()).toMillis()));
        fields.put("evidence.failureCode", input.failureCode());
        fields.put("evidence.failureDigestSha256", digest(input.failureCode()));
        fields.put("evidence.outcome", input.outcome());
        fields.put("evidence.startedAtUtc", input.started().toString());
        putCapacity(fields, input.capacityMetrics());
        fields.put("evidence.measurementStartNanos",
                Long.toString(input.measurementStartNanos()));
        fields.put("evidence.measurementEndNanos", Long.toString(input.measurementEndNanos()));
        fields.put("evidence.measurementDurationNanos", Long.toString(
                input.measurementEndNanos() - input.measurementStartNanos()));
        fields.put("gate.id", "G4");
        fields.put("gate.version", GaFormalPerformanceContract.CAMPAIGN);
        fields.put("invocation.identitySha256", configuration);
        fields.put("physicalExecution.id", input.physicalExecutionId());
        fields.put("qualification.jarSha256", requiredQualificationJar(input.context()));
        fields.put("run.commandCount", Long.toString(input.offeredCommands()));
        fields.put("run.formal", Boolean.toString(input.matrix().isApproved()));
        fields.put("run.id", runId);
        fields.put("run.loadModel", GaPerformanceMatrix.APPROVED_LOAD_MODEL);
        fields.put("run.measurementDurationNanos", Long.toString(
                input.measurementEndNanos() - input.measurementStartNanos()));
        fields.put("run.profile", input.matrix().profile());
        fields.put("run.protocol", GaPerformanceMatrix.APPROVED_PROTOCOL);
        fields.put("run.protocolV2Window", Integer.toString(
                GaPerformanceMatrix.APPROVED_PROTOCOL_V2_WINDOW));
        fields.put("run.seed", Long.toString(input.matrix().seed()));
        fields.put("run.walMode", GaPerformanceMatrix.APPROVED_WAL_MODE);
        fields.put("run.warmupDurationNanos", Long.toString(input.matrix().isApproved()
                ? GaFormalPerformanceContract.WARMUP.toNanos() : 0L));
        fields.putAll(runtime);
        fields.put("schema.version", GaEvidenceCodec.Schema.RUN.version());
        fields.put("workload.version", "qualification-memory-steady-state-v1");
        final Path manifest = root.resolve("ga-run-manifest-v1.txt");
        final String manifestSha = GaEvidenceStore.publish(
                manifest, GaEvidenceCodec.Schema.RUN, fields);
        publishArtifactSidecar(manifest);
        final PublishedRun run = new PublishedRun(
                runId, input.physicalExecutionId(), root, manifest, manifestSha,
                root.resolve("g4-gate-result-v1.txt"),
                configuration, comparability, input.outcome(), "PASS".equals(input.outcome()));
        final Path gate = publishGate(root, run, input);
        final GaFormalPerformanceEvidenceVerifier.Verification verification =
                GaFormalPerformanceEvidenceVerifier.verifyRun(root);
        if (!verification.passed() && "PASS".equals(input.outcome())) {
            throw new IOException("formal run evidence failed integrity verification: "
                    + verification.findings());
        }
        return new PublishedRun(
                run.runId(), run.physicalExecutionId(), run.evidenceDirectory(),
                run.manifestPath(), run.manifestSha256(), gate,
                run.configurationIdentitySha256(), run.comparabilityIdentitySha256(),
                run.outcome(), run.passed());
    }

    /** Publishes the derived campaign index after every constituent run is immutable. */
    public static Path publishCampaign(
            final Path campaignRoot,
            final GaCorrectnessCanonicalContext context,
            final GaPerformanceMatrix matrix,
            final List<PublishedRun> runs,
            final Instant started,
            final Instant completed,
            final List<? extends GaPerformanceEvaluator.Criterion> criteria,
            final Map<String, String> campaignMetrics,
            final boolean passed) throws IOException {
        return publishCampaign(campaignRoot, context, matrix, runs, started, completed, criteria,
                campaignMetrics, passed, UUID.randomUUID().toString());
    }

    /** Publishes a campaign using the caller-owned identity shared by every physical artifact. */
    public static Path publishCampaign(
            final Path campaignRoot,
            final GaCorrectnessCanonicalContext context,
            final GaPerformanceMatrix matrix,
            final List<PublishedRun> runs,
            final Instant started,
            final Instant completed,
            final List<? extends GaPerformanceEvaluator.Criterion> criteria,
            final Map<String, String> campaignMetrics,
            final boolean passed,
            final String campaignId) throws IOException {
        Objects.requireNonNull(campaignRoot, "campaignRoot");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(matrix, "matrix");
        Objects.requireNonNull(runs, "runs");
        Objects.requireNonNull(started, "started");
        Objects.requireNonNull(completed, "completed");
        Objects.requireNonNull(criteria, "criteria");
        Objects.requireNonNull(campaignMetrics, "campaignMetrics");
        if (runs.isEmpty() || runs.size() > matrix.runCount() || criteria.isEmpty()
                || completed.isBefore(started)) {
            throw new IllegalArgumentException("formal campaign values are outside their bounds");
        }
        final Path root = campaignRoot.toAbsolutePath().normalize();
        Files.createDirectories(root);
        requireUuid(campaignId, "campaignId");
        final Path evidence = root.resolve("campaign-evidence-v2.txt");
        final Map<String, String> evidenceFields = new LinkedHashMap<>(campaignMetrics);
        final GaCandidateVerifier.Verified candidate = context.candidate();
        evidenceFields.put("campaign.id", campaignId);
        evidenceFields.put("campaign.gate", "G4");
        evidenceFields.put("gate.id", "G4");
        evidenceFields.put("gate.version", GaFormalPerformanceContract.CAMPAIGN);
        evidenceFields.put("formal", Boolean.toString(matrix.isApproved()));
        evidenceFields.put("lane", matrix.isApproved() ? "FORMAL_G4" : "QUICK");
        evidenceFields.put("matrix.version", matrix.version());
        evidenceFields.put("warmup.duration.nanos", Long.toString(
                matrix.isApproved() ? GaFormalPerformanceContract.WARMUP.toNanos() : 0L));
        evidenceFields.put("measurement.duration.nanos", Long.toString(
                matrix.runDuration().toNanos()));
        evidenceFields.put("lifecycle.cycles", Integer.toString(matrix.lifecycleSamples()));
        evidenceFields.put("management.warmup.duration.nanos", Long.toString(
                GaFormalPerformanceContract.MANAGEMENT_WARMUP.toNanos()));
        evidenceFields.put("management.measurement.duration.nanos", Long.toString(
                GaFormalPerformanceContract.MANAGEMENT_MEASUREMENT.toNanos()));
        evidenceFields.put("management.status.interval.nanos", Long.toString(
                GaFormalPerformanceContract.MANAGEMENT_INTERVAL.toNanos()));
        evidenceFields.put("management.status.requestCount", Integer.toString(
                GaFormalPerformanceContract.MANAGEMENT_STATUS_REQUESTS));
        evidenceFields.put("campaign.requiredRunCount", Integer.toString(matrix.runCount()));
        evidenceFields.put("campaign.runCount", Integer.toString(runs.size()));
        evidenceFields.put("candidate.tag", candidate.tag());
        evidenceFields.put("candidate.tagObjectSha", candidate.tagObjectSha());
        evidenceFields.put("candidate.productionSha", candidate.productionSha());
        evidenceFields.put("candidate.applicationJarSha256", candidate.applicationJarSha256());
        evidenceFields.put("qualification.controllerSha", context.controllerGitSha());
        final String qualificationDigest = context.qualificationJarSha256();
        evidenceFields.put("qualification.jarSha256", qualificationDigest == null
                || qualificationDigest.isBlank() ? "UNAVAILABLE" : qualificationDigest);
        evidenceFields.put("protocol", GaPerformanceMatrix.APPROVED_PROTOCOL);
        evidenceFields.put("protocolV2.window", Integer.toString(
                GaPerformanceMatrix.APPROVED_PROTOCOL_V2_WINDOW));
        evidenceFields.put("walMode", GaPerformanceMatrix.APPROVED_WAL_MODE);
        evidenceFields.put("loadModel", GaPerformanceMatrix.APPROVED_LOAD_MODEL);
        evidenceFields.put("profile", matrix.profile());
        evidenceFields.put("seed", Long.toString(matrix.seed()));
        evidenceFields.put("campaign.startedAtUtc", started.toString());
        evidenceFields.put("campaign.completedAtUtc", completed.toString());
        for (int index = 0; index < runs.size(); index++) {
            final PublishedRun run = runs.get(index);
            final String prefix = String.format("run.%04d.", index + 1);
            evidenceFields.put(prefix + "id", run.runId());
            evidenceFields.put(prefix + "outcome", run.outcome());
            evidenceFields.put(prefix + "physicalExecutionId", run.physicalExecutionId());
            evidenceFields.put(prefix + "configurationIdentitySha256",
                    run.configurationIdentitySha256());
            evidenceFields.put(prefix + "comparabilityIdentitySha256",
                    run.comparabilityIdentitySha256());
            evidenceFields.put(prefix + "manifestPath", relative(root, run.manifestPath()));
            evidenceFields.put(prefix + "manifestSha256", run.manifestSha256());
        }
        addInventoryBinding(evidenceFields, "lifecycle", root.resolve("lifecycle")
                .resolve("SHA256SUMS"));
        addInventoryBinding(evidenceFields, "management", root.resolve("management")
                .resolve("SHA256SUMS"));
        QualificationEvidencePublication.text(evidence, keyValueText(evidenceFields));
        publishArtifactSidecar(evidence);
        final Map<String, String> fields = new TreeMap<>();
        final String configuration = runs.get(0).configurationIdentitySha256();
        final String comparability = runs.get(0).comparabilityIdentitySha256();
        final boolean configurationEqual = runs.stream().allMatch(run ->
                configuration.equals(run.configurationIdentitySha256()));
        final boolean comparabilityEqual = runs.stream().allMatch(run ->
                comparability.equals(run.comparabilityIdentitySha256()));
        final Path lifecycleInventory = root.resolve("lifecycle").resolve("SHA256SUMS");
        final Path managementInventory = root.resolve("management").resolve("SHA256SUMS");
        final boolean evidenceComplete = Files.isRegularFile(lifecycleInventory)
                && Files.isRegularFile(managementInventory);
        final boolean allRunResultsPass = runs.size() == matrix.runCount()
                && runs.stream().allMatch(PublishedRun::passed);
        final boolean allCriteriaPass = criteria.stream()
                .allMatch(GaPerformanceEvaluator.Criterion::passed);
        final boolean campaignPassed = passed && allRunResultsPass && allCriteriaPass
                && configurationEqual && comparabilityEqual && evidenceComplete;
        fields.put("candidate.applicationJarSha256", candidate.applicationJarSha256());
        fields.put("candidate.productionSha", candidate.productionSha());
        fields.put("candidate.tag", candidate.tag());
        fields.put("candidate.tagObjectSha", candidate.tagObjectSha());
        fields.put("campaign.completedAtUtc", completed.toString());
        fields.put("campaign.configurationIdentityEqual", Boolean.toString(configurationEqual));
        fields.put("campaign.id", campaignId);
        fields.put("campaign.outcome", campaignPassed ? "PASS" : "FAIL");
        fields.put("campaign.requiredRunCount", Integer.toString(matrix.runCount()));
        fields.put("campaign.startedAtUtc", started.toString());
        fields.put("campaign.validRunCount", Long.toString(runs.stream()
                .filter(PublishedRun::passed).count()));
        fields.put("comparability.policy", "exact-runtime");
        fields.put("controller.gitSha", context.controllerGitSha());
        fields.put("gate.id", "G4");
        fields.put("gate.version", GaFormalPerformanceContract.CAMPAIGN);
        fields.put("run.count", Integer.toString(runs.size()));
        fields.put("schema.version", GaEvidenceCodec.Schema.CAMPAIGN.version());
        for (int index = 0; index < runs.size(); index++) {
            final PublishedRun run = runs.get(index);
            final String prefix = String.format("run.%04d", index + 1);
            fields.put(prefix + ".comparabilityIdentitySha256",
                    run.comparabilityIdentitySha256());
            fields.put(prefix + ".configurationIdentitySha256",
                    run.configurationIdentitySha256());
            fields.put(prefix + ".id", run.runId());
            fields.put(prefix + ".physicalExecutionId", run.physicalExecutionId());
            fields.put(prefix + ".manifestPath", relative(root, run.manifestPath()));
            fields.put(prefix + ".manifestSha256", run.manifestSha256());
            fields.put(prefix + ".outcome", run.outcome());
        }
        final Path manifest = root.resolve("g4-campaign-manifest-v1.txt");
        GaEvidenceStore.publish(manifest, GaEvidenceCodec.Schema.CAMPAIGN, fields);
        publishArtifactSidecar(manifest);
        return manifest;
    }

    private static void addInventoryBinding(
            final Map<String, String> fields,
            final String name,
            final Path inventory) throws IOException {
        if (Files.isRegularFile(inventory)) {
            fields.put("campaign." + name + ".inventory.path", relative(
                    inventory.getParent().getParent(), inventory));
            fields.put("campaign." + name + ".inventory.sha256",
                    QualificationArtifactHasher.sha256(inventory));
            fields.put("campaign." + name + ".inventory.size",
                    Long.toString(Files.size(inventory)));
        }
    }

    /** Publishes a gate result whose manifests reference the campaign and each physical run. */
    public static Path publishCampaignGate(
            final Path campaignRoot,
            final GaCorrectnessCanonicalContext context,
            final List<PublishedRun> runs,
            final Path campaignManifest,
            final List<? extends GaPerformanceEvaluator.Criterion> criteria,
            final Instant started,
            final Instant completed,
            final String failureCode,
            final String limitationText,
            final boolean passed) throws IOException {
        Objects.requireNonNull(campaignRoot, "campaignRoot");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(runs, "runs");
        Objects.requireNonNull(campaignManifest, "campaignManifest");
        Objects.requireNonNull(criteria, "criteria");
        final Path root = campaignRoot.toAbsolutePath().normalize();
        final Map<String, String> campaignFields = GaEvidenceStore.read(
                campaignManifest.toAbsolutePath().normalize(), GaEvidenceCodec.Schema.CAMPAIGN);
        final String campaignId = campaignFields.get("campaign.id");
        requireUuid(campaignId, "campaignId");
        final Map<String, String> fields = new TreeMap<>();
        final GaCandidateVerifier.Verified candidate = context.candidate();
        final PublishedRun first = runs.get(0);
        fields.put("blocker.classification", passed ? "NONE" : failureCode);
        fields.put("candidate.applicationJarSha256", candidate.applicationJarSha256());
        fields.put("candidate.productionSha", candidate.productionSha());
        fields.put("candidate.productionTreeSha256", candidate.productionTreeSha256());
        fields.put("candidate.tag", candidate.tag());
        fields.put("candidate.tagObjectSha", candidate.tagObjectSha());
        fields.put("campaign.id", campaignId);
        fields.put("campaign.gate", "G4");
        fields.put("comparability.identitySha256", first.comparabilityIdentitySha256());
        fields.put("configuration.identitySha256", first.configurationIdentitySha256());
        fields.put("controller.gitSha", context.controllerGitSha());
        fields.put("criterion.count", Integer.toString(criteria.size()));
        fields.put("evidence.completedAtUtc", completed.toString());
        fields.put("evidence.outcome", passed ? "PASS" : "FAIL");
        fields.put("evidence.startedAtUtc", started.toString());
        fields.put("gate.id", "G4");
        fields.put("gate.version", GaFormalPerformanceContract.CAMPAIGN);
        final int limitationCount = passed ? 0 : 1;
        fields.put("limitation.count", Integer.toString(limitationCount));
        if (!passed) {
            fields.put("limitation.0001.code", failureCode);
            fields.put("limitation.0001.statementDigestSha256", digest(limitationText));
        }
        final List<Path> boundArtifacts;
        try (var paths = Files.walk(root)) {
            boundArtifacts = paths.filter(Files::isRegularFile)
                    .filter(path -> !path.equals(root.resolve("g4-gate-result-v1.txt")))
                    .filter(path -> !path.equals(root.resolve("g4-gate-result-v1.txt.sha256")))
                    .sorted(Comparator.comparing(path -> relativeUnchecked(root, path)))
                    .toList();
        }
        fields.put("manifest.count", Integer.toString(boundArtifacts.size()));
        for (int index = 0; index < boundArtifacts.size(); index++) {
            final String prefix = String.format("manifest.%04d", index + 1);
            final Path artifact = boundArtifacts.get(index);
            fields.put(prefix + ".path", relative(root, artifact));
            fields.put(prefix + ".sha256", QualificationArtifactHasher.sha256(artifact));
        }
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
        final Path gate = root.resolve("g4-gate-result-v1.txt");
        GaEvidenceStore.publish(gate, GaEvidenceCodec.Schema.GATE, fields);
        publishArtifactSidecar(gate);
        return gate;
    }

    private static Path publishGate(
            final Path root,
            final PublishedRun run,
            final RunInput input) throws IOException {
        final Map<String, String> fields = new TreeMap<>();
        final GaCandidateVerifier.Verified candidate = input.context().candidate();
        fields.put("blocker.classification", blockerFor(input.outcome(), input.failureCode()));
        fields.put("candidate.applicationJarSha256", candidate.applicationJarSha256());
        fields.put("candidate.productionSha", candidate.productionSha());
        fields.put("candidate.productionTreeSha256", candidate.productionTreeSha256());
        fields.put("candidate.tag", candidate.tag());
        fields.put("candidate.tagObjectSha", candidate.tagObjectSha());
        fields.put("comparability.identitySha256", run.comparabilityIdentitySha256());
        fields.put("configuration.identitySha256", run.configurationIdentitySha256());
        fields.put("controller.gitSha", input.context().controllerGitSha());
        fields.put("criterion.count", Integer.toString(input.criteria().size()));
        fields.put("evidence.completedAtUtc", input.completed().toString());
        fields.put("evidence.outcome", input.outcome());
        fields.put("evidence.startedAtUtc", input.started().toString());
        fields.put("gate.id", "G4");
        fields.put("gate.version", GaFormalPerformanceContract.CAMPAIGN);
        final boolean passed = "PASS".equals(input.outcome());
        fields.put("limitation.count", passed ? "0" : "1");
        if (!passed) {
            fields.put("limitation.0001.code", input.failureCode());
            fields.put("limitation.0001.statementDigestSha256", digest(input.rawEvidence()));
        }
        fields.put("manifest.count", "1");
        fields.put("manifest.0001.path", relative(root, run.manifestPath()));
        fields.put("manifest.0001.sha256", run.manifestSha256());
        fields.put("schema.version", GaEvidenceCodec.Schema.GATE.version());
        for (int index = 0; index < input.criteria().size(); index++) {
            final GaPerformanceEvaluator.Criterion criterion = input.criteria().get(index);
            final String prefix = String.format("criterion.%04d", index + 1);
            fields.put(prefix + ".id", criterion.id());
            fields.put(prefix + ".actual", criterion.actual());
            fields.put(prefix + ".operator", criterion.operator());
            fields.put(prefix + ".required", criterion.required());
            fields.put(prefix + ".result", criterion.passed() ? "PASS" : "FAIL");
        }
        final Path target = root.resolve("g4-gate-result-v1.txt");
        GaEvidenceStore.publish(target, GaEvidenceCodec.Schema.GATE, fields);
        publishArtifactSidecar(target);
        return target;
    }

    /** Publishes a basename sidecar for one formal payload artifact. */
    public static void publishArtifactSidecar(final Path artifact) throws IOException {
        final Path sidecar = artifact.resolveSibling(artifact.getFileName() + ".sha256");
        final Map<String, Path> expected = Map.of(artifact.getFileName().toString(),
                artifact);
        if (Files.exists(sidecar)) {
            final Map<String, String> actual = GaEvidenceStore.readArtifactSidecar(sidecar);
            final String expectedDigest = QualificationArtifactHasher.sha256(artifact);
            if (!expectedDigest.equals(actual.get(artifact.getFileName().toString()))
                    || actual.size() != 1) {
                throw new IOException("artifact sidecar does not match payload: " + artifact);
            }
            return;
        }
        GaEvidenceStore.publishArtifactSidecar(sidecar, expected);
    }

    private static List<Path> payloadArtifacts(final Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().equals("SHA256SUMS"))
                    .filter(path -> !path.getFileName().toString().equals("ga-run-manifest-v1.txt"))
                    .filter(path -> !path.getFileName().toString().equals("g4-gate-result-v1.txt"))
                    .filter(path -> !path.getFileName().toString().toLowerCase().endsWith(".sha256"))
                    .sorted(Comparator.comparing(path -> relativeUnchecked(root, path)))
                    .toList();
        }
    }

    private static void publishInventory(
            final Path inventory, final List<Path> artifacts, final Path root) throws IOException {
        final StringBuilder text = new StringBuilder();
        for (Path artifact : artifacts) {
            text.append(QualificationArtifactHasher.sha256(artifact)).append("  ")
                    .append(relativeUnchecked(root, artifact)).append('\n');
        }
        QualificationEvidencePublication.text(inventory, text.toString());
    }

    private static String samplesText(final List<LatencySample> samples) {
        final StringBuilder text = new StringBuilder(
                "requestId,commandSequence,offeredNanos,completedNanos,capacityReleaseNanos,latencyNanos\n");
        for (LatencySample sample : samples) {
            text.append(sample.requestId()).append(',').append(sample.commandSequence()).append(',')
                    .append(sample.offeredNanos()).append(',').append(sample.completedNanos())
                    .append(',').append(sample.capacityReleaseNanos()).append(',')
                    .append(sample.latencyNanos()).append('\n');
        }
        return text.toString();
    }

    private static String capacityText(final RunInput input) {
        final GaPerformanceObservation observation = input.observation();
        final StringBuilder text = new StringBuilder()
                .append("schema=ga-g4-capacity-v2\n")
                .append("offeredCommands=").append(input.offeredCommands()).append('\n')
                .append("acceptedCommands=").append(observation.acceptedCommands()).append('\n')
                .append("responseCount=").append(observation.responseCount()).append('\n')
                .append("maxInFlight=").append(input.capacityMetrics().maximumObservedInFlight())
                .append('\n')
                .append("maxPendingWire=")
                .append(input.capacityMetrics().maximumObservedPendingWire()).append('\n')
                .append("maxCompletedUndrained=")
                .append(input.capacityMetrics().maximumObservedCompletedUndrained()).append('\n')
                .append("readerWakeCount=").append(input.capacityMetrics().readerWakeCount())
                .append('\n')
                .append("capacityReleaseCount=")
                .append(input.capacityMetrics().capacityReleaseCount()).append('\n')
                .append("releaseDelayCount=")
                .append(input.capacityMetrics().releaseDelayNanos().length).append('\n');
        final List<CapacityReleaseSample> releaseSamples =
                input.capacityMetrics().releaseSamples();
        final long releaseSampleCount = releaseSamples.isEmpty()
                ? input.capacityMetrics().releaseDelayNanos().length : releaseSamples.size();
        text.append("releaseSampleCount=").append(releaseSampleCount).append('\n');
        long index = 1L;
        for (CapacityReleaseSample sample : releaseSamples) {
            final String prefix = String.format("releaseSample.%06d.", index++);
            text.append(prefix).append("requestId=").append(sample.requestId()).append('\n')
                    .append(prefix).append("commandSequence=")
                    .append(sample.commandSequence()).append('\n')
                    .append(prefix).append("offeredNanos=").append(sample.offeredNanos())
                    .append('\n')
                    .append(prefix).append("responseCompleteNanos=")
                    .append(sample.responseCompleteNanos()).append('\n')
                    .append(prefix).append("capacityReleaseNanos=")
                    .append(sample.capacityReleaseNanos()).append('\n')
                    .append(prefix).append("schedulerConsumedNanos=")
                    .append(sample.schedulerConsumedNanos()).append('\n')
                    .append(prefix).append("releaseDelayNanos=")
                    .append(sample.releaseDelayNanos()).append('\n');
        }
        if (releaseSamples.isEmpty()) {
            index = 1L;
            for (long delay : input.capacityMetrics().releaseDelayNanos()) {
                text.append("releaseDelay.").append(index++).append(".nanos=")
                        .append(delay).append('\n');
            }
        }
        return text.toString();
    }

    private static String keyValueText(final Map<String, String> values) {
        final StringBuilder text = new StringBuilder();
        values.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                text.append(entry.getKey()).append('=').append(entry.getValue()).append('\n'));
        return text.toString();
    }

    private static void putCapacity(
            final Map<String, String> fields, final CapacityMetrics metrics) {
        final long[] delays = metrics.releaseDelayNanos();
        final com.ultralatency.matching.qualification.QualificationPercentiles.Summary summary =
                com.ultralatency.matching.qualification.QualificationPercentiles.summarize(delays);
        fields.put("evidence.capacity.maxCompletedUndrained",
                Integer.toString(metrics.maximumObservedCompletedUndrained()));
        fields.put("evidence.capacity.maxInFlight",
                Integer.toString(metrics.maximumObservedInFlight()));
        fields.put("evidence.capacity.maxPendingWire",
                Integer.toString(metrics.maximumObservedPendingWire()));
        fields.put("evidence.capacity.readerWakeCount", Long.toString(metrics.readerWakeCount()));
        fields.put("evidence.capacity.releaseCount",
                Long.toString(metrics.capacityReleaseCount()));
        fields.put("evidence.capacity.releaseDelayP50Nanos",
                Long.toString(summary.p50Nanos()));
        fields.put("evidence.capacity.releaseDelayP90Nanos",
                Long.toString(nearestRank(delays, 0.90d)));
        fields.put("evidence.capacity.releaseDelayP99Nanos",
                Long.toString(summary.p99Nanos()));
        fields.put("evidence.capacity.releaseDelayMaxNanos",
                Long.toString(summary.maxNanos()));
    }

    private static Map<String, String> runtimeFields(final Map<String, String> observed) {
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
        runtime.put("runtime.javaVmVersion", observed.getOrDefault("java.vm.version", "UNAVAILABLE"));
        runtime.put("runtime.logicalProcessors", observed.getOrDefault("logical.processors", "0"));
        runtime.put("runtime.nettyAllocator", observed.getOrDefault(
                "netty.allocator", "io.netty.buffer.PooledByteBufAllocator"));
        runtime.put("runtime.osArch", observed.getOrDefault("os.arch", "UNAVAILABLE"));
        runtime.put("runtime.osName", observed.getOrDefault("os.name", "UNAVAILABLE"));
        runtime.put("runtime.osVersion", observed.getOrDefault("os.version", "UNAVAILABLE"));
        runtime.put("runtime.storageIdentity", observed.getOrDefault(
                "storage.identity", "UNAVAILABLE"));
        return Map.copyOf(runtime);
    }

    private static String requiredQualificationJar(final GaCorrectnessCanonicalContext context)
            throws IOException {
        final String digest = context.qualificationJarSha256();
        if (digest == null || digest.isBlank()) {
            throw new IOException("formal G4 requires a packaged qualification JAR identity");
        }
        return digest;
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

    private static String blockerFor(final String outcome, final String failureCode) {
        return "PASS".equals(outcome) ? "NONE" : failureCode;
    }

    private static String digest(final String value) {
        return QualificationIdentity.digest(Map.of("value", value));
    }

    private static void requireOutcome(final String value) {
        if (!"PASS".equals(value) && !"FAIL".equals(value) && !"ABORTED".equals(value)) {
            throw new IllegalArgumentException("outcome must be PASS, FAIL or ABORTED");
        }
    }

    private static void requireFailureCode(final String value, final String outcome) {
        if (value == null || value.isBlank()
                || ("PASS".equals(outcome) && !"NONE".equals(value))
                || (!"PASS".equals(outcome) && "NONE".equals(value))) {
            throw new IllegalArgumentException("failureCode does not match formal outcome");
        }
    }

    private static void requireUuid(final String value, final String name) {
        try {
            if (value == null || !UUID.fromString(value).toString().equals(value)) {
                throw new IllegalArgumentException(name + " must be a lowercase UUID");
            }
        } catch (final IllegalArgumentException exception) {
            throw new IllegalArgumentException(name + " must be a lowercase UUID", exception);
        }
    }

    private static void requireDigest(final String value, final String name) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must be lowercase SHA-256");
        }
    }

    private static void requireText(final String value, final String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static long[] copyNonNegative(final long[] values, final String name) {
        Objects.requireNonNull(values, name);
        final long[] copy = values.clone();
        for (long value : copy) {
            if (value < 0L) {
                throw new IllegalArgumentException(name + " must contain non-negative values");
            }
        }
        return copy;
    }

    private static long nearestRank(final long[] values, final double percentile) {
        if (values.length == 0) {
            return 0L;
        }
        final long[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        final int rank = Math.max(1, (int) Math.ceil(percentile * sorted.length));
        return sorted[rank - 1];
    }
}
