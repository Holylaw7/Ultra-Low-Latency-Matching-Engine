package com.ultralatency.matching.qualification;

import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.network.netty.durable.DurableNetworkConfiguration;
import com.ultralatency.matching.network.netty.recovery.RecoverableDurableMatchingEngineTcpServer;
import com.ultralatency.matching.network.netty.recovery.RecoverableNetworkConfiguration;
import com.ultralatency.matching.integration.durable.DurableConfiguration;
import com.ultralatency.matching.persistence.wal.WalCommandCodec;
import com.ultralatency.matching.persistence.wal.WalDurabilityMode;
import com.ultralatency.matching.persistence.wal.CommandWalReader;
import com.ultralatency.matching.persistence.wal.WalConfiguration;
import com.ultralatency.matching.recovery.online.RecoveryMode;
import com.ultralatency.matching.recovery.online.RecoveryPlanner;
import com.ultralatency.matching.recovery.online.RecoveryResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Runs a deterministic qualification workload through the public Protocol v1 TCP boundary.
 *
 * <p>Each restart cycle uses a fresh session and the same WAL directory. The live server is
 * therefore forced to recover from the authoritative WAL before the next cycle can accept a
 * command. No coordinator, pipeline or engine method is called by this class.</p>
 */
public final class QualificationRunner {

    /** Quick-lane restart count required by the Phase 9 Blueprint. */
    public static final int QUICK_RESTART_CYCLES = 3;

    /** Number of final public exchanges included in the deterministic probe suffix. */
    public static final int PUBLIC_PROBE_SUFFIX_LENGTH = 2;

    /** Runs the default three-cycle public-boundary qualification. */
    public QualificationRun run(final QualificationConfiguration configuration)
            throws IOException {
        return run(configuration, QUICK_RESTART_CYCLES);
    }

    /** Runs a workload over the requested number of recovery-backed TCP sessions. */
    public QualificationRun run(
            final QualificationConfiguration configuration,
            final int restartCycles) throws IOException {
        Objects.requireNonNull(configuration, "configuration");
        if (restartCycles <= 0 || restartCycles > configuration.commandCount()) {
            throw new IllegalArgumentException("restartCycles must be within command count");
        }
        final QualificationWorkload workload = QualificationWorkloadV1.generate(configuration);
        final String runId = "qualification-" + UUID.randomUUID();
        final Path storageDirectory = Files.createTempDirectory("qualification-run-");
        final Path walDirectory = storageDirectory.resolve("wal");
        final Path snapshotDirectory = storageDirectory.resolve("snapshots");
        final WalConfiguration walConfiguration = WalConfiguration.defaults(walDirectory);
        final MessageDigest transcriptDigest = sha256();
        final List<QualificationExchange> exchanges = new ArrayList<>(workload.commandCount());
        long responseCount = 0;
        long tradeCount = 0;
        try {
            for (int cycle = 0; cycle < restartCycles; cycle++) {
                final int start = cycle * workload.commandCount() / restartCycles;
                final int end = (cycle + 1) * workload.commandCount() / restartCycles;
                final RecoverableDurableMatchingEngineTcpServer server = server(
                        walDirectory, snapshotDirectory);
                server.start();
                ProtocolV1QualificationClient client = null;
                try {
                    client = new ProtocolV1QualificationClient(
                            server.localAddress().orElseThrow(), configuration.commandTimeout());
                    for (int index = start; index < end; index++) {
                        final EngineCommand command = workload.commands().get(index);
                        final long requestId = index - start + 1L;
                        final QualificationExchange exchange = client.exchange(command, requestId);
                        exchanges.add(exchange);
                        transcriptDigest.update(exchange.transcriptDigestHex()
                                .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                        responseCount += exchange.responseFrameCount();
                        tradeCount += exchange.matches().size();
                    }
                } finally {
                    shutdownBeforeClientClose(server, client, configuration.commandTimeout());
                }
            }

            final List<EngineCommand> persistedCommands = CommandWalReader.read(walConfiguration);
            if (!persistedCommands.equals(workload.commands())) {
                throw new IOException("WAL command stream differs from public workload");
            }
            final RecoveryResult recovered = RecoveryPlanner.create(
                    walConfiguration, snapshotDirectory).recover(RecoveryMode.PURE_WAL);
            if (recovered.walEndSequence() != persistedCommands.size()) {
                throw new IOException("recovered WAL end does not match persisted workload");
            }
            final String walCommandDigest = QualificationCanonicalizer.digest(persistedCommands);
            final String checkpointDigest = recovered.checkpointDigestHex();
            final String transcriptDigestHex = HexFormat.of().formatHex(transcriptDigest.digest());
            final int probeStart = Math.max(
                    0, exchanges.size() - PUBLIC_PROBE_SUFFIX_LENGTH);
            final String publicProbeDigest = QualificationCanonicalizer.digestPublicProbe(
                    workload.commands(), exchanges.subList(probeStart, exchanges.size()), probeStart);
            final Map<String, String> measurements = measurements(
                    configuration, restartCycles, persistedCommands, responseCount, tradeCount,
                    walDirectory, walCommandDigest, checkpointDigest, transcriptDigestHex);
            final QualificationResult result = new QualificationResult(
                    true,
                    workload.commandCount(),
                    responseCount,
                    tradeCount,
                    checkpointDigest,
                    transcriptDigestHex,
                    publicProbeDigest,
                    measurements);
            final QualificationManifest manifest = new QualificationManifest(
                    runId,
                    System.getProperty("qualification.git.sha", "working-tree"),
                    System.getProperty(
                            "qualification.baseline", "v0.7.0-engineering-baseline"),
                    workload,
                    configuration,
                    configuration.outputDirectory(),
                    environment(),
                    QualificationCanonicalizer.digest(configuration),
                    QualificationCanonicalizer.EMPTY_DIGEST,
                    Instant.now());
            return new QualificationRun(manifest.withResult(result), result, restartCycles);
        } finally {
            deleteOwnedDirectory(storageDirectory);
        }
    }

    static RecoverableDurableMatchingEngineTcpServer server(
            final Path walDirectory,
            final Path snapshotDirectory) {
        return server(walDirectory, snapshotDirectory, 0);
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

    static RecoverableDurableMatchingEngineTcpServer server(
            final Path walDirectory,
            final Path snapshotDirectory,
            final int port) {
        return server(walDirectory, snapshotDirectory, port,
                com.ultralatency.matching.persistence.wal.WalConfiguration
                        .DEFAULT_SEGMENT_SIZE_BYTES);
    }

    /**
     * Creates the qualification-only recoverable server with an explicit legal WAL segment size.
     * The overload keeps the existing transport and runtime composition unchanged while allowing
     * the G3 matrix to exercise the production-supported lower segment bound.
     */
    public static RecoverableDurableMatchingEngineTcpServer server(
            final Path walDirectory,
            final Path snapshotDirectory,
            final int port,
            final int walSegmentSize) {
        if (walSegmentSize < WalCommandCodec.MIN_SEGMENT_SIZE_BYTES) {
            throw new IllegalArgumentException("WAL segment size is below the supported minimum");
        }
        final WalConfiguration wal = new WalConfiguration(
                Objects.requireNonNull(walDirectory, "walDirectory"),
                walSegmentSize,
                WalDurabilityMode.SYNC_EACH_APPEND);
        final DurableConfiguration defaults = DurableConfiguration.defaults(walDirectory);
        final DurableConfiguration durableConfiguration = new DurableConfiguration(
                wal,
                defaults.pipelineConfiguration(),
                defaults.shutdownTimeout());
        final DurableNetworkConfiguration durable = new DurableNetworkConfiguration(
                java.net.InetAddress.getLoopbackAddress(),
                port,
                DurableNetworkConfiguration.DEFAULT_LOW_WATERMARK,
                DurableNetworkConfiguration.DEFAULT_HIGH_WATERMARK,
                durableConfiguration);
        final DurableNetworkConfiguration configured = new DurableNetworkConfiguration(
                durable.bindAddress(),
                port,
                durable.writeBufferLowWaterMark(),
                durable.writeBufferHighWaterMark(),
                durable.durableConfiguration());
        return new RecoverableDurableMatchingEngineTcpServer(
                RecoverableNetworkConfiguration.from(
                        configured, snapshotDirectory, RecoveryMode.PURE_WAL));
    }

    private static Map<String, String> measurements(
            final QualificationConfiguration configuration,
            final int restartCycles,
            final List<EngineCommand> commands,
            final long responseCount,
            final long tradeCount,
            final Path walDirectory,
            final String walCommandDigest,
            final String checkpointDigest,
            final String transcriptDigest) throws IOException {
        final long walBytes;
        final long segmentCount;
        if (Files.exists(walDirectory)) {
            try (java.util.stream.Stream<Path> paths = Files.walk(walDirectory)) {
                walBytes = paths.filter(Files::isRegularFile)
                        .mapToLong(QualificationRunner::fileSize)
                        .sum();
            }
            try (java.util.stream.Stream<Path> paths = Files.list(walDirectory)) {
                segmentCount = paths.filter(Files::isRegularFile).count();
            }
        } else {
            walBytes = 0L;
            segmentCount = 0L;
        }
        final Map<String, String> values = new HashMap<>();
        values.put("profile", configuration.profile().name());
        values.put("seed", Long.toString(configuration.seed()));
        values.put("commandCount", Integer.toString(commands.size()));
        values.put("restartCycles", Integer.toString(restartCycles));
        values.put("responseFrameCount", Long.toString(responseCount));
        values.put("tradeCount", Long.toString(tradeCount));
        values.put("walBytes", Long.toString(walBytes));
        values.put("walSegmentCount", Long.toString(segmentCount));
        values.put("walCommandDigestHex", walCommandDigest);
        values.put("checkpointDigestHex", checkpointDigest);
        values.put("transcriptDigestHex", transcriptDigest);
        values.put("publicProbeSuffixLength", Integer.toString(PUBLIC_PROBE_SUFFIX_LENGTH));
        return Map.copyOf(values);
    }

    private static long fileSize(final Path path) {
        try {
            return Files.size(path);
        } catch (final IOException exception) {
            throw new IllegalStateException("cannot read WAL file size", exception);
        }
    }

    private static Map<String, String> environment() {
        return Map.of(
                "java.version", System.getProperty("java.version", "unknown"),
                "os.name", System.getProperty("os.name", "unknown"),
                "os.arch", System.getProperty("os.arch", "unknown"));
    }

    private static void deleteOwnedDirectory(final Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(directory)) {
            final List<Path> ownedPaths = paths.sorted(java.util.Comparator.reverseOrder()).toList();
            IOException failure = null;
            for (final Path path : ownedPaths) {
                try {
                    Files.deleteIfExists(path);
                } catch (final IOException exception) {
                    failure = exception;
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JDK", exception);
        }
    }
}
