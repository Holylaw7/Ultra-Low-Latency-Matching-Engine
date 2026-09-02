package com.ultralatency.matching.qualification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.engine.CancelOrderCommand;
import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.integration.recovery.RecoveryRuntimeState;
import com.ultralatency.matching.network.netty.recovery.RecoverableDurableMatchingEngineTcpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests the qualification-only paced Protocol v2 writer/reader boundary. */
class ProtocolV2PacedQualificationClientTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void writerDoesNotWaitForResponsesAndWindowIsHardBounded() throws Exception {
        final CountDownLatch accepted = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        try (ServerSocket server = new ServerSocket(0)) {
            final Thread holder = new Thread(() -> holdConnection(server, accepted, release));
            holder.start();
            final long started = System.nanoTime();
            try (ProtocolV2PacedQualificationClient client =
                    new ProtocolV2PacedQualificationClient(
                            loopback(server), Duration.ofSeconds(2), 4)) {
                assertTrue(accepted.await(2, java.util.concurrent.TimeUnit.SECONDS));
                for (int index = 1; index <= 4; index++) {
                    assertTrue(client.tryOffer(command(index), index));
                }
                assertFalse(client.tryOffer(command(5), 5));
                assertEquals(4, client.inFlight());
                assertEquals(4, client.maximumObservedInFlight());
                assertTrue(System.nanoTime() - started < Duration.ofSeconds(1).toNanos());
            } finally {
                release.countDown();
                holder.join(2_000L);
            }
        }
    }

    @Test
    void readerConsumesOrderedResponsesFromTheRealRecoverableServer() throws Exception {
        final RecoverableDurableMatchingEngineTcpServer server = QualificationRunner.server(
                temporaryDirectory.resolve("wal"),
                temporaryDirectory.resolve("snapshots"),
                0,
                com.ultralatency.matching.persistence.wal.WalConfiguration
                        .DEFAULT_SEGMENT_SIZE_BYTES);
        server.start();
        try {
            assertEquals(RecoveryRuntimeState.RUNNING, server.state());
            final List<EngineCommand> commands = List.of(command(1), command(2), command(3));
            try (ProtocolV2PacedQualificationClient client =
                    new ProtocolV2PacedQualificationClient(
                            server.localAddress().orElseThrow(), Duration.ofSeconds(2), 4)) {
                for (int index = 0; index < commands.size(); index++) {
                    assertTrue(client.tryOffer(commands.get(index), index + 1L));
                }
                final List<ProtocolV2PacedQualificationClient.CompletedExchange> completed =
                        new ArrayList<>();
                while (client.inFlight() > 0 || client.completedCount() > 0) {
                    completed.addAll(client.awaitCompleted(Duration.ofSeconds(2)));
                }
                assertEquals(3, completed.size());
                assertEquals(0, client.inFlight());
                assertEquals(0, client.completedCount());
                for (int index = 0; index < completed.size(); index++) {
                    assertEquals(index + 1L, completed.get(index).requestId());
                    assertEquals(index + 1L, completed.get(index).exchange().commandSequence());
                }
                assertTrue(client.maximumObservedInFlight() > 1);
                assertTrue(client.maximumObservedInFlight() <= 4);
            }
        } finally {
            server.shutdown(Duration.ofSeconds(2));
        }
    }

    @Test
    void responseCompletionReleasesWireSlotBeforeCallerDrain() throws Exception {
        try (ScriptedResponder responder = new ScriptedResponder(2, false, false, false);
                ProtocolV2PacedQualificationClient client = new ProtocolV2PacedQualificationClient(
                        responder.address(), Duration.ofSeconds(2), 1)) {
            assertTrue(client.tryOffer(command(1), 1));
            responder.awaitResponse(0);
            awaitCondition(
                    () -> client.inFlight() == 0 && client.completedCount() == 1,
                    Duration.ofSeconds(2));

            assertTrue(client.tryOffer(command(2), 2));
            assertEquals(1, client.completedCount());
            assertEquals(1, client.inFlight());
        }
    }

    @Test
    void pendingInstallationWakesReaderWithoutACompletionDrain() throws Exception {
        try (ScriptedResponder responder = new ScriptedResponder(1, false, false, false);
                ProtocolV2PacedQualificationClient client = new ProtocolV2PacedQualificationClient(
                        responder.address(), Duration.ofSeconds(2), 1)) {
            responder.awaitAccepted();
            assertTrue(client.tryOffer(command(1), 1));
            responder.awaitResponse(0);
            awaitCondition(() -> client.completedCount() == 1, Duration.ofSeconds(2));
            assertEquals(0, client.inFlight());
        }
    }

    @Test
    void completedResponsesRemainWithinTheirIndependentBound() throws Exception {
        try (ScriptedResponder responder = new ScriptedResponder(4, false, false, false);
                ProtocolV2PacedQualificationClient client = new ProtocolV2PacedQualificationClient(
                        responder.address(), Duration.ofSeconds(2), 2)) {
            assertTrue(client.tryOffer(command(1), 1));
            assertTrue(client.tryOffer(command(2), 2));
            responder.awaitResponse(0);
            responder.awaitResponse(1);
            awaitCondition(
                    () -> client.inFlight() == 0 && client.completedCount() == 2,
                    Duration.ofSeconds(2));

            // Wire capacity is reusable even while the caller has not drained the completed
            // queue. The reader must stop at the independent queue bound rather than growing it.
            assertTrue(client.tryOffer(command(3), 3));
            assertTrue(client.tryOffer(command(4), 4));
            responder.awaitResponse(2);
            responder.awaitResponse(3);
            assertEquals(2, client.completedCount());
            assertEquals(2, client.inFlight());
            assertTrue(client.maximumObservedCompleted() <= 2);

            assertEquals(2, client.drainCompleted().size());
            awaitCondition(
                    () -> client.inFlight() == 0 && client.completedCount() == 2,
                    Duration.ofSeconds(2));
            assertEquals(2, client.drainCompleted().size());
        }
    }

    @Test
    void completedResponseIsReleasedExactlyOnce() throws Exception {
        try (ScriptedResponder responder = new ScriptedResponder(2, false, false, false);
                ProtocolV2PacedQualificationClient client = new ProtocolV2PacedQualificationClient(
                        responder.address(), Duration.ofSeconds(2), 1)) {
            assertTrue(client.tryOffer(command(1), 1));
            responder.awaitResponse(0);
            awaitCondition(() -> client.completedCount() == 1, Duration.ofSeconds(2));
            assertEquals(1, client.drainCompleted().size());
            assertTrue(client.drainCompleted().isEmpty());
            assertEquals(0, client.inFlight());

            assertTrue(client.tryOffer(command(2), 2));
            responder.awaitResponse(1);
            awaitCondition(() -> client.completedCount() == 1, Duration.ofSeconds(2));
            assertEquals(1, client.drainCompleted().size());
            assertEquals(1, client.maximumObservedCompleted());
        }
    }

    @Test
    void partialResponseDoesNotReleaseWireSlotBeforeValidationCompletes() throws Exception {
        try (ScriptedResponder responder = new ScriptedResponder(1, true, false, false);
                ProtocolV2PacedQualificationClient client = new ProtocolV2PacedQualificationClient(
                        responder.address(), Duration.ofSeconds(2), 1)) {
            assertTrue(client.tryOffer(command(1), 1));
            responder.awaitFirstPart();
            assertEquals(1, client.inFlight());
            assertEquals(0, client.completedCount());

            responder.releasePartial();
            responder.awaitResponse(0);
            awaitCondition(
                    () -> client.inFlight() == 0 && client.completedCount() == 1,
                    Duration.ofSeconds(2));
        }
    }

    @Test
    void protocolValidationFailureDoesNotReleaseWireSlot() throws Exception {
        try (ScriptedResponder responder = new ScriptedResponder(1, false, false, true);
                ProtocolV2PacedQualificationClient client = new ProtocolV2PacedQualificationClient(
                        responder.address(), Duration.ofSeconds(2), 1)) {
            assertTrue(client.tryOffer(command(1), 1));
            responder.awaitResponse(0);
            awaitCondition(
                    () -> throwsWhenDraining(client),
                    Duration.ofSeconds(2));
            assertEquals(1, client.inFlight());
            assertEquals(0, client.completedCount());
        }
    }

    @Test
    void shutdownRaceStopsReaderWithoutResurrectingCapacity() throws Exception {
        try (ScriptedResponder responder = new ScriptedResponder(1, false, true, false);
                ProtocolV2PacedQualificationClient client = new ProtocolV2PacedQualificationClient(
                        responder.address(), Duration.ofSeconds(2), 1)) {
            assertTrue(client.tryOffer(command(1), 1));
            responder.awaitFirstPart();
            client.close();
            assertThrows(IOException.class, () -> client.tryOffer(command(2), 2));
        }
    }

    private static InetSocketAddress loopback(final ServerSocket server) {
        return new InetSocketAddress("127.0.0.1", server.getLocalPort());
    }

    private static EngineCommand command(final long sequence) {
        return new CancelOrderCommand(Sequence.of(sequence), OrderId.of(10_000L + sequence));
    }

    private static void holdConnection(
            final ServerSocket server,
            final CountDownLatch accepted,
            final CountDownLatch release) {
        try (Socket ignored = server.accept()) {
            accepted.countDown();
            release.await();
        } catch (final Exception ignored) {
            // The test closes the holder socket as part of normal cleanup.
        }
    }

    private static boolean throwsWhenDraining(
            final ProtocolV2PacedQualificationClient client) {
        try {
            client.drainCompleted();
            return false;
        } catch (final IOException expected) {
            return true;
        }
    }

    private static void awaitCondition(
            final java.util.function.BooleanSupplier condition,
            final Duration timeout) throws Exception {
        final long deadline = System.nanoTime() + timeout.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("condition was not observed before timeout");
            }
            Thread.onSpinWait();
        }
    }

    private static byte[] readFrame(final InputStream input) throws IOException {
        final byte[] header = readFully(input, 16);
        final int length = intAt(header, 8);
        if (length < 16 || length > 104) {
            throw new IOException("invalid request frame length");
        }
        final byte[] frame = Arrays.copyOf(header, length);
        final byte[] payload = readFully(input, length - header.length);
        System.arraycopy(payload, 0, frame, header.length, payload.length);
        return frame;
    }

    private static byte[] readFully(final InputStream input, final int length) throws IOException {
        final byte[] bytes = new byte[length];
        int offset = 0;
        while (offset < length) {
            final int count = input.read(bytes, offset, length - offset);
            if (count < 0) {
                throw new IOException("connection closed while reading test frame");
            }
            offset += count;
        }
        return bytes;
    }

    private static byte[] response(final long requestId, final boolean corrupt) {
        final ByteBuffer buffer = ByteBuffer.allocate(40).order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(0x554C4D45);
        buffer.put((byte) 2);
        buffer.put((byte) 0x81);
        buffer.putShort((short) 0);
        buffer.putInt(40);
        buffer.putInt(0);
        buffer.putLong(requestId);
        buffer.putLong(requestId);
        buffer.put((byte) 1);
        buffer.put((byte) (corrupt ? 1 : 0));
        buffer.put(new byte[2]);
        buffer.putInt(0);
        return buffer.array();
    }

    private static int intAt(final byte[] bytes, final int offset) {
        return ByteBuffer.wrap(bytes, offset, Integer.BYTES)
                .order(ByteOrder.BIG_ENDIAN)
                .getInt();
    }

    /** Small deterministic TCP peer used to control response and partial-write boundaries. */
    private static final class ScriptedResponder implements AutoCloseable {

        private final ServerSocket server;
        private final int responseCount;
        private final boolean splitFirst;
        private final boolean holdFirst;
        private final boolean corruptFirst;
        private final CountDownLatch accepted = new CountDownLatch(1);
        private final CountDownLatch firstPart = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final List<CountDownLatch> responses;
        private final AtomicReference<Socket> connection = new AtomicReference<>();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private final Thread worker;
        private volatile boolean closed;

        private ScriptedResponder(
                final int responseCount,
                final boolean splitFirst,
                final boolean holdFirst,
                final boolean corruptFirst) throws IOException {
            server = new ServerSocket(0);
            this.responseCount = responseCount;
            this.splitFirst = splitFirst;
            this.holdFirst = holdFirst;
            this.corruptFirst = corruptFirst;
            responses = new ArrayList<>(responseCount);
            for (int index = 0; index < responseCount; index++) {
                responses.add(new CountDownLatch(1));
            }
            worker = new Thread(this::serve, "qualification-v2-test-peer");
            worker.start();
        }

        private InetSocketAddress address() {
            return new InetSocketAddress("127.0.0.1", server.getLocalPort());
        }

        private void awaitAccepted() throws InterruptedException {
            assertTrue(accepted.await(2, TimeUnit.SECONDS));
        }

        private void awaitFirstPart() throws InterruptedException {
            assertTrue(firstPart.await(2, TimeUnit.SECONDS));
        }

        private void releasePartial() {
            release.countDown();
        }

        private void awaitResponse(final int index) throws InterruptedException {
            assertTrue(responses.get(index).await(2, TimeUnit.SECONDS));
        }

        private void serve() {
            try (Socket socket = server.accept()) {
                connection.set(socket);
                accepted.countDown();
                final InputStream input = socket.getInputStream();
                final OutputStream output = socket.getOutputStream();
                for (int index = 0; index < responseCount; index++) {
                    final byte[] request = readFrame(input);
                    final long requestId = ByteBuffer.wrap(request, 16, Long.BYTES)
                            .order(ByteOrder.BIG_ENDIAN)
                            .getLong();
                    if (index == 0 && holdFirst) {
                        firstPart.countDown();
                        release.await();
                        continue;
                    }
                    final byte[] response = response(requestId, index == 0 && corruptFirst);
                    if (index == 0 && splitFirst) {
                        output.write(response, 0, 20);
                        output.flush();
                        firstPart.countDown();
                        release.await();
                        output.write(response, 20, response.length - 20);
                    } else {
                        output.write(response);
                    }
                    output.flush();
                    responses.get(index).countDown();
                }
            } catch (final Throwable caught) {
                if (!closed) {
                    failure.set(caught);
                }
            }
        }

        @Override
        public void close() throws IOException {
            closed = true;
            release.countDown();
            final Socket socket = connection.get();
            if (socket != null) {
                socket.close();
            }
            server.close();
            try {
                worker.join(2_000L);
            } catch (final InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while closing test peer", interrupted);
            }
            assertFalse(worker.isAlive());
            assertTrue(failure.get() == null, () -> "test peer failed: " + failure.get());
        }
    }
}
