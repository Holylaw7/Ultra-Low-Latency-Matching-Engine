package com.ultralatency.matching.benchmark;

import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.domain.Side;
import com.ultralatency.matching.engine.CancelOrderCommand;
import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.engine.SubmitLimitCommand;
import com.ultralatency.matching.persistence.wal.CommandWalReader;
import com.ultralatency.matching.persistence.wal.CommandWalWriter;
import com.ultralatency.matching.persistence.wal.WalConfiguration;
import com.ultralatency.matching.persistence.wal.WalDurabilityMode;
import com.ultralatency.matching.recovery.CommandWalReplayer;
import com.ultralatency.matching.recovery.ReplayTranscript;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * Component-level evidence for WAL append, strict scan and offline replay.
 *
 * <p>Each benchmark keeps filesystem append, strict decode and engine replay as separate
 * measurements. It does not claim live durable acknowledgement, network performance or product
 * throughput.</p>
 */
@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@Threads(1)
public class WalBenchmark {

    /**
     * Measures one complete command append under an explicit durability mode.
     *
     * @param state append state
     * @param blackhole benchmark sink
     * @throws IOException when the task-owned temporary WAL cannot be written
     */
    @Benchmark
    public void walAppend(final AppendState state, final Blackhole blackhole) throws IOException {
        state.writer.append(state.nextCommand());
        blackhole.consume(state.writer.nextCommandSequence());
    }

    /**
     * Measures strict closed-WAL scan and command decode only.
     *
     * @param state scan state
     * @param blackhole benchmark sink
     * @throws IOException when the task-owned WAL is invalid
     */
    @Benchmark
    public void walScan(final ScanState state, final Blackhole blackhole) throws IOException {
        blackhole.consume(new CommandWalReader(state.configuration).read().size());
    }

    /**
     * Measures closed-WAL command replay through a new genesis engine.
     *
     * @param state replay state
     * @param blackhole benchmark sink
     * @throws IOException when strict scan or replay fails
     */
    @Benchmark
    public void walReplay(final ReplayState state, final Blackhole blackhole) throws IOException {
        final ReplayTranscript transcript = new CommandWalReplayer(state.configuration).replay();
        blackhole.consume(transcript.results().size());
        blackhole.consume(transcript.sha256DigestHex());
    }

    /** State for one-command append measurements. */
    @State(Scope.Thread)
    public static class AppendState {

        @Param({"SYNC_EACH_APPEND", "BUFFERED"})
        private String durabilityMode;

        @Param({"4128", "65536"})
        private int segmentSizeBytes;

        private WalConfiguration configuration;
        private CommandWalWriter writer;
        private long nextSequence;

        /** Creates a task-owned WAL outside the measured append operation. */
        @Setup(Level.Iteration)
        public void setUp() throws IOException {
            final Path directory = Files.createTempDirectory("ulme-wal-append-");
            configuration = new WalConfiguration(
                    directory,
                    segmentSizeBytes,
                    WalDurabilityMode.valueOf(durabilityMode));
            writer = new CommandWalWriter(configuration);
            nextSequence = 1;
        }

        /** Closes and removes only this benchmark's temporary WAL. */
        @TearDown(Level.Iteration)
        public void tearDown() throws IOException {
            writer.close();
            deleteDirectory(configuration.directory());
        }

        private EngineCommand nextCommand() {
            final long orderId = (nextSequence + 1) / 2;
            final EngineCommand command;
            if ((nextSequence & 1L) == 1L) {
                command = new SubmitLimitCommand(
                        Sequence.of(nextSequence),
                        OrderId.of(orderId),
                        Side.SELL,
                        Price.of(100),
                        Quantity.of(1));
            } else {
                command = new CancelOrderCommand(
                        Sequence.of(nextSequence),
                        OrderId.of(orderId));
            }
            nextSequence++;
            return command;
        }
    }

    /** State for strict scan measurements over a fixed closed WAL. */
    @State(Scope.Thread)
    public static class ScanState {

        @Param({"256", "1024"})
        private int commandCount;

        @Param({"4128", "65536"})
        private int segmentSizeBytes;

        private WalConfiguration configuration;

        /** Builds and closes a deterministic WAL outside the measured scan. */
        @Setup(Level.Iteration)
        public void setUp() throws IOException {
            final Path directory = Files.createTempDirectory("ulme-wal-scan-");
            configuration = new WalConfiguration(
                    directory,
                    segmentSizeBytes,
                    WalDurabilityMode.BUFFERED);
            writeCommands(configuration, commandCount);
        }

        /** Removes only this benchmark's temporary WAL. */
        @TearDown(Level.Iteration)
        public void tearDown() throws IOException {
            deleteDirectory(configuration.directory());
        }
    }

    /** State for offline replay measurements over a fixed closed WAL. */
    @State(Scope.Thread)
    public static class ReplayState {

        @Param({"256", "1024"})
        private int commandCount;

        @Param({"4128", "65536"})
        private int segmentSizeBytes;

        private WalConfiguration configuration;

        /** Builds and closes a deterministic WAL outside the measured replay. */
        @Setup(Level.Iteration)
        public void setUp() throws IOException {
            final Path directory = Files.createTempDirectory("ulme-wal-replay-");
            configuration = new WalConfiguration(
                    directory,
                    segmentSizeBytes,
                    WalDurabilityMode.BUFFERED);
            writeCommands(configuration, commandCount);
        }

        /** Removes only this benchmark's temporary WAL. */
        @TearDown(Level.Iteration)
        public void tearDown() throws IOException {
            deleteDirectory(configuration.directory());
        }
    }

    private static void writeCommands(
            final WalConfiguration configuration,
            final int commandCount) throws IOException {
        try (CommandWalWriter writer = new CommandWalWriter(configuration)) {
            for (final EngineCommand command : commands(commandCount)) {
                writer.append(command);
            }
        }
    }

    private static List<EngineCommand> commands(final int count) {
        final List<EngineCommand> commands = new ArrayList<>(count);
        for (int sequence = 1; sequence <= count; sequence++) {
            final long orderId = (sequence + 1L) / 2L;
            if ((sequence & 1) == 1) {
                commands.add(new SubmitLimitCommand(
                        Sequence.of(sequence),
                        OrderId.of(orderId),
                        Side.SELL,
                        Price.of(100),
                        Quantity.of(1)));
            } else {
                commands.add(new CancelOrderCommand(
                        Sequence.of(sequence),
                        OrderId.of(orderId)));
            }
        }
        return List.copyOf(commands);
    }

    private static void deleteDirectory(final Path directory) throws IOException {
        if (!Files.exists(directory)) {
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
