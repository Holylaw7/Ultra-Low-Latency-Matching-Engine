package com.ultralatency.matching.network.netty.durable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.domain.Side;
import com.ultralatency.matching.engine.SubmitLimitCommand;
import com.ultralatency.matching.network.netty.codec.ProtocolRequestEncoder;
import com.ultralatency.matching.network.protocol.ClientRequestId;
import com.ultralatency.matching.network.protocol.ProtocolConstants;
import com.ultralatency.matching.network.protocol.SubmitLimitRequest;
import com.ultralatency.matching.persistence.wal.CommandWalReader;
import com.ultralatency.matching.persistence.wal.CommandWalWriter;
import com.ultralatency.matching.persistence.wal.WalConfiguration;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DurableMatchingEngineTcpServerTest {

    @TempDir
    Path tempDir;

    @Test
    void appendsBeforeProtocolResultAndUsesTheFreshWal() throws Exception {
        final DurableNetworkConfiguration configuration =
                DurableNetworkConfiguration.defaults(tempDir.resolve("fresh-wal"));
        final DurableMatchingEngineTcpServer server =
                new DurableMatchingEngineTcpServer(configuration);
        server.start();
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

                final byte[] response = readFrame(socket.getInputStream());
                assertEquals(ProtocolConstants.COMMAND_RESULT_TYPE, unsigned(response[5]));
                assertEquals(1L, longAt(response, 16));
                assertEquals(1L, longAt(response, 24));
                assertEquals(1, unsigned(response[32]));
                assertEquals(0, intAt(response, 36));
                assertEquals(
                        com.ultralatency.matching.network.netty.gateway.NetworkGatewayState.STOPPED,
                        server.shutdown(Duration.ofSeconds(2)));
            }
        } finally {
            server.shutdown(Duration.ofSeconds(2));
        }

        final List<com.ultralatency.matching.engine.EngineCommand> commands = CommandWalReader.read(
                configuration.durableConfiguration().walConfiguration());
        assertEquals(1, commands.size());
        assertEquals(Sequence.of(1), commands.get(0).sequence());
        assertEquals(
                com.ultralatency.matching.network.netty.gateway.NetworkGatewayState.STOPPED,
                server.state());
    }

    @Test
    void rejectsNonEmptyWalInsteadOfStartingOnlineRecovery() throws Exception {
        final Path walDirectory = tempDir.resolve("non-empty-wal");
        final WalConfiguration walConfiguration = WalConfiguration.defaults(walDirectory);
        try (CommandWalWriter writer = CommandWalWriter.open(walConfiguration)) {
            writer.append(new SubmitLimitCommand(
                    Sequence.of(1),
                    OrderId.of(7),
                    Side.SELL,
                    Price.of(101),
                    Quantity.of(1)));
        }

        final DurableNetworkConfiguration configuration = new DurableNetworkConfiguration(
                InetAddress.getLoopbackAddress(),
                0,
                DurableNetworkConfiguration.DEFAULT_LOW_WATERMARK,
                DurableNetworkConfiguration.DEFAULT_HIGH_WATERMARK,
                new com.ultralatency.matching.integration.durable.DurableConfiguration(
                        walConfiguration));
        final DurableMatchingEngineTcpServer server =
                new DurableMatchingEngineTcpServer(configuration);

        final IllegalStateException failure = assertThrows(IllegalStateException.class, server::start);
        assertTrue(failure.getMessage().contains("empty WAL directory"));
        assertEquals(
                com.ultralatency.matching.network.netty.gateway.NetworkGatewayState.FAILED,
                server.state());
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

    private static int unsigned(final byte value) {
        return value & 0xFF;
    }
}
