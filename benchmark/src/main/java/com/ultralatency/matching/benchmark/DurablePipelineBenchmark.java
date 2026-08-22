package com.ultralatency.matching.benchmark;

import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.domain.Side;
import com.ultralatency.matching.engine.CancelOrderCommand;
import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.engine.SubmitLimitCommand;
import com.ultralatency.matching.integration.durable.DurableCommandCoordinator;
import com.ultralatency.matching.network.netty.codec.ProtocolResponseEncoder;
import com.ultralatency.matching.network.netty.durable.DurableMatchingEngineTcpServer;
import com.ultralatency.matching.network.netty.durable.DurableNetworkConfiguration;
import com.ultralatency.matching.network.protocol.ClientRequestId;
import com.ultralatency.matching.network.protocol.CommandResultResponse;
import com.ultralatency.matching.network.protocol.ProtocolCommandOutcome;
import com.ultralatency.matching.network.protocol.ProtocolConstants;
import com.ultralatency.matching.network.protocol.ProtocolResponse;
import com.ultralatency.matching.persistence.wal.CommandWalWriter;
import com.ultralatency.matching.persistence.wal.WalConfiguration;
import com.ultralatency.matching.persistence.wal.WalDurabilityMode;
import com.ultralatency.matching.pipeline.MatchingEnginePipeline;
import com.ultralatency.matching.pipeline.PipelineConfiguration;
import com.ultralatency.matching.pipeline.PipelineState;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
 * Phase 7 component and loopback evidence for the live durable command path.
 *
 * <p>The four methods deliberately keep durability, publication, local result encoding and
 * sequential durable loopback as separate measurements. Setup and teardown create or drain
 * task-owned resources outside the measured operation. This class does not claim durable client
 * acknowledgement, crash recovery, concurrent-client throughput or production capacity.</p>
 */
@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@Threads(1)
public class DurablePipelineBenchmark {

    /**
     * Measures one synchronous WAL append including the configured force action.
     *
     * @param state append state
     * @param blackhole benchmark sink
     * @throws IOException when the task-owned WAL cannot be written
     */
    @Benchmark
    public void walAppendForce(final AppendState state, final Blackhole blackhole)
            throws IOException {
        state.writer.append(state.command);
        blackhole.consume(state.writer.nextCommandSequence());
    }

    /**
     * Measures one live coordinator admission through append/force and pipeline publication.
     *
     * @param state coordinator state
     * @param blackhole benchmark sink
     */
    @Benchmark
    public void appendPlusPublish(final CoordinatorState state, final Blackhole blackhole) {
        blackhole.consume(state.coordinator.accept(
                ClientRequestId.of(1),
                sequence -> state.command(sequence.value())));
    }

    /**
     * Measures local result encoding without a socket or client acknowledgement.
     *
     * @param state response-write state
     * @return encoded response length
     */
    @Benchmark
    public int localResultWrite(final ResultWriteState state) {
        state.channel.writeOutbound(state.response);
        final ByteBuf encoded = state.channel.readOutbound();
        if (encoded == null) {
            throw new IllegalStateException("Protocol encoder produced no response");
        }
        try {
            return encoded.readableBytes();
        } finally {
            encoded.release();
        }
    }

    /**
     * Measures one sequential Submit/Cancel request through the live durable loopback server.
     *
     * @param state durable loopback state
     * @return number of response frames consumed
     * @throws IOException when the bounded local exchange fails
     */
    @Benchmark
    public int loopbackSequentialRoundTrip(final LoopbackState state) throws IOException {
        final long requestId = state.nextRequestId;
        final long orderId = 100_000L + ((requestId + 1L) / 2L);
        final byte[] request = (requestId & 1L) == 1L
                ? submitFrame(requestId, orderId)
                : cancelFrame(requestId, orderId);
        state.output.write(request);
        state.output.flush();
        final byte[] commandResult = readFrame(state.input);
        if (longAt(commandResult, 16) != requestId) {
            throw new IllegalStateException("Durable loopback correlation mismatch");
        }
        final int matchCount = intAt(commandResult, 36);
        for (int index = 0; index < matchCount; index++) {
            readFrame(state.input);
        }
        state.nextRequestId++;
        return 1 + matchCount;
    }

    /** State for one synchronous append/force operation. */
    @State(Scope.Thread)
    public static class AppendState {

        @Param({"4128", "65536"})
        private int segmentSizeBytes;

        private Path directory;
        private CommandWalWriter writer;
        private EngineCommand command;

        /** Creates one fresh synchronous WAL outside the measured operation. */
        @Setup(Level.Invocation)
        public void setUp() throws IOException {
            directory = Files.createTempDirectory("ulme-phase7-append-");
            writer = new CommandWalWriter(new WalConfiguration(
                    directory, segmentSizeBytes, WalDurabilityMode.SYNC_EACH_APPEND));
            command = submit(1, 1, Side.BUY, 100);
        }

        /** Closes and removes only this invocation's WAL. */
        @TearDown(Level.Invocation)
        public void tearDown() throws IOException {
            closeWriter(writer);
            deleteDirectory(directory);
        }
    }

    /** State for one live append/force plus pipeline publication operation. */
    @State(Scope.Thread)
    public static class CoordinatorState {

        @Param({"4128", "65536"})
        private int segmentSizeBytes;

        private Path directory;
        private CommandWalWriter writer;
        private MatchingEnginePipeline pipeline;
        private DurableCommandCoordinator coordinator;

        @Param({"SUBMIT", "CANCEL"})
        private String commandType;

        /** Creates and starts one real WAL/pipeline composition outside measurement. */
        @Setup(Level.Invocation)
        public void setUp() throws IOException {
            directory = Files.createTempDirectory("ulme-phase7-admission-");
            final WalConfiguration walConfiguration = new WalConfiguration(
                    directory, segmentSizeBytes, WalDurabilityMode.SYNC_EACH_APPEND);
            writer = new CommandWalWriter(walConfiguration);
            pipeline = new MatchingEnginePipeline(
                    PipelineConfiguration.defaults(), result -> { });
            pipeline.start();
            coordinator = new DurableCommandCoordinator(writer::append, pipeline::tryPublish);
            coordinator.start();
        }

        /** Builds the deterministic Submit/Cancel workload vector for this invocation. */
        private EngineCommand command(final long sequence) {
            if ("CANCEL".equals(commandType)) {
                return new CancelOrderCommand(Sequence.of(sequence), OrderId.of(1));
            }
            return submit(sequence, 1, Side.BUY, 100);
        }

        /** Drains accepted work, closes the WAL and removes only this invocation's resources. */
        @TearDown(Level.Invocation)
        public void tearDown() throws IOException {
            if (coordinator != null) {
                coordinator.shutdown();
            }
            if (pipeline != null
                    && (pipeline.state() == PipelineState.RUNNING
                    || pipeline.state() == PipelineState.DRAINING)) {
                pipeline.shutdown(Duration.ofSeconds(5));
            }
            closeWriter(writer);
            deleteDirectory(directory);
        }
    }

    /** State for one local Protocol v1 result encoding operation. */
    @State(Scope.Thread)
    public static class ResultWriteState {

        @Param({"COMMAND", "MATCH"})
        private String responseType;

        private EmbeddedChannel channel;
        private ProtocolResponse response;

        /** Builds one deterministic response vector outside the measured operation. */
        @Setup(Level.Invocation)
        public void setUp() {
            channel = new EmbeddedChannel(new ProtocolResponseEncoder());
            response = "COMMAND".equals(responseType)
                    ? new CommandResultResponse(
                    ClientRequestId.of(1),
                    Sequence.of(1),
                    ProtocolCommandOutcome.ACCEPTED,
                    0)
                    : new com.ultralatency.matching.network.protocol.MatchResultResponse(
                    ClientRequestId.of(1),
                    Sequence.of(1),
                    0,
                    1,
                    com.ultralatency.matching.domain.EventSequence.of(1),
                    com.ultralatency.matching.domain.TradeId.of(1),
                    Price.of(100),
                    Quantity.of(1),
                    OrderId.of(1),
                    0,
                    OrderId.of(2),
                    0);
        }

        /** Releases the embedded channel after this invocation. */
        @TearDown(Level.Invocation)
        public void tearDown() {
            channel.finishAndReleaseAll();
        }
    }

    /** State for one persistent local durable server and one request in flight. */
    @State(Scope.Thread)
    public static class LoopbackState {

        private Path directory;
        private DurableMatchingEngineTcpServer server;
        private Socket socket;
        private InputStream input;
        private OutputStream output;
        private long nextRequestId;

        /** Starts one fresh WAL-backed durable loopback server outside measurement. */
        @Setup(Level.Iteration)
        public void setUp() throws IOException {
            directory = Files.createTempDirectory("ulme-phase7-loopback-");
            server = new DurableMatchingEngineTcpServer(
                    DurableNetworkConfiguration.defaults(directory));
            server.start();
            final var address = server.localAddress().orElseThrow();
            socket = new Socket(address.getAddress(), address.getPort());
            socket.setSoTimeout(5_000);
            input = socket.getInputStream();
            output = socket.getOutputStream();
            nextRequestId = 1;
        }

        /** Closes the client/server and removes only this benchmark WAL. */
        @TearDown(Level.Iteration)
        public void tearDown() throws IOException {
            if (socket != null) {
                socket.close();
            }
            if (server != null) {
                server.shutdown();
            }
            deleteDirectory(directory);
        }
    }

    private static EngineCommand submit(
            final long sequence,
            final long orderId,
            final Side side,
            final long price) {
        return new SubmitLimitCommand(
                Sequence.of(sequence),
                OrderId.of(orderId),
                side,
                Price.of(price),
                Quantity.of(1));
    }

    private static byte[] submitFrame(final long requestId, final long orderId) {
        final ByteBuffer buffer = ByteBuffer.allocate(ProtocolConstants.SUBMIT_LIMIT_FRAME_LENGTH);
        writeHeader(buffer, ProtocolConstants.SUBMIT_LIMIT_TYPE,
                ProtocolConstants.SUBMIT_LIMIT_FRAME_LENGTH);
        buffer.putLong(requestId);
        buffer.putLong(orderId);
        buffer.put((byte) 1);
        buffer.put(new byte[7]);
        buffer.putLong(100);
        buffer.putLong(1);
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

    private static void closeWriter(final CommandWalWriter writer) throws IOException {
        if (writer != null) {
            writer.close();
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
