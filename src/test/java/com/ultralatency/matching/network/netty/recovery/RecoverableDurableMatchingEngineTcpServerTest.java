package com.ultralatency.matching.network.netty.recovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.domain.Side;
import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.engine.SubmitLimitCommand;
import com.ultralatency.matching.integration.recovery.RecoveryRuntimeState;
import com.ultralatency.matching.network.netty.codec.ProtocolRequestEncoder;
import com.ultralatency.matching.network.netty.durable.DurableNetworkConfiguration;
import com.ultralatency.matching.network.protocol.CancelOrderRequest;
import com.ultralatency.matching.network.protocol.ClientRequestId;
import com.ultralatency.matching.network.protocol.ProtocolConstants;
import com.ultralatency.matching.persistence.wal.CommandWalWriter;
import com.ultralatency.matching.persistence.wal.WalConfiguration;
import com.ultralatency.matching.recovery.online.RecoveryMode;
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

class RecoverableDurableMatchingEngineTcpServerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void recoversBeforeBindingAndContinuesCommandSequence() throws Exception {
        final Path walDirectory = temporaryDirectory.resolve("wal");
        final WalConfiguration wal = WalConfiguration.defaults(walDirectory);
        write(wal, List.of(command(1, 42, Side.BUY, 100, 1)));
        final RecoverableDurableMatchingEngineTcpServer server = server(
                walDirectory,
                RecoveryMode.PURE_WAL,
                "snapshots");

        server.start();
        try {
            assertEquals(RecoveryRuntimeState.RUNNING, server.state());
            assertTrue(server.localAddress().isPresent());
            assertEquals(2, server.runtime().coordinator().nextCommandSequence().value());
            final InetAddress address = server.localAddress().orElseThrow().getAddress();
            final int port = server.localAddress().orElseThrow().getPort();
            try (Socket socket = new Socket(address, port)) {
                socket.setSoTimeout(2_000);
                final OutputStream output = socket.getOutputStream();
                output.write(encode(new CancelOrderRequest(
                        ClientRequestId.of(1), OrderId.of(42))));
                output.flush();

                final byte[] response = readFrame(socket.getInputStream());
                assertEquals(ProtocolConstants.COMMAND_RESULT_TYPE, unsigned(response[5]));
                assertEquals(1L, longAt(response, 16));
                assertEquals(2L, longAt(response, 24));
            }
        } finally {
            server.shutdown(Duration.ofSeconds(2));
        }
    }

    @Test
    void recoveryFailureLeavesListenerUnboundAndRuntimeTerminal() throws Exception {
        final Path walDirectory = temporaryDirectory.resolve("wal-failure");
        final WalConfiguration wal = WalConfiguration.defaults(walDirectory);
        write(wal, List.of(command(1, 42, Side.BUY, 100, 1)));
        final RecoverableDurableMatchingEngineTcpServer server = server(
                walDirectory,
                RecoveryMode.SNAPSHOT_THEN_WAL,
                "missing-snapshots");

        assertThrows(RuntimeException.class, server::start);
        assertEquals(RecoveryRuntimeState.FAILED, server.state());
        assertTrue(server.localAddress().isEmpty());
        assertTrue(server.failureCause().isPresent());
        server.shutdown(Duration.ofSeconds(2));
    }

    @Test
    void recoveryLeasePreventsASecondBootstrap() throws Exception {
        final Path walDirectory = temporaryDirectory.resolve("wal-lease");
        final WalConfiguration wal = WalConfiguration.defaults(walDirectory);
        write(wal, List.of(command(1, 42, Side.BUY, 100, 1)));
        final RecoverableDurableMatchingEngineTcpServer first = server(
                walDirectory,
                RecoveryMode.PURE_WAL,
                "snapshots");
        final RecoverableDurableMatchingEngineTcpServer second = server(
                walDirectory,
                RecoveryMode.PURE_WAL,
                "snapshots");
        first.start();
        try {
            assertThrows(RuntimeException.class, second::start);
            assertEquals(RecoveryRuntimeState.FAILED, second.state());
            assertTrue(second.localAddress().isEmpty());
        } finally {
            second.shutdown(Duration.ofSeconds(2));
            first.shutdown(Duration.ofSeconds(2));
        }
    }

    private RecoverableDurableMatchingEngineTcpServer server(
            final Path walDirectory,
            final RecoveryMode mode,
            final String snapshotDirectory) {
        final DurableNetworkConfiguration durable = DurableNetworkConfiguration.defaults(walDirectory);
        return new RecoverableDurableMatchingEngineTcpServer(
                RecoverableNetworkConfiguration.from(
                        durable,
                        temporaryDirectory.resolve(snapshotDirectory),
                        mode));
    }

    private static void write(
            final WalConfiguration configuration,
            final List<EngineCommand> commands) throws Exception {
        try (CommandWalWriter writer = CommandWalWriter.open(configuration)) {
            for (final EngineCommand command : commands) {
                writer.append(command);
            }
        }
    }

    private static SubmitLimitCommand command(
            final long sequence,
            final long orderId,
            final Side side,
            final long price,
            final long quantity) {
        return new SubmitLimitCommand(
                Sequence.of(sequence),
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
