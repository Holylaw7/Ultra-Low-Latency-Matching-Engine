package com.ultralatency.matching.benchmark;

import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.domain.Side;
import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.engine.MatchingEngine;
import com.ultralatency.matching.engine.SubmitLimitCommand;
import com.ultralatency.matching.integration.durable.DurableConfiguration;
import com.ultralatency.matching.network.netty.durable.DurableNetworkConfiguration;
import com.ultralatency.matching.network.netty.recovery.RecoverableDurableMatchingEngineTcpServer;
import com.ultralatency.matching.network.netty.recovery.RecoverableNetworkConfiguration;
import com.ultralatency.matching.persistence.snapshot.OfflineSnapshotGenerator;
import com.ultralatency.matching.persistence.snapshot.Snapshot;
import com.ultralatency.matching.persistence.snapshot.SnapshotStore;
import com.ultralatency.matching.persistence.wal.CommandWalWriter;
import com.ultralatency.matching.persistence.wal.WalConfiguration;
import com.ultralatency.matching.persistence.wal.WalDurabilityMode;
import com.ultralatency.matching.recovery.online.RecoveryMode;
import com.ultralatency.matching.recovery.online.RecoveryPlanner;
import com.ultralatency.matching.recovery.online.RecoveryResult;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Component-level Phase 8 recovery and bootstrap evidence.
 *
 * <p>Fixture construction, WAL writing and Snapshot publication are outside measured operations.
 * The benchmark does not claim production RTO, availability, power-loss safety or client outcome
 * recovery.</p>
 */
@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@Threads(1)
public class RecoveryBenchmark {

    /** Measures strict genesis replay of the complete authoritative WAL. */
    @Benchmark
    public void pureWalReplay(final RecoveryState state, final Blackhole blackhole)
            throws IOException {
        final RecoveryResult result = new RecoveryPlanner(
                state.walConfiguration,
                state.snapshotStore).recover(RecoveryMode.PURE_WAL);
        consume(result, blackhole);
    }

    /** Measures Snapshot decode and canonical engine checkpoint restore only. */
    @Benchmark
    public void snapshotDecodeRestore(final RecoveryState state, final Blackhole blackhole)
            throws IOException {
        final Snapshot snapshot = state.snapshotStore.read(state.snapshotPath);
        final MatchingEngine engine = MatchingEngine.fromCheckpoint(snapshot.checkpoint());
        blackhole.consume(engine.checkpoint().canonicalCheckpointDigest());
        blackhole.consume(snapshot.checkpointSequence());
    }

    /** Measures validated Snapshot restore followed by strict WAL-tail replay. */
    @Benchmark
    public void snapshotTailRecovery(final RecoveryState state, final Blackhole blackhole)
            throws IOException {
        final RecoveryResult result = new RecoveryPlanner(
                state.walConfiguration,
                state.snapshotStore).recover(RecoveryMode.SNAPSHOT_THEN_WAL);
        consume(result, blackhole);
    }

    /** Measures offline Snapshot generation from a closed complete WAL. */
    @Benchmark
    public void offlineSnapshotCreation(
            final SnapshotCreationState state,
            final Blackhole blackhole) throws IOException {
        final Snapshot snapshot = new OfflineSnapshotGenerator(
                state.walConfiguration,
                state.snapshotStore).generate();
        blackhole.consume(snapshot.canonicalCheckpointDigest());
    }

    /** Measures recovered process bootstrap through listener-ready state. */
    @Benchmark
    public void bootstrapToListener(
            final RecoveryState state,
            final Blackhole blackhole) throws IOException {
        final DurableConfiguration durable = new DurableConfiguration(
                new WalConfiguration(
                        state.walConfiguration.directory(),
                        state.segmentSizeBytes,
                        WalDurabilityMode.SYNC_EACH_APPEND));
        final RecoverableNetworkConfiguration configuration =
                RecoverableNetworkConfiguration.from(
                        new DurableNetworkConfiguration(
                                InetAddress.getLoopbackAddress(),
                                0,
                                DurableNetworkConfiguration.DEFAULT_LOW_WATERMARK,
                                DurableNetworkConfiguration.DEFAULT_HIGH_WATERMARK,
                                durable),
                        state.snapshotDirectory,
                        RecoveryMode.SNAPSHOT_THEN_WAL);
        state.server = new RecoverableDurableMatchingEngineTcpServer(configuration);
        state.server.start();
        blackhole.consume(state.server.localAddress().orElseThrow().getPort());
    }

    private static void consume(final RecoveryResult result, final Blackhole blackhole) {
        blackhole.consume(result.checkpointDigestHex());
        blackhole.consume(result.walDigestHex());
        blackhole.consume(result.nextCommandSequence());
        blackhole.consume(result.replayTranscript().results().size());
        blackhole.consume(result.replayTranscript().sha256DigestHex());
    }

    /** Shared closed-WAL plus Snapshot fixture for recovery and bootstrap measurements. */
    @State(Scope.Thread)
    public static class RecoveryState {

        @Param({"256", "1024"})
        private int commandCount;

        @Param({"4128", "65536"})
        private int segmentSizeBytes;

        private Path rootDirectory;
        private Path snapshotDirectory;
        private WalConfiguration walConfiguration;
        private SnapshotStore snapshotStore;
        private Path snapshotPath;
        private RecoverableDurableMatchingEngineTcpServer server;

        /** Builds a prefix Snapshot and appends a deterministic WAL tail outside the benchmark. */
        @Setup(Level.Trial)
        public void setUp() throws IOException {
            rootDirectory = Files.createTempDirectory("ulme-recovery-benchmark-");
            final Path walDirectory = rootDirectory.resolve("wal");
            snapshotDirectory = rootDirectory.resolve("snapshots");
            walConfiguration = new WalConfiguration(
                    walDirectory,
                    segmentSizeBytes,
                    WalDurabilityMode.BUFFERED);
            snapshotStore = new SnapshotStore(snapshotDirectory);
            final int snapshotSequence = commandCount / 2;
            writeCommands(walConfiguration, commands(1, snapshotSequence));
            final Snapshot snapshot = new OfflineSnapshotGenerator(
                    walConfiguration,
                    snapshotStore).generate();
            snapshotPath = snapshotStore.readLatest()
                    .map(ignored -> snapshotDirectory.resolve(
                            String.format("snapshot-%020d.bin", snapshot.checkpointSequence())))
                    .orElseThrow();
            try (CommandWalWriter writer = new CommandWalWriter(walConfiguration)) {
                for (final EngineCommand command : commands(snapshotSequence + 1, commandCount)) {
                    writer.append(command);
                }
            }
        }

        /** Stops a bootstrap server after each measured invocation. */
        @TearDown(Level.Invocation)
        public void tearDownInvocation() {
            if (server != null) {
                server.shutdown(Duration.ofSeconds(2));
                server = null;
            }
        }

        /** Removes only this benchmark's temporary fixture. */
        @TearDown(Level.Trial)
        public void tearDown() throws IOException {
            deleteDirectory(rootDirectory);
        }
    }

    /** Closed complete-WAL fixture used only for offline Snapshot creation. */
    @State(Scope.Thread)
    public static class SnapshotCreationState {

        @Param({"256", "1024"})
        private int commandCount;

        @Param({"4128", "65536"})
        private int segmentSizeBytes;

        private Path rootDirectory;
        private WalConfiguration walConfiguration;
        private SnapshotStore snapshotStore;

        /** Builds the closed WAL outside the measured Snapshot generation operation. */
        @Setup(Level.Trial)
        public void setUp() throws IOException {
            rootDirectory = Files.createTempDirectory("ulme-snapshot-benchmark-");
            walConfiguration = new WalConfiguration(
                    rootDirectory.resolve("wal"),
                    segmentSizeBytes,
                    WalDurabilityMode.BUFFERED);
            writeCommands(walConfiguration, commands(1, commandCount));
            snapshotStore = new SnapshotStore(rootDirectory.resolve("snapshots"));
        }

        /** Uses one fresh publication directory per invocation so no final Snapshot is replaced. */
        @Setup(Level.Invocation)
        public void setUpInvocation() throws IOException {
            snapshotStore = new SnapshotStore(Files.createTempDirectory(
                    rootDirectory,
                    "snapshot-publication-"));
        }

        /** Removes the per-invocation publication directory. */
        @TearDown(Level.Invocation)
        public void tearDownInvocation() throws IOException {
            deleteDirectory(snapshotStore.directory());
        }

        /** Removes the closed-WAL fixture. */
        @TearDown(Level.Trial)
        public void tearDown() throws IOException {
            deleteDirectory(rootDirectory);
        }
    }

    private static List<EngineCommand> commands(final int first, final int last) {
        final List<EngineCommand> commands = new ArrayList<>(last - first + 1);
        for (int sequence = first; sequence <= last; sequence++) {
            final int group = (sequence - 1) / 4;
            final long orderId = group * 3L + Math.min(((sequence - 1) % 4) + 1L, 3L);
            final int position = (sequence - 1) % 4;
            if (position == 0) {
                commands.add(new SubmitLimitCommand(
                        Sequence.of(sequence),
                        OrderId.of(orderId),
                        Side.SELL,
                        Price.of(100),
                        Quantity.of(1)));
            } else if (position == 1) {
                commands.add(new SubmitLimitCommand(
                        Sequence.of(sequence),
                        OrderId.of(orderId),
                        Side.SELL,
                        Price.of(101),
                        Quantity.of(1)));
            } else if (position == 2) {
                commands.add(new com.ultralatency.matching.engine.CancelOrderCommand(
                        Sequence.of(sequence),
                        OrderId.of(group * 3L + 1L)));
            } else {
                commands.add(new SubmitLimitCommand(
                        Sequence.of(sequence),
                        OrderId.of(orderId),
                        Side.BUY,
                        Price.of(99),
                        Quantity.of(1)));
            }
        }
        return List.copyOf(commands);
    }

    private static void writeCommands(
            final WalConfiguration configuration,
            final List<EngineCommand> commands) throws IOException {
        try (CommandWalWriter writer = new CommandWalWriter(configuration)) {
            for (final EngineCommand command : commands) {
                writer.append(command);
            }
        }
    }

    private static void deleteDirectory(final Path directory) throws IOException {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            final List<Path> reverse = paths.sorted((left, right) -> right.compareTo(left)).toList();
            for (final Path path : reverse) {
                Files.deleteIfExists(path);
            }
        }
    }
}
