package com.ultralatency.matching.qualification.ga.soak;

import com.ultralatency.matching.app.ReleaseCandidateRuntime;
import com.ultralatency.matching.app.RuntimeConfiguration;
import com.ultralatency.matching.app.RuntimeFailureCode;
import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.qualification.ProtocolV1QualificationClient;
import com.ultralatency.matching.qualification.QualificationConfiguration;
import com.ultralatency.matching.qualification.QualificationEvidencePublication;
import com.ultralatency.matching.qualification.QualificationExchange;
import com.ultralatency.matching.qualification.QualificationJfrRecording;
import com.ultralatency.matching.qualification.QualificationWorkloadV1;
import com.ultralatency.matching.qualification.ReleaseCandidateManagementClient;
import com.ultralatency.matching.qualification.ga.correctness.GaCorrectnessCanonicalContext;
import com.ultralatency.matching.qualification.ga.observability.GaGcEvidence;
import com.ultralatency.matching.qualification.ga.observability.GaJfrEvidence;
import com.ultralatency.matching.qualification.ga.observability.GaManagementEvidence;
import com.ultralatency.matching.qualification.ga.observability.GaObservabilityEvaluator;
import com.ultralatency.matching.qualification.ga.observability.GaObservabilityObservation;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
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
import java.util.concurrent.locks.LockSupport;
import com.ultralatency.matching.persistence.wal.WalDurabilityMode;
import com.ultralatency.matching.pipeline.PipelineWaitMode;
import com.ultralatency.matching.recovery.online.RecoveryMode;

/**
 * Executes the single shared TASK-052 Quick physical lifecycle.
 *
 * <p>Only {@link #runQuick(Path)} is exposed as an execution operation.  Stage A and Stage B
 * matrices are represented by {@link GaSoakMatrix}, but this runner refuses to execute them;
 * their formal campaigns are owned by a later Human-gated task.</p>
 */
public final class GaSoakRunner {

    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);
    private static final int WAL_SEGMENT_SIZE_BYTES = 65_536;

    private final GaCorrectnessCanonicalContext configuredContext;

    /** Creates a runner resolving the frozen candidate/context at execution time. */
    public GaSoakRunner() {
        this(null);
    }

    /** Creates a runner with an explicit context for deterministic unit tests. */
    public GaSoakRunner(final GaCorrectnessCanonicalContext context) {
        configuredContext = context;
    }

    /** Runs exactly one shared, non-formal Quick physical execution. */
    public GaSoakQuickResult runQuick(final Path outputDirectory) throws IOException {
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        final GaSoakMatrix matrix = GaSoakMatrix.quick();
        final GaCorrectnessCanonicalContext context = context();
        final Path root = newRunDirectory(outputDirectory);
        final Path physicalRoot = root.resolve("physical-root");
        final Path walRoot = physicalRoot.resolve("wal");
        final Path snapshotRoot = physicalRoot.resolve("snapshots");
        final Path evidenceRoot = root.resolve("process-evidence");
        Files.createDirectories(walRoot);
        Files.createDirectories(snapshotRoot);
        Files.createDirectories(evidenceRoot);
        final String physicalId = UUID.randomUUID().toString();
        final QualificationConfiguration workload = new QualificationConfiguration(
                com.ultralatency.matching.qualification.QualificationProfile
                        .MEMORY_STEADY_STATE_V1,
                matrix.seed(),
                Math.toIntExact(matrix.acceptedFloor()),
                COMMAND_TIMEOUT,
                physicalRoot);
        final Path jfrPath = evidenceRoot.resolve("qualification.jfr");
        final Instant started = Instant.now();
        final List<Long> latency = new ArrayList<>(Math.toIntExact(matrix.acceptedFloor()));
        final List<GaManagementEvidence> management = new ArrayList<>();
        final StringBuilder transcript = new StringBuilder();
        final RuntimeConfiguration configuration = runtimeConfiguration(walRoot, snapshotRoot);
        final ReleaseCandidateRuntime runtime = ReleaseCandidateRuntime.create(configuration);
        final GaSoakResourceSampler sampler = new GaSoakResourceSampler(
                physicalId, GaSoakMatrix.Stage.QUICK, physicalRoot, snapshotRoot);
        QualificationJfrRecording jfr = null;
        boolean publicPathCompleted = false;
        boolean gracefulShutdown = false;
        int errors = 0;
        int timeouts = 0;
        int mismatches = 0;
        long accepted = 0L;
        long responses = 0L;
        long measurementStartNanos = -1L;
        long measurementStopNanos = -1L;
        try {
            jfr = QualificationJfrRecording.start(jfrPath);
            runtime.start();
            runtime.publishReady();
            final int managementPort = runtime.managementServer().localAddress()
                    .orElseThrow(() -> new IOException("management listener did not bind"))
                    .getPort();
            management.add(ReleaseCandidateManagementClient.requestEvidence(
                    managementPort, "LIVE", COMMAND_TIMEOUT));
            management.add(ReleaseCandidateManagementClient.requestEvidence(
                    managementPort, "READY", COMMAND_TIMEOUT));
            management.add(ReleaseCandidateManagementClient.requestEvidence(
                    managementPort, "STATUS", COMMAND_TIMEOUT));
            management.add(ReleaseCandidateManagementClient.requestEvidence(
                    managementPort, "METRICS", COMMAND_TIMEOUT));
            final int protocolPort = runtime.protocolServer().localAddress()
                    .orElseThrow(() -> new IOException("protocol listener did not bind"))
                    .getPort();
            try (ProtocolV1QualificationClient client = new ProtocolV1QualificationClient(
                    new java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), protocolPort),
                    COMMAND_TIMEOUT)) {
                measurementStartNanos = System.nanoTime();
                final long durationNanos = matrix.duration().toNanos();
                final long measurementDeadline = Math.addExact(measurementStartNanos, durationNanos);
                for (int index = 0; index < workload.commandCount(); index++) {
                    final long targetNanos = pacedTarget(measurementStartNanos, index,
                            matrix.offeredRatePerSecond());
                    if (!waitUntil(targetNanos, measurementDeadline)) {
                        break;
                    }
                    final EngineCommand command = QualificationWorkloadV1.commandAtForRun(
                            workload, index);
                    final long exchangeStart = System.nanoTime();
                    try {
                        final QualificationExchange exchange = client.exchange(command, index + 1L);
                        latency.add(Math.max(1L, System.nanoTime() - exchangeStart));
                        accepted++;
                        responses += exchange.responseFrameCount();
                        transcript.append(index + 1L).append(',')
                                .append(exchange.transcriptDigestHex()).append('\n');
                    } catch (final SocketTimeoutException timeout) {
                        timeouts++;
                        throw timeout;
                    } catch (final IOException failure) {
                        errors++;
                        throw failure;
                    } catch (final RuntimeException mismatch) {
                        mismatches++;
                        throw mismatch;
                    }
                }
                if (accepted == workload.commandCount()) {
                    waitUntil(measurementDeadline, measurementDeadline);
                }
            }
            measurementStopNanos = System.nanoTime();
            publicPathCompleted = accepted == workload.commandCount();
            management.add(ReleaseCandidateManagementClient.requestEvidence(
                    managementPort, "STATUS", COMMAND_TIMEOUT));
            gracefulShutdown = shutdown(runtime);
        } finally {
            if (!gracefulShutdown) {
                try {
                    runtime.shutdown();
                    gracefulShutdown = true;
                } catch (final RuntimeException ignored) {
                    // The final observation records the unsuccessful terminal boundary.
                }
            }
            sampler.close();
            if (jfr != null) {
                try {
                    jfr.close();
                } catch (final IOException failure) {
                    errors++;
                }
            }
        }
        final Instant completed = Instant.now();
        if (measurementStartNanos > 0L && measurementStopNanos < measurementStartNanos) {
            measurementStopNanos = System.nanoTime();
        }
        final long elapsedNanos = measurementStartNanos > 0L
                ? Math.max(1L, measurementStopNanos - measurementStartNanos)
                : Math.max(1L, Duration.between(started, completed).toNanos());
        final List<GaSoakResourceSample> resources = sampler.samples();
        final GaSoakObservation g6Observation = new GaSoakObservation(
                physicalId, GaSoakMatrix.Stage.QUICK, elapsedNanos, accepted, accepted,
                errors, timeouts, mismatches, toArray(latency), new long[0], new long[0],
                resources, List.of(), publicPathCompleted, true, true, true, true,
                true, context.isApprovedCandidate() || configuredContext != null,
                context.controllerGitSha() != null, gracefulShutdown, true);
        final GaJfrEvidence jfrEvidence = GaJfrEvidence.inspect(jfrPath, true, true, true);
        final GaGcEvidence gcEvidence = GaGcEvidence.quick("NONE");
        final GaObservabilityObservation g8Observation = new GaObservabilityObservation(
                physicalId, GaSoakMatrix.Stage.QUICK, resources, gcEvidence, jfrEvidence,
                management, true, true, gracefulShutdown ? 0 : 1, sampler.samplingFailed(),
                sampler.hasUnknownOwnedFiles(), sampler.transientFilesCleanAfterShutdown(), true,
                context.isApprovedCandidate()
                        || configuredContext != null, context.controllerGitSha() != null);
        final GaSoakEvaluator.Evaluation g6 = GaSoakEvaluator.evaluateQuick(matrix, g6Observation);
        final GaObservabilityEvaluator.Evaluation g8 =
                GaObservabilityEvaluator.evaluateQuick(matrix, g8Observation);
        final Path configArtifact = root.resolve("configuration-v1.properties");
        final Path transcriptArtifact = root.resolve("transcript-v1.csv");
        final Path resourceArtifact = root.resolve("resource-samples-v1.csv");
        QualificationEvidencePublication.text(configArtifact, configuration.canonicalText());
        QualificationEvidencePublication.text(transcriptArtifact, transcript.toString());
        QualificationEvidencePublication.text(resourceArtifact, resourceText(resources));
        final Map<String, Path> artifacts = new LinkedHashMap<>();
        artifacts.put(configArtifact.getFileName().toString(), configArtifact);
        artifacts.put(transcriptArtifact.getFileName().toString(), transcriptArtifact);
        artifacts.put(resourceArtifact.getFileName().toString(), resourceArtifact);
        artifacts.put(jfrPath.getFileName().toString(), jfrPath);
        final GaSoakEvidencePublisher.PublishedQuick publication = GaSoakEvidencePublisher
                .publishQuick(root, matrix, g6Observation, g8Observation, g6, g8, context,
                        started, completed, artifacts);
        return new GaSoakQuickResult(g6Observation, g8, g6, publication);
    }

    /** Refuses to execute formal Stage A/B; those runs remain Human-gated. */
    public GaSoakQuickResult run(final GaSoakMatrix matrix, final Path outputDirectory)
            throws IOException {
        Objects.requireNonNull(matrix, "matrix");
        if (!matrix.isQuick()) {
            throw new IllegalArgumentException("formal TASK-052 soak execution is not authorized");
        }
        return runQuick(outputDirectory);
    }

    /** Returns the monotonic deadline for one offered command ordinal. */
    static long pacedTarget(
            final long measurementStartNanos,
            final long zeroBasedCommandOrdinal,
            final int offeredRatePerSecond) {
        if (measurementStartNanos < 0L || zeroBasedCommandOrdinal < 0L
                || offeredRatePerSecond <= 0) {
            throw new IllegalArgumentException("invalid pacing inputs");
        }
        final long offset = Math.multiplyExact(zeroBasedCommandOrdinal, 1_000_000_000L)
                / offeredRatePerSecond;
        return Math.addExact(measurementStartNanos, offset);
    }

    /** Waits until a monotonic target without crossing the fixed run deadline. */
    private static boolean waitUntil(final long targetNanos, final long deadlineNanos) {
        if (targetNanos > deadlineNanos) {
            return false;
        }
        while (true) {
            final long now = System.nanoTime();
            if (now >= targetNanos) {
                return now <= deadlineNanos;
            }
            final long remaining = targetNanos - now;
            LockSupport.parkNanos(Math.min(remaining, 10_000_000L));
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    private GaCorrectnessCanonicalContext context() throws IOException {
        return configuredContext == null
                ? GaCorrectnessCanonicalContext.fromSystem() : configuredContext;
    }

    private static RuntimeConfiguration runtimeConfiguration(
            final Path walRoot, final Path snapshotRoot) throws IOException {
        final int protocolPort = freePort();
        int managementPort = freePort();
        while (managementPort == protocolPort) {
            managementPort = freePort();
        }
        return new RuntimeConfiguration(
                walRoot,
                snapshotRoot,
                RecoveryMode.PURE_WAL,
                WAL_SEGMENT_SIZE_BYTES,
                WalDurabilityMode.SYNC_EACH_APPEND,
                1024,
                PipelineWaitMode.BLOCKING,
                InetAddress.getLoopbackAddress(),
                protocolPort,
                8192,
                16384,
                true,
                InetAddress.getLoopbackAddress(),
                managementPort,
                16,
                Duration.ofSeconds(1),
                Duration.ofSeconds(2));
    }

    private static boolean shutdown(final ReleaseCandidateRuntime runtime) {
        try {
            runtime.shutdown();
            return runtime.status().state()
                    == com.ultralatency.matching.app.RuntimeLifecycleState.STOPPED
                    && runtime.status().failureCode() == RuntimeFailureCode.NONE;
        } catch (final RuntimeException failure) {
            return false;
        }
    }

    private static Path newRunDirectory(final Path outputDirectory) throws IOException {
        final Path root = outputDirectory.toAbsolutePath().normalize();
        Files.createDirectories(root);
        return Files.createDirectory(root.resolve("g6-g8-quick-" + UUID.randomUUID()));
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    private static long[] toArray(final List<Long> values) {
        final long[] result = new long[values.size()];
        for (int index = 0; index < values.size(); index++) {
            result[index] = values.get(index);
        }
        return result;
    }

    private static String resourceText(final List<GaSoakResourceSample> samples) {
        final StringBuilder text = new StringBuilder(
                "physicalExecutionId,stage,sequence,monotonicNanos,threads,transientCount,"
                        + "transientBytes,heapUsedBytes\n");
        for (GaSoakResourceSample sample : samples) {
            text.append(sample.physicalExecutionId()).append(',').append(sample.stage()).append(',')
                    .append(sample.sequence()).append(',').append(sample.monotonicNanos())
                    .append(',').append(sample.threadCount()).append(',')
                    .append(sample.transientFileCount()).append(',')
                    .append(sample.transientFileBytes()).append(',')
                    .append(sample.heapUsedBytes()).append('\n');
        }
        return text.toString();
    }
}
