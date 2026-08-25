package com.ultralatency.matching.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.integration.durable.DurableFailureStage;
import com.ultralatency.matching.integration.recovery.RecoveryRuntimeState;
import com.ultralatency.matching.recovery.online.RecoveryMode;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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

    @Test
    void programmaticShutdownSignalsTheMainWaiterAfterCleanup() throws Exception {
        final ReleaseCandidateRuntime runtime =
                ReleaseCandidateRuntime.create(configuration(RecoveryMode.PURE_WAL));
        runtime.start();
        runtime.publishReady();
        final CountDownLatch waiterFinished = new CountDownLatch(1);
        final Thread waiter = new Thread(() -> {
            try {
                runtime.awaitTermination();
                waiterFinished.countDown();
            } catch (final InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }, "task045-runtime-waiter");
        waiter.start();
        try {
            assertTrue(runtime.protocolServer().state() == RecoveryRuntimeState.RUNNING);
            assertFalse(waiterFinished.await(50, TimeUnit.MILLISECONDS));
            runtime.shutdown();
            assertTrue(waiterFinished.await(2, TimeUnit.SECONDS));
            assertEquals(RuntimeLifecycleState.STOPPED, runtime.status().state());
        } finally {
            runtime.shutdown();
            waiter.join(2_000);
        }
    }

    @Test
    void firstTerminalFailureSignalsWaiterAndPreservesRuntimeFailureCode() throws Exception {
        final ReleaseCandidateRuntime runtime =
                ReleaseCandidateRuntime.create(configuration(RecoveryMode.PURE_WAL));
        runtime.start();
        runtime.publishReady();
        final CountDownLatch waiterFinished = new CountDownLatch(1);
        final Thread waiter = new Thread(() -> {
            try {
                runtime.awaitTermination();
                waiterFinished.countDown();
            } catch (final InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }, "task045-failure-waiter");
        waiter.start();
        try {
            runtime.protocolServer().runtime().fail(
                    DurableFailureStage.ENGINE,
                    new IllegalStateException("synthetic terminal failure"));
            assertTrue(waiterFinished.await(2, TimeUnit.SECONDS));
            assertEquals(RuntimeFailureCode.RUNTIME, runtime.status().failureCode());
        } finally {
            runtime.shutdown();
            waiter.join(2_000);
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
