package com.ultralatency.matching.qualification;

import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.network.netty.recovery.RecoverableDurableMatchingEngineTcpServer;
import com.ultralatency.matching.persistence.snapshot.RecoveryLease;
import com.ultralatency.matching.persistence.wal.CommandWalReader;
import com.ultralatency.matching.persistence.wal.WalConfiguration;
import com.ultralatency.matching.recovery.online.RecoveryMode;
import com.ultralatency.matching.recovery.online.RecoveryPlanner;
import com.ultralatency.matching.recovery.online.RecoveryResult;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.GarbageCollectorMXBean;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;

/**
 * Executes the Phase 9 full soak through the public Protocol v1 boundary.
 *
 * <p>This runner deliberately does not retry or filter failures. Raw artifacts remain in the
 * caller-owned output directory so a failed campaign is evidence rather than an invisible retry.
 * TASK-038 owns the separate restart/forced-termination campaign.</p>
 */
public final class QualificationFullRunner {

    /** Runs one explicit full or short harness campaign. */
    public QualificationFullRun run(final QualificationFullConfiguration configuration)
            throws IOException {
        Objects.requireNonNull(configuration, "configuration");
        final QualificationConfiguration workloadConfiguration =
                configuration.workloadConfiguration();
        final String runId = "qualification-full-" + UUID.randomUUID();
        final Path artifactDirectory = configuration.outputDirectory().resolve(runId);
        final Path walDirectory = artifactDirectory.resolve("wal");
        final Path snapshotDirectory = artifactDirectory.resolve("snapshots");
        final Path jfrPath = artifactDirectory.resolve("qualification.jfr");
        final Path manifestPath = artifactDirectory.resolve("qualification-manifest.txt");
        final Path resourcePath = artifactDirectory.resolve("resource-evidence.csv");
        final Path artifactHashesPath = artifactDirectory.resolve("artifact-hashes.txt");
        Files.createDirectories(walDirectory);
        Files.createDirectories(snapshotDirectory);
        final WalConfiguration walConfiguration = WalConfiguration.defaults(walDirectory);
        final int port = freeLoopbackPort();
        final Instant started = Instant.now();
        final QualificationStreamingAccumulator streaming =
                new QualificationStreamingAccumulator(QualificationRunner.PUBLIC_PROBE_SUFFIX_LENGTH);
        final QualificationPublicStateTracker publicState = new QualificationPublicStateTracker();
        QualificationPublicStateTracker.Summary publicStateSummary = null;
        boolean listenerRebound = false;
        boolean recoveryLeaseReacquired = false;
        boolean inventoryStable = false;
        final QualificationResourceSampler sampler = new QualificationResourceSampler(
                configuration.sampleInterval(), configuration.minimumPostGcSamples());
        QualificationJfrRecording jfr = null;
        boolean samplerClosed = false;
        boolean jfrClosed = false;
        try {
            jfr = QualificationJfrRecording.start(jfrPath);
            final RecoverableDurableMatchingEngineTcpServer server = QualificationRunner.server(
                    walDirectory, snapshotDirectory, port);
            server.start();
            ProtocolV1QualificationClient client = null;
            try {
                client = new ProtocolV1QualificationClient(
                        server.localAddress().orElseThrow(),
                        workloadConfiguration.commandTimeout());
                long commandIndex = 0L;
                final boolean continuousMemoryLane = configuration.lane() == QualificationLane.FULL
                        && configuration.profile() == QualificationProfile.MEMORY_STEADY_STATE_V1;
                final Instant minimumDeadline = started.plus(configuration.minimumDuration());
                while (commandIndex < workloadConfiguration.commandCount()
                        || continuousMemoryLane && Instant.now().isBefore(minimumDeadline)) {
                    final EngineCommand command = QualificationWorkloadV1.commandAtForRun(
                            workloadConfiguration, commandIndex);
                    final QualificationExchange exchange = client.exchange(
                            command, commandIndex + 1L);
                    streaming.accept(command, exchange);
                    publicState.accept(command, exchange);
                    commandIndex++;
                }
                publicStateSummary = publicState.finish();
                if (!continuousMemoryLane) {
                    awaitMinimumDuration(started, configuration);
                }
                if (commandIndex > Integer.MAX_VALUE) {
                    throw new IOException("qualification command count exceeds manifest bounds");
                }
            } finally {
                shutdownBeforeClientClose(
                        server, client, workloadConfiguration.commandTimeout());
            }
            requireCleanShutdown(server);

            /*
             * End heap measurement before WAL materialization and offline recovery. Those
             * operations intentionally create large verification structures and must not
             * contaminate the long-run retained-heap evidence window.
             */
            final Duration measurementElapsed = Duration.between(started, Instant.now());
            final QualificationResourceEvidence resources = closeSampler(sampler);
            samplerClosed = true;
            closeJfr(jfr);
            jfrClosed = true;
            final QualificationStreamingSummary streamingSummary = streaming.finish();
            publicStateSummary = Objects.requireNonNull(publicStateSummary,
                    "public state summary");

            final List<EngineCommand> persisted = CommandWalReader.read(walConfiguration);
            if (!QualificationWorkloadV1.matches(persisted, workloadConfiguration)) {
                throw new IOException("Full WAL command stream differs from workload");
            }
            final RecoveryResult recovered = RecoveryPlanner.create(
                    walConfiguration, snapshotDirectory).recover(RecoveryMode.PURE_WAL);
            if (recovered.walEndSequence() != persisted.size()) {
                throw new IOException("Full recovery sequence does not converge");
            }

            final RecoverableDurableMatchingEngineTcpServer rebound = QualificationRunner.server(
                    walDirectory, snapshotDirectory, port);
            rebound.start();
            try {
                listenerRebound = rebound.localAddress()
                        .map(address -> address.getPort() == port)
                        .orElse(false);
            } finally {
                rebound.shutdown(workloadConfiguration.commandTimeout());
            }
            requireCleanShutdown(rebound);
            try (RecoveryLease lease = RecoveryLease.acquire(walDirectory)) {
                recoveryLeaseReacquired = lease.isHeld();
            }
            final QualificationStorageInventory storageInventory =
                    QualificationStorageInventory.capture(walDirectory, snapshotDirectory);
            inventoryStable = storageInventory.stable();

            final Duration elapsed = Duration.between(started, Instant.now());
            final QualificationConfiguration manifestConfiguration = manifestConfiguration(
                    workloadConfiguration, persisted.size());
            final QualificationWorkload workload = QualificationWorkloadV1.generate(
                    manifestConfiguration);
            final String walDigest = QualificationCanonicalizer.digest(persisted);
            if (!walDigest.equals(streamingSummary.commandDigestHex())) {
                throw new IOException("streamed workload digest differs from persisted WAL digest");
            }
            final boolean memoryStateBoundPassed = memoryStateBoundPassed(
                    configuration.profile(), publicStateSummary, recovered);
            writeResourceEvidence(resourcePath, resources);
            final boolean lanePassed = laneAssertionsPass(
                    configuration, workload.commandCount(), listenerRebound,
                    recoveryLeaseReacquired, inventoryStable, memoryStateBoundPassed);
            final boolean fullCriteria = fullCriteriaPasses(
                    configuration, workload.commandCount(), elapsed, resources,
                    listenerRebound, recoveryLeaseReacquired, inventoryStable,
                    memoryStateBoundPassed);
            final QualificationResult result = new QualificationResult(
                    lanePassed,
                    workload.commandCount(),
                    streamingSummary.responseCount(),
                    streamingSummary.tradeCount(),
                    recovered.checkpointDigestHex(),
                    streamingSummary.transcriptDigestHex(),
                    streamingSummary.publicProbeDigestHex(),
                    measurements(
                            configuration, elapsed, measurementElapsed, persisted, walDigest,
                            recovered.checkpointDigestHex(), streamingSummary, resources,
                            storageInventory, memoryStateBoundPassed,
                            recovered.checkpoint().activeOrderCount(), publicStateSummary,
                            fullCriteria));
            final QualificationManifest manifest = new QualificationManifest(
                    runId,
                    System.getProperty("qualification.git.sha", "working-tree"),
                    System.getProperty("qualification.baseline", "v0.7.0-engineering-baseline"),
                    workload,
                    manifestConfiguration,
                    configuration.outputDirectory(),
                    environment(configuration, elapsed, walDirectory),
                    QualificationCanonicalizer.digest(manifestConfiguration),
                    result.digestHex(),
                    started);
            Files.writeString(manifestPath, manifestText(
                    configuration, manifest, result, elapsed, listenerRebound,
                    recoveryLeaseReacquired, inventoryStable, resources, jfrPath,
                    resourcePath, artifactHashesPath, storageInventory,
                    memoryStateBoundPassed, publicStateSummary, measurementElapsed, fullCriteria));
            final String jfrDigest = QualificationArtifactHasher.sha256(jfrPath);
            final String manifestDigest = QualificationArtifactHasher.sha256(manifestPath);
            final String resourceDigest = QualificationArtifactHasher.sha256(resourcePath);
            writeArtifactHashes(
                    artifactHashesPath, jfrPath, jfrDigest, manifestPath, manifestDigest,
                    resourcePath, resourceDigest);
            final String artifactHashesDigest =
                    QualificationArtifactHasher.sha256(artifactHashesPath);
            final QualificationRun run = new QualificationRun(manifest, result, 1);
            return new QualificationFullRun(
                    run,
                    resources,
                    elapsed,
                    listenerRebound,
                    recoveryLeaseReacquired,
                    inventoryStable,
                    storageInventory,
                    fullCriteria,
                    artifactDirectory,
                    artifactHashesPath,
                    jfrDigest,
                    manifestDigest,
                    resourceDigest,
                    artifactHashesDigest);
        } catch (final IOException | RuntimeException exception) {
            writeFailure(artifactDirectory, started, exception);
            throw exception;
        } finally {
            if (!jfrClosed && jfr != null) {
                closeQuietly(jfr);
            }
            if (!samplerClosed) {
                closeQuietly(sampler);
            }
        }
    }

    private static QualificationResourceEvidence closeSampler(
            final QualificationResourceSampler sampler) {
        sampler.close();
        return sampler.evidence();
    }

    private static void closeJfr(final QualificationJfrRecording jfr) throws IOException {
        jfr.close();
    }

    private static void closeQuietly(final AutoCloseable resource) {
        try {
            resource.close();
        } catch (final Exception ignored) {
            // The primary campaign failure is retained in the failure artifact.
        }
    }

    /** Holds a FULL lane alive until its approved duration threshold is reached. */
    private static void awaitMinimumDuration(
            final Instant started,
            final QualificationFullConfiguration configuration) throws IOException {
        if (configuration.lane() != QualificationLane.FULL) {
            return;
        }
        final Instant deadline = started.plus(configuration.minimumDuration());
        while (Instant.now().isBefore(deadline)) {
            final long remainingNanos = Duration.between(Instant.now(), deadline).toNanos();
            if (remainingNanos > 0) {
                LockSupport.parkNanos(Math.min(remainingNanos, Duration.ofSeconds(1).toNanos()));
            }
            if (Thread.currentThread().isInterrupted()) {
                throw new IOException("Full Qualification duration hold interrupted");
            }
        }
    }

    private static boolean laneAssertionsPass(
            final QualificationFullConfiguration configuration,
            final int acceptedCommands,
            final boolean listenerRebound,
            final boolean leaseReacquired,
            final boolean inventoryStable,
            final boolean memoryStateBoundPassed) {
        return configuration.lane() == QualificationLane.TEST
                && acceptedCommands > 0
                && listenerRebound
                && leaseReacquired
                && inventoryStable
                && memoryStateBoundPassed;
    }

    private static boolean fullCriteriaPasses(
            final QualificationFullConfiguration configuration,
            final int acceptedCommands,
            final Duration elapsed,
            final QualificationResourceEvidence resources,
            final boolean listenerRebound,
            final boolean leaseReacquired,
            final boolean inventoryStable,
            final boolean memoryStateBoundPassed) {
        if (configuration.lane() != QualificationLane.FULL) {
            return false;
        }
        return acceptedCommands >= QualificationFullConfiguration.FULL_MINIMUM_COMMANDS
                && elapsed.compareTo(configuration.minimumDuration()) >= 0
                && resources.threadBaselineRestored()
                && resources.heapGuardAssessed()
                && resources.heapGuardPassed()
                && listenerRebound
                && leaseReacquired
                && inventoryStable
                && memoryStateBoundPassed;
    }

    private static Map<String, String> measurements(
            final QualificationFullConfiguration configuration,
            final Duration elapsed,
            final Duration measurementElapsed,
            final List<EngineCommand> commands,
            final String walDigest,
            final String checkpointDigest,
            final QualificationStreamingSummary streaming,
            final QualificationResourceEvidence resources,
            final QualificationStorageInventory inventory,
            final boolean memoryStateBoundPassed,
            final int memoryStateActiveOrderCount,
            final QualificationPublicStateTracker.Summary publicState,
            final boolean fullCriteriaPassed) {
        final Map<String, String> values = new HashMap<>();
        values.put("lane", configuration.lane().name());
        values.put("profile", configuration.profile().name());
        values.put("elapsedMillis", Long.toString(elapsed.toMillis()));
        values.put("heapMeasurementElapsedMillis", Long.toString(measurementElapsed.toMillis()));
        values.put("commandCount", Integer.toString(commands.size()));
        values.put("walCommandDigestHex", walDigest);
        values.put("checkpointDigestHex", checkpointDigest);
        values.put("streamedCommandDigestHex", streaming.commandDigestHex());
        values.put("transcriptDigestHex", streaming.transcriptDigestHex());
        values.put("resourceSampleCount", Integer.toString(resources.samples().size()));
        values.put("naturalPostGcSampleCount",
                Integer.toString(resources.naturalPostGcHeapBytes().size()));
        values.put("retainedProbeCount", Integer.toString(streaming.retainedProbeCount()));
        values.put("threadBaselineRestored",
                Boolean.toString(resources.threadBaselineRestored()));
        values.put("memoryStateBoundPassed", Boolean.toString(memoryStateBoundPassed));
        values.put("memoryStateActiveOrderCount", Integer.toString(memoryStateActiveOrderCount));
        values.put("memoryStateActiveOrderBound",
                Integer.toString(QualificationWorkloadV1.MEMORY_STEADY_STATE_MAX_ACTIVE_ORDERS));
        values.put("publicStateMaximumActiveOrderCount",
                Integer.toString(publicState.maximumActiveOrderCount()));
        values.put("publicStateFinalActiveOrderCount",
                Integer.toString(publicState.finalActiveOrderCount()));
        values.put("publicStateBoundPassed", Boolean.toString(publicState.boundPassed()));
        values.put("publicStateMatchesRecovered", Boolean.toString(
                publicState.finalActiveOrderCount() == memoryStateActiveOrderCount));
        values.put("heapGuardPassed", Boolean.toString(resources.heapGuardPassed()));
        values.put("fullCriteriaPassed", Boolean.toString(fullCriteriaPassed));
        values.put("campaignMinimumPostGcSamples",
                Integer.toString(QualificationFullConfiguration.CAMPAIGN_MINIMUM_POST_GC_SAMPLES));
        values.put("walFileCount", Long.toString(inventory.walFileCount()));
        values.put("walBytes", Long.toString(inventory.walBytes()));
        values.put("walFiles", inventory.walFilesText());
        values.put("snapshotFileCount", Long.toString(inventory.snapshotFileCount()));
        values.put("snapshotBytes", Long.toString(inventory.snapshotBytes()));
        values.put("snapshotFiles", inventory.snapshotFilesText());
        values.put("temporaryFileCount", Long.toString(inventory.temporaryFileCount()));
        return Map.copyOf(values);
    }

    private static Map<String, String> environment(
            final QualificationFullConfiguration configuration,
            final Duration elapsed,
            final Path walDirectory) throws IOException {
        return Map.of(
                "java.version", System.getProperty("java.version", "unknown"),
                "java.vm.name", System.getProperty("java.vm.name", "unknown"),
                "java.vm.version", System.getProperty("java.vm.version", "unknown"),
                "java.vm.inputArguments", inputArguments(),
                "gc.collectors", ManagementFactory.getGarbageCollectorMXBeans().stream()
                        .map(GarbageCollectorMXBean::getName)
                        .sorted()
                        .collect(Collectors.joining(",")),
                "os.name", System.getProperty("os.name", "unknown"),
                "os.arch", System.getProperty("os.arch", "unknown"),
                "filesystem", Files.getFileStore(walDirectory).type(),
                "qualification.lane", configuration.lane().name(),
                "qualification.elapsedMillis", Long.toString(elapsed.toMillis()));
    }

    private static boolean memoryStateBoundPassed(
            final QualificationProfile profile,
            final QualificationPublicStateTracker.Summary publicState,
            final RecoveryResult recovered) {
        return profile != QualificationProfile.MEMORY_STEADY_STATE_V1
                || publicState.boundPassed()
                && publicState.finalActiveOrderCount() == recovered.checkpoint().activeOrderCount()
                && recovered.checkpoint().activeOrderCount()
                <= QualificationWorkloadV1.MEMORY_STEADY_STATE_MAX_ACTIVE_ORDERS;
    }

    private static QualificationConfiguration manifestConfiguration(
            final QualificationConfiguration configuration,
            final int persistedCommandCount) {
        if (persistedCommandCount == configuration.commandCount()) {
            return configuration;
        }
        if (configuration.profile() != QualificationProfile.MEMORY_STEADY_STATE_V1
                || persistedCommandCount < configuration.commandCount()) {
            throw new IllegalArgumentException("persisted command count cannot describe manifest");
        }
        return new QualificationConfiguration(
                configuration.profile(), configuration.seed(), persistedCommandCount,
                configuration.commandTimeout(), configuration.outputDirectory());
    }

    private static String inputArguments() {
        final String arguments = String.join(
                " ", ManagementFactory.getRuntimeMXBean().getInputArguments());
        return arguments.isBlank() ? "<none>" : arguments;
    }

    private static String manifestText(
            final QualificationFullConfiguration configuration,
            final QualificationManifest manifest,
            final QualificationResult result,
            final Duration elapsed,
            final boolean listenerRebound,
            final boolean leaseReacquired,
            final boolean inventoryStable,
            final QualificationResourceEvidence resources,
            final Path jfrPath,
            final Path resourcePath,
            final Path artifactHashesPath,
            final QualificationStorageInventory inventory,
            final boolean memoryStateBoundPassed,
            final QualificationPublicStateTracker.Summary publicState,
            final Duration measurementElapsed,
            final boolean fullCriteriaPassed) {
        final Map<String, String> values = new java.util.TreeMap<>();
        values.put("acceptedCommands", Long.toString(result.acceptedCommands()));
        values.put("baselineTag", manifest.baselineTag());
        values.put("checkpointDigestHex", result.checkpointDigestHex());
        values.put("elapsedMillis", Long.toString(elapsed.toMillis()));
        values.put("heapMeasurementElapsedMillis", Long.toString(measurementElapsed.toMillis()));
        values.put("commandCount", Integer.toString(manifest.workload().commandCount()));
        values.put("commandTimeout", manifest.configuration().commandTimeout().toString());
        values.put("gcCollectors", manifest.environment().getOrDefault("gc.collectors", ""));
        values.put("fullCriteriaPassed", Boolean.toString(fullCriteriaPassed));
        values.put("heapGuardPassed", Boolean.toString(resources.heapGuardPassed()));
        values.put("heapGuardAssessed", Boolean.toString(resources.heapGuardAssessed()));
        values.put("jfrPath", jfrPath.toString());
        values.put("lane", configuration.lane().name());
        values.put("leaseReacquired", Boolean.toString(leaseReacquired));
        values.put("listenerRebound", Boolean.toString(listenerRebound));
        values.put("manifestResultDigestHex", manifest.resultDigestHex());
        values.put("naturalPostGcSampleCount",
                Integer.toString(resources.naturalPostGcHeapBytes().size()));
        values.put("publicProbeDigestHex", result.publicProbeDigestHex());
        values.put("resourceEvidencePath", resourcePath.toString());
        values.put("artifactHashesPath", artifactHashesPath.toString());
        values.put("walFileCount", Long.toString(inventory.walFileCount()));
        values.put("walBytes", Long.toString(inventory.walBytes()));
        values.put("walFiles", inventory.walFilesText());
        values.put("snapshotFileCount", Long.toString(inventory.snapshotFileCount()));
        values.put("snapshotBytes", Long.toString(inventory.snapshotBytes()));
        values.put("snapshotFiles", inventory.snapshotFilesText());
        values.put("temporaryFileCount", Long.toString(inventory.temporaryFileCount()));
        values.put("memoryStateBoundPassed", Boolean.toString(memoryStateBoundPassed));
        values.put("publicStateMaximumActiveOrderCount",
                Integer.toString(publicState.maximumActiveOrderCount()));
        values.put("publicStateFinalActiveOrderCount",
                Integer.toString(publicState.finalActiveOrderCount()));
        values.put("publicStateBoundPassed", Boolean.toString(publicState.boundPassed()));
        values.put("publicStateMatchesRecovered", Boolean.toString(
                Boolean.parseBoolean(result.measurements().get("publicStateMatchesRecovered"))));
        values.put("memoryStateActiveOrderBound",
                Integer.toString(QualificationWorkloadV1.MEMORY_STEADY_STATE_MAX_ACTIVE_ORDERS));
        values.put("baselineThreadCount", Long.toString(resources.baselineThreadCount()));
        values.put("finalThreadCount", Long.toString(resources.finalThreadCount()));
        values.put("baselineRuntimeThreads", String.join(
                "|", resources.baselineRuntimeThreads()));
        values.put("finalRuntimeThreads", String.join(
                "|", resources.finalRuntimeThreads()));
        values.put("threadBaselineRestored",
                Boolean.toString(resources.threadBaselineRestored()));
        values.put("responseCount", Long.toString(result.responseCount()));
        values.put("tradeCount", Long.toString(result.tradeCount()));
        values.put("transcriptDigestHex", result.transcriptDigestHex());
        values.put("walCommandDigestHex", result.measurements().get("walCommandDigestHex"));
        values.put("streamedCommandDigestHex",
                result.measurements().get("streamedCommandDigestHex"));
        values.put("retainedProbeCount", result.measurements().get("retainedProbeCount"));
        values.put("workloadVersion", manifest.workload().version());
        values.put("profile", manifest.workload().profile().name());
        values.put("seed", Long.toString(manifest.workload().seed()));
        values.put("sampleInterval", configuration.sampleInterval().toString());
        values.put("minimumDuration", configuration.minimumDuration().toString());
        values.put("minimumPostGcSamples", Integer.toString(configuration.minimumPostGcSamples()));
        values.put("campaignMinimumPostGcSamples",
                Integer.toString(QualificationFullConfiguration.CAMPAIGN_MINIMUM_POST_GC_SAMPLES));
        values.put("jvmInputArguments",
                manifest.environment().getOrDefault("java.vm.inputArguments", ""));
        values.put("filesystem", manifest.environment().getOrDefault("filesystem", "unknown"));
        final StringBuilder output = new StringBuilder();
        values.forEach((key, value) -> output.append(key).append('=').append(value).append('\n'));
        return output.toString();
    }

    private static void writeResourceEvidence(
            final Path path,
            final QualificationResourceEvidence evidence) throws IOException {
        final StringBuilder output = new StringBuilder();
        output.append("#baselineThreadCount=").append(evidence.baselineThreadCount()).append('\n');
        output.append("#finalThreadCount=").append(evidence.finalThreadCount()).append('\n');
        output.append("#threadBaselineRestored=")
                .append(evidence.threadBaselineRestored()).append('\n');
        output.append("#heapGuardAssessed=").append(evidence.heapGuardAssessed()).append('\n');
        output.append("#heapGuardPassed=").append(evidence.heapGuardPassed()).append('\n');
        output.append("#baselineRuntimeThreads=")
                .append(String.join("|", evidence.baselineRuntimeThreads())).append('\n');
        output.append("#finalRuntimeThreads=")
                .append(String.join("|", evidence.finalRuntimeThreads())).append('\n');
        output.append("timestamp,threadCount,peakThreadCount,gcCollections,gcTimeMillis,heapUsed,"
                + "naturalPostGcHeapUsed\n");
        for (final QualificationResourceSample sample : evidence.samples()) {
            output.append(sample.timestamp()).append(',')
                    .append(sample.liveThreadCount()).append(',')
                    .append(sample.peakThreadCount()).append(',')
                    .append(sample.totalGcCollections()).append(',')
                    .append(sample.totalGcTimeMillis()).append(',')
                    .append(sample.heapUsedBytes()).append(',')
                    .append(sample.naturalPostGcHeapBytes() == null
                            ? "" : sample.naturalPostGcHeapBytes())
                    .append('\n');
        }
        Files.writeString(path, output.toString(), StandardCharsets.UTF_8);
    }

    private static void requireCleanShutdown(
            final RecoverableDurableMatchingEngineTcpServer server) throws IOException {
        if (server.failureCause().isPresent()) {
            throw new IOException("qualification server entered terminal failure",
                    server.failureCause().orElseThrow());
        }
    }

    /** Stops the server before closing the active client session. */
    private static void shutdownBeforeClientClose(
            final RecoverableDurableMatchingEngineTcpServer server,
            final ProtocolV1QualificationClient client,
            final Duration timeout) throws IOException {
        try {
            server.shutdown(timeout);
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    private static void writeArtifactHashes(
            final Path path,
            final Path jfrPath,
            final String jfrDigest,
            final Path manifestPath,
            final String manifestDigest,
            final Path resourcePath,
            final String resourceDigest) throws IOException {
        final String output = "jfr=" + jfrPath.getFileName() + "\t" + jfrDigest + "\n"
                + "manifest=" + manifestPath.getFileName() + "\t" + manifestDigest + "\n"
                + "resourceEvidence=" + resourcePath.getFileName() + "\t" + resourceDigest
                + "\n";
        Files.writeString(path, output, StandardCharsets.UTF_8);
    }

    private static int freeLoopbackPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    private static void writeFailure(
            final Path artifactDirectory,
            final Instant started,
            final Throwable failure) {
        try {
            Files.createDirectories(artifactDirectory);
            final Path report = artifactDirectory.resolve("failure-report.txt");
            Files.writeString(
                    report,
                    "started=" + started + "\n"
                            + "type=" + failure.getClass().getName() + "\n"
                            + "message=" + String.valueOf(failure.getMessage()) + "\n",
                    StandardCharsets.UTF_8);
            final String digest = QualificationArtifactHasher.sha256(report);
            Files.writeString(
                    artifactDirectory.resolve("failure-report.sha256"),
                    "failure-report.txt\t" + digest + "\n",
                    StandardCharsets.UTF_8);
        } catch (final IOException ignored) {
            // Preserve the original campaign failure; the caller receives it unchanged.
        }
    }

}
