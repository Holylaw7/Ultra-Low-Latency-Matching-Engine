package com.ultralatency.matching.network.netty.durable;

import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.engine.CancelOrderCommand;
import com.ultralatency.matching.engine.CommandOutcome;
import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.engine.EngineResult;
import com.ultralatency.matching.engine.MatchResult;
import com.ultralatency.matching.engine.SubmitLimitCommand;
import com.ultralatency.matching.integration.durable.DurableCommandCoordinator;
import com.ultralatency.matching.integration.durable.DurableCommandIdentity;
import com.ultralatency.matching.integration.durable.DurableConfiguration;
import com.ultralatency.matching.integration.durable.DurableFailureStage;
import com.ultralatency.matching.integration.durable.DurableLifecycleState;
import com.ultralatency.matching.integration.durable.DurableTerminalFailure;
import com.ultralatency.matching.integration.durable.LiveAcceptedOutcome;
import com.ultralatency.matching.network.netty.codec.ProtocolCodecException;
import com.ultralatency.matching.network.netty.codec.ProtocolFrameDecoder;
import com.ultralatency.matching.network.netty.codec.ProtocolRequestDecoder;
import com.ultralatency.matching.network.netty.codec.ProtocolResponseEncoder;
import com.ultralatency.matching.network.netty.gateway.NetworkGatewayState;
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
import com.ultralatency.matching.persistence.wal.CommandWalWriter;
import com.ultralatency.matching.pipeline.MatchingEnginePipeline;
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
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Opt-in single-session Netty composition for the Phase 7 durable command path.
 *
 * <p>This class is additive. It reuses the frozen Protocol v1 codec, WAL v1 writer, Pipeline and
 * MatchingEngine through adapters, while keeping the legacy Phase 6 gateway unchanged. Startup
 * accepts only a fresh WAL directory: non-empty WAL state is rejected rather than replayed.</p>
 */
public final class DurableMatchingEngineTcpServer {

    private final Object lifecycleMonitor = new Object();
    private final DurableNetworkConfiguration configuration;
    private final DurableRuntimePortFactory runtimePortFactory;
    private final DurableResponseWritePort responseWritePort;
    private volatile NetworkGatewayState state = NetworkGatewayState.NEW;
    private volatile Throwable failureCause;
    private volatile Channel serverChannel;
    private volatile Channel activeChannel;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private CommandWalWriter walWriter;
    private MatchingEnginePipeline pipeline;
    private DurableCommandCoordinator coordinator;
    private boolean sessionClaimed;
    private InetSocketAddress localAddress;
    private long expectedRequestId = 1;
    private volatile InFlight inFlight;

    /**
     * Creates an opt-in durable server with the supplied composition settings.
     *
     * @param configuration validated transport and durable settings
     */
    public DurableMatchingEngineTcpServer(
            final DurableNetworkConfiguration configuration) {
        this(configuration, DurableRuntimePortFactory.production(),
                DurableResponseWritePort.production());
    }

    /**
     * Creates a durable server with an additive Phase 7 runtime composition boundary.
     *
     * <p>The normal public constructor uses the production identity adapters. This package-local
     * overload lets deterministic integration tests wrap those same real adapters and control
     * completion boundaries without changing frozen protocol, WAL, pipeline or engine code.</p>
     *
     * @param configuration validated transport and durable settings
     * @param runtimePortFactory wrapper for the real append and publish adapters
     * @param responseWritePort result-write boundary
     */
    DurableMatchingEngineTcpServer(
            final DurableNetworkConfiguration configuration,
            final DurableRuntimePortFactory runtimePortFactory,
            final DurableResponseWritePort responseWritePort) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.runtimePortFactory = Objects.requireNonNull(runtimePortFactory, "runtimePortFactory");
        this.responseWritePort = Objects.requireNonNull(responseWritePort, "responseWritePort");
    }

    /**
     * Creates an opt-in durable server from durable settings and Phase 6 transport settings.
     *
     * @param networkConfiguration transport settings
     * @param durableConfiguration fresh-WAL and pipeline settings
     */
    public DurableMatchingEngineTcpServer(
            final com.ultralatency.matching.network.netty.gateway.NetworkConfiguration
                    networkConfiguration,
            final DurableConfiguration durableConfiguration) {
        this(new DurableNetworkConfiguration(
                networkConfiguration.bindAddress(),
                networkConfiguration.port(),
                networkConfiguration.writeBufferLowWaterMark(),
                networkConfiguration.writeBufferHighWaterMark(),
                Objects.requireNonNull(durableConfiguration, "durableConfiguration")));
    }

    /**
     * Starts WAL, pipeline and the single-session TCP listener.
     *
     * @throws IllegalStateException when the WAL is not fresh or startup fails
     */
    public void start() {
        synchronized (lifecycleMonitor) {
            requireState(NetworkGatewayState.NEW, "start");
            try {
                ensureFreshWal(configuration.durableConfiguration().walConfiguration().directory());
                walWriter = CommandWalWriter.open(
                        configuration.durableConfiguration().walConfiguration());
                pipeline = new MatchingEnginePipeline(
                        configuration.durableConfiguration().pipelineConfiguration(),
                        this::onEngineResult,
                        this::onPipelineFailure);
                final DurableRuntimePorts runtimePorts = runtimePortFactory.create(
                        walWriter::append,
                        pipeline::tryPublish);
                coordinator = new DurableCommandCoordinator(
                        runtimePorts.appendPort(),
                        runtimePorts.publishPort(),
                        this::onDurableFailure);
                pipeline.start();
                coordinator.start();
                state = NetworkGatewayState.RUNNING;
                final ServerBootstrap bootstrap = new ServerBootstrap()
                        .group(bossGroup(), workerGroup())
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
                        .childHandler(new DurableChannelInitializer(this));
                serverChannel = bootstrap
                        .bind(configuration.bindAddress(), configuration.port())
                        .syncUninterruptibly()
                        .channel();
                localAddress = (InetSocketAddress) serverChannel.localAddress();
            } catch (final Throwable failure) {
                failTerminal(failure);
                closeResources(Duration.ofSeconds(2));
                throw rethrow(failure, "Durable gateway failed to start");
            }
        }
    }

    /**
     * Stops the durable composition and closes its writer.
     *
     * @param timeout maximum total shutdown duration
     * @return final lifecycle state
     */
    public NetworkGatewayState shutdown(final Duration timeout) {
        final long timeoutNanos = timeoutNanos(timeout);
        final long deadline = deadline(timeoutNanos);
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
        }
        close(activeChannel);
        close(serverChannel);
        if (coordinator != null && coordinator.state() == DurableLifecycleState.RUNNING) {
            coordinator.shutdown();
        }
        if (pipeline != null
                && (pipeline.state() == PipelineState.RUNNING
                || pipeline.state() == PipelineState.DRAINING)) {
            pipeline.shutdown(Duration.ofNanos(remaining(deadline)));
        }
        shutdownGroup(bossGroup, remaining(deadline));
        shutdownGroup(workerGroup, remaining(deadline));
        closeWal();
        synchronized (lifecycleMonitor) {
            if (state == NetworkGatewayState.DRAINING) {
                state = NetworkGatewayState.STOPPED;
            }
            return state;
        }
    }

    /**
     * Stops using the configured bounded drain timeout.
     *
     * @return final lifecycle state
     */
    public NetworkGatewayState shutdown() {
        return shutdown(configuration.durableConfiguration().shutdownTimeout());
    }

    /** @return current durable gateway lifecycle state */
    public NetworkGatewayState state() {
        return state;
    }

    /** @return first terminal failure, if one exists */
    public Optional<Throwable> failureCause() {
        return Optional.ofNullable(failureCause);
    }

    /** @return bound address after successful startup */
    public Optional<InetSocketAddress> localAddress() {
        return Optional.ofNullable(localAddress);
    }

    /** @return validated durable network configuration */
    public DurableNetworkConfiguration configuration() {
        return configuration;
    }

    /**
     * Returns the active coordinator.
     *
     * @return coordinator after startup
     * @throws IllegalStateException when startup has not created it
     */
    public DurableCommandCoordinator coordinator() {
        return requireRuntime(coordinator, "coordinator");
    }

    /**
     * Returns the active pipeline.
     *
     * @return pipeline after startup
     * @throws IllegalStateException when startup has not created it
     */
    public MatchingEnginePipeline pipeline() {
        return requireRuntime(pipeline, "pipeline");
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
            final IllegalStateException failure =
                    new IllegalStateException("Active durable session disconnected");
            failCoordinator(DurableFailureStage.DISCONNECT, failure);
            failTerminal(failure);
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
        try {
            final DurableCommandIdentity candidateIdentity = new DurableCommandIdentity(
                    request.requestId(), coordinator.nextCommandSequence());
            inFlight = new InFlight(candidateIdentity);
            final LiveAcceptedOutcome outcome = coordinator.accept(
                    request.requestId(),
                    sequence -> toCommand(request, sequence.toSequence()));
            if (!candidateIdentity.equals(outcome.identity())) {
                throw new IllegalStateException("Durable command identity changed during admission");
            }
            expectedRequestId = Math.addExact(expectedRequestId, 1);
        } catch (final Throwable failure) {
            inFlight = null;
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
        final InFlight current = inFlight;
        if (channel == null || !channel.isOpen() || current == null
                || !current.identity().domainCommandSequence().equals(result.commandSequence())) {
            failTerminal(new IllegalStateException("Durable result correlation mismatch"));
            return;
        }
        try {
            channel.eventLoop().execute(() -> handleEngineResult(channel, current, result));
        } catch (final Throwable failure) {
            failTerminal(failure);
        }
    }

    private void handleEngineResult(
            final Channel channel,
            final InFlight current,
            final EngineResult result) {
        if (state != NetworkGatewayState.RUNNING || inFlight != current) {
            failTerminal(new IllegalStateException("Durable result arrived without in-flight request"));
            return;
        }
        final List<ProtocolResponse> responses = new ArrayList<>(1 + result.matches().size());
        responses.add(new CommandResultResponse(
                current.identity().requestId(),
                result.commandSequence(),
                toProtocolOutcome(result.outcome()),
                result.matches().size()));
        for (int index = 0; index < result.matches().size(); index++) {
            responses.add(toMatchResponse(current.identity().requestId(), result, index));
        }
        writeResultFrames(channel, current, responses);
    }

    private void writeResultFrames(
            final Channel channel,
            final InFlight current,
            final List<ProtocolResponse> responses) {
        try {
            for (int index = 0; index < responses.size() - 1; index++) {
                responseWritePort.write(channel, responses.get(index));
            }
            final ChannelFuture completion = responseWritePort.writeAndFlush(
                    channel,
                    responses.get(responses.size() - 1));
            completion.addListener(future -> {
                if (!future.isSuccess()) {
                    failCoordinator(DurableFailureStage.OUTBOUND_WRITE, future.cause());
                    failTerminal(future.cause());
                } else if (inFlight == current && state == NetworkGatewayState.RUNNING) {
                    inFlight = null;
                    channel.read();
                }
            });
        } catch (final Throwable failure) {
            failCoordinator(DurableFailureStage.OUTBOUND_WRITE, failure);
            failTerminal(failure);
        }
    }

    private void onPipelineFailure(final Throwable failure) {
        failCoordinator(DurableFailureStage.PIPELINE, failure);
        scheduleTerminalFailure(failure);
    }

    private void onDurableFailure(final DurableTerminalFailure failure) {
        final Throwable cause = failure.cause();
        if (failure.stage() == DurableFailureStage.DURABLE_THEN_FULL) {
            scheduleTerminalFailure(new IllegalStateException(
                    "Durable append succeeded but pipeline admission was FULL", cause));
        } else {
            scheduleTerminalFailure(cause);
        }
    }

    private void scheduleTerminalFailure(final Throwable failure) {
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

    private void closeResources(final Duration timeout) {
        try {
            if (coordinator != null && coordinator.state() == DurableLifecycleState.RUNNING) {
                coordinator.shutdown();
            }
            if (pipeline != null
                    && (pipeline.state() == PipelineState.RUNNING
                    || pipeline.state() == PipelineState.DRAINING)) {
                pipeline.shutdown(timeout);
            }
        } finally {
            shutdownGroup(bossGroup, timeout.toNanos());
            shutdownGroup(workerGroup, timeout.toNanos());
            closeWal();
        }
    }

    private void closeWal() {
        if (walWriter != null) {
            try {
                walWriter.close();
            } catch (final IOException failure) {
                if (failureCause == null) {
                    failureCause = failure;
                }
                state = NetworkGatewayState.FAILED;
            } finally {
                walWriter = null;
            }
        }
    }

    private EventLoopGroup bossGroup() {
        bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        return bossGroup;
    }

    private EventLoopGroup workerGroup() {
        workerGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        return workerGroup;
    }

    private static void ensureFreshWal(final Path directory) throws IOException {
        Files.createDirectories(directory);
        try (var entries = Files.list(directory)) {
            if (entries.findAny().isPresent()) {
                throw new IllegalStateException(
                        "Durable live startup requires an empty WAL directory: " + directory);
            }
        }
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
            final ChannelFuture completion = responseWritePort.writeAndFlush(channel, response);
            completion.addListener(future -> {
                if (!future.isSuccess()) {
                    failCoordinator(DurableFailureStage.OUTBOUND_WRITE, future.cause());
                    failTerminal(future.cause());
                } else {
                    channel.close();
                }
            });
        } catch (final Throwable failure) {
            failCoordinator(DurableFailureStage.OUTBOUND_WRITE, failure);
            failTerminal(failure);
        }
    }

    private void failCoordinator(
            final DurableFailureStage stage,
            final Throwable failure) {
        if (coordinator != null) {
            coordinator.fail(stage, failure);
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
                    "Cannot " + operation + " while durable gateway is " + state);
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

    private static <T> T requireRuntime(final T value, final String name) {
        if (value == null) {
            throw new IllegalStateException(name + " is not started");
        }
        return value;
    }

    private record InFlight(DurableCommandIdentity identity) {
    }

    private static final class DurableChannelInitializer extends ChannelInitializer<SocketChannel> {

        private final DurableMatchingEngineTcpServer server;

        private DurableChannelInitializer(final DurableMatchingEngineTcpServer server) {
            this.server = server;
        }

        @Override
        protected void initChannel(final SocketChannel channel) {
            channel.pipeline()
                    .addLast("frameDecoder", new ProtocolFrameDecoder())
                    .addLast("requestDecoder", new ProtocolRequestDecoder())
                    .addLast("responseEncoder", new ProtocolResponseEncoder())
                    .addLast("session", new DurableSessionHandler(server));
        }
    }

    private static final class DurableSessionHandler
            extends io.netty.channel.SimpleChannelInboundHandler<ProtocolRequest> {

        private final DurableMatchingEngineTcpServer server;

        private DurableSessionHandler(final DurableMatchingEngineTcpServer server) {
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
