package com.ultralatency.matching.network.netty.gateway;

import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.engine.CancelOrderCommand;
import com.ultralatency.matching.engine.CommandOutcome;
import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.engine.EngineResult;
import com.ultralatency.matching.engine.MatchResult;
import com.ultralatency.matching.engine.SubmitLimitCommand;
import com.ultralatency.matching.network.netty.codec.ProtocolCodecException;
import com.ultralatency.matching.network.netty.codec.ProtocolFrameDecoder;
import com.ultralatency.matching.network.netty.codec.ProtocolRequestDecoder;
import com.ultralatency.matching.network.netty.codec.ProtocolResponseEncoder;
import com.ultralatency.matching.network.protocol.CancelOrderRequest;
import com.ultralatency.matching.network.protocol.ClientRequestId;
import com.ultralatency.matching.network.protocol.CommandResultResponse;
import com.ultralatency.matching.network.protocol.ErrorResponse;
import com.ultralatency.matching.network.protocol.MatchResultResponse;
import com.ultralatency.matching.network.protocol.ProtocolCommandOutcome;
import com.ultralatency.matching.network.protocol.ProtocolErrorCode;
import com.ultralatency.matching.network.protocol.ProtocolRequest;
import com.ultralatency.matching.network.protocol.ProtocolResponse;
import com.ultralatency.matching.network.protocol.SubmitLimitRequest;
import com.ultralatency.matching.pipeline.MatchingEnginePipeline;
import com.ultralatency.matching.pipeline.PipelinePublishOutcome;
import com.ultralatency.matching.pipeline.PipelineState;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.util.concurrent.Future;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Single-session TCP adapter for the frozen matching pipeline.
 *
 * <p>The worker event-loop owns request admission and is the sole pipeline producer. The server
 * deliberately provides no WAL, reconnect, pipelining, deduplication or durability semantics.</p>
 */
public final class MatchingEngineTcpServer {

    private final Object lifecycleMonitor = new Object();
    private final NetworkConfiguration configuration;
    private final MatchingEnginePipeline pipeline;
    private volatile NetworkGatewayState state = NetworkGatewayState.NEW;
    private volatile Throwable failureCause;
    private volatile Channel serverChannel;
    private volatile Channel activeChannel;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private boolean sessionClaimed;
    private InetSocketAddress localAddress;
    private long expectedRequestId = 1;
    private long nextCommandSequence = 1;
    private InFlight inFlight;

    /**
     * Creates a loopback-default gateway.
     */
    public MatchingEngineTcpServer() {
        this(NetworkConfiguration.defaults());
    }

    /**
     * Creates a gateway with explicit network and pipeline bounds.
     *
     * @param configuration validated gateway configuration
     */
    public MatchingEngineTcpServer(final NetworkConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        pipeline = new MatchingEnginePipeline(
                configuration.pipelineConfiguration(),
                this::onEngineResult,
                this::onPipelineFailure);
    }

    /**
     * Starts the pipeline and binds the TCP listener.
     *
     * @throws IllegalStateException when the gateway is not new or binding fails
     */
    public void start() {
        synchronized (lifecycleMonitor) {
            requireState(NetworkGatewayState.NEW, "start");
            try {
                bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
                workerGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
                pipeline.start();
                state = NetworkGatewayState.RUNNING;
                final ServerBootstrap bootstrap = new ServerBootstrap()
                        .group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                        .option(ChannelOption.SO_BACKLOG, 64)
                        .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                        .childOption(ChannelOption.AUTO_READ, false)
                        .childOption(
                                ChannelOption.WRITE_BUFFER_WATER_MARK,
                                new WriteBufferWaterMark(
                                        configuration.writeBufferLowWaterMark(),
                                        configuration.writeBufferHighWaterMark()))
                        .childHandler(new GatewayChannelInitializer(this));
                serverChannel = bootstrap
                        .bind(new InetSocketAddress(
                                configuration.bindAddress(), configuration.port()))
                        .syncUninterruptibly()
                        .channel();
                localAddress = (InetSocketAddress) serverChannel.localAddress();
            } catch (final Throwable failure) {
                failTerminal(failure);
                throw rethrow(failure, "Gateway failed to start");
            }
        }
    }

    /**
     * Requests bounded shutdown of all gateway, pipeline and event-loop resources.
     *
     * @param timeout maximum total shutdown duration
     * @return final lifecycle state
     */
    public NetworkGatewayState shutdown(final Duration timeout) {
        final long timeoutNanos = timeoutNanos(timeout);
        final long deadline = deadline(timeoutNanos);
        final Channel currentServer;
        final Channel currentSession;
        final EventLoopGroup currentBoss;
        final EventLoopGroup currentWorker;
        synchronized (lifecycleMonitor) {
            if (state == NetworkGatewayState.NEW) {
                state = NetworkGatewayState.STOPPED;
                return state;
            }
            if (state == NetworkGatewayState.STOPPED) {
                return state;
            }
            if (state == NetworkGatewayState.RUNNING) {
                state = NetworkGatewayState.DRAINING;
            }
            currentServer = serverChannel;
            currentSession = activeChannel;
            currentBoss = bossGroup;
            currentWorker = workerGroup;
        }
        close(currentSession);
        close(currentServer);
        shutdownPipeline(remaining(deadline));
        shutdownGroup(currentBoss, remaining(deadline));
        shutdownGroup(currentWorker, remaining(deadline));
        synchronized (lifecycleMonitor) {
            if (state == NetworkGatewayState.DRAINING) {
                state = NetworkGatewayState.STOPPED;
            }
            return state;
        }
    }

    /**
     * Shuts down using the configured default bound.
     *
     * @return final lifecycle state
     */
    public NetworkGatewayState shutdown() {
        return shutdown(configuration.shutdownTimeout());
    }

    /**
     * Returns the current gateway lifecycle state.
     *
     * @return lifecycle state
     */
    public NetworkGatewayState state() {
        return state;
    }

    /**
     * Returns the first terminal failure, when one exists.
     *
     * @return optional failure cause
     */
    public Optional<Throwable> failureCause() {
        return Optional.ofNullable(failureCause);
    }

    /**
     * Returns the bound local address after startup.
     *
     * @return optional local address
     */
    public Optional<InetSocketAddress> localAddress() {
        return Optional.ofNullable(localAddress);
    }

    /**
     * Returns the existing pipeline owned by this gateway.
     *
     * @return pipeline facade
     */
    public MatchingEnginePipeline pipeline() {
        return pipeline;
    }

    void onSessionActive(final ChannelHandlerContext context) {
        final Channel channel = context.channel();
        synchronized (lifecycleMonitor) {
            if (state != NetworkGatewayState.RUNNING || sessionClaimed) {
                writeAndClose(channel, new ErrorResponse(0, ProtocolErrorCode.SERVER_BUSY));
                return;
            }
            sessionClaimed = true;
            activeChannel = channel;
        }
        context.read();
    }

    void onSessionInactive(final Channel channel) {
        if (channel == activeChannel && state == NetworkGatewayState.RUNNING) {
            failTerminal(new IllegalStateException("Active client session disconnected"));
        }
    }

    void onRequest(final ChannelHandlerContext context, final ProtocolRequest request) {
        if (state != NetworkGatewayState.RUNNING || context.channel() != activeChannel) {
            return;
        }
        if (inFlight != null) {
            writeAndClose(
                    context.channel(),
                    new ErrorResponse(request.requestId().value(), ProtocolErrorCode.INVALID_FIELD));
            return;
        }
        if (request.requestId().value() != expectedRequestId) {
            writeAndClose(
                    context.channel(),
                    new ErrorResponse(
                            request.requestId().value(), ProtocolErrorCode.UNEXPECTED_REQUEST_ID));
            return;
        }
        final Sequence candidateSequence;
        final EngineCommand command;
        try {
            candidateSequence = Sequence.of(nextCommandSequence);
            command = toCommand(request, candidateSequence);
        } catch (final RuntimeException failure) {
            failTerminal(failure);
            return;
        }
        inFlight = new InFlight(request.requestId(), candidateSequence);
        final PipelinePublishOutcome outcome;
        try {
            outcome = pipeline.tryPublish(command);
        } catch (final RuntimeException failure) {
            failTerminal(failure);
            return;
        }
        if (outcome == PipelinePublishOutcome.FULL) {
            inFlight = null;
            writeRetryableFull(context.channel(), request.requestId().value());
            return;
        }
        try {
            expectedRequestId = Math.addExact(expectedRequestId, 1);
            nextCommandSequence = Math.addExact(nextCommandSequence, 1);
        } catch (final ArithmeticException failure) {
            failTerminal(failure);
        }
    }

    void onProtocolFailure(final Channel channel, final Throwable cause) {
        final Throwable unwrapped = unwrap(cause);
        if (unwrapped instanceof ProtocolCodecException protocolFailure) {
            writeAndClose(channel, new ErrorResponse(0, protocolFailure.errorCode()));
        } else {
            failTerminal(unwrapped);
        }
    }

    private void onEngineResult(final EngineResult result) {
        final Channel channel = activeChannel;
        if (channel == null || !channel.isOpen()) {
            failTerminal(new IllegalStateException("Engine result has no active session"));
            return;
        }
        try {
            channel.eventLoop().execute(() -> handleEngineResult(channel, result));
        } catch (final Throwable failure) {
            failTerminal(failure);
        }
    }

    private void handleEngineResult(final Channel channel, final EngineResult result) {
        final InFlight current = inFlight;
        if (state != NetworkGatewayState.RUNNING
                || current == null
                || !current.commandSequence().equals(result.commandSequence())) {
            failTerminal(new IllegalStateException("Engine result correlation mismatch"));
            return;
        }
        final List<ProtocolResponse> responses = new ArrayList<>(1 + result.matches().size());
        responses.add(new CommandResultResponse(
                current.requestId(),
                result.commandSequence(),
                toProtocolOutcome(result.outcome()),
                result.matches().size()));
        for (int index = 0; index < result.matches().size(); index++) {
            responses.add(toMatchResponse(current.requestId(), result, index));
        }
        writeResultFrames(channel, current, responses);
    }

    private void writeResultFrames(
            final Channel channel,
            final InFlight current,
            final List<ProtocolResponse> responses) {
        try {
            for (int index = 0; index < responses.size() - 1; index++) {
                channel.write(responses.get(index));
            }
            final ChannelFuture completion = channel.writeAndFlush(responses.get(responses.size() - 1));
            completion.addListener(future -> {
                if (!future.isSuccess()) {
                    failTerminal(future.cause());
                } else if (inFlight == current && state == NetworkGatewayState.RUNNING) {
                    inFlight = null;
                    channel.read();
                }
            });
        } catch (final Throwable failure) {
            failTerminal(failure);
        }
    }

    private void writeRetryableFull(final Channel channel, final long requestId) {
        try {
            final ChannelFuture completion = channel.writeAndFlush(
                    new ErrorResponse(requestId, ProtocolErrorCode.BACKPRESSURE_FULL));
            completion.addListener(future -> {
                if (!future.isSuccess()) {
                    failTerminal(future.cause());
                } else if (state == NetworkGatewayState.RUNNING) {
                    channel.read();
                }
            });
        } catch (final Throwable failure) {
            failTerminal(failure);
        }
    }

    private void onPipelineFailure(final Throwable failure) {
        final Channel channel = activeChannel;
        if (channel == null) {
            failTerminal(failure);
            return;
        }
        try {
            channel.eventLoop().execute(() -> failTerminal(failure));
        } catch (final Throwable schedulingFailure) {
            failTerminal(schedulingFailure);
        }
    }

    private void failTerminal(final Throwable failure) {
        final Throwable nonNullFailure = Objects.requireNonNull(failure, "failure");
        final Channel currentSession;
        final Channel currentServer;
        synchronized (lifecycleMonitor) {
            if (state == NetworkGatewayState.FAILED || state == NetworkGatewayState.STOPPED) {
                return;
            }
            failureCause = nonNullFailure;
            state = NetworkGatewayState.FAILED;
            currentSession = activeChannel;
            currentServer = serverChannel;
        }
        close(currentSession);
        close(currentServer);
    }

    private static EngineCommand toCommand(
            final ProtocolRequest request,
            final Sequence sequence) {
        if (request instanceof SubmitLimitRequest submit) {
            return new SubmitLimitCommand(
                    sequence,
                    submit.orderId(),
                    submit.side(),
                    submit.price(),
                    submit.quantity());
        }
        if (request instanceof CancelOrderRequest cancel) {
            return new CancelOrderCommand(sequence, cancel.orderId());
        }
        throw new IllegalArgumentException("Unsupported protocol request: " + request.getClass());
    }

    private static ProtocolCommandOutcome toProtocolOutcome(final CommandOutcome outcome) {
        return switch (outcome) {
            case ACCEPTED -> ProtocolCommandOutcome.ACCEPTED;
            case CANCELED -> ProtocolCommandOutcome.CANCELED;
            case NOT_FOUND -> ProtocolCommandOutcome.NOT_FOUND;
        };
    }

    private static MatchResultResponse toMatchResponse(
            final ClientRequestId requestId,
            final EngineResult result,
            final int index) {
        final MatchResult match = result.matches().get(index);
        return new MatchResultResponse(
                requestId,
                result.commandSequence(),
                index,
                result.matches().size(),
                match.eventSequence(),
                match.trade().tradeId(),
                match.trade().price(),
                match.trade().quantity(),
                match.trade().makerOrderId(),
                match.makerExecution().remainingQuantityUnits(),
                match.trade().takerOrderId(),
                match.takerExecution().remainingQuantityUnits());
    }

    private void writeAndClose(final Channel channel, final ProtocolResponse response) {
        try {
            final ChannelFuture completion = channel.writeAndFlush(response);
            completion.addListener(future -> {
                if (!future.isSuccess()) {
                    failTerminal(future.cause());
                } else {
                    channel.close();
                }
            });
        } catch (final Throwable failure) {
            failTerminal(failure);
        }
    }

    private static Throwable unwrap(final Throwable failure) {
        Throwable current = failure;
        while ((current instanceof io.netty.handler.codec.DecoderException
                || current instanceof io.netty.handler.codec.EncoderException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private void requireState(final NetworkGatewayState expected, final String operation) {
        if (state != expected) {
            throw new IllegalStateException(
                    "Cannot " + operation + " while gateway is " + state);
        }
    }

    private void shutdownPipeline(final long timeoutNanos) {
        final PipelineState pipelineState = pipeline.state();
        if (pipelineState == PipelineState.RUNNING || pipelineState == PipelineState.DRAINING) {
            pipeline.shutdown(Duration.ofNanos(Math.max(1, timeoutNanos)));
        }
    }

    private static void shutdownGroup(final EventLoopGroup group, final long timeoutNanos) {
        if (group == null) {
            return;
        }
        final Future<?> termination = group.shutdownGracefully(
                0,
                Math.max(1, timeoutNanos),
                TimeUnit.NANOSECONDS);
        termination.awaitUninterruptibly(Math.max(1, timeoutNanos), TimeUnit.NANOSECONDS);
    }

    private static void close(final Channel channel) {
        if (channel != null) {
            channel.close();
        }
    }

    private static long timeoutNanos(final Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Shutdown timeout must be positive");
        }
        try {
            return timeout.toNanos();
        } catch (final ArithmeticException exception) {
            throw new IllegalArgumentException("Shutdown timeout is too large", exception);
        }
    }

    private static long deadline(final long timeoutNanos) {
        try {
            return Math.addExact(System.nanoTime(), timeoutNanos);
        } catch (final ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static long remaining(final long deadline) {
        return Math.max(1, deadline - System.nanoTime());
    }

    private static RuntimeException rethrow(final Throwable failure, final String message) {
        if (failure instanceof RuntimeException exception) {
            return exception;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        return new IllegalStateException(message, failure);
    }

    private record InFlight(ClientRequestId requestId, Sequence commandSequence) {
    }

    private static final class GatewayChannelInitializer extends ChannelInitializer<SocketChannel> {

        private final MatchingEngineTcpServer server;

        private GatewayChannelInitializer(final MatchingEngineTcpServer server) {
            this.server = server;
        }

        @Override
        protected void initChannel(final SocketChannel channel) {
            channel.pipeline()
                    .addLast("frameDecoder", new ProtocolFrameDecoder())
                    .addLast("requestDecoder", new ProtocolRequestDecoder())
                    .addLast("responseEncoder", new ProtocolResponseEncoder())
                    .addLast("session", new GatewaySessionHandler(server));
        }
    }

    private static final class GatewaySessionHandler
            extends io.netty.channel.SimpleChannelInboundHandler<ProtocolRequest> {

        private final MatchingEngineTcpServer server;

        private GatewaySessionHandler(final MatchingEngineTcpServer server) {
            this.server = server;
        }

        @Override
        public void channelActive(final ChannelHandlerContext context) {
            server.onSessionActive(context);
        }

        @Override
        protected void channelRead0(
                final ChannelHandlerContext context,
                final ProtocolRequest request) {
            server.onRequest(context, request);
        }

        @Override
        public void channelInactive(final ChannelHandlerContext context) {
            server.onSessionInactive(context.channel());
        }

        @Override
        public void exceptionCaught(
                final ChannelHandlerContext context,
                final Throwable cause) {
            server.onProtocolFailure(context.channel(), cause);
        }
    }
}
