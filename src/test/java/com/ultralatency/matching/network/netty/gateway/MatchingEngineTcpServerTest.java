package com.ultralatency.matching.network.netty.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Side;
import com.ultralatency.matching.network.netty.codec.ProtocolRequestEncoder;
import com.ultralatency.matching.network.protocol.CancelOrderRequest;
import com.ultralatency.matching.network.protocol.ClientRequestId;
import com.ultralatency.matching.network.protocol.ProtocolConstants;
import com.ultralatency.matching.network.protocol.SubmitLimitRequest;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class MatchingEngineTcpServerTest {

    @Test
    void acceptsOneSessionAndWritesOrderedSubmitThenCancelResults() throws Exception {
        final MatchingEngineTcpServer server = newServer();
        try {
            final InetAddress address = server.localAddress().orElseThrow().getAddress();
            final int port = server.localAddress().orElseThrow().getPort();
            try (Socket socket = new Socket(address, port)) {
                socket.setSoTimeout(2_000);
                final OutputStream output = socket.getOutputStream();
                output.write(encode(new SubmitLimitRequest(
                        ClientRequestId.of(1),
                        OrderId.of(42),
                        Side.BUY,
                        Price.of(100),
                        Quantity.of(3))));
                output.flush();
                final byte[] submitResult = readFrame(socket.getInputStream());
                assertEquals(ProtocolConstants.COMMAND_RESULT_TYPE, unsigned(submitResult[5]));
                assertEquals(1L, longAt(submitResult, 16));
                assertEquals(1L, longAt(submitResult, 24));
                assertEquals(1, unsigned(submitResult[32]));
                assertEquals(0, intAt(submitResult, 36));

                output.write(encode(new CancelOrderRequest(
                        ClientRequestId.of(2), OrderId.of(42))));
                output.flush();
                final byte[] cancelResult = readFrame(socket.getInputStream());
                assertEquals(ProtocolConstants.COMMAND_RESULT_TYPE, unsigned(cancelResult[5]));
                assertEquals(2L, longAt(cancelResult, 16));
                assertEquals(2L, longAt(cancelResult, 24));
                assertEquals(2, unsigned(cancelResult[32]));
            }
            awaitState(server, NetworkGatewayState.FAILED);
            assertEquals(NetworkGatewayState.FAILED, server.state());
        } finally {
            server.shutdown(Duration.ofSeconds(2));
        }
    }

    @Test
    void rejectsSecondSessionWithoutPublishingIt() throws Exception {
        final MatchingEngineTcpServer server = newServer();
        try {
            final InetAddress address = server.localAddress().orElseThrow().getAddress();
            final int port = server.localAddress().orElseThrow().getPort();
            try (Socket first = new Socket(address, port);
                    Socket second = new Socket(address, port)) {
                second.setSoTimeout(2_000);
                final byte[] error = readFrame(second.getInputStream());
                assertEquals(ProtocolConstants.ERROR_TYPE, unsigned(error[5]));
                assertEquals(7, unsignedShortAt(error, 24));
                assertTrue(second.getInputStream().read() < 0);
                assertTrue(first.isConnected());
            }
        } finally {
            assertEquals(NetworkGatewayState.STOPPED, server.shutdown(Duration.ofSeconds(2)));
        }
    }

    @Test
    void rejectsUnexpectedRequestIdBeforePipelinePublication() throws Exception {
        final MatchingEngineTcpServer server = newServer();
        try {
            final InetAddress address = server.localAddress().orElseThrow().getAddress();
            final int port = server.localAddress().orElseThrow().getPort();
            try (Socket socket = new Socket(address, port)) {
                socket.setSoTimeout(2_000);
                socket.getOutputStream().write(encode(new SubmitLimitRequest(
                        ClientRequestId.of(2),
                        OrderId.of(42),
                        Side.BUY,
                        Price.of(100),
                        Quantity.of(1))));
                socket.getOutputStream().flush();
                final byte[] error = readFrame(socket.getInputStream());
                assertEquals(ProtocolConstants.ERROR_TYPE, unsigned(error[5]));
                assertEquals(5, unsignedShortAt(error, 24));
            }
            awaitState(server, NetworkGatewayState.FAILED);
            assertEquals(NetworkGatewayState.FAILED, server.state());
            assertFalse(server.pipeline().failureCause().isPresent());
        } finally {
            server.shutdown(Duration.ofSeconds(2));
        }
    }

    @Test
    void cleanShutdownIsBoundedAndTerminal() throws Exception {
        final MatchingEngineTcpServer server = newServer();
        final InetAddress address = server.localAddress().orElseThrow().getAddress();
        final int port = server.localAddress().orElseThrow().getPort();
        final Socket socket = new Socket(address, port);
        assertNotNull(socket);
        assertEquals(NetworkGatewayState.STOPPED, server.shutdown(Duration.ofSeconds(2)));
        socket.close();
        assertEquals(NetworkGatewayState.STOPPED, server.state());
    }

    private static MatchingEngineTcpServer newServer() {
        final MatchingEngineTcpServer server = new MatchingEngineTcpServer(
                NetworkConfiguration.of(InetAddress.getLoopbackAddress(), 0));
        server.start();
        assertEquals(NetworkGatewayState.RUNNING, server.state());
        return server;
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
        final int length = intAt(header, 8);
        final byte[] frame = Arrays.copyOf(header, length);
        final byte[] payload = input.readNBytes(length - ProtocolConstants.HEADER_LENGTH);
        assertEquals(length - ProtocolConstants.HEADER_LENGTH, payload.length);
        System.arraycopy(payload, 0, frame, ProtocolConstants.HEADER_LENGTH, payload.length);
        return frame;
    }

    private static long longAt(final byte[] bytes, final int offset) {
        return ByteBuffer.wrap(bytes, offset, Long.BYTES).getLong();
    }

    private static int intAt(final byte[] bytes, final int offset) {
        return ByteBuffer.wrap(bytes, offset, Integer.BYTES).getInt();
    }

    private static int unsignedShortAt(final byte[] bytes, final int offset) {
        return ByteBuffer.wrap(bytes, offset, Short.BYTES).getShort() & 0xFFFF;
    }

    private static int unsigned(final byte value) {
        return value & 0xFF;
    }

    private static void awaitState(
            final MatchingEngineTcpServer server,
            final NetworkGatewayState expected) {
        final long deadline = System.nanoTime() + 2_000_000_000L;
        while (server.state() != expected && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertEquals(expected, server.state());
    }
}
