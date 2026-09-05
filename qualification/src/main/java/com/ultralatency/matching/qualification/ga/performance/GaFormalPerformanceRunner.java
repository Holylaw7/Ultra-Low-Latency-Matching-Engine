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
import com.ultralatency.matching.qualification.ga.observability.GaManagementEvidence;
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
                    packagedArtifact, qualificationArtifact,
                    root.resolve(String.format("run-%02d", index)), context, matrix,
                    qualificationJar, index);
            runs.add(run.publishedRun());
            evaluations.add(run.evaluation());
            if (!run.evaluation().passed()) {
                throw new IOException("formal G4 stopped at performance run " + index
                        + "; preserved gate result: " + run.publishedRun().gateResultPath());
            }
        }
        final LifecycleResult lifecycle = runLifecycle(
                packagedArtifact, qualificationArtifact, root.resolve("lifecycle"), context,
                qualificationJar, matrix);
        if (!shouldStartManagement(lifecycle)) {
            throw new IOException("formal G4 stopped at lifecycle campaign; preserved lifecycle evidence: "
                    + root.resolve("lifecycle"));
        }
        final ManagementResult management = runManagement(
                packagedArtifact, qualificationArtifact, root.resolve("management"), context,
                qualificationJar, matrix);
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

    static GaFormalRunResult runPerformance(
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
            accumulator.markReadyObserved();
            final CommandCursor cursor = new CommandCursor();
            final Duration warmup = matrix.isApproved()
                    ? GaFormalPerformanceContract.WARMUP : Duration.ZERO;
            runContinuousPhase(client, workload, cursor, warmup,
                    0L, 0L, accumulator, false);
            awaitDrain(client, accumulator, false, 0L, 0L);
            measurementStart = System.nanoTime();
            accumulator.markMeasurementStarted();
            final long deadline = addDeadline(measurementStart, matrix.runDuration());
            runContinuousPhase(client, workload, cursor, matrix.runDuration(), measurementStart,
                    deadline, accumulator, true);
            measurementEnd = deadline;
            awaitDrain(client, accumulator, true, measurementStart, measurementEnd);
            accumulator.capture(client);
            final int exit = child.gracefulShutdown(GaFormalPerformanceContract.PROCESS_TIMEOUT);
            accumulator.markShutdown(exit, !child.isAlive());
            if (exit != 0) {
                accumulator.errors++;
                accumulator.raw.append("shutdown.exitCode=").append(exit).append('\n');
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
        final boolean environmentComparable = GaPerformanceEnvironment.matchesReference(environment);
        final boolean configurationBound = configurationMatches(configurationPath, matrix);
        final boolean candidateBound = candidateMatches(context, artifact);
        final boolean controllerBound = controllerMatches(context);
        final boolean publicPathCompleted = accumulator.measurementStarted
                && accumulator.readyObserved && accumulator.shutdownCompleted;
        final GaPerformanceObservation observation = accumulator.observation(
                elapsed, measurementStart, measurementEnd,
                configurationBound, environmentComparable, candidateBound, controllerBound,
                publicPathCompleted);
        final GaPerformanceEvaluator.Evaluation evaluation = evaluateSafely(observation);
        final String outcome = evaluation.passed() ? "PASS"
                : publicPathCompleted ? "FAIL" : "ABORTED";
        final String failureCode = evaluation.passed() ? "NONE"
                : failureCode(observation, accumulator);
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

    private static String failureCode(
            final GaPerformanceObservation observation,
            final RunAccumulator accumulator) {
        if (accumulator.shutdownExitCode != 0) {
            return "B1";
        }
        return GaPerformanceEvaluator.failureCode(observation, observation.latency());
    }

    private static boolean candidateMatches(
            final GaCorrectnessCanonicalContext context,
            final Path packagedArtifact) throws IOException {
        return context.isApprovedCandidate()
                && context.candidate().applicationJarSha256().equals(
                        QualificationArtifactHasher.sha256(packagedArtifact));
    }

    private static boolean controllerMatches(final GaCorrectnessCanonicalContext context) {
        try {
            final Path repository = context.repository();
            if (!Files.exists(repository.resolve(".git"))) {
                return false;
            }
            final Process process = new ProcessBuilder(
                    "git", "-C", repository.toString(), "rev-parse", "HEAD")
                    .redirectErrorStream(true).start();
            final String head = new String(process.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.US_ASCII).trim();
            return process.waitFor() == 0 && context.controllerGitSha().equals(head);
        } catch (final IOException exception) {
            return false;
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static boolean configurationMatches(
            final Path configurationPath,
            final GaPerformanceMatrix matrix) {
        try {
            final java.util.Properties properties = new java.util.Properties();
            try (java.io.InputStream input = Files.newInputStream(configurationPath)) {
                properties.load(input);
            }
            final java.util.Set<String> expectedKeys = java.util.Set.of(
                    "storage.wal.directory", "storage.snapshot.directory", "recovery.mode",
                    "wal.segment.size.bytes", "wal.durability.mode", "pipeline.capacity",
                    "pipeline.wait.mode", "protocol.bind.address", "protocol.port",
                    "protocol.write.low.bytes", "protocol.write.high.bytes",
                    "management.enabled", "management.bind.address", "management.port",
                    "management.max.connections", "management.request.timeout.ms",
                    "lifecycle.shutdown.timeout.ms");
            if (!matrix.isApproved()
                    || !GaPerformanceMatrix.APPROVED_PROFILE.equals(matrix.profile())
                    || !properties.stringPropertyNames().equals(expectedKeys)) {
                return false;
            }
            final Path wal = Path.of(properties.getProperty("storage.wal.directory"))
                    .toAbsolutePath().normalize();
            final Path snapshots = Path.of(properties.getProperty("storage.snapshot.directory"))
                    .toAbsolutePath().normalize();
            return Files.isDirectory(wal) && Files.isDirectory(snapshots)
                    && "PURE_WAL".equals(properties.getProperty("recovery.mode"))
                    && GaPerformanceMatrix.APPROVED_WAL_MODE.equals(
                            properties.getProperty("wal.durability.mode"))
                    && "65536".equals(properties.getProperty("wal.segment.size.bytes"))
                    && "1024".equals(properties.getProperty("pipeline.capacity"))
                    && "BLOCKING".equals(properties.getProperty("pipeline.wait.mode"))
                    && "127.0.0.1".equals(properties.getProperty("protocol.bind.address"))
                    && "8192".equals(properties.getProperty("protocol.write.low.bytes"))
                    && "16384".equals(properties.getProperty("protocol.write.high.bytes"))
                    && "true".equals(properties.getProperty("management.enabled"))
                    && "127.0.0.1".equals(properties.getProperty("management.bind.address"))
                    && port(properties.getProperty("protocol.port")) > 0
                    && port(properties.getProperty("management.port")) > 0
                    && !properties.getProperty("protocol.port").equals(
                            properties.getProperty("management.port"))
                    && "16".equals(properties.getProperty("management.max.connections"))
                    && "1000".equals(properties.getProperty("management.request.timeout.ms"))
                    && "2000".equals(properties.getProperty("lifecycle.shutdown.timeout.ms"));
        } catch (final IOException | RuntimeException exception) {
            return false;
        }
    }

    private static int port(final String value) {
        try {
            final int parsed = Integer.parseInt(value);
            return parsed >= 1 && parsed <= 65_535 ? parsed : -1;
        } catch (final NumberFormatException exception) {
            return -1;
        }
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
                final long offeredNanos = System.nanoTime();
                if (!client.tryOffer(command, requestId)) {
                    break;
                }
                offered = true;
                accumulator.recordOffer(requestId, command.sequence().value(), offeredNanos,
                        collect && offeredNanos >= measurementStart
                                && offeredNanos < measurementEnd);
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
        accumulator.markDrainComplete(client.inFlight() == 0 && client.completedCount() == 0);
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

    record GaFormalRunResult(
            GaFormalPerformanceEvidencePublisher.PublishedRun publishedRun,
            GaPerformanceEvaluator.Evaluation evaluation) {
    }

    private static final class RunAccumulator {
        private final List<Long> latencies = new ArrayList<>();
        private final List<GaFormalPerformanceEvidencePublisher.LatencySample> samples =
                new ArrayList<>();
        private final List<Long> releaseDelays = new ArrayList<>();
        private final Map<Long, RequestState> requests = new LinkedHashMap<>();
        private final StringBuilder raw = new StringBuilder();
        private long offered;
        private long accepted;
        private long responses;
        private long postMeasurementDrain;
        private long crossBoundary;
        private long warmupCrossBoundary;
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
        private boolean boundedDrainComplete;
        private boolean measurementStarted;
        private boolean readyObserved;
        private int shutdownExitCode = -1;
        private boolean shutdownCompleted;

        private void recordOffer(
                final long requestId,
                final long commandSequence,
                final long offeredNanos,
                final boolean inMeasurement) {
            if (requests.putIfAbsent(requestId,
                    new RequestState(requestId, commandSequence, offeredNanos, inMeasurement))
                    != null) {
                mismatches++;
                return;
            }
            if (inMeasurement) {
                offered++;
            }
        }

        private void accept(
                final List<ProtocolV2PacedQualificationClient.CompletedExchange> values,
                final boolean collect,
                final long start,
                final long end) {
            for (ProtocolV2PacedQualificationClient.CompletedExchange value : values) {
                final RequestState state = requests.get(value.requestId());
                if (state == null) {
                    errors++;
                    raw.append("response.unknownRequestId=")
                            .append(value.requestId()).append('\n');
                    continue;
                }
                if (state.completedNanos != 0L) {
                    mismatches++;
                    continue;
                }
                state.completedNanos = value.completedNanos();
                state.capacityReleaseNanos = value.capacityReleaseNanos();
                state.outcomeCode = value.exchange().outcomeCode();
                releaseDelays.add(value.capacityReleaseDelayNanos());
                if (!collect || start <= 0L || end <= start) {
                    continue;
                }
                final boolean completedInMeasurement = value.completedNanos() >= start
                        && value.completedNanos() < end;
                if (state.inMeasurement) {
                    // Acceptance is an authoritative validated response, not a successful offer.
                    // It remains in this population even when its response is observed during
                    // the bounded post-measurement drain.
                    accepted++;
                    if (completedInMeasurement) {
                        responses++;
                        trades += value.exchange().matches().size();
                        latencies.add(value.latencyNanos());
                        samples.add(new GaFormalPerformanceEvidencePublisher.LatencySample(
                                value.requestId(), value.exchange().commandSequence(),
                                value.offeredNanos(), value.completedNanos(),
                                value.capacityReleaseNanos()));
                    } else if (value.completedNanos() >= end) {
                        postMeasurementDrain++;
                    } else {
                        crossBoundary++;
                    }
                } else if (completedInMeasurement) {
                    // This request was offered during warmup, not in the measurement
                    // population. Preserve the crossing in raw evidence without adding it to
                    // the measurement accepted/completed partition.
                    warmupCrossBoundary++;
                }
            }
        }

        private void markDrainComplete(final boolean complete) {
            boundedDrainComplete = complete;
        }

        private void markMeasurementStarted() {
            measurementStarted = true;
            boundedDrainComplete = false;
        }

        private void markReadyObserved() {
            readyObserved = true;
        }

        private void markShutdown(final int exitCode, final boolean completed) {
            shutdownExitCode = exitCode;
            shutdownCompleted = completed;
        }

        private void capture(final ProtocolV2PacedQualificationClient client) {
            if (client == null) {
                return;
            }
            maxInFlight = client.maximumObservedInFlight();
            maxCompleted = client.maximumObservedCompleted();
            readerWakes = client.readerWakeCount();
            releaseCount = client.capacityReleaseCount();
        }

        private GaPerformanceObservation observation(
                final long elapsed, final long start, final long end,
                final boolean configurationBound,
                final boolean comparable,
                final boolean candidateBound,
                final boolean controllerBound,
                final boolean publicPathCompleted) {
            measurementStart = start;
            measurementEnd = end;
            final long incomplete = requests.values().stream()
                    .filter(state -> state.inMeasurement && state.completedNanos == 0L)
                    .count();
            final GaPerformanceMeasurement population = new GaPerformanceMeasurement(
                    offered, accepted, responses, postMeasurementDrain, crossBoundary,
                    incomplete, boundedDrainComplete);
            // commandCount is the formal latency population, not every request offered before
            // the interval boundary. The wider offered/accepted/drain populations remain in the
            // measurement object and raw evidence for independent recomputation.
            final int commandCount = Math.max(1,
                    Math.toIntExact(Math.min(Integer.MAX_VALUE, responses)));
            return new GaPerformanceObservation(
                    commandCount, accepted, responses, elapsed, toArray(latencies),
                    new long[0], new long[0], responses * 1_000_000_000.0 / elapsed,
                    responses * 1_000_000_000.0 / elapsed, 0L, 0L, errors, timeouts, mismatches,
                    publicPathCompleted, configurationBound, comparable, candidateBound,
                    controllerBound, population);
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
                    .append("measurement.offeredCommands=").append(offered).append('\n')
                    .append("measurement.acceptedCommands=").append(accepted).append('\n')
                    .append("measurement.completedCommands=").append(responses).append('\n')
                    .append("measurement.postMeasurementDrainCommands=")
                    .append(postMeasurementDrain).append('\n')
                    .append("measurement.crossBoundaryCommands=").append(crossBoundary)
                    .append('\n')
                    .append("measurement.warmupCrossBoundaryCommands=")
                    .append(warmupCrossBoundary).append('\n')
                    .append("measurement.unfinishedCommands=")
                    .append(requests.values().stream()
                            .filter(state -> state.inMeasurement && state.completedNanos == 0L)
                            .count()).append('\n')
                    .append("measurement.boundedDrainComplete=")
                    .append(boundedDrainComplete).append('\n')
                    .append("errors=").append(errors).append('\n')
                    .append("timeouts=").append(timeouts).append('\n')
                    .append("mismatches=").append(mismatches).append('\n');
            final QualificationPercentiles.Summary latency = observation.latency();
            latency.appendTo(result, "latency");
            requests.values().stream().sorted(java.util.Comparator.comparingLong(
                    state -> state.requestId)).forEach(state -> result
                    .append("request.").append(state.requestId)
                    .append(".commandSequence=").append(state.commandSequence).append('\n')
                    .append("request.").append(state.requestId)
                    .append(".offeredNanos=").append(state.offeredNanos).append('\n')
                    .append("request.").append(state.requestId)
                    .append(".inMeasurement=").append(state.inMeasurement).append('\n')
                    .append("request.").append(state.requestId)
                    .append(".completedNanos=").append(state.completedNanos).append('\n')
                    .append("request.").append(state.requestId)
                    .append(".capacityReleaseNanos=").append(state.capacityReleaseNanos)
                    .append('\n')
                    .append("request.").append(state.requestId)
                    .append(".outcomeCode=").append(state.outcomeCode).append('\n'));
            result.append(raw);
            environment.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                    result.append("environment.").append(entry.getKey()).append('=').append(
                            entry.getValue()).append('\n'));
            return result.toString();
        }

        private static final class RequestState {
            private final long requestId;
            private final long commandSequence;
            private final long offeredNanos;
            private final boolean inMeasurement;
            private long completedNanos;
            private long capacityReleaseNanos;
            private int outcomeCode;

            private RequestState(
                    final long requestId,
                    final long commandSequence,
                    final long offeredNanos,
                    final boolean inMeasurement) {
                this.requestId = requestId;
                this.commandSequence = commandSequence;
                this.offeredNanos = offeredNanos;
                this.inMeasurement = inMeasurement;
            }
        }

        private static long[] toArray(final List<Long> values) {
            final long[] result = new long[values.size()];
            for (int index = 0; index < values.size(); index++) {
                result[index] = values.get(index);
            }
            return result;
        }
    }

    record LifecycleSample(
            int cycle,
            String physicalExecutionId,
            long startupNanos,
            long shutdownNanos,
            int shutdownExitCode,
            boolean ready,
            boolean protocolBound,
            String state,
            String failureCode,
            long terminalFailures,
            boolean shutdownCompleted,
            String configurationSha256,
            String environmentIdentitySha256,
            String outcome,
            String failureType,
            boolean configurationBound,
            boolean environmentBound,
            boolean candidateBound,
            boolean controllerBound) {

        private boolean passed() {
            return ready && protocolBound && "READY".equals(state)
                    && "NONE".equals(failureCode) && terminalFailures == 0L
                    && shutdownCompleted && shutdownExitCode == 0 && "PASS".equals(outcome)
                    && configurationBound && environmentBound && candidateBound
                    && controllerBound;
        }
    }

    record LifecycleResult(List<LifecycleSample> samples, boolean passed, String blocker) {

        private long[] startup() {
            return samples.stream().mapToLong(LifecycleSample::startupNanos).toArray();
        }

        private long[] shutdown() {
            return samples.stream().mapToLong(LifecycleSample::shutdownNanos).toArray();
        }

        private boolean complete() {
            return samples.size() == GaFormalPerformanceContract.LIFECYCLE_CYCLES;
        }
    }

    record ManagementResult(
            boolean pairAPassed,
            boolean pairBPassed,
            double pairAIdleThroughput,
            double pairAStatusThroughput,
            long pairAIdleP99,
            long pairAStatusP99,
            double pairBIdleThroughput,
            double pairBStatusThroughput,
            long pairBIdleP99,
            long pairBStatusP99,
            String blocker) {
        private boolean passed() {
            return pairAPassed && pairBPassed && "NONE".equals(blocker);
        }
    }

    private static LifecycleResult runLifecycle(
            final Path artifact,
            final Path qualificationArtifact,
            final Path root,
            final GaCorrectnessCanonicalContext context,
            final String qualificationJar,
            final GaPerformanceMatrix matrix) throws IOException {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(qualificationJar, "qualificationJar");
        Files.createDirectories(root);
        final boolean candidateBound = candidateMatches(context, artifact);
        final boolean controllerBound = controllerMatches(context);
        final List<LifecycleSample> samples = new ArrayList<>();
        String blocker = "NONE";
        for (int index = 1; index <= GaFormalPerformanceContract.LIFECYCLE_CYCLES; index++) {
            final Path cycle = root.resolve(String.format("cycle-%02d", index));
            Files.createDirectories(cycle);
            final Path config = writeConfiguration(cycle, cycle.resolve("wal"),
                    cycle.resolve("snapshots"));
            Files.createDirectories(cycle.resolve("wal"));
            Files.createDirectories(cycle.resolve("snapshots"));
            final String configurationSha = QualificationArtifactHasher.sha256(config);
            final String physicalExecutionId = UUID.randomUUID().toString();
            final long started = System.nanoTime();
            long startupNanos = 0L;
            long shutdownNanos = 0L;
            int exitCode = -1;
            boolean ready = false;
            boolean protocolBound = false;
            boolean shutdownCompleted = false;
            long terminalFailures = 0L;
            String state = "UNAVAILABLE";
            String observedFailureCode = "B3";
            String failureType = "";
            try (ReleaseCandidateQualificationProcess child =
                    ReleaseCandidateQualificationProcess.startPackagedCandidate(
                            artifact, qualificationArtifact, config,
                            cycle.resolve("process-evidence"), READY_TIMEOUT, true)) {
                final GaManagementEvidence readyEvidence =
                        ReleaseCandidateManagementClient.requestEvidence(
                                child.managementPort(), "READY", READY_TIMEOUT);
                if (!readyEvidence.hasValidStateSemantics()) {
                    throw new IOException("lifecycle READY evidence was invalid");
                }
                ready = true;
                startupNanos = Math.max(1L, System.nanoTime() - started);
                final GaManagementEvidence status =
                        ReleaseCandidateManagementClient.requestEvidence(
                                child.managementPort(), "STATUS", READY_TIMEOUT);
                state = status.state();
                observedFailureCode = status.failureCode();
                protocolBound = Boolean.TRUE.equals(status.protocolBound());
                terminalFailures = status.terminalFailures();
                if (!healthyStatus(status)) {
                    failureType = "MANAGEMENT_STATUS";
                    throw new IOException("lifecycle STATUS evidence was not healthy");
                }
                final long stopped = System.nanoTime();
                exitCode = child.gracefulShutdown(GaFormalPerformanceContract.PROCESS_TIMEOUT);
                shutdownNanos = Math.max(1L, System.nanoTime() - stopped);
                shutdownCompleted = !child.isAlive();
                if (exitCode != 0) {
                    failureType = "SHUTDOWN_EXIT";
                }
            } catch (final IOException | RuntimeException failure) {
                if (failureType.isBlank()) {
                    failureType = failure.getClass().getSimpleName();
                }
            }
            Map<String, String> environment;
            boolean environmentBound;
            String environmentIdentity;
            try {
                environment = GaPerformanceEnvironment.capture(cycle);
                environmentBound = GaPerformanceEnvironment.matchesReference(environment);
                environmentIdentity = GaPerformanceEnvironment.identity(environment);
            } catch (final IOException failure) {
                environment = Map.of("capture.error", failure.getClass().getSimpleName());
                environmentBound = false;
                environmentIdentity = GaPerformanceEnvironment.identity(environment);
                if (failureType.isBlank()) {
                    failureType = "ENVIRONMENT_CAPTURE";
                }
            }
            final boolean configurationBound = configurationMatches(config, matrix);
            final boolean successfulState = ready && protocolBound && "READY".equals(state)
                    && "NONE".equals(observedFailureCode) && terminalFailures == 0L
                    && shutdownCompleted && exitCode == 0;
            final String sampleBlocker = lifecycleBlocker(
                    successfulState, configurationBound, environmentBound,
                    candidateBound, controllerBound, exitCode, terminalFailures);
            final String outcome = successfulState && configurationBound && environmentBound
                    && candidateBound && controllerBound ? "PASS"
                    : ready ? "FAIL" : "ABORTED";
            final LifecycleSample sample = new LifecycleSample(
                    index, physicalExecutionId, startupNanos, shutdownNanos, exitCode, ready,
                    protocolBound, state, observedFailureCode, terminalFailures,
                    shutdownCompleted, configurationSha, environmentIdentity, outcome,
                    failureType.isBlank() ? "NONE" : failureType, configurationBound,
                    environmentBound, candidateBound, controllerBound);
            publishLifecycleSample(cycle, sample, context, qualificationJar, matrix,
                    environment, failureType);
            samples.add(sample);
            if (!sample.passed()) {
                blocker = sampleBlocker;
                break;
            }
        }
        final LifecycleResult result = new LifecycleResult(
                List.copyOf(samples), samples.size() == GaFormalPerformanceContract.LIFECYCLE_CYCLES
                        && samples.stream().allMatch(LifecycleSample::passed)
                        && "NONE".equals(blocker), blocker);
        publishLifecycleSummary(root, result, context, qualificationJar, matrix);
        return result;
    }

    private static boolean healthyStatus(final GaManagementEvidence status) {
        return status.kind() == GaManagementEvidence.Kind.STATUS
                && status.hasRequiredFields() && status.hasValidStateSemantics()
                && Boolean.TRUE.equals(status.live()) && Boolean.TRUE.equals(status.ready())
                && Boolean.TRUE.equals(status.protocolBound())
                && "READY".equals(status.state()) && "NONE".equals(status.failureCode())
                && status.terminalFailures() == 0L;
    }

    private static String lifecycleBlocker(
            final boolean successfulState,
            final boolean configurationBound,
            final boolean environmentBound,
            final boolean candidateBound,
            final boolean controllerBound,
            final int exitCode,
            final long terminalFailures) {
        if (!candidateBound || !controllerBound) {
            return "B0";
        }
        if (!configurationBound || !environmentBound) {
            return "B3";
        }
        if (!successfulState || exitCode != 0 || terminalFailures != 0L) {
            return "B1";
        }
        return "NONE";
    }

    private static void publishLifecycleSample(
            final Path cycle,
            final LifecycleSample sample,
            final GaCorrectnessCanonicalContext context,
            final String qualificationJar,
            final GaPerformanceMatrix matrix,
            final Map<String, String> environment,
            final String failureMessage) throws IOException {
        final StringBuilder text = new StringBuilder()
                .append("schema=ga-g4-lifecycle-cycle-v2\n")
                .append("formal=true\n")
                .append("cycle=").append(sample.cycle()).append('\n')
                .append("physicalExecutionId=").append(sample.physicalExecutionId()).append('\n')
                .append("candidate.tag=").append(context.candidate().tag()).append('\n')
                .append("candidate.productionSha=").append(context.candidate().productionSha())
                .append('\n')
                .append("candidate.applicationJarSha256=")
                .append(context.candidate().applicationJarSha256()).append('\n')
                .append("qualification.jarSha256=").append(qualificationJar).append('\n')
                .append("controller.gitSha=").append(context.controllerGitSha()).append('\n')
                .append("protocol=v2\nwindow=8\nwalMode=SYNC_EACH_APPEND\n")
                .append("matrix.version=").append(matrix.version()).append('\n')
                .append("startupNanos=").append(sample.startupNanos()).append('\n')
                .append("shutdownNanos=").append(sample.shutdownNanos()).append('\n')
                .append("shutdown.exitCode=").append(sample.shutdownExitCode()).append('\n')
                .append("shutdown.completed=").append(sample.shutdownCompleted()).append('\n')
                .append("candidate.ready=").append(sample.ready()).append('\n')
                .append("candidate.protocolBound=").append(sample.protocolBound()).append('\n')
                .append("candidate.state=").append(sample.state()).append('\n')
                .append("candidate.failureCode=").append(sample.failureCode()).append('\n')
                .append("candidate.terminalFailures=").append(sample.terminalFailures()).append('\n')
                .append("configuration.sha256=").append(sample.configurationSha256()).append('\n')
                .append("configuration.bound=").append(sample.configurationBound()).append('\n')
                .append("environment.identitySha256=")
                .append(sample.environmentIdentitySha256()).append('\n')
                .append("environment.bound=").append(sample.environmentBound()).append('\n')
                .append("candidate.bound=").append(sample.candidateBound()).append('\n')
                .append("controller.bound=").append(sample.controllerBound()).append('\n')
                .append("outcome=").append(sample.outcome()).append('\n')
                .append("blocker=").append(lifecycleBlocker(sample.passed(),
                        sample.configurationBound(), sample.environmentBound(),
                        sample.candidateBound(), sample.controllerBound(),
                        sample.shutdownExitCode(), sample.terminalFailures())).append('\n')
                .append("failure.type=").append(sample.failureType()).append('\n');
        if (failureMessage != null && !failureMessage.isBlank()) {
            text.append("failure.message=").append(singleLine(failureMessage)).append('\n');
        }
        environment.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                text.append("environment.").append(entry.getKey()).append('=')
                        .append(singleLine(entry.getValue())).append('\n'));
        final Path raw = cycle.resolve("lifecycle-raw-evidence-v2.txt");
        com.ultralatency.matching.qualification.QualificationEvidencePublication.text(
                raw, text.toString());
        GaFormalPerformanceEvidencePublisher.publishArtifactSidecar(raw);
    }

    private static void publishLifecycleSummary(
            final Path root,
            final LifecycleResult result,
            final GaCorrectnessCanonicalContext context,
            final String qualificationJar,
            final GaPerformanceMatrix matrix) throws IOException {
        final StringBuilder text = new StringBuilder()
                .append("schema=ga-g4-lifecycle-summary-v2\n")
                .append("formal=true\n")
                .append("matrix.version=").append(matrix.version()).append('\n')
                .append("matrix.cycles=").append(GaFormalPerformanceContract.LIFECYCLE_CYCLES)
                .append('\n')
                .append("sample.count=").append(result.samples().size()).append('\n')
                .append("complete=").append(result.complete()).append('\n')
                .append("passed=").append(result.passed()).append('\n')
                .append("blocker=").append(result.blocker()).append('\n')
                .append("candidate.tag=").append(context.candidate().tag()).append('\n')
                .append("candidate.productionSha=").append(context.candidate().productionSha())
                .append('\n')
                .append("candidate.applicationJarSha256=")
                .append(context.candidate().applicationJarSha256()).append('\n')
                .append("qualification.jarSha256=").append(qualificationJar).append('\n')
                .append("controller.gitSha=").append(context.controllerGitSha()).append('\n')
                .append("protocol=v2\nwindow=8\nwalMode=SYNC_EACH_APPEND\n");
        for (LifecycleSample sample : result.samples()) {
            text.append("cycle.").append(sample.cycle()).append(".physicalExecutionId=")
                    .append(sample.physicalExecutionId()).append('\n')
                    .append("cycle.").append(sample.cycle()).append(".outcome=")
                    .append(sample.outcome()).append('\n')
                    .append("cycle.").append(sample.cycle()).append(".rawSha256=")
                    .append(QualificationArtifactHasher.sha256(root.resolve(
                            String.format("cycle-%02d/lifecycle-raw-evidence-v2.txt",
                                    sample.cycle())))).append('\n');
        }
        final Path summary = root.resolve("lifecycle-summary-v2.txt");
        com.ultralatency.matching.qualification.QualificationEvidencePublication.text(
                summary, text.toString());
        GaFormalPerformanceEvidencePublisher.publishArtifactSidecar(summary);
        publishDirectoryInventory(root, root.resolve("SHA256SUMS"));
    }

    private static void publishDirectoryInventory(final Path root, final Path inventory)
            throws IOException {
        final List<Path> artifacts;
        try (var paths = Files.walk(root)) {
            artifacts = paths.filter(Files::isRegularFile)
                    .filter(path -> !path.equals(inventory))
                    .filter(path -> !path.getFileName().toString().toLowerCase().endsWith(".sha256"))
                    .sorted(java.util.Comparator.comparing(path -> relativeUnchecked(root, path)))
                    .toList();
        }
        final StringBuilder text = new StringBuilder();
        for (Path artifact : artifacts) {
            text.append(QualificationArtifactHasher.sha256(artifact)).append("  ")
                    .append(relativeUnchecked(root, artifact)).append('\n');
        }
        com.ultralatency.matching.qualification.QualificationEvidencePublication.text(
                inventory, text.toString());
        GaFormalPerformanceEvidencePublisher.publishArtifactSidecar(inventory);
    }

    private static String singleLine(final String value) {
        return value == null ? "UNAVAILABLE" : value.replace('\r', ' ').replace('\n', ' ');
    }

    private static String relativeUnchecked(final Path root, final Path file) {
        return root.toAbsolutePath().normalize().relativize(
                file.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private static ManagementResult runManagement(
            final Path artifact,
            final Path qualificationArtifact,
            final Path root,
            final GaCorrectnessCanonicalContext context,
            final String qualificationJar,
            final GaPerformanceMatrix matrix) throws IOException {
        Files.createDirectories(root);
        final ManagementTrial aIdle = runManagementTrial(
                artifact, qualificationArtifact, root.resolve("pair-a-idle"), false,
                context, qualificationJar, matrix);
        if (!aIdle.complete()) {
            final ManagementResult result = new ManagementResult(false, false,
                    aIdle.throughput(), 0.0d, p99(aIdle.responseP99()), 0L,
                    0.0d, 0.0d, 0L, 0L, aIdle.blocker());
            publishManagementSummary(root, result, List.of(aIdle), context,
                    qualificationJar, matrix);
            return result;
        }
        final ManagementTrial aStatus = runManagementTrial(
                artifact, qualificationArtifact, root.resolve("pair-a-status"), true,
                context, qualificationJar, matrix);
        final boolean pairA = compares(aIdle, aStatus);
        if (!pairA) {
            final ManagementResult result = new ManagementResult(false, false,
                    aIdle.throughput(), aStatus.throughput(), p99(aIdle.responseP99()),
                    p99(aStatus.responseP99()), 0.0d, 0.0d, 0L, 0L,
                    comparisonBlocker(aIdle, aStatus));
            publishManagementSummary(root, result, List.of(aIdle, aStatus), context,
                    qualificationJar, matrix);
            return result;
        }
        final ManagementTrial bStatus = runManagementTrial(
                artifact, qualificationArtifact, root.resolve("pair-b-status"), true,
                context, qualificationJar, matrix);
        if (!bStatus.complete()) {
            final ManagementResult result = new ManagementResult(true, false,
                    aIdle.throughput(), aStatus.throughput(), p99(aIdle.responseP99()),
                    p99(aStatus.responseP99()), 0.0d, bStatus.throughput(), 0L,
                    p99(bStatus.responseP99()), bStatus.blocker());
            publishManagementSummary(root, result, List.of(aIdle, aStatus, bStatus), context,
                    qualificationJar, matrix);
            return result;
        }
        final ManagementTrial bIdle = runManagementTrial(
                artifact, qualificationArtifact, root.resolve("pair-b-idle"), false,
                context, qualificationJar, matrix);
        final boolean pairB = compares(bIdle, bStatus);
        final ManagementResult result = new ManagementResult(pairA, pairB,
                aIdle.throughput(), aStatus.throughput(), p99(aIdle.responseP99()),
                p99(aStatus.responseP99()), bIdle.throughput(), bStatus.throughput(),
                p99(bIdle.responseP99()), p99(bStatus.responseP99()),
                comparisonBlocker(bIdle, bStatus));
        publishManagementSummary(root, result, List.of(aIdle, aStatus, bStatus, bIdle), context,
                qualificationJar, matrix);
        return result;
    }

    private static ManagementTrial runManagementTrial(
            final Path artifact,
            final Path qualificationArtifact,
            final Path root,
            final boolean pollStatus,
            final GaCorrectnessCanonicalContext context,
            final String qualificationJar,
            final GaPerformanceMatrix matrix) throws IOException {
        Files.createDirectories(root);
        final Path config = writeConfiguration(root, root.resolve("wal"), root.resolve("snapshots"));
        Files.createDirectories(root.resolve("wal"));
        Files.createDirectories(root.resolve("snapshots"));
        final QualificationConfiguration workload = new QualificationConfiguration(
                com.ultralatency.matching.qualification.QualificationProfile.MEMORY_STEADY_STATE_V1,
                matrix.seed(), MAX_COMMANDS, GaFormalPerformanceContract.COMMAND_TIMEOUT, root);
        final RunAccumulator accumulator = new RunAccumulator();
        final List<Long> statusLatency = new ArrayList<>();
        ReleaseCandidateQualificationProcess child = null;
        ProtocolV2PacedQualificationClient client = null;
        GaManagementEvidence lastStatus = null;
        GaManagementEvidence lastMetrics = null;
        long measurementStart = 0L;
        long measurementEnd = 0L;
        boolean candidateReady = false;
        boolean statusHealthy = false;
        boolean metricsComplete = false;
        boolean statusPolled = false;
        String failureCode = "B2";
        String failureType = "NONE";
        String failureMessage = "";
        long terminalFailures = 0L;
        int shutdownExitCode = -1;
        boolean shutdownCompleted = false;
        try {
            child = ReleaseCandidateQualificationProcess.startPackagedCandidate(
                    artifact, qualificationArtifact, config,
                    root.resolve("process-evidence"), READY_TIMEOUT, true);
            client = new ProtocolV2PacedQualificationClient(
                    new java.net.InetSocketAddress("127.0.0.1", child.protocolPort()),
                    GaFormalPerformanceContract.COMMAND_TIMEOUT,
                    GaPerformanceMatrix.APPROVED_PROTOCOL_V2_WINDOW);
            final GaManagementEvidence ready = ReleaseCandidateManagementClient.requestEvidence(
                    child.managementPort(), "READY", READY_TIMEOUT);
            candidateReady = ready.hasValidStateSemantics();
            if (!candidateReady) {
                throw new IOException("management READY evidence was not ready");
            }
            accumulator.markReadyObserved();
            lastStatus = ReleaseCandidateManagementClient.requestEvidence(
                    child.managementPort(), "STATUS", READY_TIMEOUT);
            statusHealthy = healthyStatus(lastStatus);
            terminalFailures = lastStatus.terminalFailures();
            failureCode = lastStatus.failureCode();
            if (!statusHealthy) {
                throw new IOException("management STATUS evidence was not healthy");
            }
            final CommandCursor cursor = new CommandCursor();
            runContinuousPhase(client, workload, cursor,
                    GaFormalPerformanceContract.MANAGEMENT_WARMUP,
                    0L, 0L, accumulator, false);
            awaitDrain(client, accumulator, false, 0L, 0L);
            measurementStart = System.nanoTime();
            accumulator.markMeasurementStarted();
            measurementEnd = addDeadline(measurementStart,
                    GaFormalPerformanceContract.MANAGEMENT_MEASUREMENT);
            long nextStatus = measurementStart;
            while (System.nanoTime() < measurementEnd) {
                drain(client, accumulator, true, measurementStart, measurementEnd);
                boolean offered = false;
                while (System.nanoTime() < measurementEnd
                        && client.inFlight() < client.maxInFlight()) {
                    final EngineCommand command = QualificationWorkloadV1.commandAtForRun(
                            workload, cursor.commandIndex++);
                    final long requestId = cursor.requestId++;
                    final long offeredNanos = System.nanoTime();
                    if (!client.tryOffer(command, requestId)) {
                        break;
                    }
                    accumulator.recordOffer(requestId, command.sequence().value(), offeredNanos,
                            offeredNanos >= measurementStart && offeredNanos < measurementEnd);
                    offered = true;
                }
                final long now = System.nanoTime();
                if (pollStatus && now >= nextStatus) {
                    statusPolled = true;
                    final long statusStarted = System.nanoTime();
                    lastStatus = ReleaseCandidateManagementClient.requestEvidence(
                            child.managementPort(), "STATUS", Duration.ofSeconds(5));
                    statusLatency.add(Math.max(1L, System.nanoTime() - statusStarted));
                    statusHealthy = healthyStatus(lastStatus);
                    terminalFailures = lastStatus.terminalFailures();
                    failureCode = lastStatus.failureCode();
                    if (!statusHealthy) {
                        throw new IOException("management STATUS evidence failed during trial");
                    }
                    lastMetrics = ReleaseCandidateManagementClient.requestEvidence(
                            child.managementPort(), "METRICS", Duration.ofSeconds(5));
                    metricsComplete = healthyMetrics(lastMetrics);
                    terminalFailures = lastMetrics.terminalFailures();
                    failureCode = lastMetrics.failureCode();
                    if (!metricsComplete) {
                        throw new IOException("management METRICS evidence failed during trial");
                    }
                    nextStatus = addDeadline(nextStatus,
                            GaFormalPerformanceContract.MANAGEMENT_INTERVAL);
                }
                if (!offered || client.inFlight() >= client.maxInFlight()) {
                    accumulator.accept(client.awaitCompleted(POLL_TIMEOUT), true,
                            measurementStart, measurementEnd);
                }
            }
            awaitDrain(client, accumulator, true, measurementStart, measurementEnd);
            lastMetrics = ReleaseCandidateManagementClient.requestEvidence(
                    child.managementPort(), "METRICS", Duration.ofSeconds(5));
            metricsComplete = healthyMetrics(lastMetrics);
            terminalFailures = lastMetrics.terminalFailures();
            failureCode = lastMetrics.failureCode();
            if (!metricsComplete) {
                throw new IOException("final management METRICS evidence was incomplete");
            }
            shutdownExitCode = child.gracefulShutdown(
                    GaFormalPerformanceContract.PROCESS_TIMEOUT);
            shutdownCompleted = !child.isAlive();
            if (shutdownExitCode != 0) {
                failureType = "SHUTDOWN_EXIT";
                failureCode = "B1";
            }
        } catch (final IOException | RuntimeException failure) {
            failureType = failureType.equals("NONE")
                    ? failure.getClass().getSimpleName() : failureType;
            failureMessage = String.valueOf(failure.getMessage());
            if ("NONE".equals(failureCode)) {
                failureCode = "B2";
            }
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (final IOException failure) {
                    failureType = failureType.equals("NONE")
                            ? "CLIENT_CLOSE" : failureType;
                    failureMessage = failure.getMessage();
                }
            }
            if (child != null && child.isAlive()) {
                try {
                    shutdownExitCode = child.gracefulShutdown(
                            GaFormalPerformanceContract.PROCESS_TIMEOUT);
                    shutdownCompleted = !child.isAlive();
                } catch (final IOException failure) {
                    failureType = failureType.equals("NONE")
                            ? "SHUTDOWN" : failureType;
                    failureMessage = failure.getMessage();
                }
            }
            if (child != null) {
                child.close();
            }
        }
        if (measurementStart == 0L) {
            measurementStart = System.nanoTime();
        }
        if (measurementEnd <= measurementStart) {
            measurementEnd = Math.max(measurementStart + 1L, System.nanoTime());
        }
        accumulator.capture(client);
        Map<String, String> environment;
        boolean environmentBound;
        try {
            environment = GaPerformanceEnvironment.capture(root);
            environmentBound = GaPerformanceEnvironment.matchesReference(environment);
        } catch (final IOException failure) {
            environment = Map.of("capture.error", failure.getClass().getSimpleName());
            environmentBound = false;
        }
        final boolean configurationBound = configurationMatches(config, matrix);
        final boolean candidateBound = candidateMatches(context, artifact);
        final boolean controllerBound = controllerMatches(context);
        final long elapsed = Math.max(1L, measurementEnd - measurementStart);
        final boolean publicPathCompleted = accumulator.readyObserved && shutdownCompleted;
        final GaPerformanceObservation observation = accumulator.observation(
                elapsed, measurementStart, measurementEnd, configurationBound,
                environmentBound, candidateBound, controllerBound, publicPathCompleted);
        final boolean complete = candidateReady && statusHealthy && metricsComplete
                && observation.measurement().boundaryComplete()
                && observation.completeResponsePopulation() && shutdownExitCode == 0
                && shutdownCompleted && configurationBound && environmentBound
                && candidateBound && controllerBound && observation.errors() == 0
                && observation.timeouts() == 0 && observation.mismatches() == 0
                && observation.responseCount() > 0;
        final String blocker = managementBlocker(complete, candidateReady, statusHealthy,
                metricsComplete, configurationBound, environmentBound, candidateBound,
                controllerBound, shutdownExitCode, terminalFailures);
        final String outcome = complete ? "PASS" : candidateReady ? "FAIL" : "ABORTED";
        final String raw = managementRawEvidence(context, qualificationJar, matrix, pollStatus,
                candidateReady,
                statusHealthy, metricsComplete, statusPolled, failureCode, terminalFailures,
                shutdownExitCode, shutdownCompleted, blocker, failureType, failureMessage,
                observation, accumulator, statusLatency, environment);
        final Path rawPath = root.resolve("management-raw-evidence-v2.txt");
        com.ultralatency.matching.qualification.QualificationEvidencePublication.text(
                rawPath, raw);
        GaFormalPerformanceEvidencePublisher.publishArtifactSidecar(rawPath);
        return new ManagementTrial(
                GaPerformanceEvaluator.throughput(observation),
                observation.responseCount() == 0L ? null : observation.latency().p99Nanos(),
                statusLatency.isEmpty() ? null
                        : QualificationPercentiles.summarize(toArray(statusLatency)).p99Nanos(),
                observation.measurement().offeredCommands(), observation.acceptedCommands(),
                observation.measurement().completedCommands(),
                observation.measurement().postMeasurementDrainCommands(),
                observation.measurement().crossBoundaryCommands(),
                observation.measurement().unfinishedCommands(),
                observation.measurement().boundedDrainComplete(), candidateReady,
                statusHealthy, metricsComplete, statusPolled, failureCode, terminalFailures,
                shutdownExitCode, shutdownCompleted, configurationBound, environmentBound,
                candidateBound, controllerBound, outcome, blocker, rawPath);
    }

    private static boolean compares(final ManagementTrial idle, final ManagementTrial status) {
        return idle.complete() && status.complete() && status.statusPolled()
                && status.managementP99() != null && idle.responseP99() != null
                && status.responseP99() != null
                && status.throughput() >= idle.throughput() * 0.90d
                && status.responseP99() <= idle.responseP99() * 1.10d;
    }

    private static boolean healthyMetrics(final GaManagementEvidence metrics) {
        return (metrics.kind() == GaManagementEvidence.Kind.METRICS)
                && metrics.hasRequiredFields() && metrics.hasValidStateSemantics()
                && Boolean.TRUE.equals(metrics.live()) && Boolean.TRUE.equals(metrics.ready())
                && Boolean.TRUE.equals(metrics.protocolBound())
                && "READY".equals(metrics.state()) && "NONE".equals(metrics.failureCode())
                && metrics.terminalFailures() == 0L;
    }

    static String managementBlocker(
            final boolean complete,
            final boolean candidateReady,
            final boolean statusHealthy,
            final boolean metricsComplete,
            final boolean configurationBound,
            final boolean environmentBound,
            final boolean candidateBound,
            final boolean controllerBound,
            final int shutdownExitCode,
            final long terminalFailures) {
        if (!candidateBound || !controllerBound) {
            return "B0";
        }
        if (!configurationBound || !environmentBound) {
            return "B3";
        }
        if (!candidateReady || !statusHealthy || !metricsComplete
                || shutdownExitCode != 0 || terminalFailures != 0L) {
            return "B1";
        }
        return complete ? "NONE" : "B2";
    }

    private static String firstManagementBlocker(
            final ManagementTrial first,
            final ManagementTrial second,
            final String fallback) {
        if (!"NONE".equals(first.blocker())) {
            return first.blocker();
        }
        if (!"NONE".equals(second.blocker())) {
            return second.blocker();
        }
        return fallback;
    }

    static String comparisonBlocker(
            final ManagementTrial idle,
            final ManagementTrial status) {
        final String existing = firstManagementBlocker(idle, status, "");
        if (!existing.isBlank()) {
            return existing;
        }
        // A missing status observation is a qualification defect; a complete status trial that
        // violates the frozen regression thresholds is a candidate/runtime B1 result.
        return status.statusPolled() && status.managementP99() != null ? "B1" : "B2";
    }

    private static long p99(final Long value) {
        return value == null ? 0L : value;
    }

    static boolean shouldStartManagement(final LifecycleResult lifecycle) {
        return lifecycle != null && lifecycle.complete() && lifecycle.passed()
                && "NONE".equals(lifecycle.blocker())
                && QualificationPercentiles.summarize(lifecycle.startup()).p99Nanos()
                        <= GaPerformanceEvaluator.MAX_LIFECYCLE_P99_NANOS
                && QualificationPercentiles.summarize(lifecycle.shutdown()).p99Nanos()
                        <= GaPerformanceEvaluator.MAX_LIFECYCLE_P99_NANOS;
    }

    private static String managementRawEvidence(
            final GaCorrectnessCanonicalContext context,
            final String qualificationJar,
            final GaPerformanceMatrix matrix,
            final boolean pollStatus,
            final boolean candidateReady,
            final boolean statusHealthy,
            final boolean metricsComplete,
            final boolean statusPolled,
            final String failureCode,
            final long terminalFailures,
            final int shutdownExitCode,
            final boolean shutdownCompleted,
            final String blocker,
            final String failureType,
            final String failureMessage,
            final GaPerformanceObservation observation,
            final RunAccumulator accumulator,
            final List<Long> statusLatency,
            final Map<String, String> environment) {
        final StringBuilder text = new StringBuilder()
                .append("schema=ga-g4-management-trial-v2\n")
                .append("formal=true\n")
                .append("candidate.tag=").append(context.candidate().tag()).append('\n')
                .append("candidate.productionSha=").append(context.candidate().productionSha())
                .append('\n')
                .append("candidate.applicationJarSha256=")
                .append(context.candidate().applicationJarSha256()).append('\n')
                .append("qualification.jarSha256=").append(qualificationJar)
                .append('\n')
                .append("controller.gitSha=").append(context.controllerGitSha()).append('\n')
                .append("protocol=v2\nwindow=8\nwalMode=SYNC_EACH_APPEND\n")
                .append("matrix.version=").append(matrix.version()).append('\n')
                .append("pollStatus=").append(pollStatus).append('\n')
                .append("candidate.ready=").append(candidateReady).append('\n')
                .append("status.healthy=").append(statusHealthy).append('\n')
                .append("metrics.complete=").append(metricsComplete).append('\n')
                .append("status.polled=").append(statusPolled).append('\n')
                .append("candidate.failureCode=").append(failureCode).append('\n')
                .append("candidate.terminalFailures=").append(terminalFailures).append('\n')
                .append("shutdown.exitCode=").append(shutdownExitCode).append('\n')
                .append("shutdown.completed=").append(shutdownCompleted).append('\n')
                .append("outcome=").append("NONE".equals(blocker) ? "PASS" : "FAIL")
                .append('\n').append("blocker=").append(blocker).append('\n')
                .append("failure.type=").append(failureType).append('\n')
                .append("measurement.offeredCommands=")
                .append(observation.measurement().offeredCommands()).append('\n')
                .append("measurement.acceptedCommands=")
                .append(observation.measurement().acceptedCommands()).append('\n')
                .append("measurement.completedCommands=")
                .append(observation.measurement().completedCommands()).append('\n')
                .append("measurement.postMeasurementDrainCommands=")
                .append(observation.measurement().postMeasurementDrainCommands()).append('\n')
                .append("measurement.crossBoundaryCommands=")
                .append(observation.measurement().crossBoundaryCommands()).append('\n')
                .append("measurement.warmupCrossBoundaryCommands=")
                .append(accumulator.warmupCrossBoundary).append('\n')
                .append("measurement.unfinishedCommands=")
                .append(observation.measurement().unfinishedCommands()).append('\n')
                .append("measurement.boundedDrainComplete=")
                .append(observation.measurement().boundedDrainComplete()).append('\n')
                .append("responseCount=").append(observation.responseCount()).append('\n')
                .append("errors=").append(observation.errors()).append('\n')
                .append("timeouts=").append(observation.timeouts()).append('\n')
                .append("mismatches=").append(observation.mismatches()).append('\n');
        observation.latency().appendTo(text, "latency");
        text.append("status.sampleCount=").append(statusLatency.size()).append('\n');
        for (int index = 0; index < statusLatency.size(); index++) {
            text.append("status.sample.").append(index + 1).append(".latencyNanos=")
                    .append(statusLatency.get(index)).append('\n');
        }
        for (GaFormalPerformanceEvidencePublisher.LatencySample sample : accumulator.samples) {
            text.append("request.").append(sample.requestId()).append(".commandSequence=")
                    .append(sample.commandSequence()).append('\n')
                    .append("request.").append(sample.requestId()).append(".offeredNanos=")
                    .append(sample.offeredNanos()).append('\n')
                    .append("request.").append(sample.requestId()).append(".completedNanos=")
                    .append(sample.completedNanos()).append('\n')
                    .append("request.").append(sample.requestId()).append(".latencyNanos=")
                    .append(sample.latencyNanos()).append('\n');
        }
        if (failureMessage != null && !failureMessage.isBlank()) {
            text.append("failure.message=").append(singleLine(failureMessage)).append('\n');
        }
        environment.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                text.append("environment.").append(entry.getKey()).append('=')
                        .append(singleLine(entry.getValue())).append('\n'));
        return text.append("capacity.maxInFlight=").append(accumulator.maxInFlight).append('\n')
                .append("capacity.maxCompleted=").append(accumulator.maxCompleted).append('\n')
                .toString();
    }

    private static void publishManagementSummary(
            final Path root,
            final ManagementResult result,
            final List<ManagementTrial> trials,
            final GaCorrectnessCanonicalContext context,
            final String qualificationJar,
            final GaPerformanceMatrix matrix) throws IOException {
        final StringBuilder text = new StringBuilder()
                .append("schema=ga-g4-management-summary-v2\n")
                .append("formal=true\n")
                .append("matrix.version=").append(matrix.version()).append('\n')
                .append("candidate.tag=").append(context.candidate().tag()).append('\n')
                .append("candidate.productionSha=").append(context.candidate().productionSha())
                .append('\n')
                .append("candidate.applicationJarSha256=")
                .append(context.candidate().applicationJarSha256()).append('\n')
                .append("qualification.jarSha256=").append(qualificationJar).append('\n')
                .append("controller.gitSha=").append(context.controllerGitSha()).append('\n')
                .append("pairA.passed=").append(result.pairAPassed()).append('\n')
                .append("pairB.passed=").append(result.pairBPassed()).append('\n')
                .append("blocker=").append(result.blocker()).append('\n');
        for (int index = 0; index < trials.size(); index++) {
            final ManagementTrial trial = trials.get(index);
            final String prefix = "trial." + (index + 1) + ".";
            text.append(prefix).append("outcome=").append(trial.outcome()).append('\n')
                    .append(prefix).append("blocker=").append(trial.blocker()).append('\n')
                    .append(prefix).append("rawSha256=")
                    .append(QualificationArtifactHasher.sha256(trial.rawEvidencePath()))
                    .append('\n')
                    .append(prefix).append("offeredCommands=")
                    .append(trial.offeredCommands()).append('\n')
                    .append(prefix).append("acceptedCommands=")
                    .append(trial.acceptedCommands()).append('\n')
                    .append(prefix).append("completedCommands=")
                    .append(trial.completedCommands()).append('\n')
                    .append(prefix).append("postMeasurementDrainCommands=")
                    .append(trial.postMeasurementDrainCommands()).append('\n')
                    .append(prefix).append("crossBoundaryCommands=")
                    .append(trial.crossBoundaryCommands()).append('\n')
                    .append(prefix).append("unfinishedCommands=")
                    .append(trial.unfinishedCommands()).append('\n')
                    .append(prefix).append("boundedDrainComplete=")
                    .append(trial.boundedDrainComplete()).append('\n')
                    .append(prefix).append("shutdown.exitCode=")
                    .append(trial.shutdownExitCode()).append('\n')
                    .append(prefix).append("shutdown.completed=")
                    .append(trial.shutdownCompleted()).append('\n');
        }
        final Path summary = root.resolve("management-summary-v2.txt");
        com.ultralatency.matching.qualification.QualificationEvidencePublication.text(
                summary, text.toString());
        GaFormalPerformanceEvidencePublisher.publishArtifactSidecar(summary);
        publishDirectoryInventory(root, root.resolve("SHA256SUMS"));
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

    record ManagementTrial(
            double throughput,
            Long responseP99,
            Long managementP99,
            long offeredCommands,
            long acceptedCommands,
            long completedCommands,
            long postMeasurementDrainCommands,
            long crossBoundaryCommands,
            long unfinishedCommands,
            boolean boundedDrainComplete,
            boolean candidateReady,
            boolean statusHealthy,
            boolean metricsComplete,
            boolean statusPolled,
            String failureCode,
            long terminalFailures,
            int shutdownExitCode,
            boolean shutdownCompleted,
            boolean configurationBound,
            boolean environmentBound,
            boolean candidateBound,
            boolean controllerBound,
            String outcome,
            String blocker,
            Path rawEvidencePath) {

        private boolean complete() {
            return "PASS".equals(outcome) && "NONE".equals(blocker)
                    && responseP99 != null && candidateReady && statusHealthy && metricsComplete
                    && "NONE".equals(failureCode) && terminalFailures == 0L
                    && offeredCommands > 0L && acceptedCommands == offeredCommands
                    && completedCommands + postMeasurementDrainCommands
                            + crossBoundaryCommands == acceptedCommands
                    && unfinishedCommands == 0L && boundedDrainComplete
                    && shutdownExitCode == 0 && shutdownCompleted
                    && configurationBound && environmentBound && candidateBound
                    && controllerBound;
        }
    }
}
