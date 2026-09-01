package com.ultralatency.matching.qualification.ga.performance;

import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.qualification.ProtocolV1QualificationClient;
import com.ultralatency.matching.qualification.QualificationConfiguration;
import com.ultralatency.matching.qualification.QualificationExchange;
import com.ultralatency.matching.qualification.QualificationRunner;
import com.ultralatency.matching.qualification.QualificationWorkloadV1;
import com.ultralatency.matching.qualification.ga.correctness.GaCorrectnessCanonicalContext;
import com.ultralatency.matching.network.netty.recovery.RecoverableDurableMatchingEngineTcpServer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Executes the bounded G4 public-path Quick readiness smoke. */
public final class GaPerformanceRunner {

    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(5);
    private static final int WAL_SEGMENT_SIZE_BYTES = 65_536;

    private final GaCorrectnessCanonicalContext configuredContext;

    /** Creates a runner which resolves the frozen candidate at execution time. */
    public GaPerformanceRunner() {
        this(null);
    }

    /** Creates a runner with an explicit context, primarily for deterministic tests. */
    public GaPerformanceRunner(final GaCorrectnessCanonicalContext context) {
        configuredContext = context;
    }

    /** Runs one short public Protocol v1 smoke; this is never formal G4 evidence. */
    public GaPerformanceQuickResult runQuick(final Path outputDirectory) throws IOException {
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        final GaCorrectnessCanonicalContext context = context();
        final GaPerformanceMatrix matrix = GaPerformanceMatrix.quick();
        final Path root = newRunDirectory(outputDirectory, "g4-quick");
        final Path storage = Files.createTempDirectory("ga-g4-quick-");
        final Path wal = storage.resolve("wal");
        final Path snapshots = storage.resolve("snapshots");
        final QualificationConfiguration configuration = new QualificationConfiguration(
                com.ultralatency.matching.qualification.QualificationProfile
                        .MEMORY_STEADY_STATE_V1,
                matrix.seed(),
                matrix.quickCommandCount(),
                COMMAND_TIMEOUT,
                root);
        final Instant started = Instant.now();
        final List<Long> latency = new ArrayList<>(matrix.quickCommandCount());
        final StringBuilder raw = new StringBuilder();
        long accepted = 0L;
        long responses = 0L;
        long trades = 0L;
        int errors = 0;
        int timeouts = 0;
        int mismatches = 0;
        boolean publicPathCompleted = false;
        long startupNanos = 0L;
        long shutdownNanos = 0L;
        RecoverableDurableMatchingEngineTcpServer server = null;
        ProtocolV1QualificationClient client = null;
        try {
            final long startupStart = System.nanoTime();
            server = QualificationRunner.server(wal, snapshots, 0, WAL_SEGMENT_SIZE_BYTES);
            server.start();
            startupNanos = positiveElapsed(startupStart);
            client = new ProtocolV1QualificationClient(
                    server.localAddress().orElseThrow(), COMMAND_TIMEOUT);
            for (int index = 0; index < matrix.quickCommandCount(); index++) {
                final EngineCommand command = QualificationWorkloadV1.commandAtForRun(
                        configuration, index);
                final long exchangeStart = System.nanoTime();
                try {
                    final QualificationExchange exchange = client.exchange(command, index + 1L);
                    latency.add(positiveElapsed(exchangeStart));
                    accepted++;
                    responses++;
                    trades += exchange.matches().size();
                } catch (final java.net.SocketTimeoutException timeout) {
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
            publicPathCompleted = true;
        } catch (final IOException | RuntimeException failure) {
            raw.append("failure.type=").append(failure.getClass().getName()).append('\n')
                    .append("failure.message=").append(String.valueOf(failure.getMessage()))
                    .append('\n');
        } finally {
            final long shutdownStart = System.nanoTime();
            if (server != null) {
                server.shutdown(COMMAND_TIMEOUT);
            }
            shutdownNanos = positiveElapsed(shutdownStart);
            if (client != null) {
                try {
                    client.close();
                } catch (final IOException closeFailure) {
                    errors++;
                    raw.append("client.close.failure=").append(closeFailure.getClass().getName())
                            .append('\n');
                }
            }
            deleteTree(storage);
        }
        final Instant completed = Instant.now();
        final long elapsedNanos = Math.max(1L, Duration.between(started, completed).toNanos());
        final Map<String, String> environment = GaPerformanceEnvironment.capture(root);
        final Map<String, String> configurationFields = configurationFields(matrix, configuration);
        final boolean infrastructureBound = true;
        final double observedThroughput = accepted * 1_000_000_000.0 / elapsedNanos;
        final GaPerformanceObservation observation = new GaPerformanceObservation(
                matrix.quickCommandCount(),
                accepted,
                responses,
                elapsedNanos,
                toArray(latency),
                new long[]{startupNanos},
                new long[]{shutdownNanos},
                observedThroughput,
                observedThroughput,
                0L,
                0L,
                errors,
                timeouts,
                mismatches,
                publicPathCompleted,
                true,
                infrastructureBound,
                true,
                true);
        final GaPerformanceEvaluator.Evaluation evaluation =
                GaPerformanceEvaluator.evaluateQuick(observation);
        raw.insert(0, rawEvidence(matrix, configuration, context, environment, observation));
        final boolean completedDetermination = publicPathCompleted;
        final String outcome = evaluation.passed() ? "PASS"
                : completedDetermination ? "FAIL" : "ABORTED";
        final String failureCode = evaluation.passed() ? "NONE"
                : completedDetermination ? "B2" : "B3";
        final GaQuickEvidencePublisher.RunInput input = new GaQuickEvidencePublisher.RunInput(
                "G4",
                "ga-g4-quick-v1",
                matrix.profile(),
                matrix.seed(),
                matrix.quickCommandCount(),
                QualificationWorkloadV1.MEMORY_STEADY_STATE_VERSION,
                started,
                completed,
                outcome,
                failureCode,
                accepted,
                responses,
                trades,
                raw.toString(),
                observation.responseLatencyNanos(),
                configurationFields,
                context);
        final GaQuickEvidencePublisher.PublishedRun run =
                GaQuickEvidencePublisher.publishRun(root, input);
        final Path gate = GaQuickEvidencePublisher.publishGate(
                root,
                "G4",
                "ga-g4-quick-v1",
                run,
                evaluation.criteria(),
                context,
                started,
                completed,
                "QUICK_READINESS_ONLY",
                "Quick public-path readiness evidence; not formal G4 qualification.");
        return new GaPerformanceQuickResult(observation, evaluation, root,
                run.manifestPath(), gate);
    }

    private GaCorrectnessCanonicalContext context() throws IOException {
        return configuredContext == null
                ? GaCorrectnessCanonicalContext.fromSystem() : configuredContext;
    }

    private static Path newRunDirectory(final Path outputDirectory, final String prefix)
            throws IOException {
        final Path root = outputDirectory.toAbsolutePath().normalize();
        Files.createDirectories(root);
        return Files.createDirectory(root.resolve(prefix + "-" + java.util.UUID.randomUUID()));
    }

    private static long positiveElapsed(final long start) {
        return Math.max(1L, System.nanoTime() - start);
    }

    private static long[] toArray(final List<Long> values) {
        final long[] result = new long[values.size()];
        for (int index = 0; index < values.size(); index++) {
            result[index] = values.get(index);
        }
        return result;
    }

    private static Map<String, String> configurationFields(
            final GaPerformanceMatrix matrix,
            final QualificationConfiguration configuration) {
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put("matrix.version", matrix.version());
        fields.put("lane", "QUICK");
        fields.put("profile", matrix.profile());
        fields.put("seed", Long.toString(matrix.seed()));
        fields.put("commandCount", Integer.toString(configuration.commandCount()));
        fields.put("protocol", "v1 TCP");
        fields.put("durability", "SYNC_EACH_APPEND");
        fields.put("pipeline", "1024/BLOCKING");
        fields.put("client", "single sequential client");
        fields.put("formalDuration", matrix.runDuration().toString());
        return Map.copyOf(fields);
    }

    private static String rawEvidence(
            final GaPerformanceMatrix matrix,
            final QualificationConfiguration configuration,
            final GaCorrectnessCanonicalContext context,
            final Map<String, String> environment,
            final GaPerformanceObservation observation) {
        final StringBuilder output = new StringBuilder();
        output.append("schema=ga-g4-quick-readiness-v1\n")
                .append("formal=false\n")
                .append("matrix.version=").append(matrix.version()).append('\n')
                .append("profile=").append(matrix.profile()).append('\n')
                .append("seed=").append(matrix.seed()).append('\n')
                .append("commandCount=").append(configuration.commandCount()).append('\n')
                .append("acceptedCommands=").append(observation.acceptedCommands()).append('\n')
                .append("responseCount=").append(observation.responseCount()).append('\n')
                .append("candidate.tag=").append(context.candidate().tag()).append('\n')
                .append("controller.gitSha=").append(context.controllerGitSha()).append('\n')
                .append("environment.identitySha256=")
                .append(GaPerformanceEnvironment.identity(environment)).append('\n')
                .append("environment.referenceMatch=")
                .append(GaPerformanceEnvironment.matchesReference(environment)).append('\n');
        environment.forEach((key, value) -> output.append("environment.").append(key)
                .append('=').append(value).append('\n'));
        return output.toString();
    }

    private static void deleteTree(final Path directory) {
        try (java.util.stream.Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (final IOException ignored) {
                    // Temporary storage is owned by this runner; cleanup is best effort.
                }
            });
        } catch (final IOException ignored) {
            // The evidence root remains authoritative; only the temporary storage is removed.
        }
    }
}
