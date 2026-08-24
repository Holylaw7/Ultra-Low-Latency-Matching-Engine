package com.ultralatency.matching.network.netty.recovery;

import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.engine.CancelOrderCommand;
import com.ultralatency.matching.engine.CommandOutcome;
import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.engine.EngineResult;
import com.ultralatency.matching.engine.MatchResult;
import com.ultralatency.matching.engine.SubmitLimitCommand;
import com.ultralatency.matching.integration.durable.DurableCommandCoordinator;
import com.ultralatency.matching.integration.durable.DurableCommandIdentity;
import com.ultralatency.matching.integration.durable.DurableFailureStage;
import com.ultralatency.matching.integration.durable.DurableTerminalFailure;
import com.ultralatency.matching.integration.recovery.RecoverableDurableRuntime;
import com.ultralatency.matching.integration.recovery.RecoveryRuntimeConfiguration;
import com.ultralatency.matching.integration.recovery.RecoveryRuntimeState;
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
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Listener-last Netty adapter for a recovered durable runtime.
 *
 * <p>Recovery, sequence convergence, Pipeline and coordinator startup complete before the TCP
 * listener is bound. Protocol v1 and the single-session/one-in-flight topology are reused without
 * adding reconnect, deduplication or a second producer.</p>
 */
public final class RecoverableDurableMatchingEngineTcpServer {

    private final Object lifecycleMonitor = new Object();
    private final RecoverableNetworkConfiguration configuration;
    private final BooleanSupplier admissionPredicate;
    private final Consumer<Throwable> failureObserver;
    private volatile RecoveryRuntimeState state = RecoveryRuntimeState.NEW;
    private volatile Throwable failureCause;
    private volatile Channel serverChannel;
    private volatile Channel activeChannel;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private RecoverableDurableRuntime runtime;
    private boolean sessionClaimed;
    private boolean admissionOpen;
    private long expectedRequestId = 1;
    private InFlight inFlight;

    /** Creates a recovered server with the supplied transport/recovery settings. */
    public RecoverableDurableMatchingEngineTcpServer(
            final RecoverableNetworkConfiguration configuration) {
        this(configuration, () -> true, failure -> { });
    }

    /**
     * Creates a server with an externally owned admission predicate and first-failure observer.
     *
     * <p>The legacy constructor remains always-admission-open compatible. The predicate is
     * sampled only at session activation/request admission and never becomes an engine producer.
     * The observer is notified once, after the server retains its first terminal cause.</p>
     *
     * @param configuration transport/recovery settings
     * @param admissionPredicate shared readiness/admission predicate
     * @param failureObserver first terminal failure observer
     */
    public RecoverableDurableMatchingEngineTcpServer(
            final RecoverableNetworkConfiguration configuration,
            final BooleanSupplier admissionPredicate,
            final Consumer<Throwable> failureObserver) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.admissionPredicate = Objects.requireNonNull(admissionPredicate, "admissionPredicate");
        this.failureObserver = Objects.requireNonNull(failureObserver, "failureObserver");
    }

    /** Starts recovery and live resources before binding the listener last. */
    public void start() {
        synchronized (lifecycleMonitor) {
            requireState(RecoveryRuntimeState.NEW, "start");
            state = RecoveryRuntimeState.RECOVERING;
            admissionOpen = false;
        }
        try {
            runtime = new RecoverableDurableRuntime(
                    new RecoveryRuntimeConfiguration(
                            configuration.recoveryMode(),
                            configuration.snapshotDirectory(),
                            configuration.durableConfiguration()),
                    this::onEngineResult,
                    this::onRuntimeFailure);
            runtime.start();
            synchronized (lifecycleMonitor) {
                state = RecoveryRuntimeState.RECOVERED;
            }
            bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
            workerGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
            synchronized (lifecycleMonitor) {
                state = RecoveryRuntimeState.STARTING;
            }
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
                    .childHandler(new RecoveryChannelInitializer(this));
            serverChannel = bootstrap
                    .bind(new InetSocketAddress(
                            configuration.bindAddress(), configuration.port()))
                    .syncUninterruptibly()
                    .channel();
            synchronized (lifecycleMonitor) {
                state = RecoveryRuntimeState.RUNNING;
                admissionOpen = true;
            }
        } catch (final Throwable failure) {
            failTerminal(failure);
            closeResources(Duration.ofSeconds(2));
            throw rethrow(failure, "Recoverable durable server failed to start");
        }
    }

    /** Shuts down listener, runtime and event-loop resources. */
    public RecoveryRuntimeState shutdown(final Duration timeout) {
        final long timeoutNanos = timeoutNanos(timeout);
        final long deadline = deadline(timeoutNanos);
        synchronized (lifecycleMonitor) {
            if (state == RecoveryRuntimeState.NEW || state == RecoveryRuntimeState.STOPPED) {
                admissionOpen = false;
                state = RecoveryRuntimeState.STOPPED;
                return state;
            }
            admissionOpen = false;
            if (state != RecoveryRuntimeState.FAILED) {
                state = RecoveryRuntimeState.STOPPED;
            }
            lifecycleMonitor.notifyAll();
        }
        close(activeChannel);
        close(serverChannel);
        closeRuntime(remaining(deadline));
        shutdownGroup(bossGroup, remaining(deadline));
        shutdownGroup(workerGroup, remaining(deadline));
        return state;
    }

    /** Shuts down using the configured durable timeout. */
    public RecoveryRuntimeState shutdown() {
        return shutdown(configuration.durableConfiguration().shutdownTimeout());
    }

    /**
     * Closes the listener and prevents new sessions while allowing the active request to finish.
     *
     * <p>This operation is idempotent and does not close transitive runtime resources.</p>
     */
    public void stopAdmission() {
        final Channel listener;
        synchronized (lifecycleMonitor) {
            admissionOpen = false;
            listener = serverChannel;
            lifecycleMonitor.notifyAll();
        }
        close(listener);
    }

    /**
     * Waits for the current in-flight request to reach a terminal boundary.
     *
     * @param timeout cooperative wait bound
     * @return {@code true} when no request remains in flight, otherwise {@code false}
     */
    public boolean awaitInFlight(final Duration timeout) {
        final long timeoutNanos = timeoutNanos(timeout);
        final long deadline = deadline(timeoutNanos);
        synchronized (lifecycleMonitor) {
            while (inFlight != null && state != RecoveryRuntimeState.STOPPED) {
                final long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return false;
                }
                try {
                    final long millis = remaining / 1_000_000;
                    final int nanos = (int) (remaining % 1_000_000);
                    lifecycleMonitor.wait(Math.max(0, millis), nanos);
                } catch (final InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return inFlight == null;
        }
    }

    /** @return current recovery/live lifecycle state */
    public RecoveryRuntimeState state() {
        return state;
    }

    /** @return first terminal startup/runtime failure, if any */
    public Optional<Throwable> failureCause() {
        return Optional.ofNullable(failureCause);
    }

    /** @return listener address after successful bind */
    public Optional<InetSocketAddress> localAddress() {
        final Channel channel = serverChannel;
        return channel == null
                ? Optional.empty()
                : Optional.of((InetSocketAddress) channel.localAddress());
    }

    /** @return recovered runtime composition after startup */
    public RecoverableDurableRuntime runtime() {
        return requireRuntime(runtime, "runtime");
    }

    /** @return configuration for this recovered server */
    public RecoverableNetworkConfiguration configuration() {
        return configuration;
    }

    void onSessionActive(final ChannelHandlerContext context) {
        final Channel channel = context.channel();
        synchronized (lifecycleMonitor) {
            if (state != RecoveryRuntimeState.RUNNING || !admissionAllowed() || sessionClaimed) {
                writeAndClose(channel, new ErrorResponse(0, ProtocolErrorCode.SERVER_BUSY));
                return;
            }
            sessionClaimed = true;
            activeChannel = channel;
        }
        context.read();
    }

    void onSessionInactive(final Channel channel) {
        if (channel == activeChannel && state == RecoveryRuntimeState.RUNNING) {
            failRuntime(DurableFailureStage.DISCONNECT,
                    new IllegalStateException("Active recovered session disconnected"));
        }
    }

    void onRequest(final ChannelHandlerContext context, final ProtocolRequest request) {
        if (state != RecoveryRuntimeState.RUNNING
                || context.channel() != activeChannel) {
            return;
        }
        if (!admissionAllowed()) {
            writeAndClose(
                    context.channel(),
                    new ErrorResponse(request.requestId().value(), ProtocolErrorCode.SERVER_BUSY));
            return;
        }
        synchronized (lifecycleMonitor) {
            if (state != RecoveryRuntimeState.RUNNING
                    || context.channel() != activeChannel
                    || !admissionAllowed()) {
                writeAndClose(
                        context.channel(),
                        new ErrorResponse(request.requestId().value(), ProtocolErrorCode.SERVER_BUSY));
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
            final DurableCommandCoordinator coordinator = runtime().coordinator();
            final DurableCommandIdentity identity = new DurableCommandIdentity(
                    request.requestId(), coordinator.nextCommandSequence());
            inFlight = new InFlight(identity);
        }
        try {
            final DurableCommandCoordinator coordinator = runtime().coordinator();
            final DurableCommandIdentity identity = inFlightIdentity();
            final com.ultralatency.matching.integration.durable.LiveAcceptedOutcome outcome =
                    coordinator.accept(
                            request.requestId(),
                            sequence -> toCommand(request, sequence.toSequence()));
            if (!identity.equals(outcome.identity())) {
                throw new IllegalStateException("Recovered command identity changed during admission");
            }
            synchronized (lifecycleMonitor) {
                expectedRequestId = Math.addExact(expectedRequestId, 1);
            }
        } catch (final Throwable failure) {
            clearInFlight();
            failRuntime(DurableFailureStage.ENGINE, failure);
        }
    }

    private DurableCommandIdentity inFlightIdentity() {
        synchronized (lifecycleMonitor) {
            if (inFlight == null) {
                throw new IllegalStateException("No in-flight request is available");
            }
            return inFlight.identity();
        }
    }

    private void clearInFlight() {
        synchronized (lifecycleMonitor) {
            inFlight = null;
            lifecycleMonitor.notifyAll();
        }
    }

    void onProtocolFailure(final Channel channel, final Throwable cause) {
        final Throwable unwrapped = unwrap(cause);
        if (unwrapped instanceof ProtocolCodecException protocolFailure) {
            writeAndClose(channel, new ErrorResponse(0, protocolFailure.errorCode()));
        } else {
            failRuntime(DurableFailureStage.RESPONSE_ENCODING, unwrapped);
        }
    }

    private void onEngineResult(final EngineResult result) {
        final Channel channel;
        final InFlight current;
        synchronized (lifecycleMonitor) {
            channel = activeChannel;
            current = inFlight;
        }
        if (channel == null || !channel.isOpen() || current == null
                || !current.identity().domainCommandSequence().equals(result.commandSequence())) {
            failRuntime(DurableFailureStage.ENGINE,
                    new IllegalStateException("Recovered result correlation mismatch"));
            return;
        }
        try {
            channel.eventLoop().execute(() -> handleEngineResult(channel, current, result));
        } catch (final Throwable failure) {
            failRuntime(DurableFailureStage.ENGINE, failure);
        }
    }

    private void handleEngineResult(
            final Channel channel,
            final InFlight current,
            final EngineResult result) {
        synchronized (lifecycleMonitor) {
            if (state != RecoveryRuntimeState.RUNNING || inFlight != current) {
                failRuntime(DurableFailureStage.ENGINE,
                        new IllegalStateException("Recovered result arrived without in-flight request"));
                return;
            }
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
                channel.write(responses.get(index));
            }
            final ChannelFuture completion = channel.writeAndFlush(
                    responses.get(responses.size() - 1));
            completion.addListener(future -> {
                if (!future.isSuccess()) {
                    failRuntime(DurableFailureStage.OUTBOUND_WRITE, future.cause());
                } else {
                    synchronized (lifecycleMonitor) {
                        if (inFlight != current || state != RecoveryRuntimeState.RUNNING) {
                            return;
                        }
                        inFlight = null;
                        lifecycleMonitor.notifyAll();
                    }
                    channel.read();
                }
            });
        } catch (final Throwable failure) {
            failRuntime(DurableFailureStage.OUTBOUND_WRITE, failure);
        }
    }

    private void onRuntimeFailure(final DurableTerminalFailure failure) {
        failTerminal(failure.cause());
    }

    private void failRuntime(final DurableFailureStage stage, final Throwable failure) {
        if (runtime != null) {
            runtime().fail(stage, failure);
        }
        failTerminal(failure);
    }

    private void failTerminal(final Throwable failure) {
        final Throwable nonNullFailure = Objects.requireNonNull(failure, "failure");
        final Channel currentSession;
        final Channel currentServer;
        final Consumer<Throwable> observer;
        synchronized (lifecycleMonitor) {
            if (failureCause != null || state == RecoveryRuntimeState.STOPPED) {
                return;
            }
            failureCause = nonNullFailure;
            state = RecoveryRuntimeState.FAILED;
            admissionOpen = false;
            inFlight = null;
            lifecycleMonitor.notifyAll();
            currentSession = activeChannel;
            currentServer = serverChannel;
            observer = failureObserver;
        }
        close(currentSession);
        close(currentServer);
        try {
            observer.accept(nonNullFailure);
        } catch (final Throwable ignored) {
            // An observer cannot replace the retained first terminal failure.
        }
    }

    private void closeResources(final Duration timeout) {
        closeRuntime(timeout.toNanos());
        shutdownGroup(bossGroup, timeout.toNanos());
        shutdownGroup(workerGroup, timeout.toNanos());
    }

    private void closeRuntime(final long timeoutNanos) {
        if (runtime != null) {
            runtime.shutdown();
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
            final ChannelFuture completion = channel.writeAndFlush(response);
            completion.addListener(future -> {
                if (!future.isSuccess()) {
                    failRuntime(DurableFailureStage.OUTBOUND_WRITE, future.cause());
                } else {
                    channel.close();
                }
            });
        } catch (final Throwable failure) {
            failRuntime(DurableFailureStage.OUTBOUND_WRITE, failure);
        }
    }

    private void requireState(final RecoveryRuntimeState expected, final String operation) {
        if (state != expected) {
            throw new IllegalStateException(
                    "Cannot " + operation + " while recovered server is " + state);
        }
    }

    private boolean admissionAllowed() {
        return admissionOpen && admissionPredicate.getAsBoolean();
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

    private static void close(final Channel channel) {
        if (channel != null) {
            channel.close();
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

    private static final class RecoveryChannelInitializer extends ChannelInitializer<SocketChannel> {

        private final RecoverableDurableMatchingEngineTcpServer server;

        private RecoveryChannelInitializer(
                final RecoverableDurableMatchingEngineTcpServer server) {
            this.server = server;
        }

        @Override
        protected void initChannel(final SocketChannel channel) {
            channel.pipeline()
                    .addLast("frameDecoder", new ProtocolFrameDecoder())
                    .addLast("requestDecoder", new ProtocolRequestDecoder())
                    .addLast("responseEncoder", new ProtocolResponseEncoder())
                    .addLast("session", new RecoverySessionHandler(server));
        }
    }

    private static final class RecoverySessionHandler
            extends io.netty.channel.SimpleChannelInboundHandler<ProtocolRequest> {

        private final RecoverableDurableMatchingEngineTcpServer server;

        private RecoverySessionHandler(
                final RecoverableDurableMatchingEngineTcpServer server) {
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
