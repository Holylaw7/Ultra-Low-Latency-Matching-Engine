package com.ultralatency.matching.qualification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.engine.CancelOrderCommand;
import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.integration.recovery.RecoveryRuntimeState;
import com.ultralatency.matching.network.netty.recovery.RecoverableDurableMatchingEngineTcpServer;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies the qualification-owned Protocol v2 bounded client against the real server path. */
class ProtocolV2QualificationClientTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void exchangesOneBoundedWindowAgainstRecoverableServer() throws Exception {
        final RecoverableDurableMatchingEngineTcpServer server = QualificationRunner.server(
                temporaryDirectory.resolve("wal"),
                temporaryDirectory.resolve("snapshots"),
                0,
                com.ultralatency.matching.persistence.wal.WalConfiguration
                        .DEFAULT_SEGMENT_SIZE_BYTES);
        server.start();
        try {
            assertEquals(RecoveryRuntimeState.RUNNING, server.state());
            final InetSocketAddress address = server.localAddress().orElseThrow();
            final List<EngineCommand> commands = List.of(
                    new CancelOrderCommand(Sequence.of(1), OrderId.of(101)),
                    new CancelOrderCommand(Sequence.of(2), OrderId.of(102)));
            try (ProtocolV2QualificationClient client = new ProtocolV2QualificationClient(
                    address, Duration.ofSeconds(2), 2)) {
                final List<QualificationExchange> exchanges = client.exchangeAll(commands, 1);
                assertEquals(2, exchanges.size());
                assertEquals(1L, exchanges.get(0).requestId());
                assertEquals(2L, exchanges.get(1).requestId());
                assertEquals(1L, exchanges.get(0).commandSequence());
                assertEquals(2L, exchanges.get(1).commandSequence());
                assertTrue(exchanges.stream().allMatch(item -> item.matches().isEmpty()));
            }
        } finally {
            server.shutdown(Duration.ofSeconds(2));
        }
    }
}
