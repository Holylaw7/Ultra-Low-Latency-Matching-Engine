package com.ultralatency.matching.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.operations.ManagementProtocol;
import com.ultralatency.matching.recovery.online.RecoveryMode;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ManagementServerIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void servesBoundedStatusBeforeAndAfterReadinessThenStopsCleanly() throws Exception {
        final RuntimeConfiguration configuration = configuration(16, null);
        final ReleaseCandidateRuntime runtime = ReleaseCandidateRuntime.create(configuration);
        runtime.start();
        try {
            assertEquals(RuntimeLifecycleState.STARTING, runtime.status().state());
            assertFalse(runtime.status().ready());
            assertTrue(runtime.managementServer().isBound());
            assertTrue(query(runtime, "LIVE").contains("\"live\":true"));
            assertTrue(query(runtime, "READY").contains("\"ready\":false"));
            assertTrue(query(runtime, "STATUS").contains("\"protocolBound\":true"));

            runtime.publishReady();
            assertTrue(query(runtime, "READY").contains("\"ready\":true"));
            final String metrics = query(runtime, "METRICS");
            assertTrue(metrics.contains("\"managementRequests\":"));
            assertTrue(metrics.contains("\"managementRejected\":"));
        } finally {
            runtime.shutdown();
        }
        assertEquals(RuntimeLifecycleState.STOPPED, runtime.status().state());
        assertFalse(runtime.status().ready());
        assertFalse(runtime.managementServer().isBound());
    }

    @Test
    void rejectsMalformedMultipleAndOversizedRequestsWithoutEchoingInput() throws Exception {
        final RuntimeConfiguration configuration = configuration(16, null);
        final ReleaseCandidateRuntime runtime = ReleaseCandidateRuntime.create(configuration);
        runtime.start();
        try {
            assertTrue(query(runtime, "UNKNOWN").contains("INVALID_REQUEST"));
            assertTrue(queryRaw(runtime, "LIVE\nREADY\n").contains("INVALID_REQUEST"));
            assertTrue(queryRaw(runtime, "123456789012345678901234567890123\n")
                    .contains("INVALID_REQUEST"));
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void capsConcurrentConnectionsAndFailsClosedOnManagementBindFailure() throws Exception {
        final RuntimeConfiguration capped = configuration(1, null);
        final ReleaseCandidateRuntime runtime = ReleaseCandidateRuntime.create(capped);
        runtime.start();
        try (Socket first = connect(runtime)) {
            first.setSoTimeout(1_000);
            try (Socket second = connect(runtime)) {
                second.setSoTimeout(2_000);
                assertEquals(-1, second.getInputStream().read());
            }
        } finally {
            runtime.shutdown();
        }

        try (ServerSocket occupied = new ServerSocket(
                0, 50, InetAddress.getLoopbackAddress())) {
            final RuntimeConfiguration colliding = configuration(16, occupied.getLocalPort());
            final ReleaseCandidateRuntime failed = ReleaseCandidateRuntime.create(colliding);
            assertThrows(RuntimeException.class, failed::start);
            assertEquals(RuntimeLifecycleState.FAILED, failed.status().state());
            assertEquals(RuntimeFailureCode.MANAGEMENT_BIND, failed.status().failureCode());
            assertFalse(failed.status().ready());
            failed.shutdown();
        }
    }

    private String query(final ReleaseCandidateRuntime runtime, final String command)
            throws IOException {
        return queryRaw(runtime, command + "\n");
    }

    private String queryRaw(final ReleaseCandidateRuntime runtime, final String request)
            throws IOException {
        try (Socket socket = connect(runtime)) {
            socket.setSoTimeout(2_000);
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.US_ASCII));
                    BufferedReader reader = new BufferedReader(new InputStreamReader(
                            socket.getInputStream(), StandardCharsets.UTF_8))) {
                writer.write(request);
                writer.flush();
                final String line = reader.readLine();
                assertTrue(line != null && line.length() <= ManagementProtocol.MAX_RESPONSE_BYTES);
                return line;
            }
        } catch (final SocketTimeoutException timeout) {
            throw new IOException("Management response timed out", timeout);
        }
    }

    private Socket connect(final ReleaseCandidateRuntime runtime) throws IOException {
        return new Socket(
                runtime.managementServer().localAddress().orElseThrow().getAddress(),
                runtime.managementServer().localAddress().orElseThrow().getPort());
    }

    private RuntimeConfiguration configuration(
            final int maxConnections,
            final Integer managementPort) throws IOException {
        final int protocolPort = freePort();
        final int selectedManagementPort = managementPort == null
                ? freePort()
                : managementPort;
        return new RuntimeConfiguration(
                temporaryDirectory.resolve("wal-" + protocolPort),
                temporaryDirectory.resolve("snapshot-" + protocolPort),
                RecoveryMode.PURE_WAL,
                65_536,
                com.ultralatency.matching.persistence.wal.WalDurabilityMode.SYNC_EACH_APPEND,
                1_024,
                com.ultralatency.matching.pipeline.PipelineWaitMode.BLOCKING,
                InetAddress.getLoopbackAddress(),
                protocolPort,
                8_192,
                16_384,
                true,
                InetAddress.getLoopbackAddress(),
                selectedManagementPort,
                maxConnections,
                Duration.ofSeconds(1),
                Duration.ofSeconds(2));
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
