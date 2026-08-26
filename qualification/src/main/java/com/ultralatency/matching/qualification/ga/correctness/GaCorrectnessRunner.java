package com.ultralatency.matching.qualification.ga.correctness;

import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.engine.EngineResult;
import com.ultralatency.matching.integration.durable.DurableConfiguration;
import com.ultralatency.matching.network.netty.durable.DurableNetworkConfiguration;
import com.ultralatency.matching.network.netty.recovery.RecoverableDurableMatchingEngineTcpServer;
import com.ultralatency.matching.network.netty.recovery.RecoverableNetworkConfiguration;
import com.ultralatency.matching.persistence.snapshot.OfflineSnapshotGenerator;
import com.ultralatency.matching.persistence.snapshot.SnapshotStore;
import com.ultralatency.matching.persistence.wal.CommandWalReader;
import com.ultralatency.matching.persistence.wal.CommandWalWriter;
import com.ultralatency.matching.persistence.wal.WalConfiguration;
import com.ultralatency.matching.persistence.wal.WalDurabilityMode;
import com.ultralatency.matching.qualification.ProtocolV1QualificationClient;
import com.ultralatency.matching.qualification.QualificationConfiguration;
import com.ultralatency.matching.qualification.QualificationEvidencePublication;
import com.ultralatency.matching.qualification.QualificationExchange;
import com.ultralatency.matching.qualification.QualificationWorkloadV1;
import com.ultralatency.matching.recovery.online.RecoveryMode;
import com.ultralatency.matching.recovery.online.RecoveryPlanner;
import com.ultralatency.matching.recovery.online.RecoveryResult;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
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
import java.util.UUID;
import java.util.stream.Collectors;

/** Runs the qualification-only G1/G2 public-boundary correctness matrix. */
public final class GaCorrectnessRunner {

    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);
    private final GaCorrectnessCanonicalContext configuredContext;

    /** Creates a runner that verifies the frozen candidate for the approved matrix. */
    public GaCorrectnessRunner() {
        this(null);
    }

    /** Creates a runner with an explicit identity context, primarily for focused tests. */
    public GaCorrectnessRunner(final GaCorrectnessCanonicalContext context) {
        configuredContext = context;
    }

    /** Runs the approved matrix below the supplied raw artifact directory. */
    public GaCorrectnessCampaignResult run(final Path outputDirectory) throws IOException {
        return run(GaCorrectnessMatrix.approved(), outputDirectory);
    }

    /** Runs one explicit matrix; test matrices must remain separate from GA evidence. */
    public GaCorrectnessCampaignResult run(
            final GaCorrectnessMatrix matrix,
            final Path outputDirectory) throws IOException {
        final GaCorrectnessCanonicalContext context = configuredContext == null
                ? defaultContext(matrix, outputDirectory) : configuredContext;
        return run(matrix, outputDirectory, context);
    }

    /** Runs one matrix while publishing canonical G1/G2 evidence for each physical case. */
    GaCorrectnessCampaignResult run(
            final GaCorrectnessMatrix matrix,
            final Path outputDirectory,
            final GaCorrectnessCanonicalContext context) throws IOException {
        Objects.requireNonNull(matrix, "matrix");
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        Objects.requireNonNull(context, "context");
        if (!"ga-g1-g2-test-v1".equals(matrix.version()) && !context.isApprovedCandidate()) {
            throw new IOException("approved matrix requires the frozen candidate context");
        }
        final Path output = outputDirectory.toAbsolutePath().normalize();
        Files.createDirectories(output);
        final Path root = Files.createDirectory(output.resolve(
                "ga-g1-g2-" + UUID.randomUUID()));
        final Instant campaignStarted = Instant.now();
        final List<GaCorrectnessCaseResult> cases = new ArrayList<>();
        final List<GaCorrectnessCanonicalEvidence.ViewPair> canonicalViews = new ArrayList<>();
        for (final GaCorrectnessCase matrixCase : matrix.cases()) {
            final Path caseDirectory = root.resolve(matrixCase.id());
            Files.createDirectories(caseDirectory);
            final Instant physicalStarted = Instant.now();
            final long physicalStartNanos = System.nanoTime();
            final String physicalExecutionId = UUID.randomUUID().toString();
            Map<String, String> runtime = null;
            try {
                runtime = GaCorrectnessRuntimeProvenance.capture(caseDirectory);
                final GaCorrectnessCaseResult result = runCase(matrix, matrixCase, caseDirectory);
                final Instant physicalCompleted = Instant.now();
                final GaCorrectnessCanonicalEvidence.ViewPair views =
                        GaCorrectnessCanonicalEvidence.publishCaseViews(
                                caseDirectory,
                                matrix,
                                result,
                                context,
                                physicalExecutionId,
                                physicalStarted,
                                physicalCompleted,
                                System.nanoTime() - physicalStartNanos,
                                runtime);
                cases.add(result);
                canonicalViews.add(views);
            } catch (final IOException | RuntimeException failure) {
                publishCaseFailure(caseDirectory, failure);
                final GaCorrectnessCaseResult failed = GaCorrectnessCaseResult.failed(
                        matrixCase, caseDirectory, failureDescription(failure));
                cases.add(failed);
                if (runtime != null) {
                    try {
                        canonicalViews.add(GaCorrectnessCanonicalEvidence.publishCaseViews(
                                caseDirectory,
                                matrix,
                                failed,
                                context,
                                physicalExecutionId,
                                physicalStarted,
                                Instant.now(),
                                System.nanoTime() - physicalStartNanos,
                                runtime));
                    } catch (final IOException | RuntimeException canonicalFailure) {
                        publishCaseFailure(caseDirectory, canonicalFailure);
                    }
                }
                break;
            }
        }

        final List<String> failures = GaCorrectnessEvaluator.evaluate(matrix, cases);
        final boolean passed = failures.isEmpty() && cases.size() == matrix.cases().size();
        final Path summary = root.resolve("ga-g1-g2-summary-v1.txt");
        final Path manifest = root.resolve("ga-g1-g2-manifest-v1.txt");
        final Path hashes = root.resolve("artifact-hashes-v1.txt");
        final String summaryText = summaryText(matrix, cases, failures, passed);
        QualificationEvidencePublication.text(summary, summaryText);
        GaCorrectnessArtifactInventory.publishAdjacentSidecar(summary);
        final String summarySha256 = com.ultralatency.matching.qualification
                .QualificationArtifactHasher.sha256(summary);
        final Instant campaignCompleted = Instant.now();
        final GaCorrectnessCanonicalEvidence.GatePair gates = canonicalViews.isEmpty()
                ? null
                : GaCorrectnessCanonicalEvidence.publishGateResults(
                        root,
                        matrix,
                        canonicalViews,
                        context,
                        campaignStarted,
                        campaignCompleted,
                        passed);
        QualificationEvidencePublication.text(manifest, manifestText(
                matrix, cases, failures, passed, summarySha256, canonicalViews, gates, context));
        GaCorrectnessArtifactInventory.publishAdjacentSidecar(manifest);
        QualificationEvidencePublication.text(hashes, artifactHashes(root, hashes));
        GaCorrectnessArtifactInventory.publishAdjacentSidecar(hashes);
        return new GaCorrectnessCampaignResult(
                matrix,
                cases,
                passed,
                failures,
                root,
                summary,
                manifest,
                hashes,
                summarySha256);
    }

    private static GaCorrectnessCanonicalContext defaultContext(
            final GaCorrectnessMatrix matrix,
            final Path outputDirectory) throws IOException {
        if ("ga-g1-g2-test-v1".equals(matrix.version())) {
            return GaCorrectnessCanonicalContext.test(outputDirectory);
        }
        return GaCorrectnessCanonicalContext.fromSystem();
    }

    private static GaCorrectnessCaseResult runCase(
            final GaCorrectnessMatrix matrix,
            final GaCorrectnessCase matrixCase,
            final Path caseDirectory) throws IOException {
        final Path walDirectory = caseDirectory.resolve("wal");
        final Path pureSnapshotDirectory = caseDirectory.resolve("pure-snapshot");
        final Path snapshotDirectory = caseDirectory.resolve("snapshots");
        Files.createDirectories(walDirectory);
        Files.createDirectories(pureSnapshotDirectory);
        Files.createDirectories(snapshotDirectory);
        final QualificationConfiguration configuration = new QualificationConfiguration(
                matrixCase.profile(),
                matrixCase.seed(),
                matrix.commandCount(),
                COMMAND_TIMEOUT,
                caseDirectory);
        final WalConfiguration walConfiguration = new WalConfiguration(
                walDirectory,
                matrix.walSegmentSizeBytes(),
                WalDurabilityMode.SYNC_EACH_APPEND);
        final GaCorrectnessObservationAccumulator.Summary live = runLive(
                configuration, walConfiguration, pureSnapshotDirectory);
        final List<EngineCommand> persisted = CommandWalReader.read(walConfiguration);
        if (!QualificationWorkloadV1.matches(persisted, configuration)) {
            throw new IOException("persisted public WAL differs from deterministic workload");
        }
        if (!live.commandDigestHex().equals(GaCorrectnessCanonicalizer.commands(persisted))) {
            throw new IOException("live command digest differs from persisted WAL workload");
        }

        final RecoveryResult pure = RecoveryPlanner.create(
                walConfiguration, pureSnapshotDirectory).recover(RecoveryMode.PURE_WAL);
        final List<EngineResult> pureResults = pure.replayTranscript().results();
        final String pureTranscript = GaCorrectnessCanonicalizer.results(pureResults);
        final String pureProbe = GaCorrectnessCanonicalizer.probeResults(persisted, pureResults, 0);
        final long pureTrades = tradeCount(pureResults);
        if (pure.walEndSequence() != matrix.commandCount()) {
            throw new IOException("PURE_WAL end sequence does not match matrix command count");
        }
        final List<GaCorrectnessObservation> observations = new ArrayList<>();
        observations.add(new GaCorrectnessObservation(
                "LIVE",
                0,
                live.acceptedCommands(),
                live.tradeCount(),
                pure.walDigestHex(),
                pure.checkpointDigestHex(),
                live.transcriptDigestHex(),
                live.publicProbeDigestHex()));
        observations.add(new GaCorrectnessObservation(
                "PURE_WAL",
                0,
                pureResults.size(),
                pureTrades,
                pure.walDigestHex(),
                pure.checkpointDigestHex(),
                pureTranscript,
                pureProbe));

        final Map<Integer, String> suffixDigests = new LinkedHashMap<>();
        for (final int prefix : matrix.snapshotPrefixes()) {
            final Path prefixDirectory = snapshotDirectory.resolve("prefix-" + prefix);
            final Path publishedSnapshotDirectory = writeSnapshotPrefix(
                    persisted, prefix, matrix.walSegmentSizeBytes(), prefixDirectory);
            final RecoveryResult snapshot = RecoveryPlanner.create(
                    walConfiguration, publishedSnapshotDirectory)
                    .recover(RecoveryMode.SNAPSHOT_THEN_WAL);
            final List<EngineResult> tail = snapshot.replayTranscript().results();
            if (snapshot.snapshotSequence() != prefix) {
                throw new IOException("Snapshot sequence does not match requested prefix");
            }
            final String tailTranscript = GaCorrectnessCanonicalizer.results(tail);
            final String expectedSuffix = GaCorrectnessCanonicalizer.results(
                    pureResults, prefix);
            suffixDigests.put(prefix, expectedSuffix);
            observations.add(new GaCorrectnessObservation(
                    "SNAPSHOT_THEN_WAL",
                    prefix,
                    tail.size(),
                    tradeCount(tail),
                    snapshot.walDigestHex(),
                    snapshot.checkpointDigestHex(),
                    tailTranscript,
                    GaCorrectnessCanonicalizer.probeResults(persisted, tail, prefix)));
        }
        final GaCorrectnessCaseResult result = new GaCorrectnessCaseResult(
                matrixCase,
                observations,
                suffixDigests,
                true,
                List.of(),
                caseDirectory);
        QualificationEvidencePublication.text(
                caseDirectory.resolve("ga-case-result-v1.txt"), caseText(matrix, result));
        return result;
    }

    private static GaCorrectnessObservationAccumulator.Summary runLive(
            final QualificationConfiguration configuration,
            final WalConfiguration walConfiguration,
            final Path snapshotDirectory) throws IOException {
        final RecoverableDurableMatchingEngineTcpServer server = server(
                walConfiguration, snapshotDirectory, freePort());
        ProtocolV1QualificationClient client = null;
        final GaCorrectnessObservationAccumulator accumulator =
                new GaCorrectnessObservationAccumulator();
        try {
            server.start();
            client = new ProtocolV1QualificationClient(
                    server.localAddress().orElseThrow(), configuration.commandTimeout());
            for (int index = 0; index < configuration.commandCount(); index++) {
                final EngineCommand command = QualificationWorkloadV1.commandAtForRun(
                        configuration, index);
                final QualificationExchange exchange = client.exchange(command, index + 1L);
                accumulator.accept(command, exchange);
            }
        } finally {
            try {
                server.shutdown(SHUTDOWN_TIMEOUT);
            } finally {
                if (client != null) {
                    client.close();
                }
            }
        }
        if (server.failureCause().isPresent()) {
            throw new IOException("live qualification server entered terminal failure",
                    server.failureCause().orElseThrow());
        }
        return accumulator.finish();
    }

    private static RecoverableDurableMatchingEngineTcpServer server(
            final WalConfiguration walConfiguration,
            final Path snapshotDirectory,
            final int port) {
        final DurableNetworkConfiguration defaults = DurableNetworkConfiguration.defaults(
                walConfiguration.directory());
        final DurableConfiguration durable = new DurableConfiguration(
                walConfiguration,
                defaults.durableConfiguration().pipelineConfiguration(),
                defaults.durableConfiguration().shutdownTimeout());
        final DurableNetworkConfiguration configured = new DurableNetworkConfiguration(
                InetAddress.getLoopbackAddress(),
                port,
                defaults.writeBufferLowWaterMark(),
                defaults.writeBufferHighWaterMark(),
                durable);
        return new RecoverableDurableMatchingEngineTcpServer(
                RecoverableNetworkConfiguration.from(
                        configured, snapshotDirectory, RecoveryMode.PURE_WAL));
    }

    private static Path writeSnapshotPrefix(
            final List<EngineCommand> commands,
            final int prefix,
            final int segmentSizeBytes,
            final Path directory) throws IOException {
        final Path walDirectory = directory.resolve("wal");
        final Path snapshotDirectory = directory.resolve("snapshots");
        Files.createDirectories(walDirectory);
        Files.createDirectories(snapshotDirectory);
        final WalConfiguration configuration = new WalConfiguration(
                walDirectory, segmentSizeBytes, WalDurabilityMode.SYNC_EACH_APPEND);
        try (CommandWalWriter writer = new CommandWalWriter(configuration)) {
            for (int index = 0; index < prefix; index++) {
                writer.append(commands.get(index));
            }
        }
        new OfflineSnapshotGenerator(configuration, new SnapshotStore(snapshotDirectory)).generate();
        return snapshotDirectory;
    }

    private static long tradeCount(final List<EngineResult> results) {
        return results.stream().mapToLong(result -> result.matches().size()).sum();
    }

    private static String caseText(
            final GaCorrectnessMatrix matrix,
            final GaCorrectnessCaseResult result) {
        final StringBuilder output = new StringBuilder();
        output.append("schemaVersion=ga-g1-g2-case-v1\n");
        output.append("matrixVersion=").append(matrix.version()).append('\n');
        output.append("caseId=").append(result.matrixCase().id()).append('\n');
        output.append("profile=").append(result.matrixCase().profile()).append('\n');
        output.append("seed=").append(result.matrixCase().seed()).append('\n');
        output.append("repetition=").append(result.matrixCase().repetition()).append('\n');
        output.append("passed=").append(result.passed()).append('\n');
        for (final GaCorrectnessObservation observation : result.observations()) {
            final String prefix = "observation." + observation.mode() + "."
                    + observation.snapshotSequence() + ".";
            output.append(prefix).append("acceptedCommands=")
                    .append(observation.acceptedCommands()).append('\n');
            output.append(prefix).append("tradeCount=")
                    .append(observation.tradeCount()).append('\n');
            output.append(prefix).append("walDigestHex=")
                    .append(observation.walDigestHex()).append('\n');
            output.append(prefix).append("checkpointDigestHex=")
                    .append(observation.checkpointDigestHex()).append('\n');
            output.append(prefix).append("transcriptDigestHex=")
                    .append(observation.transcriptDigestHex()).append('\n');
            output.append(prefix).append("publicProbeDigestHex=")
                    .append(observation.publicProbeDigestHex()).append('\n');
        }
        result.expectedSnapshotTranscriptDigests().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> output.append("snapshotPrefix.").append(entry.getKey())
                        .append(".expectedTranscriptDigestHex=")
                        .append(entry.getValue()).append('\n'));
        return output.toString();
    }

    private static String summaryText(
            final GaCorrectnessMatrix matrix,
            final List<GaCorrectnessCaseResult> cases,
            final List<String> failures,
            final boolean passed) {
        final StringBuilder output = new StringBuilder();
        output.append("schemaVersion=ga-g1-g2-summary-v1\n");
        output.append("matrixVersion=").append(matrix.version()).append('\n');
        output.append("profileCount=").append(matrix.profiles().size()).append('\n');
        output.append("seedCount=").append(matrix.seeds().size()).append('\n');
        output.append("repetitions=").append(matrix.repetitions()).append('\n');
        output.append("commandCount=").append(matrix.commandCount()).append('\n');
        output.append("walSegmentSizeBytes=").append(matrix.walSegmentSizeBytes()).append('\n');
        output.append("snapshotPrefixes=").append(matrix.snapshotPrefixes()).append('\n');
        output.append("expectedCaseCount=").append(matrix.cases().size()).append('\n');
        output.append("observedCaseCount=").append(cases.size()).append('\n');
        output.append("expectedRecoveryObservationCount=")
                .append(matrix.recoveryObservationCount()).append('\n');
        output.append("passed=").append(passed).append('\n');
        output.append("failureCount=").append(failures.size()).append('\n');
        failures.forEach(failure -> output.append("failure=").append(failure).append('\n'));
        cases.forEach(result -> output.append("case.").append(result.matrixCase().id())
                .append("=").append(result.passed()).append('\n'));
        return output.toString();
    }

    private static String manifestText(
            final GaCorrectnessMatrix matrix,
            final List<GaCorrectnessCaseResult> cases,
            final List<String> failures,
            final boolean passed,
            final String summarySha256,
            final List<GaCorrectnessCanonicalEvidence.ViewPair> canonicalViews,
            final GaCorrectnessCanonicalEvidence.GatePair gates,
            final GaCorrectnessCanonicalContext context) {
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put("schemaVersion", "ga-g1-g2-manifest-v1");
        fields.put("matrixVersion", matrix.version());
        fields.put("candidate", context.candidate().tag());
        fields.put("controllerGitSha", context.controllerGitSha());
        fields.put("baselineTag", context.candidate().tag());
        fields.put("commandCount", Integer.toString(matrix.commandCount()));
        fields.put("walSegmentSizeBytes", Integer.toString(matrix.walSegmentSizeBytes()));
        fields.put("profiles", matrix.profiles().stream()
                .map(Enum::name).collect(Collectors.joining(",")));
        fields.put("seeds", matrix.seeds().stream()
                .map(Object::toString).collect(Collectors.joining(",")));
        fields.put("repetitions", Integer.toString(matrix.repetitions()));
        fields.put("snapshotPrefixes", matrix.snapshotPrefixes().stream()
                .map(Object::toString).collect(Collectors.joining(",")));
        fields.put("expectedCaseCount", Integer.toString(matrix.cases().size()));
        fields.put("observedCaseCount", Integer.toString(cases.size()));
        fields.put("recoveryObservationCount", Integer.toString(matrix.recoveryObservationCount()));
        fields.put("passed", Boolean.toString(passed));
        fields.put("failureCount", Integer.toString(failures.size()));
        fields.put("summarySha256", summarySha256);
        fields.put("canonicalPhysicalExecutionCount", Integer.toString(canonicalViews.size()));
        fields.put("canonicalG1ManifestCount", Integer.toString(canonicalViews.size()));
        fields.put("canonicalG2ManifestCount", Integer.toString(canonicalViews.size()));
        fields.put("canonicalG1GateResultPath", gates == null
                ? "none" : gates.g1ResultPath().getFileName().toString());
        fields.put("canonicalG2GateResultPath", gates == null
                ? "none" : gates.g2ResultPath().getFileName().toString());
        fields.put("canonicalEvidenceContract", "ga-run-manifest-v1+ga-gate-result-v1");
        final StringBuilder output = new StringBuilder();
        fields.forEach((key, value) -> output.append(key).append('=').append(value).append('\n'));
        return output.toString();
    }

    private static String artifactHashes(final Path root, final Path hashFile) throws IOException {
        final List<Path> files;
        try (var stream = Files.walk(root)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(path -> !path.equals(hashFile))
                    .sorted(Comparator.comparing(path -> root.relativize(path).toString()))
                    .toList();
        }
        final StringBuilder output = new StringBuilder();
        for (final Path file : files) {
            output.append(com.ultralatency.matching.qualification.QualificationArtifactHasher
                    .sha256(file)).append("  ")
                    .append(root.relativize(file).toString().replace('\\', '/')).append('\n');
        }
        return output.toString();
    }

    private static void publishCaseFailure(final Path directory, final Throwable failure) {
        try {
            QualificationEvidencePublication.text(
                    directory.resolve("failure-report-v1.txt"),
                    "status=FAIL\n" + failureDescription(failure) + "\n");
        } catch (final IOException ignored) {
            // The parent campaign still records the failure in its immutable summary.
        }
    }

    private static String failureDescription(final Throwable failure) {
        return "failureType=" + failure.getClass().getName() + "\n"
                + "failureMessage=" + String.valueOf(failure.getMessage());
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }
}
