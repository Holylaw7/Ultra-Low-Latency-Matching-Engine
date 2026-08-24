package com.ultralatency.matching.operations;

import com.ultralatency.matching.app.RuntimeConfiguration;
import com.ultralatency.matching.app.RuntimeStatusSnapshot;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Bounded loopback operational listener owned by the release-candidate root. */
public final class ManagementServer {

    private static final int BACKLOG = 16;

    private final Object lifecycleMonitor = new Object();
    private final RuntimeConfiguration configuration;
    private final Supplier<RuntimeStatusSnapshot> statusSupplier;
    private final Consumer<Throwable> failureObserver;
    private final AtomicLong managementRequests = new AtomicLong();
    private final AtomicLong managementRejected = new AtomicLong();
    private final AtomicLong activeConnections = new AtomicLong();
    private volatile Channel serverChannel;
    private EventLoopGroup eventLoopGroup;
    private boolean started;
    private boolean stopped;

    /** Creates a management server using immutable application configuration. */
    public ManagementServer(
            final RuntimeConfiguration configuration,
            final Supplier<RuntimeStatusSnapshot> statusSupplier,
            final Consumer<Throwable> failureObserver) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.statusSupplier = Objects.requireNonNull(statusSupplier, "statusSupplier");
        this.failureObserver = Objects.requireNonNull(failureObserver, "failureObserver");
    }

    /** Starts the one-thread management event loop and listener, if enabled. */
    public void start() {
        synchronized (lifecycleMonitor) {
            if (started || stopped) {
                throw new IllegalStateException("Management server has already been started");
            }
            started = true;
        }
        if (!configuration.managementEnabled()) {
            return;
        }
        try {
            eventLoopGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
            final ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(eventLoopGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                    .option(ChannelOption.SO_BACKLOG, BACKLOG)
                    .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                    .childOption(ChannelOption.AUTO_READ, false)
                    .childHandler(new ManagementChannelInitializer(this));
            serverChannel = bootstrap.bind(new InetSocketAddress(
                    configuration.managementBindAddress(), configuration.managementPort()))
                    .syncUninterruptibly()
                    .channel();
        } catch (final Throwable failure) {
            final ManagementBindFailure bindFailure = new ManagementBindFailure(failure);
            notifyFailure(bindFailure);
            shutdown(configuration.shutdownTimeout());
            throw bindFailure;
        }
    }

    /** Stops the listener and its event loop within the supplied cooperative bound. */
    public void shutdown(final Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        final Channel listener;
        final EventLoopGroup group;
        synchronized (lifecycleMonitor) {
            if (stopped) {
                return;
            }
            stopped = true;
            listener = serverChannel;
            group = eventLoopGroup;
            serverChannel = null;
            eventLoopGroup = null;
        }
        close(listener);
        if (group != null) {
            group.shutdownGracefully(0, timeout.toMillis(), TimeUnit.MILLISECONDS)
                    .syncUninterruptibly();
        }
    }

    /** @return true when the configured listener is bound, or when disabled */
    public boolean isBound() {
        return !configuration.managementEnabled()
                || (serverChannel != null && serverChannel.isOpen());
    }

    /** @return the bound local address when enabled and bound */
    public Optional<InetSocketAddress> localAddress() {
        final Channel channel = serverChannel;
        return channel == null
                ? Optional.empty()
                : Optional.of((InetSocketAddress) channel.localAddress());
    }

    /** @return immutable configuration used by this listener */
    public RuntimeConfiguration configuration() {
        return configuration;
    }

    /** @return monotonically observed management request count */
    public long managementRequests() {
        return managementRequests.get();
    }

    /** @return monotonically observed rejected management request count */
    public long managementRejected() {
        return managementRejected.get();
    }

    private boolean tryAcquireConnection(final Channel channel) {
        final long count = activeConnections.incrementAndGet();
        if (count <= configuration.managementMaxConnections()) {
            return true;
        }
        activeConnections.decrementAndGet();
        increment(managementRejected);
        close(channel);
        return false;
    }

    private void releaseConnection() {
        activeConnections.updateAndGet(value -> value == 0 ? 0 : value - 1);
    }

    private byte[] response(final ManagementProtocol.Request request) {
        increment(managementRequests);
        return ManagementProtocol.encode(
                request,
                statusSupplier.get(),
                managementRequests.get(),
                managementRejected.get());
    }

    private void reject() {
        increment(managementRejected);
    }

    private void notifyFailure(final Throwable failure) {
        try {
            failureObserver.accept(failure);
        } catch (final Throwable ignored) {
            // A management observer cannot replace the retained startup cause.
        }
    }

    private static void increment(final AtomicLong counter) {
        counter.updateAndGet(value -> value == Long.MAX_VALUE ? value : value + 1);
    }

    private static void close(final Channel channel) {
        if (channel != null) {
            channel.close().awaitUninterruptibly();
        }
    }

    private static final class ManagementChannelInitializer
            extends ChannelInitializer<SocketChannel> {

        private final ManagementServer server;

        private ManagementChannelInitializer(final ManagementServer server) {
            this.server = server;
        }

        @Override
        protected void initChannel(final SocketChannel channel) {
            channel.pipeline().addLast("management", new ManagementHandler(server));
        }
    }

    private static final class ManagementHandler
            extends io.netty.channel.ChannelInboundHandlerAdapter {

        private final ManagementServer server;
        private final byte[] request = new byte[ManagementProtocol.MAX_REQUEST_BYTES];
        private int length;
        private boolean acquired;
        private boolean complete;
        private ScheduledFuture<?> timeout;

        private ManagementHandler(final ManagementServer server) {
            this.server = server;
        }

        @Override
        public void channelActive(final ChannelHandlerContext context) {
            if (!server.tryAcquireConnection(context.channel())) {
                return;
            }
            acquired = true;
            timeout = context.executor().schedule(
                    () -> {
                        if (!complete) {
                            server.reject();
                            complete = true;
                            context.close();
                        }
                    },
                    server.configuration().managementRequestTimeout().toMillis(),
                    TimeUnit.MILLISECONDS);
            context.read();
        }

        @Override
        public void channelRead(final ChannelHandlerContext context, final Object message) {
            try {
                if (!(message instanceof ByteBuf buffer) || complete) {
                    if (!complete) {
                        rejectAndClose(context);
                    }
                    return;
                }
                while (buffer.isReadable() && !complete) {
                    if (length >= request.length) {
                        rejectAndClose(context);
                        return;
                    }
                    request[length++] = buffer.readByte();
                    if (request[length - 1] == '\n') {
                        if (buffer.isReadable()) {
                            rejectAndClose(context);
                            return;
                        }
                        completeRequest(context);
                    } else if (length == request.length) {
                        rejectAndClose(context);
                        return;
                    }
                }
                if (!complete) {
                    context.read();
                }
            } finally {
                ReferenceCountUtil.release(message);
            }
        }

        @Override
        public void channelInactive(final ChannelHandlerContext context) {
            complete = true;
            cancelTimeout();
            if (acquired) {
                acquired = false;
                server.releaseConnection();
            }
        }

        @Override
        public void exceptionCaught(
                final ChannelHandlerContext context,
                final Throwable cause) {
            rejectAndClose(context);
        }

        private void completeRequest(final ChannelHandlerContext context) {
            cancelTimeout();
            try {
                final ManagementProtocol.Request decoded = ManagementProtocol.decode(
                        Arrays.copyOf(request, length));
                complete = true;
                context.writeAndFlush(UnpooledBytes.wrap(server.response(decoded)))
                        .addListener(future -> context.close());
            } catch (final IllegalArgumentException failure) {
                rejectAndClose(context);
            }
        }

        private void rejectAndClose(final ChannelHandlerContext context) {
            if (!complete) {
                complete = true;
                server.reject();
                cancelTimeout();
                context.writeAndFlush(UnpooledBytes.wrap(ManagementProtocol.invalidResponse()))
                        .addListener(future -> context.close());
            } else {
                context.close();
            }
        }

        private void cancelTimeout() {
            if (timeout != null) {
                timeout.cancel(false);
                timeout = null;
            }
        }
    }

    /** Exception category used to preserve the required management bind mapping. */
    public static final class ManagementBindFailure extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private ManagementBindFailure(final Throwable cause) {
            super("Management listener failed to bind", cause);
        }
    }

    /** Small dependency-free adapter keeping ByteBuf construction local to the handler. */
    private static final class UnpooledBytes {

        private UnpooledBytes() {
        }

        private static ByteBuf wrap(final byte[] bytes) {
            return io.netty.buffer.Unpooled.wrappedBuffer(bytes);
        }
    }
}
