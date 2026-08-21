package com.ultralatency.matching.benchmark;

import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.network.netty.codec.ProtocolResponseEncoder;
import com.ultralatency.matching.network.netty.gateway.MatchingEngineTcpServer;
import com.ultralatency.matching.network.netty.gateway.NetworkConfiguration;
import com.ultralatency.matching.network.protocol.ClientRequestId;
import com.ultralatency.matching.network.protocol.CommandResultResponse;
import com.ultralatency.matching.network.protocol.ErrorResponse;
import com.ultralatency.matching.network.protocol.MatchResultResponse;
import com.ultralatency.matching.network.protocol.ProtocolConstants;
import com.ultralatency.matching.network.protocol.ProtocolCommandOutcome;
import com.ultralatency.matching.network.protocol.ProtocolErrorCode;
import com.ultralatency.matching.network.protocol.ProtocolRequest;
import com.ultralatency.matching.network.protocol.ProtocolResponse;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;
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

/**
 * Component and local-host evidence for the Phase 6 protocol boundary.
 *
 * <p>Codec measurements isolate request decode and response encode. The loopback measurement
 * covers one sequential request through TCP, gateway admission, the existing pipeline and a
 * complete ordered result write. It does not measure durable acknowledgement, client receipt,
 * concurrent clients or production exchange throughput.</p>
 */
@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@Threads(1)
public class NetworkBenchmark {

    /**
     * Measures strict decode of one fixed Submit or Cancel request frame.
     *
     * @param state codec state
     * @return decoded request hash
     */
    @Benchmark
    public int requestDecode(final CodecState state) {
        state.channel.writeInbound(Unpooled.wrappedBuffer(state.requestBytes));
        final ProtocolRequest request = state.channel.readInbound();
        if (request == null) {
            throw new IllegalStateException("Protocol decoder produced no request");
        }
        return request.hashCode();
    }

    /**
     * Measures bounded encoding of one fixed command or match/error response.
     *
     * @param state codec state
     * @return encoded frame size
     */
    @Benchmark
    public int responseEncode(final CodecState state) {
        state.responseChannel.writeOutbound(state.response);
        final ByteBuf encoded = state.responseChannel.readOutbound();
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
     * Measures one sequential loopback request through the gateway to complete result bytes.
     *
     * @param state persistent one-session loopback state
     * @return number of response frames consumed
     * @throws IOException when the bounded loopback exchange fails
     */
    @Benchmark
    public int loopbackSequentialRoundTrip(final LoopbackState state) throws IOException {
        final long requestId = state.nextRequestId;
        final long orderId = 100_000L + ((requestId + 1L) / 2L);
        final byte[] request = (requestId & 1L) == 1L
                ? submitFrame(requestId, orderId, 1, 100, 1)
                : cancelFrame(requestId, orderId);
        state.output.write(request);
        state.output.flush();
        final byte[] commandResult = readFrame(state.input);
        if (longAt(commandResult, 16) != requestId) {
            throw new IllegalStateException("Loopback response correlation mismatch");
        }
        final int matchCount = intAt(commandResult, 36);
        for (int index = 0; index < matchCount; index++) {
            readFrame(state.input);
        }
        state.nextRequestId++;
        return 1 + matchCount;
    }

    /**
     * State for isolated request decode and response encode measurements.
     */
    @State(Scope.Thread)
    public static class CodecState {

        @Param({"SUBMIT", "CANCEL"})
        private String requestType;

        @Param({"COMMAND", "MATCH", "ERROR"})
        private String responseType;

        private EmbeddedChannel channel;
        private EmbeddedChannel responseChannel;
        private byte[] requestBytes;
        private ProtocolResponse response;

        /**
         * Builds fixed deterministic vectors outside measured operations.
         */
        @Setup(Level.Iteration)
        public void setUp() {
            channel = new EmbeddedChannel();
            channel.pipeline().addLast(
                    new com.ultralatency.matching.network.netty.codec.ProtocolFrameDecoder());
            channel.pipeline().addLast(
                    new com.ultralatency.matching.network.netty.codec.ProtocolRequestDecoder());
            requestBytes = "SUBMIT".equals(requestType)
                    ? submitFrame(1, 42, 1, 100, 3)
                    : cancelFrame(1, 42);
            responseChannel = new EmbeddedChannel(new ProtocolResponseEncoder());
            response = fixedResponse(responseType);
        }

        /**
         * Releases embedded channels after measurement.
         */
        @TearDown(Level.Iteration)
        public void tearDown() {
            channel.finishAndReleaseAll();
            responseChannel.finishAndReleaseAll();
        }
    }

    /**
     * State for one persistent local loopback session and one request in flight.
     */
    @State(Scope.Thread)
    public static class LoopbackState {

        private MatchingEngineTcpServer server;
        private Socket socket;
        private InputStream input;
        private OutputStream output;
        private long nextRequestId;

        /**
         * Starts one fork-owned loopback gateway outside measured operations.
         *
         * @throws IOException when the local socket cannot be opened
         */
        @Setup(Level.Iteration)
        public void setUp() throws IOException {
            server = new MatchingEngineTcpServer(
                    NetworkConfiguration.of(InetAddress.getLoopbackAddress(), 0));
            server.start();
            final var address = server.localAddress().orElseThrow();
            socket = new Socket(address.getAddress(), address.getPort());
            socket.setSoTimeout(5_000);
            input = socket.getInputStream();
            output = socket.getOutputStream();
            nextRequestId = 1;
        }

        /**
         * Closes the client and gateway outside measurement.
         *
         * @throws IOException when the client close fails
         */
        @TearDown(Level.Iteration)
        public void tearDown() throws IOException {
            if (socket != null) {
                socket.close();
            }
            if (server != null) {
                server.shutdown();
            }
        }
    }

    private static ProtocolResponse fixedResponse(final String responseType) {
        return switch (responseType) {
            case "COMMAND" -> new CommandResultResponse(
                    ClientRequestId.of(1),
                    Sequence.of(1),
                    ProtocolCommandOutcome.ACCEPTED,
                    0);
            case "MATCH" -> new MatchResultResponse(
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
            case "ERROR" -> new ErrorResponse(1, ProtocolErrorCode.BACKPRESSURE_FULL);
            default -> throw new IllegalArgumentException("Unknown response type: " + responseType);
        };
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
            throw new IOException("Loopback connection closed before response header");
        }
        final int length = intAt(header, 8);
        final byte[] frame = Arrays.copyOf(header, length);
        final byte[] payload = input.readNBytes(length - ProtocolConstants.HEADER_LENGTH);
        if (payload.length != length - ProtocolConstants.HEADER_LENGTH) {
            throw new IOException("Loopback connection closed before response payload");
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
}
