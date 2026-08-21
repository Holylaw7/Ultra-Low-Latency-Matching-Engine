package com.ultralatency.matching.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Side;
import com.ultralatency.matching.network.netty.codec.ProtocolRequestEncoder;
import com.ultralatency.matching.network.netty.gateway.MatchingEngineTcpServer;
import com.ultralatency.matching.network.netty.gateway.NetworkGatewayState;
import com.ultralatency.matching.network.protocol.ClientRequestId;
import com.ultralatency.matching.network.protocol.ProtocolConstants;
import com.ultralatency.matching.network.protocol.SubmitLimitRequest;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class NetworkVerificationTest {

    @Test
    void twoGenesisRunsProduceIdenticalOrderedResponseStreams() throws Exception {
        final List<SubmitLimitRequest> requests = fixedRequests();
        final byte[] first = runSequential(requests);
        final byte[] second = runSequential(requests);

        assertTrue(first.length > 0);
        assertTrue(Arrays.equals(first, second));
    }

    @Test
    void oneByteTcpFragmentationStillProducesOneCompleteResult() throws Exception {
        final MatchingEngineTcpServer server = newServer();
        try (Socket socket = connect(server)) {
            socket.setSoTimeout(2_000);
            final byte[] request = encode(fixedRequests().get(0));
            final OutputStream output = socket.getOutputStream();
            for (final byte value : request) {
                output.write(value);
                output.flush();
            }
            final byte[] commandResult = readFrame(socket.getInputStream());
            assertEquals(ProtocolConstants.COMMAND_RESULT_TYPE, unsigned(commandResult[5]));
            assertEquals(1L, longAt(commandResult, 16));
            assertEquals(1L, longAt(commandResult, 24));
            assertEquals(0, intAt(commandResult, 36));
        } finally {
            server.shutdown(Duration.ofSeconds(2));
        }
    }

    @Test
    void coalescedSecondRequestIsRejectedWithoutASecondCommandResult() throws Exception {
        final MatchingEngineTcpServer server = newServer();
        try (Socket socket = connect(server)) {
            socket.setSoTimeout(2_000);
            final byte[] first = encode(fixedRequests().get(0));
            final byte[] second = encode(fixedRequests().get(1));
            final byte[] coalesced = Arrays.copyOf(first, first.length + second.length);
            System.arraycopy(second, 0, coalesced, first.length, second.length);
            socket.getOutputStream().write(coalesced);
            socket.getOutputStream().flush();

            final ByteArrayOutputStream responses = new ByteArrayOutputStream();
            final InputStream input = socket.getInputStream();
            while (true) {
                try {
                    final byte[] frame = readFrame(input);
                    responses.write(frame);
                } catch (final EOFException endOfStream) {
                    break;
                } catch (final java.net.SocketTimeoutException timeout) {
                    break;
                }
                if (input.available() == 0 && socket.isClosed()) {
                    break;
                }
                if (responses.size() > 512) {
                    break;
                }
            }
            final byte[] bytes = responses.toByteArray();
            assertTrue(bytes.length > 0);
            assertTrue(countFrameType(bytes, ProtocolConstants.COMMAND_RESULT_TYPE) <= 1);
            assertTrue(countFrameType(bytes, ProtocolConstants.ERROR_TYPE) >= 1);
        } finally {
            server.shutdown(Duration.ofSeconds(2));
        }
    }

    @Test
    void malformedFrameFailsClosedBeforePublication() throws Exception {
        final MatchingEngineTcpServer server = newServer();
        try (Socket socket = connect(server)) {
            socket.setSoTimeout(2_000);
            final byte[] malformed = encode(fixedRequests().get(0));
            malformed[0] = 0;
            socket.getOutputStream().write(malformed);
            socket.getOutputStream().flush();
            final byte[] error = readFrame(socket.getInputStream());
            assertEquals(ProtocolConstants.ERROR_TYPE, unsigned(error[5]));
            assertEquals(1, unsignedShortAt(error, 24));
            awaitState(server, NetworkGatewayState.FAILED);
        } finally {
            server.shutdown(Duration.ofSeconds(2));
        }
    }

    private static byte[] runSequential(final List<SubmitLimitRequest> requests) throws Exception {
        final MatchingEngineTcpServer server = newServer();
        try (Socket socket = connect(server)) {
            socket.setSoTimeout(2_000);
            final OutputStream output = socket.getOutputStream();
            final InputStream input = socket.getInputStream();
            final ByteArrayOutputStream transcript = new ByteArrayOutputStream();
            for (final SubmitLimitRequest request : requests) {
                output.write(encode(request));
                output.flush();
                final byte[] commandResult = readFrame(input);
                transcript.write(commandResult);
                final int matchCount = intAt(commandResult, 36);
                for (int index = 0; index < matchCount; index++) {
                    transcript.write(readFrame(input));
                }
            }
            return transcript.toByteArray();
        } finally {
            server.shutdown(Duration.ofSeconds(2));
        }
    }

    private static List<SubmitLimitRequest> fixedRequests() {
        return List.of(
                new SubmitLimitRequest(
                        ClientRequestId.of(1),
                        OrderId.of(1),
                        Side.SELL,
                        Price.of(100),
                        Quantity.of(3)),
                new SubmitLimitRequest(
                        ClientRequestId.of(2),
                        OrderId.of(2),
                        Side.BUY,
                        Price.of(100),
                        Quantity.of(3)),
                new SubmitLimitRequest(
                        ClientRequestId.of(3),
                        OrderId.of(3),
                        Side.BUY,
                        Price.of(99),
                        Quantity.of(1)),
                new SubmitLimitRequest(
                        ClientRequestId.of(4),
                        OrderId.of(4),
                        Side.SELL,
                        Price.of(99),
                        Quantity.of(1)));
    }

    private static MatchingEngineTcpServer newServer() {
        final MatchingEngineTcpServer server = new MatchingEngineTcpServer(
                com.ultralatency.matching.network.netty.gateway.NetworkConfiguration.of(
                        InetAddress.getLoopbackAddress(), 0));
        server.start();
        assertEquals(NetworkGatewayState.RUNNING, server.state());
        return server;
    }

    private static Socket connect(final MatchingEngineTcpServer server) throws Exception {
        final InetAddress address = server.localAddress().orElseThrow().getAddress();
        final int port = server.localAddress().orElseThrow().getPort();
        return new Socket(address, port);
    }

    private static byte[] encode(final SubmitLimitRequest request) {
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
        if (header.length == 0) {
            throw new EOFException("Connection closed");
        }
        assertEquals(ProtocolConstants.HEADER_LENGTH, header.length);
        final int length = intAt(header, 8);
        final byte[] frame = Arrays.copyOf(header, length);
        final byte[] payload = input.readNBytes(length - ProtocolConstants.HEADER_LENGTH);
        assertEquals(length - ProtocolConstants.HEADER_LENGTH, payload.length);
        System.arraycopy(payload, 0, frame, ProtocolConstants.HEADER_LENGTH, payload.length);
        return frame;
    }

    private static int countFrameType(final byte[] bytes, final int type) {
        int offset = 0;
        int count = 0;
        while (offset + ProtocolConstants.HEADER_LENGTH <= bytes.length) {
            final int length = intAt(bytes, offset + 8);
            if (length < ProtocolConstants.HEADER_LENGTH || offset + length > bytes.length) {
                break;
            }
            if (unsigned(bytes[offset + 5]) == type) {
                count++;
            }
            offset += length;
        }
        return count;
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
