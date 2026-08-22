package com.ultralatency.matching.network.netty.durable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Side;
import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.engine.SubmitLimitCommand;
import com.ultralatency.matching.integration.durable.DurableAppendPort;
import com.ultralatency.matching.integration.durable.DurableCommandSequence;
import com.ultralatency.matching.integration.durable.DurableFailureStage;
import com.ultralatency.matching.integration.durable.DurableLifecycleState;
import com.ultralatency.matching.integration.durable.DurablePublishPort;
import com.ultralatency.matching.integration.durable.DurableTerminalException;
import com.ultralatency.matching.network.netty.codec.ProtocolRequestEncoder;
import com.ultralatency.matching.network.netty.gateway.NetworkGatewayState;
import com.ultralatency.matching.network.protocol.ClientRequestId;
import com.ultralatency.matching.network.protocol.CommandResultResponse;
import com.ultralatency.matching.network.protocol.ProtocolConstants;
import com.ultralatency.matching.network.protocol.ProtocolResponse;
import com.ultralatency.matching.network.protocol.SubmitLimitRequest;
import com.ultralatency.matching.persistence.wal.CommandWalReader;
import com.ultralatency.matching.pipeline.PipelinePublishOutcome;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Phase7RuntimeCompositionBoundaryTest {

    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(3);

    @TempDir
    Path tempDir;

    @Test
    void appendReturnBarrierPrecedesPublicationAndResponse() throws Exception {
        final DurableNetworkConfiguration configuration =
                DurableNetworkConfiguration.defaults(tempDir.resolve("append-barrier-wal"));
        final AppendPublishBarrier barrier = new AppendPublishBarrier();
        final DurableMatchingEngineTcpServer server = new DurableMatchingEngineTcpServer(
                configuration,
                barrier,
                DurableResponseWritePort.production());
        server.start();
        try (Socket socket = connect(server)) {
            socket.setSoTimeout((int) TEST_TIMEOUT.toMillis());
            final OutputStream output = socket.getOutputStream();
            output.write(encode(new SubmitLimitRequest(
                    ClientRequestId.of(1),
                    OrderId.of(9001),
                    Side.BUY,
                    Price.of(100),
                    Quantity.of(1))));
            output.flush();

            assertTrue(barrier.appendReturned.await(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
            assertFalse(barrier.publishAccepted.await(100, TimeUnit.MILLISECONDS));

            barrier.releaseAppend.countDown();
            assertTrue(barrier.publishAccepted.await(
                    TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
                    () -> "publish outcome=" + barrier.publishOutcome.get());
            socket.setSoTimeout(100);
            assertThrows(SocketTimeoutException.class, () -> socket.getInputStream().read());
            socket.setSoTimeout((int) TEST_TIMEOUT.toMillis());

            barrier.releasePublish.countDown();
            assertEquals(NetworkGatewayState.RUNNING, server.state(),
                    () -> "unexpected failure: " + server.failureCause()
                            + " coordinator=" + server.coordinator().terminalFailure());
            final byte[] response = readFrame(socket.getInputStream());
            assertEquals(ProtocolConstants.COMMAND_RESULT_TYPE, unsigned(response[5]));
            assertEquals(2, server.coordinator().nextCommandSequence().value());
        } finally {
            server.shutdown(TEST_TIMEOUT);
        }
        assertEquals(1, CommandWalReader.read(
                configuration.durableConfiguration().walConfiguration()).size());
    }

    @Test
    void outboundWriteFailureRetainsCauseAndRejectsLaterAdmission() throws Exception {
        final DurableNetworkConfiguration configuration =
                DurableNetworkConfiguration.defaults(tempDir.resolve("write-failure-wal"));
        final ControlledResponseWritePort responseWriter = new ControlledResponseWritePort();
        final DurableMatchingEngineTcpServer server = new DurableMatchingEngineTcpServer(
                configuration,
                DurableRuntimePortFactory.production(),
                responseWriter);
        server.start();
        final IllegalStateException writeFailure = new IllegalStateException("controlled write");
        try (Socket socket = connect(server)) {
            final OutputStream output = socket.getOutputStream();
            output.write(encode(new SubmitLimitRequest(
                    ClientRequestId.of(1),
                    OrderId.of(9002),
                    Side.BUY,
                    Price.of(100),
                    Quantity.of(1))));
            output.flush();

            assertTrue(responseWriter.writeAttempted.await(
                    TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
            assertInstanceOf(CommandResultResponse.class, responseWriter.pendingResponse.get());
            assertEquals(NetworkGatewayState.RUNNING, server.state());
            responseWriter.fail(writeFailure);
            awaitState(server, NetworkGatewayState.FAILED);
            awaitCoordinatorState(server, DurableLifecycleState.FAILED);

            assertSame(writeFailure, server.failureCause().orElseThrow());
            assertEquals(DurableFailureStage.OUTBOUND_WRITE,
                    server.coordinator().terminalFailure().orElseThrow().stage());
            assertSame(writeFailure, server.coordinator().terminalFailure().orElseThrow().cause());
            assertThrows(DurableTerminalException.class, () -> server.coordinator().accept(
                    ClientRequestId.of(2),
                    (DurableCommandSequence sequence) -> new SubmitLimitCommand(
                            sequence.toSequence(),
                            OrderId.of(9003),
                            Side.BUY,
                            Price.of(100),
                            Quantity.of(1))));
        } finally {
            server.shutdown(TEST_TIMEOUT);
        }
        assertEquals(1, CommandWalReader.read(
                configuration.durableConfiguration().walConfiguration()).size());
    }

    @Test
    void disconnectBeforeResponseCompletionFailsCoordinatorAndRetainsWal() throws Exception {
        final DurableNetworkConfiguration configuration =
                DurableNetworkConfiguration.defaults(tempDir.resolve("disconnect-window-wal"));
        final AppendPublishBarrier barrier = new AppendPublishBarrier();
        final ControlledResponseWritePort responseWriter = new ControlledResponseWritePort();
        final DurableMatchingEngineTcpServer server = new DurableMatchingEngineTcpServer(
                configuration,
                barrier,
                responseWriter);
        server.start();
        try (Socket socket = connect(server)) {
            final OutputStream output = socket.getOutputStream();
            output.write(encode(new SubmitLimitRequest(
                    ClientRequestId.of(1),
                    OrderId.of(9004),
                    Side.BUY,
                    Price.of(100),
                    Quantity.of(1))));
            output.flush();

            assertTrue(barrier.appendReturned.await(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
            barrier.releaseAppend.countDown();
            assertTrue(barrier.publishAccepted.await(
                    TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
            barrier.releasePublish.countDown();
            assertTrue(responseWriter.writeAttempted.await(
                    TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
            assertFalse(responseWriter.pendingFuture().isDone());
            assertEquals(NetworkGatewayState.RUNNING, server.state());

            socket.setSoLinger(true, 0);
            socket.shutdownInput();
            socket.shutdownOutput();
            socket.close();

            awaitState(server, NetworkGatewayState.FAILED);
            awaitCoordinatorState(server, DurableLifecycleState.FAILED);
            assertEquals(DurableFailureStage.DISCONNECT,
                    server.coordinator().terminalFailure().orElseThrow().stage());
            assertThrows(DurableTerminalException.class, () -> server.coordinator().accept(
                    ClientRequestId.of(2),
                    (DurableCommandSequence sequence) -> new SubmitLimitCommand(
                            sequence.toSequence(),
                            OrderId.of(9005),
                            Side.BUY,
                            Price.of(100),
                            Quantity.of(1))));
        } finally {
            server.shutdown(TEST_TIMEOUT);
        }
        assertEquals(1, CommandWalReader.read(
                configuration.durableConfiguration().walConfiguration()).size());
    }

    @Test
    void synchronousOutboundWriteFailureConvergesCoordinatorAndGateway() throws Exception {
        final DurableNetworkConfiguration configuration =
                DurableNetworkConfiguration.defaults(tempDir.resolve("sync-write-failure-wal"));
        final ControlledResponseWritePort responseWriter = new ControlledResponseWritePort();
        final DurableMatchingEngineTcpServer server = new DurableMatchingEngineTcpServer(
                configuration,
                DurableRuntimePortFactory.production(),
                responseWriter);
        final IllegalStateException writeFailure = new IllegalStateException("synchronous write");
        responseWriter.throwSynchronously(writeFailure);
        server.start();
        try (Socket socket = connect(server)) {
            final OutputStream output = socket.getOutputStream();
            output.write(encode(new SubmitLimitRequest(
                    ClientRequestId.of(1),
                    OrderId.of(9006),
                    Side.BUY,
                    Price.of(100),
                    Quantity.of(1))));
            output.flush();

            awaitState(server, NetworkGatewayState.FAILED);
            awaitCoordinatorState(server, DurableLifecycleState.FAILED);
            assertSame(writeFailure, server.failureCause().orElseThrow());
            assertEquals(DurableFailureStage.OUTBOUND_WRITE,
                    server.coordinator().terminalFailure().orElseThrow().stage());
            assertSame(writeFailure, server.coordinator().terminalFailure().orElseThrow().cause());
            assertThrows(DurableTerminalException.class, () -> server.coordinator().accept(
                    ClientRequestId.of(2),
                    (DurableCommandSequence sequence) -> new SubmitLimitCommand(
                            sequence.toSequence(),
                            OrderId.of(9007),
                            Side.BUY,
                            Price.of(100),
                            Quantity.of(1))));
        } finally {
            server.shutdown(TEST_TIMEOUT);
        }
        assertEquals(1, CommandWalReader.read(
                configuration.durableConfiguration().walConfiguration()).size());
    }

    private static Socket connect(final DurableMatchingEngineTcpServer server) throws Exception {
        final InetAddress address = server.localAddress().orElseThrow().getAddress();
        final int port = server.localAddress().orElseThrow().getPort();
        return new Socket(address, port);
    }

    private static byte[] encode(final Object request) {
        final EmbeddedChannel channel = new EmbeddedChannel(new ProtocolRequestEncoder());
        channel.writeOutbound(request);
        final ByteBuf encoded = channel.readOutbound();
        try {
            final byte[] bytes = new byte[encoded.readableBytes()];
            encoded.getBytes(encoded.readerIndex(), bytes);
            return bytes;
        } finally {
            encoded.release();
            channel.finishAndReleaseAll();
        }
    }

    private static byte[] readFrame(final InputStream input) throws Exception {
        final byte[] header = input.readNBytes(ProtocolConstants.HEADER_LENGTH);
        assertEquals(ProtocolConstants.HEADER_LENGTH, header.length);
        final int length = java.nio.ByteBuffer.wrap(header, 8, Integer.BYTES).getInt();
        final byte[] frame = new byte[length];
        System.arraycopy(header, 0, frame, 0, header.length);
        final byte[] payload = input.readNBytes(length - header.length);
        assertEquals(length - header.length, payload.length);
        System.arraycopy(payload, 0, frame, header.length, payload.length);
        return frame;
    }

    private static void awaitState(
            final DurableMatchingEngineTcpServer server,
            final NetworkGatewayState expected) {
        final long deadline = System.nanoTime() + TEST_TIMEOUT.toNanos();
        while (server.state() != expected && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertEquals(expected, server.state());
    }

    private static void awaitCoordinatorState(
            final DurableMatchingEngineTcpServer server,
            final DurableLifecycleState expected) {
        final long deadline = System.nanoTime() + TEST_TIMEOUT.toNanos();
        while (server.coordinator().state() != expected && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertEquals(expected, server.coordinator().state());
    }

    private static void awaitRelease(final CountDownLatch release, final String name) {
        try {
            if (!release.await(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException(name + " barrier release timed out");
            }
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Barrier wait interrupted", interrupted);
        }
    }

    private static int unsigned(final byte value) {
        return value & 0xFF;
    }

    private static final class AppendPublishBarrier implements DurableRuntimePortFactory {

        private final CountDownLatch appendReturned = new CountDownLatch(1);
        private final CountDownLatch releaseAppend = new CountDownLatch(1);
        private final CountDownLatch publishAccepted = new CountDownLatch(1);
        private final CountDownLatch releasePublish = new CountDownLatch(1);
        private final AtomicReference<PipelinePublishOutcome> publishOutcome = new AtomicReference<>();

        @Override
        public DurableRuntimePorts create(
                final DurableAppendPort appendPort,
                final DurablePublishPort publishPort) {
            return new DurableRuntimePorts(
                    new DurableAppendPort() {
                        @Override
                        public void append(final EngineCommand command) throws java.io.IOException {
                            appendPort.append(command);
                            appendReturned.countDown();
                            awaitRelease(releaseAppend, "append");
                        }
                    },
                    new DurablePublishPort() {
                        @Override
                        public PipelinePublishOutcome tryPublish(final EngineCommand command) {
                            final PipelinePublishOutcome outcome = publishPort.tryPublish(command);
                            publishOutcome.set(outcome);
                            if (outcome == PipelinePublishOutcome.ACCEPTED) {
                                publishAccepted.countDown();
                                awaitRelease(releasePublish, "publish");
                            }
                            return outcome;
                        }
                    });
        }
    }

    private static final class ControlledResponseWritePort implements DurableResponseWritePort {

        private final CountDownLatch writeAttempted = new CountDownLatch(1);
        private final AtomicReference<ChannelPromise> pending = new AtomicReference<>();
        private final AtomicReference<ProtocolResponse> pendingResponse = new AtomicReference<>();
        private final AtomicReference<RuntimeException> synchronousFailure = new AtomicReference<>();

        @Override
        public void write(final Channel channel, final ProtocolResponse response) {
            channel.write(response);
        }

        @Override
        public ChannelFuture writeAndFlush(
                final Channel channel,
                final ProtocolResponse response) {
            final RuntimeException failure = synchronousFailure.get();
            if (failure != null) {
                throw failure;
            }
            final ChannelPromise promise = channel.newPromise();
            pending.set(promise);
            pendingResponse.set(response);
            writeAttempted.countDown();
            channel.read();
            return promise;
        }

        private void fail(final Throwable cause) {
            final ChannelPromise promise = pending.get();
            assertTrue(promise != null, "write future was not captured");
            promise.setFailure(cause);
        }

        private ChannelFuture pendingFuture() {
            return pending.get();
        }

        private void throwSynchronously(final RuntimeException failure) {
            synchronousFailure.set(failure);
        }
    }
}
