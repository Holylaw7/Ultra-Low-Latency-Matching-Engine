package com.ultralatency.matching.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.integration.recovery.RecoveryRuntimeState;
import com.ultralatency.matching.recovery.online.RecoveryMode;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReleaseCandidateRuntimeTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void startsRecoveryBeforeReadinessAndClosesDirectChildOnce() throws Exception {
        final ReleaseCandidateRuntime runtime =
                ReleaseCandidateRuntime.create(configuration(RecoveryMode.PURE_WAL));

        runtime.start();
        try {
            assertEquals(RuntimeLifecycleState.STARTING, runtime.status().state());
            assertFalse(runtime.status().ready());
            assertTrue(runtime.status().protocolBound());
            assertEquals(RecoveryRuntimeState.RUNNING, runtime.protocolServer().state());
            assertTrue(runtime.protocolServer().localAddress().isPresent());

            runtime.publishReady();

            assertEquals(RuntimeLifecycleState.READY, runtime.status().state());
            assertTrue(runtime.status().live());
            assertTrue(runtime.status().ready());
        } finally {
            runtime.shutdown();
            runtime.shutdown();
        }

        assertEquals(RuntimeLifecycleState.STOPPED, runtime.status().state());
        assertFalse(runtime.status().live());
        assertFalse(runtime.status().ready());
    }

    @Test
    void recoveryFailureKeepsReadinessFalseAndListenerUnbound() throws Exception {
        final ReleaseCandidateRuntime runtime = ReleaseCandidateRuntime.create(
                configuration(RecoveryMode.SNAPSHOT_THEN_WAL));

        assertThrows(RuntimeException.class, runtime::start);
        assertEquals(RuntimeLifecycleState.FAILED, runtime.status().state());
        assertFalse(runtime.status().ready());
        assertTrue(runtime.protocolServer().localAddress().isEmpty());

        runtime.shutdown();
        assertEquals(RuntimeLifecycleState.STOPPED, runtime.status().state());
    }

    @Test
    void readinessRequiresTheBoundManagementChild() throws Exception {
        final ReleaseCandidateRuntime runtime = ReleaseCandidateRuntime.create(
                configuration(RecoveryMode.PURE_WAL, true));

        runtime.start();
        try {
            assertEquals(RuntimeLifecycleState.STARTING, runtime.status().state());
            assertFalse(runtime.status().ready());
            assertTrue(runtime.managementServer().isBound());
            runtime.publishReady();
            assertTrue(runtime.status().ready());
        } finally {
            runtime.shutdown();
        }
    }

    private RuntimeConfiguration configuration(final RecoveryMode recoveryMode)
            throws IOException {
        return configuration(recoveryMode, false);
    }

    private RuntimeConfiguration configuration(
            final RecoveryMode recoveryMode,
            final boolean managementEnabled) throws IOException {
        final Path walDirectory = temporaryDirectory.resolve("wal-" + recoveryMode.name());
        final Path snapshotDirectory = temporaryDirectory.resolve("snapshot-" + recoveryMode.name());
        return new RuntimeConfiguration(
                walDirectory,
                snapshotDirectory,
                recoveryMode,
                65_536,
                com.ultralatency.matching.persistence.wal.WalDurabilityMode.SYNC_EACH_APPEND,
                1_024,
                com.ultralatency.matching.pipeline.PipelineWaitMode.BLOCKING,
                InetAddress.getLoopbackAddress(),
                freePort(),
                8_192,
                16_384,
                managementEnabled,
                InetAddress.getLoopbackAddress(),
                freePort(),
                16,
                java.time.Duration.ofSeconds(1),
                java.time.Duration.ofSeconds(2));
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
