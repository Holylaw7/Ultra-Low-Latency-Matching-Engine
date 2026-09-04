package com.ultralatency.matching.qualification.ga.performance;

import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.qualification.ProtocolV2PacedQualificationClient;
import com.ultralatency.matching.qualification.QualificationArtifactHasher;
import com.ultralatency.matching.qualification.QualificationConfiguration;
import com.ultralatency.matching.qualification.QualificationPercentiles;
import com.ultralatency.matching.qualification.QualificationWorkloadV1;
import com.ultralatency.matching.qualification.ReleaseCandidateManagementClient;
import com.ultralatency.matching.qualification.ReleaseCandidateQualificationProcess;
import com.ultralatency.matching.qualification.ga.correctness.GaCorrectnessCanonicalContext;
import com.ultralatency.matching.recovery.online.RecoveryMode;
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

/** Executes the qualification-only RC2 formal G4 public-path campaign. */
public final class GaFormalPerformanceRunner {

    private static final int WAL_SEGMENT_SIZE_BYTES = 65_536;
    private static final int MAX_COMMANDS =
            QualificationConfiguration.MEMORY_STEADY_STATE_MAX_COMMAND_COUNT;
    private static final Duration READY_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration POLL_TIMEOUT = Duration.ofMillis(100);

    private final GaCorrectnessCanonicalContext configuredContext;

    /** Creates a runner which resolves the frozen candidate at execution time. */
    public GaFormalPerformanceRunner() {
        this(null);
    }

    /** Creates a runner with an explicit context, primarily for direct tests. */
    public GaFormalPerformanceRunner(final GaCorrectnessCanonicalContext context) {
        configuredContext = context;
    }

    /**
     * Runs exactly the three-run frozen G4 campaign.  The method is intentionally not used by
     * the Quick lane and rejects an exploded/in-process candidate before starting any child.
     */
    public GaPerformanceFormalResult run(
            final Path packagedArtifact,
            final Path outputDirectory) throws IOException {
        Objects.requireNonNull(packagedArtifact, "packagedArtifact");
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        final GaCorrectnessCanonicalContext context = context();
        final GaPerformanceMatrix matrix = GaPerformanceMatrix.approved();
        GaFormalPerformanceContract.requireFrozenIdentity(context, matrix, packagedArtifact);
        final String qualificationJar = context.qualificationJarSha256();
        if (qualificationJar == null || qualificationJar.isBlank()) {
            throw new IOException("formal G4 requires a packaged qualification artifact");
        }
        final Path qualificationArtifact = context.qualificationJarPath();
        if (!qualificationJar.equals(QualificationArtifactHasher.sha256(qualificationArtifact))) {
            throw new IOException("formal G4 qualification JAR identity does not match artifact");
        }
        final Path root = newCampaignDirectory(outputDirectory);
        final Instant campaignStarted = Instant.now();
        final List<GaFormalPerformanceEvidencePublisher.PublishedRun> runs = new ArrayList<>();
        final List<GaPerformanceEvaluator.Evaluation> evaluations = new ArrayList<>();
        for (int index = 1; index <= matrix.runCount(); index++) {
            final GaFormalRunResult run = runPerformance(
                    packagedArtifact, root.resolve(String.format("run-%02d", index)),
                    qualificationArtifact, context, matrix, qualificationJar, index);
            runs.add(run.publishedRun());
            evaluations.add(run.evaluation());
            if (!run.evaluation().passed()) {
                throw new IOException("formal G4 stopped at performance run " + index
                        + "; preserved gate result: " + run.publishedRun().gateResultPath());
            }
        }
        final LifecycleResult lifecycle = runLifecycle(
                packagedArtifact, qualificationArtifact, root.resolve("lifecycle"), matrix);
        if (!lifecycle.passed()) {
            throw new IOException("formal G4 stopped at lifecycle campaign; preserved lifecycle evidence: "
                    + root.resolve("lifecycle"));
        }
        final ManagementResult management = runManagement(
                packagedArtifact, qualificationArtifact, root.resolve("management"), matrix);
        if (!management.passed()) {
            throw new IOException("formal G4 stopped at management campaign; preserved management evidence: "
                    + root.resolve("management"));
        }
        final List<GaPerformanceEvaluator.Criterion> campaignCriteria = campaignCriteria(
                evaluations, lifecycle, management);
        final boolean passed = campaignCriteria.stream().allMatch(
                GaPerformanceEvaluator.Criterion::passed);
        final Path campaign = GaFormalPerformanceEvidencePublisher.publishCampaign(
                root, context, matrix, runs, campaignStarted, Instant.now(),
                campaignCriteria, campaignMetrics(lifecycle, management), passed);
        final Path gate = GaFormalPerformanceEvidencePublisher.publishCampaignGate(
                root, context, runs, campaign, campaignCriteria, campaignStarted, Instant.now(),
                passed ? "NONE" : "B1",
                passed ? "Formal G4 contract passed."
                        : "One or more formal G4 predicates failed.", passed);
        return new GaPerformanceFormalResult(passed, matrix, runs, root, campaign, gate);
    }

    private GaCorrectnessCanonicalContext context() throws IOException {
        return configuredContext == null
                ? GaCorrectnessCanonicalContext.fromSystem() : configuredContext;
    }

    private static Path newCampaignDirectory(final Path outputDirectory) throws IOException {
        final Path root = outputDirectory.toAbsolutePath().normalize();
        Files.createDirectories(root);
        return Files.createDirectory(root.resolve("g4-formal-" + UUID.randomUUID()));
    }

    private static GaFormalRunResult runPerformance(
            final Path artifact,
            final Path qualificationArtifact,
            final Path root,
            final GaCorrectnessCanonicalContext context,
            final GaPerformanceMatrix matrix,
            final String qualificationJar,
            final int runOrdinal) throws IOException {
        Files.createDirectories(root);
        final Path storage = root.resolve("storage");
        final Path wal = storage.resolve("wal");
        final Path snapshots = storage.resolve("snapshots");
        Files.createDirectories(wal);
        Files.createDirectories(snapshots);
        final Path configurationPath = writeConfiguration(root, wal, snapshots);
        final QualificationConfiguration workload = new QualificationConfiguration(
                com.ultralatency.matching.qualification.QualificationProfile.MEMORY_STEADY_STATE_V1,
                matrix.seed(), MAX_COMMANDS, GaFormalPerformanceContract.COMMAND_TIMEOUT, root);
        final String physicalExecutionId = UUID.randomUUID().toString();
        final Instant started = Instant.now();
        final RunAccumulator accumulator = new RunAccumulator();
        long measurementStart;
        long measurementEnd;
        try (ReleaseCandidateQualificationProcess child = ReleaseCandidateQualificationProcess
                .startPackagedCandidate(
                        artifact, qualificationArtifact, configurationPath,
                        root.resolve("process-evidence"), READY_TIMEOUT, true);
                ProtocolV2PacedQualificationClient client =
                        new ProtocolV2PacedQualificationClient(
                                new java.net.InetSocketAddress("127.0.0.1", child.protocolPort()),
                                GaFormalPerformanceContract.COMMAND_TIMEOUT,
                                GaPerformanceMatrix.APPROVED_PROTOCOL_V2_WINDOW)) {
            ReleaseCandidateManagementClient.requireReady(ReleaseCandidateManagementClient.request(
                    child.managementPort(), "READY", READY_TIMEOUT));
            final CommandCursor cursor = new CommandCursor();
            runContinuousPhase(client, workload, cursor, GaFormalPerformanceContract.WARMUP,
                    0L, 0L, accumulator, false);
            awaitDrain(client, accumulator, false, 0L, 0L);
            measurementStart = System.nanoTime();
            final long deadline = addDeadline(measurementStart, matrix.runDuration());
            runContinuousPhase(client, workload, cursor, matrix.runDuration(), measurementStart,
                    deadline, accumulator, true);
            measurementEnd = deadline;
            awaitDrain(client, accumulator, true, measurementStart, measurementEnd);
            accumulator.capture(client);
            final int exit = child.gracefulShutdown(GaFormalPerformanceContract.PROCESS_TIMEOUT);
            if (exit != 0) {
                accumulator.errors++;
            }
        } catch (final IOException | RuntimeException failure) {
            accumulator.errors++;
            accumulator.raw.append("failure.type=").append(failure.getClass().getName())
                    .append('\n').append("failure.message=")
                    .append(String.valueOf(failure.getMessage())).append('\n');
            measurementStart = accumulator.measurementStart == 0L
                    ? System.nanoTime() : accumulator.measurementStart;
            measurementEnd = Math.max(measurementStart + 1L, System.nanoTime());
        }
        final Instant completed = Instant.now();
        final Map<String, String> environment = GaPerformanceEnvironment.capture(root);
        final Map<String, String> configuration = GaFormalPerformanceContract.configurationFields(
                context, matrix, context.candidate().applicationJarSha256(), qualificationJar);
        final long elapsed = Math.max(1L, measurementEnd - measurementStart);
        final GaPerformanceObservation observation = accumulator.observation(
                elapsed, measurementStart, measurementEnd,
                GaPerformanceEnvironment.matchesReference(environment));
        final GaPerformanceEvaluator.Evaluation evaluation = evaluateSafely(observation);
        final boolean publicPathCompleted = evaluation.passed();
        final String outcome = publicPathCompleted ? "PASS" : accumulator.errors == 0 ? "FAIL" : "ABORTED";
        final String failureCode = publicPathCompleted ? "NONE"
                : !observation.comparabilityBound() ? "B3"
                : accumulator.errors == 0 ? "B1" : "B2";
        final String raw = accumulator.rawEvidence(context, matrix, runOrdinal, physicalExecutionId,
                measurementStart, measurementEnd, environment, observation);
        final List<GaFormalPerformanceEvidencePublisher.LatencySample> samples =
                List.copyOf(accumulator.samples);
        final GaFormalPerformanceEvidencePublisher.RunInput input =
                new GaFormalPerformanceEvidencePublisher.RunInput(
                        matrix, context, physicalExecutionId, started, completed,
                        measurementStart, measurementEnd, observation, accumulator.offered,
                        accumulator.trades, samples, accumulator.capacityMetrics(), configuration,
                        environment, raw, outcome, failureCode, evaluation.criteria());
        final GaFormalPerformanceEvidencePublisher.PublishedRun published =
                GaFormalPerformanceEvidencePublisher.publishRun(root, input);
        return new GaFormalRunResult(published, evaluation);
    }

    private static GaPerformanceEvaluator.Evaluation evaluateSafely(
            final GaPerformanceObservation observation) {
        return GaPerformanceEvaluator.evaluateRun(observation);
    }

    private static void runContinuousPhase(
            final ProtocolV2PacedQualificationClient client,
            final QualificationConfiguration workload,
            final CommandCursor cursor,
            final Duration duration,
            final long measurementStart,
            final long measurementEnd,
            final RunAccumulator accumulator,
            final boolean collect) throws IOException {
        final long start = measurementStart > 0L ? measurementStart : System.nanoTime();
        final long deadline = measurementEnd > start ? measurementEnd : addDeadline(start, duration);
        while (System.nanoTime() < deadline) {
            drain(client, accumulator, collect, measurementStart, measurementEnd);
            boolean offered = false;
            while (System.nanoTime() < deadline && client.inFlight() < client.maxInFlight()) {
                final EngineCommand command = QualificationWorkloadV1.commandAtForRun(
                        workload, cursor.commandIndex++);
                final long requestId = cursor.requestId++;
                if (!client.tryOffer(command, requestId)) {
                    break;
                }
                offered = true;
                if (collect) {
                    accumulator.offered++;
                }
            }
            if (!offered || client.inFlight() >= client.maxInFlight()) {
                final List<ProtocolV2PacedQualificationClient.CompletedExchange> values =
                        client.awaitCompleted(POLL_TIMEOUT);
                accumulator.accept(values, collect, measurementStart, measurementEnd);
            }
        }
        drain(client, accumulator, collect, measurementStart, measurementEnd);
    }

    private static void awaitDrain(
            final ProtocolV2PacedQualificationClient client,
            final RunAccumulator accumulator,
            final boolean collect,
            final long measurementStart,
            final long measurementEnd) throws IOException {
        final long deadline = addDeadline(System.nanoTime(), DRAIN_TIMEOUT);
        while ((client.inFlight() > 0 || client.completedCount() > 0)
                && System.nanoTime() < deadline) {
            drain(client, accumulator, collect, measurementStart, measurementEnd);
            if (client.inFlight() > 0) {
                accumulator.accept(client.awaitCompleted(POLL_TIMEOUT), collect,
                        measurementStart, measurementEnd);
            }
        }
        if (client.inFlight() > 0 || client.completedCount() > 0) {
            accumulator.timeouts++;
        }
    }

    private static void drain(
            final ProtocolV2PacedQualificationClient client,
            final RunAccumulator accumulator,
            final boolean collect,
            final long measurementStart,
            final long measurementEnd) throws IOException {
        accumulator.accept(client.drainCompleted(), collect, measurementStart, measurementEnd);
    }

    private static long addDeadline(final long start, final Duration duration) {
        try {
            return Math.addExact(start, duration.toNanos());
        } catch (final ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static Path writeConfiguration(
            final Path root, final Path wal, final Path snapshots) throws IOException {
        final int protocolPort = freePort();
        int managementPort = freePort();
        while (managementPort == protocolPort) {
            managementPort = freePort();
        }
        final Map<String, String> values = new LinkedHashMap<>();
        values.put("storage.wal.directory", wal.toAbsolutePath().toString());
        values.put("storage.snapshot.directory", snapshots.toAbsolutePath().toString());
        values.put("recovery.mode", RecoveryMode.PURE_WAL.name());
        values.put("wal.segment.size.bytes", Integer.toString(WAL_SEGMENT_SIZE_BYTES));
        values.put("wal.durability.mode", GaPerformanceMatrix.APPROVED_WAL_MODE);
        values.put("pipeline.capacity", "1024");
        values.put("pipeline.wait.mode", "BLOCKING");
        values.put("protocol.bind.address", "127.0.0.1");
        values.put("protocol.port", Integer.toString(protocolPort));
        values.put("protocol.write.low.bytes", "8192");
        values.put("protocol.write.high.bytes", "16384");
        values.put("management.enabled", "true");
        values.put("management.bind.address", "127.0.0.1");
        values.put("management.port", Integer.toString(managementPort));
        values.put("management.max.connections", "16");
        values.put("management.request.timeout.ms", "1000");
        values.put("lifecycle.shutdown.timeout.ms", "2000");
        final StringBuilder text = new StringBuilder();
        values.forEach((key, value) -> text.append(key).append('=').append(value).append('\n'));
        final Path target = root.resolve("runtime.properties");
        com.ultralatency.matching.qualification.QualificationEvidencePublication.text(
                target, text.toString());
        return target;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    private static final class CommandCursor {
        private long commandIndex;
        private long requestId = 1L;
    }

    private record GaFormalRunResult(
            GaFormalPerformanceEvidencePublisher.PublishedRun publishedRun,
            GaPerformanceEvaluator.Evaluation evaluation) {
    }

    private static final class RunAccumulator {
        private final List<Long> latencies = new ArrayList<>();
        private final List<GaFormalPerformanceEvidencePublisher.LatencySample> samples =
                new ArrayList<>();
        private final List<Long> releaseDelays = new ArrayList<>();
        private final StringBuilder raw = new StringBuilder();
        private long offered;
        private long accepted;
        private long responses;
        private long trades;
        private int errors;
        private int timeouts;
        private int mismatches;
        private long measurementStart;
        private long measurementEnd;
        private int maxInFlight;
        private int maxCompleted;
        private long readerWakes;
        private long releaseCount;

        private void accept(
                final List<ProtocolV2PacedQualificationClient.CompletedExchange> values,
                final boolean collect,
                final long start,
                final long end) {
            for (ProtocolV2PacedQualificationClient.CompletedExchange value : values) {
                if (!collect || value.completedNanos() < start || value.completedNanos() >= end) {
                    continue;
                }
                accepted++;
                responses++;
                trades += value.exchange().matches().size();
                latencies.add(value.latencyNanos());
                releaseDelays.add(value.capacityReleaseDelayNanos());
                samples.add(new GaFormalPerformanceEvidencePublisher.LatencySample(
                        value.requestId(), value.exchange().commandSequence(), value.offeredNanos(),
                        value.completedNanos(), value.capacityReleaseNanos()));
            }
        }

        private void capture(final ProtocolV2PacedQualificationClient client) {
            maxInFlight = client.maximumObservedInFlight();
            maxCompleted = client.maximumObservedCompleted();
            readerWakes = client.readerWakeCount();
            releaseCount = client.capacityReleaseCount();
        }

        private GaPerformanceObservation observation(
                final long elapsed, final long start, final long end,
                final boolean comparable) {
            measurementStart = start;
            measurementEnd = end;
            return new GaPerformanceObservation(
                    Math.max(1, Math.toIntExact(Math.min(Integer.MAX_VALUE, offered))), accepted,
                    responses, elapsed, toArray(latencies), new long[0], new long[0],
                    accepted * 1_000_000_000.0 / elapsed,
                    accepted * 1_000_000_000.0 / elapsed, 0L, 0L, errors, timeouts, mismatches,
                    accepted == offered && errors == 0 && timeouts == 0 && mismatches == 0,
                    true, comparable, true, true);
        }

        private GaFormalPerformanceEvidencePublisher.CapacityMetrics capacityMetrics() {
            return new GaFormalPerformanceEvidencePublisher.CapacityMetrics(
                    maxInFlight, maxInFlight, maxCompleted, readerWakes, releaseCount,
                    toArray(releaseDelays));
        }

        private String rawEvidence(
                final GaCorrectnessCanonicalContext context,
                final GaPerformanceMatrix matrix,
                final int runOrdinal,
                final String physicalExecutionId,
                final long start,
                final long end,
                final Map<String, String> environment,
                final GaPerformanceObservation observation) {
            final StringBuilder result = new StringBuilder()
                    .append("schema=ga-g4-performance-v2\n")
                    .append("formal=true\n")
                    .append("runOrdinal=").append(runOrdinal).append('\n')
                    .append("physicalExecutionId=").append(physicalExecutionId).append('\n')
                    .append("candidate.tag=").append(context.candidate().tag()).append('\n')
                    .append("candidate.productionSha=")
                    .append(context.candidate().productionSha()).append('\n')
                    .append("protocol=v2\nwindow=8\nwalMode=SYNC_EACH_APPEND\n")
                    .append("loadModel=BOUNDED_CLOSED_LOOP_CONTINUOUS_REFILL\n")
                    .append("measurementStartNanos=").append(start).append('\n')
                    .append("measurementEndNanos=").append(end).append('\n')
                    .append("measurementDurationNanos=").append(end - start).append('\n')
                    .append("offeredCommands=").append(offered).append('\n')
                    .append("acceptedCommands=").append(accepted).append('\n')
                    .append("responseCount=").append(responses).append('\n')
                    .append("errors=").append(errors).append('\n')
                    .append("timeouts=").append(timeouts).append('\n')
                    .append("mismatches=").append(mismatches).append('\n');
            final QualificationPercentiles.Summary latency = observation.latency();
            latency.appendTo(result, "latency");
            result.append(raw);
            environment.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                    result.append("environment.").append(entry.getKey()).append('=').append(
                            entry.getValue()).append('\n'));
            return result.toString();
        }

        private static long[] toArray(final List<Long> values) {
            final long[] result = new long[values.size()];
            for (int index = 0; index < values.size(); index++) {
                result[index] = values.get(index);
            }
            return result;
        }
    }

    private record LifecycleResult(long[] startup, long[] shutdown, boolean passed) {
    }

    private record ManagementResult(
            boolean pairAPassed,
            boolean pairBPassed,
            double pairAIdleThroughput,
            double pairAStatusThroughput,
            long pairAIdleP99,
            long pairAStatusP99,
            double pairBIdleThroughput,
            double pairBStatusThroughput,
            long pairBIdleP99,
            long pairBStatusP99) {
        private boolean passed() {
            return pairAPassed && pairBPassed;
        }
    }

    private static LifecycleResult runLifecycle(
            final Path artifact,
            final Path qualificationArtifact,
            final Path root,
            final GaPerformanceMatrix matrix) throws IOException {
        final long[] startup = new long[GaFormalPerformanceContract.LIFECYCLE_CYCLES];
        final long[] shutdown = new long[GaFormalPerformanceContract.LIFECYCLE_CYCLES];
        boolean passed = true;
        for (int index = 0; index < startup.length; index++) {
            final Path cycle = root.resolve(String.format("cycle-%02d", index + 1));
            Files.createDirectories(cycle);
            final Path config = writeConfiguration(cycle, cycle.resolve("wal"),
                    cycle.resolve("snapshots"));
            final long start = System.nanoTime();
            try (ReleaseCandidateQualificationProcess child = ReleaseCandidateQualificationProcess
                    .startPackagedCandidate(
                            artifact, qualificationArtifact, config,
                            cycle.resolve("process-evidence"), READY_TIMEOUT, true)) {
                ReleaseCandidateManagementClient.requireReady(ReleaseCandidateManagementClient.request(
                        child.managementPort(), "READY", READY_TIMEOUT));
                startup[index] = Math.max(1L, System.nanoTime() - start);
                final long stop = System.nanoTime();
                final int exit = child.gracefulShutdown(GaFormalPerformanceContract.PROCESS_TIMEOUT);
                shutdown[index] = Math.max(1L, System.nanoTime() - stop);
                if (exit != 0) {
                    passed = false;
                    break;
                }
            } catch (final IOException | RuntimeException failure) {
                passed = false;
                break;
            }
        }
        return new LifecycleResult(startup, shutdown, passed);
    }

    private static ManagementResult runManagement(
            final Path artifact,
            final Path qualificationArtifact,
            final Path root,
            final GaPerformanceMatrix matrix) throws IOException {
        final ManagementTrial aIdle = runManagementTrial(
                artifact, qualificationArtifact, root.resolve("pair-a-idle"), false, matrix);
        final ManagementTrial aStatus = runManagementTrial(
                artifact, qualificationArtifact, root.resolve("pair-a-status"), true, matrix);
        final boolean pairA = compares(aIdle, aStatus);
        if (!pairA) {
            return new ManagementResult(false, false, aIdle.throughput, aStatus.throughput,
                    aIdle.responseP99, aStatus.responseP99, 0.0d, 0.0d, 0L, 0L);
        }
        final ManagementTrial bStatus = runManagementTrial(
                artifact, qualificationArtifact, root.resolve("pair-b-status"), true, matrix);
        final ManagementTrial bIdle = runManagementTrial(
                artifact, qualificationArtifact, root.resolve("pair-b-idle"), false, matrix);
        final boolean pairB = compares(bIdle, bStatus);
        return new ManagementResult(pairA, pairB, aIdle.throughput, aStatus.throughput,
                aIdle.responseP99, aStatus.responseP99, bIdle.throughput, bStatus.throughput,
                bIdle.responseP99, bStatus.responseP99);
    }

    private static ManagementTrial runManagementTrial(
            final Path artifact,
            final Path qualificationArtifact,
            final Path root,
            final boolean pollStatus,
            final GaPerformanceMatrix matrix) throws IOException {
        Files.createDirectories(root);
        final Path config = writeConfiguration(root, root.resolve("wal"), root.resolve("snapshots"));
        final QualificationConfiguration workload = new QualificationConfiguration(
                com.ultralatency.matching.qualification.QualificationProfile.MEMORY_STEADY_STATE_V1,
                matrix.seed(), MAX_COMMANDS, GaFormalPerformanceContract.COMMAND_TIMEOUT, root);
        long accepted = 0L;
        final List<Long> responseLatency = new ArrayList<>();
        final List<Long> statusLatency = new ArrayList<>();
        try (ReleaseCandidateQualificationProcess child = ReleaseCandidateQualificationProcess
                .startPackagedCandidate(
                        artifact, qualificationArtifact, config,
                        root.resolve("process-evidence"), READY_TIMEOUT, true);
                ProtocolV2PacedQualificationClient client =
                        new ProtocolV2PacedQualificationClient(
                                new java.net.InetSocketAddress("127.0.0.1", child.protocolPort()),
                                GaFormalPerformanceContract.COMMAND_TIMEOUT,
                                GaPerformanceMatrix.APPROVED_PROTOCOL_V2_WINDOW)) {
            ReleaseCandidateManagementClient.requireReady(ReleaseCandidateManagementClient.request(
                    child.managementPort(), "READY", READY_TIMEOUT));
            final CommandCursor cursor = new CommandCursor();
            final long warmupEnd = addDeadline(System.nanoTime(),
                    GaFormalPerformanceContract.MANAGEMENT_WARMUP);
            while (System.nanoTime() < warmupEnd) {
                offerAndDrain(client, workload, cursor, responseLatency, false, 0L, 0L);
            }
            awaitDrain(client, new RunAccumulator(), false, 0L, 0L);
            final long start = System.nanoTime();
            final long end = addDeadline(start, GaFormalPerformanceContract.MANAGEMENT_MEASUREMENT);
            long nextStatus = start;
            while (System.nanoTime() < end) {
                final List<ProtocolV2PacedQualificationClient.CompletedExchange> values =
                        client.drainCompleted();
                for (ProtocolV2PacedQualificationClient.CompletedExchange value : values) {
                    if (value.completedNanos() >= start && value.completedNanos() < end) {
                        accepted++;
                        responseLatency.add(value.latencyNanos());
                    }
                }
                while (System.nanoTime() < end && client.inFlight() < client.maxInFlight()) {
                    final EngineCommand command = QualificationWorkloadV1.commandAtForRun(
                            workload, cursor.commandIndex++);
                    if (!client.tryOffer(command, cursor.requestId++)) {
                        break;
                    }
                }
                final long now = System.nanoTime();
                if (pollStatus && now >= nextStatus) {
                    final long statusStart = System.nanoTime();
                    ReleaseCandidateManagementClient.request(
                            child.managementPort(), "STATUS", Duration.ofSeconds(5));
                    statusLatency.add(Math.max(1L, System.nanoTime() - statusStart));
                    nextStatus = addDeadline(nextStatus,
                            GaFormalPerformanceContract.MANAGEMENT_INTERVAL);
                }
                if (client.inFlight() > 0) {
                    final List<ProtocolV2PacedQualificationClient.CompletedExchange> ready =
                            client.awaitCompleted(POLL_TIMEOUT);
                    for (ProtocolV2PacedQualificationClient.CompletedExchange value : ready) {
                        if (value.completedNanos() >= start && value.completedNanos() < end) {
                            accepted++;
                            responseLatency.add(value.latencyNanos());
                        }
                    }
                }
            }
            awaitDrain(client, new RunAccumulator(), true, start, end);
            child.gracefulShutdown(GaFormalPerformanceContract.PROCESS_TIMEOUT);
            final long elapsed = Math.max(1L, end - start);
            return new ManagementTrial(
                    accepted * 1_000_000_000.0 / elapsed,
                    QualificationPercentiles.summarize(toArray(responseLatency)).p99Nanos(),
                    statusLatency.isEmpty() ? 0L
                            : QualificationPercentiles.summarize(toArray(statusLatency)).p99Nanos());
        }
    }

    private static void offerAndDrain(
            final ProtocolV2PacedQualificationClient client,
            final QualificationConfiguration workload,
            final CommandCursor cursor,
            final List<Long> responses,
            final boolean collect,
            final long start,
            final long end) throws IOException {
        if (client.inFlight() < client.maxInFlight()) {
            client.tryOffer(QualificationWorkloadV1.commandAtForRun(workload, cursor.commandIndex++),
                    cursor.requestId++);
        }
        for (ProtocolV2PacedQualificationClient.CompletedExchange value : client.drainCompleted()) {
            if (!collect || value.completedNanos() >= start && value.completedNanos() < end) {
                responses.add(value.latencyNanos());
            }
        }
        if (client.inFlight() > 0) {
            client.awaitCompleted(POLL_TIMEOUT);
        }
    }

    private static boolean compares(final ManagementTrial idle, final ManagementTrial status) {
        return status.throughput >= idle.throughput * 0.90d
                && status.responseP99 <= idle.responseP99 * 1.10d;
    }

    private static List<GaPerformanceEvaluator.Criterion> campaignCriteria(
            final List<GaPerformanceEvaluator.Evaluation> evaluations,
            final LifecycleResult lifecycle,
            final ManagementResult management) {
        final List<GaPerformanceEvaluator.Criterion> criteria = new ArrayList<>();
        for (int index = 0; index < evaluations.size(); index++) {
            criteria.add(new GaPerformanceEvaluator.Criterion(
                    "run." + (index + 1) + ".result",
                    Boolean.toString(evaluations.get(index).passed()), "EQ", "true",
                    evaluations.get(index).passed()));
        }
        final QualificationPercentiles.Summary startup =
                QualificationPercentiles.summarize(lifecycle.startup());
        final QualificationPercentiles.Summary shutdown =
                QualificationPercentiles.summarize(lifecycle.shutdown());
        criteria.add(new GaPerformanceEvaluator.Criterion(
                "lifecycle.startup.sampleCount", Integer.toString(lifecycle.startup().length),
                "GE", "60", lifecycle.startup().length == 60));
        criteria.add(new GaPerformanceEvaluator.Criterion(
                "lifecycle.shutdown.sampleCount", Integer.toString(lifecycle.shutdown().length),
                "GE", "60", lifecycle.shutdown().length == 60));
        criteria.add(new GaPerformanceEvaluator.Criterion(
                "lifecycle.startup.p99Nanos", Long.toString(startup.p99Nanos()), "LE",
                Long.toString(GaPerformanceEvaluator.MAX_LIFECYCLE_P99_NANOS),
                lifecycle.passed() && startup.p99Nanos()
                        <= GaPerformanceEvaluator.MAX_LIFECYCLE_P99_NANOS));
        criteria.add(new GaPerformanceEvaluator.Criterion(
                "lifecycle.shutdown.p99Nanos", Long.toString(shutdown.p99Nanos()), "LE",
                Long.toString(GaPerformanceEvaluator.MAX_LIFECYCLE_P99_NANOS),
                lifecycle.passed() && shutdown.p99Nanos()
                        <= GaPerformanceEvaluator.MAX_LIFECYCLE_P99_NANOS));
        criteria.add(new GaPerformanceEvaluator.Criterion(
                "management.pairA", Boolean.toString(management.pairAPassed()), "EQ", "true",
                management.pairAPassed()));
        criteria.add(new GaPerformanceEvaluator.Criterion(
                "management.pairB", Boolean.toString(management.pairBPassed()), "EQ", "true",
                management.pairBPassed()));
        return List.copyOf(criteria);
    }

    private static Map<String, String> campaignMetrics(
            final LifecycleResult lifecycle, final ManagementResult management) {
        final Map<String, String> values = new LinkedHashMap<>();
        final QualificationPercentiles.Summary startup =
                QualificationPercentiles.summarize(lifecycle.startup());
        final QualificationPercentiles.Summary shutdown =
                QualificationPercentiles.summarize(lifecycle.shutdown());
        values.put("schema", "ga-g4-performance-campaign-v2");
        values.put("lifecycle.startup.count", Integer.toString(lifecycle.startup().length));
        values.put("lifecycle.startup.p99Nanos", Long.toString(startup.p99Nanos()));
        values.put("lifecycle.shutdown.count", Integer.toString(lifecycle.shutdown().length));
        values.put("lifecycle.shutdown.p99Nanos", Long.toString(shutdown.p99Nanos()));
        values.put("management.pairA.idleThroughput", Double.toString(
                management.pairAIdleThroughput()));
        values.put("management.pairA.statusThroughput", Double.toString(
                management.pairAStatusThroughput()));
        values.put("management.pairA.idleP99Nanos", Long.toString(management.pairAIdleP99()));
        values.put("management.pairA.statusP99Nanos", Long.toString(management.pairAStatusP99()));
        values.put("management.pairB.idleThroughput", Double.toString(
                management.pairBIdleThroughput()));
        values.put("management.pairB.statusThroughput", Double.toString(
                management.pairBStatusThroughput()));
        values.put("management.pairB.idleP99Nanos", Long.toString(management.pairBIdleP99()));
        values.put("management.pairB.statusP99Nanos", Long.toString(management.pairBStatusP99()));
        values.put("management.passed", Boolean.toString(management.passed()));
        return Map.copyOf(values);
    }

    private static long[] toArray(final List<Long> values) {
        final long[] result = new long[values.size()];
        for (int index = 0; index < values.size(); index++) {
            result[index] = values.get(index);
        }
        return result;
    }

    private record ManagementTrial(double throughput, long responseP99, long managementP99) {
    }
}
