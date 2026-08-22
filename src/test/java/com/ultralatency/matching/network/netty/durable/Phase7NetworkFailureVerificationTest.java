package com.ultralatency.matching.network.netty.durable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Side;
import com.ultralatency.matching.network.netty.gateway.NetworkGatewayState;
import com.ultralatency.matching.network.netty.codec.ProtocolRequestEncoder;
import com.ultralatency.matching.network.protocol.ClientRequestId;
import com.ultralatency.matching.network.protocol.ProtocolConstants;
import com.ultralatency.matching.network.protocol.SubmitLimitRequest;
import com.ultralatency.matching.persistence.wal.CommandWalReader;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Phase7NetworkFailureVerificationTest {

    @TempDir
    Path tempDir;

    @Test
    void disconnectBeforeAppendLeavesAnEmptyWalAndFailsTheSession() throws Exception {
        final DurableNetworkConfiguration configuration =
                DurableNetworkConfiguration.defaults(tempDir.resolve("before-append-wal"));
        final DurableMatchingEngineTcpServer server =
                new DurableMatchingEngineTcpServer(configuration);
        server.start();
        try (Socket socket = connect(server)) {
            socket.shutdownInput();
            socket.shutdownOutput();
        }
        try {
            awaitState(server, NetworkGatewayState.FAILED);
        } finally {
            server.shutdown(Duration.ofSeconds(2));
        }

        assertEquals(0, CommandWalReader.read(
                configuration.durableConfiguration().walConfiguration()).size());
    }

    @Test
    void disconnectAfterDurableAdmissionIsTerminalAndWalRemainsClosedForOfflineReplay()
            throws Exception {
        final DurableNetworkConfiguration configuration =
                DurableNetworkConfiguration.defaults(tempDir.resolve("disconnect-wal"));
        final DurableMatchingEngineTcpServer server =
                new DurableMatchingEngineTcpServer(configuration);
        server.start();
        try {
            try (Socket socket = connect(server)) {
                final OutputStream output = socket.getOutputStream();
                output.write(encode(request(1, 801)));
                output.flush();
                awaitDurableSequence(server, 2);
                socket.setSoLinger(true, 0);
            }
            awaitState(server, NetworkGatewayState.FAILED);
            assertTrue(server.failureCause().isPresent());
        } finally {
            server.shutdown(Duration.ofSeconds(2));
        }

        assertEquals(1, CommandWalReader.read(
                configuration.durableConfiguration().walConfiguration()).size());
    }

    @Test
    void disconnectAfterPublishBeforeResponseCompletionIsTerminal() throws Exception {
        final DurableNetworkConfiguration configuration =
                DurableNetworkConfiguration.defaults(tempDir.resolve("response-wal"));
        final DurableMatchingEngineTcpServer server =
                new DurableMatchingEngineTcpServer(configuration);
        server.start();
        try (Socket socket = connect(server)) {
            socket.setSoTimeout(2_000);
            final OutputStream output = socket.getOutputStream();
            output.write(encode(request(1, 821, Side.SELL, 100, 1)));
            output.flush();
            readFrame(socket.getInputStream());
            output.write(encode(request(2, 822, Side.SELL, 100, 1)));
            output.flush();
            readFrame(socket.getInputStream());

            output.write(encode(request(3, 823, Side.BUY, 100, 2)));
            output.flush();
            final byte[] commandResult = readFrame(socket.getInputStream());
            assertEquals(ProtocolConstants.COMMAND_RESULT_TYPE, unsigned(commandResult[5]));
            socket.setSoLinger(true, 0);
        }
        awaitState(server, NetworkGatewayState.FAILED);
        try {
            assertTrue(server.failureCause().isPresent());
        } finally {
            server.shutdown(Duration.ofSeconds(2));
        }

        assertEquals(3, CommandWalReader.read(
                configuration.durableConfiguration().walConfiguration()).size());
    }

    @Test
    void coalescedSecondRequestIsRejectedWithoutASecondDurableCommand() throws Exception {
        final DurableNetworkConfiguration configuration =
                DurableNetworkConfiguration.defaults(tempDir.resolve("coalesced-wal"));
        final DurableMatchingEngineTcpServer server =
                new DurableMatchingEngineTcpServer(configuration);
        server.start();
        try {
            try (Socket socket = connect(server)) {
                socket.setSoTimeout(2_000);
                final OutputStream output = socket.getOutputStream();
                final byte[] first = encode(request(1, 811));
                final byte[] second = encode(request(2, 812));
                output.write(first);
                output.write(second);
                output.flush();
            }
            awaitState(server, NetworkGatewayState.FAILED);
        } finally {
            server.shutdown(Duration.ofSeconds(2));
        }

        final List<com.ultralatency.matching.engine.EngineCommand> commands =
                CommandWalReader.read(configuration.durableConfiguration().walConfiguration());
        assertEquals(1, commands.size());
        assertEquals(1, commands.get(0).sequence().value());
    }

    private static Socket connect(final DurableMatchingEngineTcpServer server) throws Exception {
        final InetAddress address = server.localAddress().orElseThrow().getAddress();
        final int port = server.localAddress().orElseThrow().getPort();
        return new Socket(address, port);
    }

    private static SubmitLimitRequest request(final long requestId, final long orderId) {
        return request(requestId, orderId, Side.BUY, 100, 1);
    }

    private static SubmitLimitRequest request(
            final long requestId,
            final long orderId,
            final Side side,
            final long price,
            final long quantity) {
        return new SubmitLimitRequest(
                ClientRequestId.of(requestId),
                OrderId.of(orderId),
                side,
                Price.of(price),
                Quantity.of(quantity));
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
        final byte[] header = input.readNBytes(12);
        assertEquals(12, header.length);
        final int length = java.nio.ByteBuffer.wrap(header, 8, 4).getInt();
        final byte[] frame = Arrays.copyOf(header, length);
        final byte[] payload = input.readNBytes(length - 12);
        assertEquals(length - 12, payload.length);
        System.arraycopy(payload, 0, frame, 12, payload.length);
        return frame;
    }

    private static void awaitState(
            final DurableMatchingEngineTcpServer server,
            final NetworkGatewayState expected) {
        final long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (server.state() != expected && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertEquals(expected, server.state());
    }

    private static void awaitDurableSequence(
            final DurableMatchingEngineTcpServer server,
            final long expectedNextSequence) {
        final long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (System.nanoTime() < deadline) {
            if (server.coordinator().nextCommandSequence().value() >= expectedNextSequence) {
                return;
            }
            Thread.onSpinWait();
        }
        assertTrue(server.coordinator().nextCommandSequence().value() >= expectedNextSequence);
    }

    private static int unsigned(final byte value) {
        return value & 0xFF;
    }
}
