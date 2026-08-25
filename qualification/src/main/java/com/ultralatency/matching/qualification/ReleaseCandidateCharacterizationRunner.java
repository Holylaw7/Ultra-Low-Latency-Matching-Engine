package com.ultralatency.matching.qualification;

import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.persistence.snapshot.OfflineSnapshotGenerator;
import com.ultralatency.matching.persistence.snapshot.RecoveryLease;
import com.ultralatency.matching.persistence.snapshot.SnapshotStore;
import com.ultralatency.matching.persistence.wal.CommandWalWriter;
import com.ultralatency.matching.persistence.wal.WalConfiguration;
import com.ultralatency.matching.recovery.online.RecoveryMode;
import com.ultralatency.matching.recovery.online.RecoveryPlanner;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Qualification-only Phase 10 characterization through the packaged public runtime.
 *
 * <p>This runner is deliberately separate from {@link ReleaseCandidateAssembledFullRunner}:
 * existing Full Run A/B evidence is immutable, while this unit produces bounded lifecycle,
 * latency and management-overhead evidence.</p>
 */
public final class ReleaseCandidateCharacterizationRunner {

    private static final String CHARACTERIZATION_VERSION = "phase10-rc-characterization-v1";
    private static final Duration STATUS_TIMEOUT = Duration.ofSeconds(5);

    /** Runs the approved characterization unit and atomically publishes its evidence. */
    public ReleaseCandidateCharacterizationResult run(
            final ReleaseCandidateCharacterizationConfiguration configuration) throws IOException {
        Objects.requireNonNull(configuration, "configuration");
        if (configuration.packagedArtifact() != null) {
            requireArtifact(configuration.packagedArtifact());
        }
        Files.createDirectories(configuration.outputDirectory());
        final Path root = Files.createDirectory(configuration.outputDirectory().resolve(
                "rc-characterization-" + UUID.randomUUID()));
        final List<ReleaseCandidateCharacterizationResult.LifecycleSample> lifecycle =
                new ArrayList<>();
        for (int index = 1; index <= configuration.emptyWalSamples(); index++) {
            lifecycle.add(runLifecycle(configuration, root, "EMPTY_PURE_WAL", index, false));
        }
        for (int index = 1; index <= configuration.snapshotTailSamples(); index++) {
            lifecycle.add(runLifecycle(configuration, root, "SNAPSHOT_THEN_WAL", index, true));
        }
        final ReleaseCandidateCharacterizationResult.TrialResult idle = runTrial(
                configuration, root, "management-idle", false);
        final ReleaseCandidateCharacterizationResult.TrialResult status = runTrial(
                configuration, root, "status-1hz", true);
        final Path lifecycleSamples = root.resolve("lifecycle-samples.csv");
        publishLifecycleSamples(lifecycleSamples, lifecycle);
        final Path summary = root.resolve("characterization-summary-v1.txt");
        final Path manifest = root.resolve("characterization-manifest-v1.txt");
        final Path hashes = root.resolve("artifact-hashes-v1.txt");
        final Map<String, String> provenance = new LinkedHashMap<>(
                QualificationIdentity.runtimeProvenance(root));
        provenance.put("runtime.recordOnly.cpuModel",
                System.getenv().getOrDefault("PROCESSOR_IDENTIFIER", "UNAVAILABLE"));
        provenance.put("runtime.recordOnly.storageType",
                Files.getFileStore(root).type());
        provenance.put("runtime.recordOnly.nettyAllocator", "default-configured");
        final Path packagedArtifactCopy = configuration.packagedArtifact() == null
                ? null : root.resolve("application-artifact.jar");
        if (packagedArtifactCopy != null) {
            Files.copy(configuration.packagedArtifact(), packagedArtifactCopy);
        }
        final String artifactSha = packagedArtifactCopy == null
                ? "classpath-test-runtime"
                : QualificationArtifactHasher.sha256(packagedArtifactCopy);
        final String configurationSha = configurationIdentity(configuration, artifactSha);
        final String comparabilitySha = QualificationIdentity.digest(
                QualificationIdentity.comparabilityFields(provenance));
        final boolean success = lifecycle.stream().allMatch(
                ReleaseCandidateCharacterizationResult.LifecycleSample::passed)
                && idle.throughputPassed() && idle.responsePassed()
                && status.throughputPassed() && status.responsePassed();
        final String summaryText = summaryText(
                configuration, lifecycle, idle, status, provenance, artifactSha,
                configurationSha, comparabilitySha, success);
        QualificationEvidencePublication.text(summary, summaryText);
        final String manifestText = manifestText(
                configuration, lifecycle, idle, status, provenance, artifactSha,
                configurationSha, comparabilitySha, success,
                QualificationArtifactHasher.sha256(summary));
        QualificationEvidencePublication.text(manifest, manifestText);
        final Map<String, Path> artifacts = collectArtifacts(root, hashes);
        QualificationEvidencePublication.text(hashes, hashesText(artifacts));
        return new ReleaseCandidateCharacterizationResult(
                success,
                root,
                summary,
                hashes,
                QualificationArtifactHasher.sha256(summary),
                lifecycle,
                idle,
                status);
    }

    private static ReleaseCandidateCharacterizationResult.LifecycleSample runLifecycle(
            final ReleaseCandidateCharacterizationConfiguration configuration,
            final Path root,
            final String scenario,
            final int sampleNumber,
            final boolean snapshotTail) throws IOException {
        final Path directory = root.resolve(scenario.toLowerCase() + "-"
                + String.format("%02d", sampleNumber));
        Files.createDirectories(directory);
        final Path walDirectory = directory.resolve("wal");
        final Path snapshotDirectory = directory.resolve("snapshots");
        Files.createDirectories(walDirectory);
        Files.createDirectories(snapshotDirectory);
        if (snapshotTail) {
            prepareSnapshotTail(walDirectory, snapshotDirectory);
        }
        final Path config = writeConfiguration(directory, walDirectory, snapshotDirectory,
                snapshotTail ? RecoveryMode.SNAPSHOT_THEN_WAL : RecoveryMode.PURE_WAL);
        final Path evidence = directory.resolve("process-evidence");
        final QualificationConfiguration workload =
                QualificationFullConfiguration.memorySteadyStateTest(directory)
                        .workloadConfiguration();
        long startupNanos = 0L;
        long responseNanos = 0L;
        long shutdownNanos = 0L;
        boolean ready = false;
        boolean responsePassed = false;
        boolean recovery = false;
        boolean lease = false;
        boolean temporaryFiles = false;
        try {
            final long startupStart = System.nanoTime();
            try (ReleaseCandidateQualificationProcess child =
                    ReleaseCandidateQualificationProcess.start(
                            configuration.packagedArtifact(), config, evidence,
                            configuration.startupTimeout(), true)) {
                ReleaseCandidateManagementClient.requireReady(
                        ReleaseCandidateManagementClient.request(
                                child.managementPort(), "READY", STATUS_TIMEOUT));
                startupNanos = System.nanoTime() - startupStart;
                ready = true;
                final long workloadIndex = snapshotTail ? 2L : 0L;
                final EngineCommand command = QualificationWorkloadV1.commandAtForRun(
                        workload, workloadIndex);
                final long responseStart = System.nanoTime();
                try (ProtocolV1QualificationClient client = new ProtocolV1QualificationClient(
                        new java.net.InetSocketAddress("127.0.0.1", child.protocolPort()),
                        configuration.commandTimeout())) {
                    client.exchange(command, 1L);
                }
                responseNanos = System.nanoTime() - responseStart;
                responsePassed = true;
                final long shutdownStart = System.nanoTime();
                final int exitCode = child.gracefulShutdown(configuration.processTimeout());
                shutdownNanos = System.nanoTime() - shutdownStart;
                responsePassed = responsePassed && exitCode == 0;
            }
            recovery = recoveryConverged(walDirectory, snapshotDirectory,
                    snapshotTail ? RecoveryMode.SNAPSHOT_THEN_WAL : RecoveryMode.PURE_WAL);
            lease = leaseReacquired(walDirectory);
            temporaryFiles = noTemporaryFiles(directory);
        } catch (final IOException | RuntimeException failure) {
            publishFailure(directory, failure);
        }
        if (Files.isRegularFile(evidence.resolve("qualification.jfr"))) {
            QualificationJfrAllocationEvidence.write(
                    evidence.resolve("qualification.jfr"),
                    evidence.resolve("allocation-summary-v1.txt"));
        }
        final boolean passed = ready && responsePassed && recovery && lease && temporaryFiles;
        return new ReleaseCandidateCharacterizationResult.LifecycleSample(
                scenario, sampleNumber, startupNanos, shutdownNanos, responseNanos,
                ready, responsePassed, recovery, lease, temporaryFiles, passed,
                root.relativize(directory).toString().replace('\\', '/'));
    }

    private static ReleaseCandidateCharacterizationResult.TrialResult runTrial(
            final ReleaseCandidateCharacterizationConfiguration configuration,
            final Path root,
            final String name,
            final boolean pollManagement) throws IOException {
        final Path directory = root.resolve(name);
        Files.createDirectories(directory);
        final Path walDirectory = directory.resolve("wal");
        final Path snapshotDirectory = directory.resolve("snapshots");
        Files.createDirectories(walDirectory);
        Files.createDirectories(snapshotDirectory);
        final Path config = writeConfiguration(directory, walDirectory, snapshotDirectory,
                RecoveryMode.PURE_WAL);
        final Path evidence = directory.resolve("process-evidence");
        final Path responseSamples = directory.resolve("response-samples.csv");
        final Path managementSamples = directory.resolve("management-samples.csv");
        final QualificationConfiguration workload =
                QualificationFullConfiguration.memorySteadyStateTest(directory)
                        .workloadConfiguration();
        final QualificationLatencySamples response = new QualificationLatencySamples();
        final QualificationLatencySamples management = new QualificationLatencySamples();
        final StringBuilder managementRaw = new StringBuilder(
                "sampleIndex,elapsedNanos,latencyNanos\n");
        long accepted = 0L;
        long managementRequests = 0L;
        int exitCode = -1;
        long measurementStartNanos = 0L;
        long measurementEndNanos = 0L;
        try (ReleaseCandidateQualificationProcess child =
                    ReleaseCandidateQualificationProcess.start(
                            configuration.packagedArtifact(), config, evidence,
                            configuration.startupTimeout(), true)) {
            ReleaseCandidateManagementClient.requireReady(
                    ReleaseCandidateManagementClient.request(
                            child.managementPort(), "READY", STATUS_TIMEOUT));
            try (ProtocolV1QualificationClient client = new ProtocolV1QualificationClient(
                    new java.net.InetSocketAddress("127.0.0.1", child.protocolPort()),
                    configuration.commandTimeout())) {
                // Exclude one-time Protocol connection setup from the measured interval.
                measurementStartNanos = System.nanoTime();
                final Instant deadline = Instant.now().plus(configuration.pairedTrialDuration());
                Instant nextManagement = Instant.now();
                while (Instant.now().isBefore(deadline)) {
                    final EngineCommand command = QualificationWorkloadV1.commandAtForRun(
                            workload, accepted);
                    final long responseStart = System.nanoTime();
                    client.exchange(command, accepted + 1L);
                    response.add(System.nanoTime() - responseStart);
                    accepted++;
                    if (pollManagement && !Instant.now().isBefore(nextManagement)) {
                        final long managementStart = System.nanoTime();
                        ReleaseCandidateManagementClient.request(
                                child.managementPort(), "STATUS", STATUS_TIMEOUT);
                        final long observedNanos = System.nanoTime();
                        management.add(observedNanos - managementStart);
                        managementRaw.append(managementRequests)
                                .append(',').append(observedNanos - measurementStartNanos)
                                .append(',').append(observedNanos - managementStart).append('\n');
                        managementRequests++;
                        nextManagement = nextManagement.plus(
                                configuration.managementInterval());
                    }
                }
                measurementEndNanos = System.nanoTime();
            }
            exitCode = child.gracefulShutdown(configuration.processTimeout());
        } catch (final IOException | RuntimeException failure) {
            publishFailure(directory, failure);
        }
        QualificationEvidencePublication.samples(responseSamples, response.toArray());
        QualificationEvidencePublication.text(managementSamples, managementRaw.toString());
        final long elapsedMillis = measurementStartNanos == 0L
                ? 0L : Duration.ofNanos(Math.max(0L, measurementEndNanos - measurementStartNanos))
                        .toMillis();
        final QualificationPercentiles.Summary responseSummary = response.summarize();
        final QualificationPercentiles.Summary managementSummary = management.summarize();
        final boolean passed = accepted > 0 && exitCode == 0 && response.size() == accepted;
        final Path jfr = evidence.resolve("qualification.jfr");
        final Path resources = evidence.resolve("resource-evidence.csv");
        if (Files.isRegularFile(jfr)) {
            QualificationJfrAllocationEvidence.write(
                    jfr, evidence.resolve("allocation-summary-v1.txt"));
        }
        final String configurationSha = QualificationArtifactHasher.sha256(config);
        final String trialText = trialText(
                name, elapsedMillis, accepted, managementRequests, passed,
                responseSummary, managementSummary, configurationSha);
        final Path trialSummary = directory.resolve("trial-summary-v1.txt");
        QualificationEvidencePublication.text(trialSummary, trialText);
        return new ReleaseCandidateCharacterizationResult.TrialResult(
                name, elapsedMillis, accepted, managementRequests, passed,
                response.size() == accepted, responseSummary, managementSummary,
                rootRelative(root, responseSamples), rootRelative(root, managementSamples),
                rootRelative(root, jfr), rootRelative(root, resources), configurationSha,
                QualificationArtifactHasher.sha256(trialSummary));
    }

    private static void publishLifecycleSamples(
            final Path target,
            final List<ReleaseCandidateCharacterizationResult.LifecycleSample> samples)
            throws IOException {
        final StringBuilder text = new StringBuilder(
                "scenario,sampleNumber,startupToReadyNanos,shutdownNanos,responseNanos,"
                        + "ready,responsePassed,recoveryConverged,leaseReacquired,"
                        + "temporaryFilesClear,passed,artifactDirectory\n");
        for (final ReleaseCandidateCharacterizationResult.LifecycleSample sample : samples) {
            text.append(sample.scenario()).append(',').append(sample.sampleNumber()).append(',')
                    .append(sample.startupToReadyNanos()).append(',')
                    .append(sample.shutdownNanos()).append(',').append(sample.responseNanos())
                    .append(',').append(sample.ready()).append(',').append(sample.responsePassed())
                    .append(',').append(sample.recoveryConverged()).append(',')
                    .append(sample.leaseReacquired()).append(',')
                    .append(sample.temporaryFilesClear()).append(',').append(sample.passed())
                    .append(',').append(sample.artifactDirectory()).append('\n');
        }
        QualificationEvidencePublication.text(target, text.toString());
    }

    private static String summaryText(
            final ReleaseCandidateCharacterizationConfiguration configuration,
            final List<ReleaseCandidateCharacterizationResult.LifecycleSample> lifecycle,
            final ReleaseCandidateCharacterizationResult.TrialResult idle,
            final ReleaseCandidateCharacterizationResult.TrialResult status,
            final Map<String, String> provenance,
            final String artifactSha,
            final String configurationSha,
            final String comparabilitySha,
            final boolean success) {
        final StringBuilder text = new StringBuilder()
                .append("schemaVersion=").append(CHARACTERIZATION_VERSION).append('\n')
                .append("result=").append(success ? "PASS" : "FAIL").append('\n')
                .append("emptyWalSamples=").append(configuration.emptyWalSamples()).append('\n')
                .append("snapshotTailSamples=").append(configuration.snapshotTailSamples()).append('\n')
                .append("lifecycleSamplesPassed=")
                .append(lifecycle.stream().filter(
                        ReleaseCandidateCharacterizationResult.LifecycleSample::passed).count())
                .append('\n')
                .append("configurationIdentitySha256=").append(configurationSha).append('\n')
                .append("comparabilityIdentitySha256=").append(comparabilitySha).append('\n')
                .append("applicationJarSha256=").append(artifactSha).append('\n')
                .append("executionModel=one-packaged-child-process-per-trial;single-protocol-client-thread;single-sequential-management-thread\n")
                .append("warmup=none\n")
                .append("forks=not-applicable;one-child-process-per-trial\n")
                .append("clientThreads=1\n")
                .append("managementThreads=1-sequential\n")
                .append("measurementBoundary=after READY and Protocol connection established, before command loop and graceful shutdown\n")
                .append("allocationEvidence=bounded-JFR-object-allocation-sampling\n")
                .append("allocationEvidenceArtifact=allocation-summary-v1.txt-per-process\n");
        appendLifecycleDistributions(text, lifecycle);
        appendTrial(text, "managementIdle", idle);
        appendTrial(text, "statusOneHz", status);
        appendComparison(text, idle, status);
        text.append("claims.qualificationOnly=true\n")
                .append("claims.productionRtoOrAvailability=NOT_CLAIMED\n")
                .append("claims.productionReady=NOT_CLAIMED\n")
                .append("claims.hardwarePowerLoss=NOT_CLAIMED\n");
        provenance.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                text.append(entry.getKey()).append('=').append(entry.getValue()).append('\n'));
        return text.toString();
    }

    private static String manifestText(
            final ReleaseCandidateCharacterizationConfiguration configuration,
            final List<ReleaseCandidateCharacterizationResult.LifecycleSample> lifecycle,
            final ReleaseCandidateCharacterizationResult.TrialResult idle,
            final ReleaseCandidateCharacterizationResult.TrialResult status,
            final Map<String, String> provenance,
            final String artifactSha,
            final String configurationSha,
            final String comparabilitySha,
            final boolean success,
            final String summarySha) {
        final StringBuilder text = new StringBuilder()
                .append("schemaVersion=qualification-characterization-manifest-v1\n")
                .append("characterizationVersion=").append(CHARACTERIZATION_VERSION).append('\n')
                .append("source.gitSha=").append(configuration.gitSha()).append('\n')
                .append("source.baselineTag=").append(configuration.baselineTag()).append('\n')
                .append("result.status=").append(success ? "PASS" : "FAIL").append('\n')
                .append("result.lifecycleSampleCount=").append(lifecycle.size()).append('\n')
                .append("result.lifecyclePassedCount=").append(lifecycle.stream().filter(
                        ReleaseCandidateCharacterizationResult.LifecycleSample::passed).count())
                .append('\n')
                .append("identity.configurationIdentitySha256=").append(configurationSha).append('\n')
                .append("identity.comparabilityIdentitySha256=").append(comparabilitySha).append('\n')
                .append("artifact.applicationJarSha256=").append(artifactSha).append('\n')
                .append("artifact.lifecycleAndTrialFiles=artifact-hashes-v1.txt\n")
                .append("artifact.allocationEvidence=allocation-summary-v1.txt-per-process\n")
                .append("artifact.characterizationSummarySha256=").append(summarySha).append('\n')
                .append("executionModel=one-packaged-child-process-per-trial;single-protocol-client-thread;single-sequential-management-thread\n")
                .append("warmup=none\n")
                .append("forks=not-applicable;one-child-process-per-trial\n")
                .append("clientThreads=1\n")
                .append("managementThreads=1-sequential\n")
                .append("measurementBoundary=after-READY-and-Protocol-connection-before-command-loop\n")
                .append("trial.managementIdle.artifactSha256=").append(idle.artifactSha256()).append('\n')
                .append("trial.statusOneHz.artifactSha256=").append(status.artifactSha256()).append('\n')
                .append("trial.managementIdle.acceptedCommands=").append(idle.acceptedCommands()).append('\n')
                .append("trial.statusOneHz.acceptedCommands=").append(status.acceptedCommands()).append('\n')
                .append("trial.managementIdle.responseP99Nanos=")
                .append(idle.responseLatency().p99Nanos()).append('\n')
                .append("trial.statusOneHz.responseP99Nanos=")
                .append(status.responseLatency().p99Nanos()).append('\n')
                .append("claims.qualificationOnly=true\n")
                .append("claims.noProductionOptimization=true\n")
                .append("claims.noProductionReady=true\n");
        provenance.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                text.append(entry.getKey()).append('=').append(entry.getValue()).append('\n'));
        return text.toString();
    }

    private static void appendLifecycleDistributions(
            final StringBuilder text,
            final List<ReleaseCandidateCharacterizationResult.LifecycleSample> samples) {
        appendLifecycleDistribution(text, "startupToReadyNanos", samples.stream()
                .mapToLong(ReleaseCandidateCharacterizationResult.LifecycleSample::startupToReadyNanos)
                .toArray());
        appendLifecycleDistribution(text, "shutdownNanos", samples.stream()
                .mapToLong(ReleaseCandidateCharacterizationResult.LifecycleSample::shutdownNanos)
                .toArray());
        appendLifecycleDistribution(text, "responseNanos", samples.stream()
                .mapToLong(ReleaseCandidateCharacterizationResult.LifecycleSample::responseNanos)
                .toArray());
    }

    private static void appendLifecycleDistribution(
            final StringBuilder text, final String name, final long[] samples) {
        QualificationPercentiles.summarize(samples).appendTo(text, "lifecycle." + name);
    }

    private static void appendTrial(
            final StringBuilder text,
            final String name,
            final ReleaseCandidateCharacterizationResult.TrialResult trial) {
        text.append(name).append(".elapsedMillis=").append(trial.elapsedMillis()).append('\n')
                .append(name).append(".acceptedCommands=").append(trial.acceptedCommands()).append('\n')
                .append(name).append(".managementRequests=").append(trial.managementRequests()).append('\n')
                .append(name).append(".passed=").append(trial.throughputPassed()
                        && trial.responsePassed()).append('\n');
        trial.responseLatency().appendTo(text, name + ".responseLatency");
        trial.managementLatency().appendTo(text, name + ".managementLatency");
    }

    private static void appendComparison(
            final StringBuilder text,
            final ReleaseCandidateCharacterizationResult.TrialResult idle,
            final ReleaseCandidateCharacterizationResult.TrialResult status) {
        final double idleThroughput = throughputPerSecond(idle);
        final double statusThroughput = throughputPerSecond(status);
        final boolean throughputRegression = statusThroughput < idleThroughput * 0.90d;
        final boolean p99Regression = idle.responseLatency().p99Nanos() > 0
                && status.responseLatency().p99Nanos()
                > idle.responseLatency().p99Nanos() * 1.10d;
        text.append("managementComparison.idleThroughputOpsPerSecond=")
                .append(Double.toString(idleThroughput)).append('\n')
                .append("managementComparison.statusOneHzThroughputOpsPerSecond=")
                .append(Double.toString(statusThroughput)).append('\n')
                .append("managementComparison.throughputRegressionGreaterThan10Percent=")
                .append(throughputRegression).append('\n')
                .append("managementComparison.responseP99RegressionGreaterThan10Percent=")
                .append(p99Regression).append('\n')
                .append("managementComparison.result=EVIDENCE_REVIEW_IF_TRUE\n");
    }

    private static double throughputPerSecond(
            final ReleaseCandidateCharacterizationResult.TrialResult trial) {
        if (trial.elapsedMillis() <= 0) {
            return 0.0d;
        }
        return trial.acceptedCommands() * 1_000.0d / trial.elapsedMillis();
    }

    private static String trialText(
            final String name,
            final long elapsedMillis,
            final long accepted,
            final long managementRequests,
            final boolean passed,
            final QualificationPercentiles.Summary response,
            final QualificationPercentiles.Summary management,
            final String configurationSha) {
        final StringBuilder text = new StringBuilder()
                .append("schemaVersion=phase10-rc-trial-v1\n")
                .append("name=").append(name).append('\n')
                .append("measurementBoundary=after READY and Protocol connection established, before command loop and graceful shutdown\n")
                .append("executionModel=one-packaged-child-process-per-trial;single-protocol-client-thread;single-sequential-management-thread\n")
                .append("warmup=none\n")
                .append("forks=not-applicable;one-child-process-per-trial\n")
                .append("clientThreads=1\n")
                .append("managementThreads=1-sequential\n")
                .append("elapsedMillis=").append(elapsedMillis).append('\n')
                .append("acceptedCommands=").append(accepted).append('\n')
                .append("managementRequests=").append(managementRequests).append('\n')
                .append("passed=").append(passed).append('\n')
                .append("configurationSha256=").append(configurationSha).append('\n');
        response.appendTo(text, "responseLatency");
        management.appendTo(text, "managementLatency");
        return text.toString();
    }

    private static String hashesText(final Map<String, Path> artifacts) throws IOException {
        final StringBuilder text = new StringBuilder("schemaVersion=artifact-hashes-v1\n");
        for (final Map.Entry<String, Path> entry : artifacts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            text.append(entry.getKey()).append('=')
                    .append(QualificationArtifactHasher.sha256(entry.getValue())).append('\n');
        }
        return text.toString();
    }

    private static Map<String, Path> collectArtifacts(
            final Path root, final Path hashes) throws IOException {
        final Map<String, Path> artifacts = new LinkedHashMap<>();
        try (var paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> !path.equals(hashes))
                    .sorted()
                    .forEach(path -> artifacts.put(root.relativize(path).toString()
                            .replace('\\', '/'), path));
        }
        return artifacts;
    }

    private static String configurationIdentity(
            final ReleaseCandidateCharacterizationConfiguration configuration,
            final String artifactSha) {
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put("schema", CHARACTERIZATION_VERSION);
        fields.put("workload", QualificationWorkloadV1.MEMORY_STEADY_STATE_VERSION);
        fields.put("seed", "20260823");
        fields.put("emptyWalSamples", Integer.toString(configuration.emptyWalSamples()));
        fields.put("snapshotTailSamples", Integer.toString(configuration.snapshotTailSamples()));
        fields.put("liveResponseSampling", "duration-driven-fixed-window");
        fields.put("executionModel", "one-packaged-child-process-per-trial-single-protocol-client-thread-single-sequential-management-thread");
        fields.put("warmup", "none");
        fields.put("forks", "not-applicable-one-child-process-per-trial");
        fields.put("measurementBoundary", "after-ready-and-protocol-connection-before-command-loop");
        fields.put("gitSha", configuration.gitSha());
        fields.put("baselineTag", configuration.baselineTag());
        fields.put("pairedTrialDuration", configuration.pairedTrialDuration().toString());
        fields.put("managementInterval", configuration.managementInterval().toString());
        fields.put("startupTimeout", configuration.startupTimeout().toString());
        fields.put("commandTimeout", configuration.commandTimeout().toString());
        fields.put("processTimeout", configuration.processTimeout().toString());
        fields.put("allocationEvidence", "jdk.ObjectAllocationSample-throttle-100/s");
        fields.put("durability", "SYNC_EACH_APPEND");
        fields.put("pipeline", "SPSC-1024-BLOCKING");
        fields.put("wal", "WAL-v1-segment-65536");
        fields.put("applicationJarSha256", artifactSha);
        fields.put("protocol", "Protocol-v1");
        fields.put("management", "loopback-status");
        return QualificationIdentity.digest(fields);
    }

    private static String rootRelative(final Path root, final Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private static void publishFailure(final Path directory, final Throwable failure) {
        try {
            QualificationEvidencePublication.text(
                    directory.resolve("failure.txt"),
                    failure.getClass().getName() + " " + String.valueOf(failure.getMessage()) + '\n');
        } catch (final IOException ignored) {
            // The primary qualification observation remains the returned failed sample.
        }
    }

    private static boolean recoveryConverged(
            final Path walDirectory,
            final Path snapshotDirectory,
            final RecoveryMode mode) throws IOException {
        final WalConfiguration wal = WalConfiguration.defaults(walDirectory);
        final long commandCount = java.nio.file.Files.exists(walDirectory)
                ? com.ultralatency.matching.persistence.wal.CommandWalReader.read(wal).size() : 0L;
        final var result = RecoveryPlanner.create(wal, snapshotDirectory).recover(mode);
        return result.walEndSequence() == commandCount
                && result.nextCommandSequence() == commandCount + 1L;
    }

    private static boolean leaseReacquired(final Path walDirectory) throws IOException {
        try (RecoveryLease lease = RecoveryLease.acquire(walDirectory)) {
            return lease.isHeld();
        }
    }

    private static boolean noTemporaryFiles(final Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            return paths.noneMatch(path -> path.getFileName().toString().endsWith(".tmp"));
        }
    }

    private static void prepareSnapshotTail(
            final Path walDirectory,
            final Path snapshotDirectory) throws IOException {
        final WalConfiguration wal = WalConfiguration.defaults(walDirectory);
        final QualificationConfiguration workload =
                QualificationFullConfiguration.memorySteadyStateTest(walDirectory).workloadConfiguration();
        final EngineCommand prefix = QualificationWorkloadV1.commandAtForRun(workload, 0);
        final EngineCommand tail = QualificationWorkloadV1.commandAtForRun(workload, 1);
        try (CommandWalWriter writer = CommandWalWriter.open(wal)) {
            writer.append(prefix);
        }
        new OfflineSnapshotGenerator(wal, new SnapshotStore(snapshotDirectory)).generate();
        try (CommandWalWriter writer = CommandWalWriter.open(wal)) {
            writer.append(tail);
        }
    }

    private static Path writeConfiguration(
            final Path directory,
            final Path walDirectory,
            final Path snapshotDirectory,
            final RecoveryMode mode) throws IOException {
        final int protocolPort = freePort();
        int managementPort = freePort();
        while (protocolPort == managementPort) {
            managementPort = freePort();
        }
        final String text = "storage.wal.directory=wal\n"
                + "storage.snapshot.directory=snapshots\n"
                + "recovery.mode=" + mode + "\n"
                + "wal.segment.size.bytes=65536\n"
                + "wal.durability.mode=SYNC_EACH_APPEND\n"
                + "pipeline.capacity=1024\n"
                + "pipeline.wait.mode=BLOCKING\n"
                + "protocol.bind.address=127.0.0.1\n"
                + "protocol.port=" + protocolPort + "\n"
                + "protocol.write.low.bytes=8192\n"
                + "protocol.write.high.bytes=16384\n"
                + "management.enabled=true\n"
                + "management.bind.address=127.0.0.1\n"
                + "management.port=" + managementPort + "\n"
                + "management.max.connections=16\n"
                + "management.request.timeout.ms=1000\n"
                + "lifecycle.shutdown.timeout.ms=2000\n";
        if (!walDirectory.getParent().equals(directory)
                || !snapshotDirectory.getParent().equals(directory)) {
            throw new IOException("characterization storage paths escaped directory");
        }
        final Path target = directory.resolve("runtime.properties");
        QualificationEvidencePublication.text(target, text);
        return target;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    private static void requireArtifact(final Path artifact) {
        if (artifact == null || !Files.isRegularFile(artifact)
                || !artifact.getFileName().toString().endsWith(".jar")) {
            throw new IllegalArgumentException("packagedArtifact must be a regular JAR");
        }
    }

}
