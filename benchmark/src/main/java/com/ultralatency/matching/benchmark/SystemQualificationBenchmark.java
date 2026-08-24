package com.ultralatency.matching.benchmark;

import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.domain.Side;
import com.ultralatency.matching.engine.CancelOrderCommand;
import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.engine.SubmitLimitCommand;
import com.ultralatency.matching.integration.durable.DurableConfiguration;
import com.ultralatency.matching.network.netty.durable.DurableMatchingEngineTcpServer;
import com.ultralatency.matching.network.netty.durable.DurableNetworkConfiguration;
import com.ultralatency.matching.network.netty.recovery.RecoverableDurableMatchingEngineTcpServer;
import com.ultralatency.matching.network.netty.recovery.RecoverableNetworkConfiguration;
import com.ultralatency.matching.network.protocol.ProtocolConstants;
import com.ultralatency.matching.persistence.snapshot.OfflineSnapshotGenerator;
import com.ultralatency.matching.persistence.snapshot.SnapshotStore;
import com.ultralatency.matching.persistence.wal.CommandWalWriter;
import com.ultralatency.matching.persistence.wal.WalConfiguration;
import com.ultralatency.matching.persistence.wal.WalDurabilityMode;
import com.ultralatency.matching.recovery.online.RecoveryMode;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
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
 * Phase 9 full-path performance characterization.
 *
 * <p>The benchmark keeps the durable TCP round trip and restart-to-ready recovery
 * boundaries separate. It uses the real public Protocol v1 boundary and the real
 * durable/recoverable server composition. Fixture construction and cleanup are not
 * part of the measured operation.</p>
 *
 * <p>Results are component/local-host observations. They do not establish production
 * latency, throughput, availability, RTO, durable acknowledgement or Product Release
 * readiness.</p>
 */
@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3)
@Threads(1)
public class SystemQualificationBenchmark {

    /**
     * Measures one complete durable Protocol v1 request/result round trip.
     *
     * @param state live loopback state
     * @param blackhole benchmark sink
     * @return number of response frames consumed
     * @throws IOException when the bounded loopback exchange fails
     */
    @Benchmark
    public int durableProtocolRoundTrip(
            final DurableLoopbackState state,
            final Blackhole blackhole) throws IOException {
        final long requestId = state.nextRequestId++;
        final byte[] request = state.request(requestId);
        state.output.write(request);
        state.output.flush();
        final byte[] commandResult = readFrame(state.input);
        if (longAt(commandResult, Long.BYTES * 2) != requestId) {
            throw new IllegalStateException("Durable response correlation mismatch");
        }
        final int matchCount = intAt(commandResult, 36);
        if (matchCount < 0 || matchCount > 1024) {
            throw new IllegalStateException("Invalid match count: " + matchCount);
        }
        for (int index = 0; index < matchCount; index++) {
            blackhole.consume(readFrame(state.input));
        }
        return 1 + matchCount;
    }

    /**
     * Measures recovery, sequence convergence and listener binding.
     *
     * @param state closed WAL/Snapshot fixture state
     * @param blackhole benchmark sink
     * @return bound listener port
     */
    @Benchmark
    public int recoveryBootstrapToListener(
            final RecoveryBootstrapState state,
            final Blackhole blackhole) {
        state.server = new RecoverableDurableMatchingEngineTcpServer(state.configuration);
        state.server.start();
        final int port = state.server.localAddress().orElseThrow().getPort();
        blackhole.consume(state.server.state());
        return port;
    }

    /** State for the real durable Protocol v1 loopback boundary. */
    @State(Scope.Thread)
    public static class DurableLoopbackState {

        @Param({"LIFECYCLE_MIX", "CROSSING_MULTI_MATCH", "RESTING_DEPTH",
            "MEMORY_STEADY_STATE_V1"})
        private String workload;

        @Param({"4128", "65536"})
        private int segmentSizeBytes;

        private Path directory;
        private DurableMatchingEngineTcpServer server;
        private Socket socket;
        private InputStream input;
        private OutputStream output;
        private long nextRequestId;

        /** Starts one fresh live durable server outside measured operations. */
        @Setup(Level.Iteration)
        public void setUp() throws IOException {
            directory = Files.createTempDirectory("ulme-system-qualification-");
            final WalConfiguration wal = new WalConfiguration(
                    directory, segmentSizeBytes, WalDurabilityMode.SYNC_EACH_APPEND);
            final DurableNetworkConfiguration configuration =
                    new DurableNetworkConfiguration(
                            InetAddress.getLoopbackAddress(),
                            0,
                            DurableNetworkConfiguration.DEFAULT_LOW_WATERMARK,
                            DurableNetworkConfiguration.DEFAULT_HIGH_WATERMARK,
                            new DurableConfiguration(wal));
            server = new DurableMatchingEngineTcpServer(configuration);
            server.start();
            final var address = server.localAddress().orElseThrow();
            socket = new Socket(address.getAddress(), address.getPort());
            socket.setSoTimeout(5_000);
            input = socket.getInputStream();
            output = socket.getOutputStream();
            nextRequestId = 1;
        }

        /** Closes the client/server and removes only this benchmark fixture. */
        @TearDown(Level.Iteration)
        public void tearDown() throws IOException {
            if (socket != null) {
                socket.close();
            }
            if (server != null) {
                server.shutdown(Duration.ofSeconds(5));
            }
            deleteDirectory(directory);
        }

        private byte[] request(final long requestId) {
            final long position = (requestId - 1L) % 4L;
            final long group = (requestId - 1L) / 4L;
            return switch (workload) {
                case "LIFECYCLE_MIX", "MEMORY_STEADY_STATE_V1" ->
                        (requestId & 1L) == 1L
                                ? submitFrame(requestId, (requestId + 1L) / 2L, 1, 100L, 1L)
                                : cancelFrame(requestId, requestId / 2L);
                case "CROSSING_MULTI_MATCH" -> crossingFrame(
                        requestId, group, position);
                case "RESTING_DEPTH" -> submitFrame(
                        requestId, requestId, 1, 100L + (requestId % 16L), 1L);
                default -> throw new IllegalStateException("Unknown workload: " + workload);
            };
        }
    }

    /** State for restart-to-ready recovery measurements. */
    @State(Scope.Thread)
    public static class RecoveryBootstrapState {

        @Param({"PURE_WAL", "SNAPSHOT_THEN_WAL"})
        private String recoveryMode;

        @Param({"256", "1024"})
        private int commandCount;

        @Param({"4128", "65536"})
        private int segmentSizeBytes;

        private Path rootDirectory;
        private RecoverableNetworkConfiguration configuration;
        private RecoverableDurableMatchingEngineTcpServer server;

        /** Builds the closed authoritative WAL and optional Snapshot fixture. */
        @Setup(Level.Trial)
        public void setUp() throws IOException {
            rootDirectory = Files.createTempDirectory("ulme-system-recovery-");
            final Path walDirectory = rootDirectory.resolve("wal");
            final Path snapshotDirectory = rootDirectory.resolve("snapshots");
            final WalConfiguration wal = new WalConfiguration(
                    walDirectory, segmentSizeBytes, WalDurabilityMode.SYNC_EACH_APPEND);
            final int snapshotSequence = commandCount / 2;
            if ("SNAPSHOT_THEN_WAL".equals(recoveryMode)) {
                writeCommands(wal, commands(1, snapshotSequence));
                new OfflineSnapshotGenerator(wal, new SnapshotStore(snapshotDirectory)).generate();
                writeCommands(wal, commands(snapshotSequence + 1, commandCount));
            } else {
                writeCommands(wal, commands(1, commandCount));
            }
            final DurableConfiguration durable = new DurableConfiguration(wal);
            final DurableNetworkConfiguration network = new DurableNetworkConfiguration(
                    InetAddress.getLoopbackAddress(),
                    0,
                    DurableNetworkConfiguration.DEFAULT_LOW_WATERMARK,
                    DurableNetworkConfiguration.DEFAULT_HIGH_WATERMARK,
                    durable);
            configuration = RecoverableNetworkConfiguration.from(
                    network,
                    snapshotDirectory,
                    RecoveryMode.valueOf(recoveryMode));
        }

        /** Stops the server after each measured bootstrap invocation. */
        @TearDown(Level.Invocation)
        public void tearDownInvocation() {
            if (server != null) {
                server.shutdown(Duration.ofSeconds(5));
                server = null;
            }
        }

        /** Removes only this benchmark's recovery fixture. */
        @TearDown(Level.Trial)
        public void tearDown() throws IOException {
            deleteDirectory(rootDirectory);
        }
    }

    private static byte[] crossingFrame(
            final long requestId,
            final long group,
            final long position) {
        return switch ((int) position) {
            case 0 -> submitFrame(requestId, group * 3L + 1L, 1, 100L, 1L);
            case 1 -> submitFrame(requestId, group * 3L + 2L, 1, 101L, 1L);
            case 2 -> submitFrame(requestId, group * 3L + 3L, 2, 102L, 2L);
            default -> cancelFrame(requestId, group * 3L + 1L);
        };
    }

    private static List<EngineCommand> commands(final int first, final int last) {
        final List<EngineCommand> commands = new ArrayList<>(last - first + 1);
        for (int sequence = first; sequence <= last; sequence++) {
            final int group = (sequence - 1) / 4;
            final long orderId = group * 3L + Math.min(((sequence - 1) % 4) + 1L, 3L);
            final int position = (sequence - 1) % 4;
            if (position == 0) {
                commands.add(new SubmitLimitCommand(
                        Sequence.of(sequence), OrderId.of(orderId), Side.SELL,
                        Price.of(100), Quantity.of(1)));
            } else if (position == 1) {
                commands.add(new SubmitLimitCommand(
                        Sequence.of(sequence), OrderId.of(orderId), Side.SELL,
                        Price.of(101), Quantity.of(1)));
            } else if (position == 2) {
                commands.add(new CancelOrderCommand(
                        Sequence.of(sequence), OrderId.of(group * 3L + 1L)));
            } else {
                commands.add(new SubmitLimitCommand(
                        Sequence.of(sequence), OrderId.of(orderId), Side.BUY,
                        Price.of(99), Quantity.of(1)));
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

    private static byte[] submitFrame(
            final long requestId,
            final long orderId,
            final int side,
            final long price,
            final long quantity) {
        final ByteBuffer buffer = ByteBuffer.allocate(ProtocolConstants.SUBMIT_LIMIT_FRAME_LENGTH);
        writeHeader(buffer, ProtocolConstants.SUBMIT_LIMIT_TYPE,
                ProtocolConstants.SUBMIT_LIMIT_FRAME_LENGTH);
        buffer.putLong(requestId);
        buffer.putLong(orderId);
        buffer.put((byte) side);
        buffer.put(new byte[7]);
        buffer.putLong(price);
        buffer.putLong(quantity);
        return buffer.array();
    }

    private static byte[] cancelFrame(final long requestId, final long orderId) {
        final ByteBuffer buffer = ByteBuffer.allocate(ProtocolConstants.CANCEL_ORDER_FRAME_LENGTH);
        writeHeader(buffer, ProtocolConstants.CANCEL_ORDER_TYPE,
                ProtocolConstants.CANCEL_ORDER_FRAME_LENGTH);
        buffer.putLong(requestId);
        buffer.putLong(orderId);
        return buffer.array();
    }

    private static void writeHeader(
            final ByteBuffer buffer,
            final int type,
            final int length) {
        buffer.putInt(ProtocolConstants.MAGIC);
        buffer.put((byte) ProtocolConstants.VERSION);
        buffer.put((byte) type);
        buffer.putShort((short) 0);
        buffer.putInt(length);
        buffer.putInt(0);
    }

    private static byte[] readFrame(final InputStream input) throws IOException {
        final byte[] header = input.readNBytes(ProtocolConstants.HEADER_LENGTH);
        if (header.length != ProtocolConstants.HEADER_LENGTH) {
            throw new IOException("Durable loopback closed before response header");
        }
        final int length = intAt(header, 8);
        if (length < ProtocolConstants.HEADER_LENGTH || length > 1_048_576) {
            throw new IOException("Invalid response length: " + length);
        }
        final byte[] frame = Arrays.copyOf(header, length);
        final byte[] payload = input.readNBytes(length - ProtocolConstants.HEADER_LENGTH);
        if (payload.length != length - ProtocolConstants.HEADER_LENGTH) {
            throw new IOException("Durable loopback closed before response payload");
        }
        System.arraycopy(payload, 0, frame, ProtocolConstants.HEADER_LENGTH, payload.length);
        return frame;
    }

    private static long longAt(final byte[] bytes, final int offset) {
        return ByteBuffer.wrap(bytes, offset, Long.BYTES).getLong();
    }

    private static int intAt(final byte[] bytes, final int offset) {
        return ByteBuffer.wrap(bytes, offset, Integer.BYTES).getInt();
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
