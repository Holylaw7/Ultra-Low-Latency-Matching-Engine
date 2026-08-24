package com.ultralatency.matching.app;

import com.ultralatency.matching.integration.durable.DurableConfiguration;
import com.ultralatency.matching.network.netty.durable.DurableNetworkConfiguration;
import com.ultralatency.matching.network.netty.recovery.RecoverableDurableMatchingEngineTcpServer;
import com.ultralatency.matching.network.netty.recovery.RecoverableNetworkConfiguration;
import com.ultralatency.matching.persistence.wal.WalConfiguration;
import com.ultralatency.matching.pipeline.PipelineConfiguration;
import com.ultralatency.matching.operations.ManagementServer;
import com.ultralatency.matching.operations.RuntimeAvailability;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

/**
 * Composition root for the Phase 10 release-candidate runtime.
 *
 * <p>This root owns the Protocol server as a direct child. The Protocol server remains the sole
 * owner of the recovered runtime, and the recovered runtime remains the owner of its lease, WAL,
 * Pipeline and durable coordinator. The ManagementServer becomes the second direct child in the
 * later operational task; readiness is therefore published explicitly only after all required
 * children have been bound.</p>
 */
public final class ReleaseCandidateRuntime implements AutoCloseable {

    private final Object lifecycleMonitor = new Object();
    private final RuntimeConfiguration configuration;
    private final RuntimeAvailability availability;
    private final RecoverableDurableMatchingEngineTcpServer protocolServer;
    private final ManagementServer managementServer;
    private final CountDownLatch terminationSignal;
    private boolean shutdownRequested;

    private ReleaseCandidateRuntime(
            final RuntimeConfiguration configuration,
            final RuntimeAvailability availability,
            final RecoverableDurableMatchingEngineTcpServer protocolServer,
            final ManagementServer managementServer,
            final CountDownLatch terminationSignal) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.availability = Objects.requireNonNull(availability, "availability");
        this.protocolServer = Objects.requireNonNull(protocolServer, "protocolServer");
        this.managementServer = Objects.requireNonNull(managementServer, "managementServer");
        this.terminationSignal = Objects.requireNonNull(terminationSignal, "terminationSignal");
    }

    /**
     * Creates the approved runtime composition from the immutable application configuration.
     *
     * @param configuration validated Phase 10 configuration
     * @return an unstarted composition root
     */
    public static ReleaseCandidateRuntime create(final RuntimeConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        final RuntimeAvailability availability = new RuntimeAvailability();
        final CountDownLatch terminationSignal = new CountDownLatch(1);
        final RecoverableNetworkConfiguration network = networkConfiguration(configuration);
        final RecoverableDurableMatchingEngineTcpServer protocolServer =
                new RecoverableDurableMatchingEngineTcpServer(
                        network,
                        availability::isReady,
                        failure -> {
                            availability.fail(RuntimeFailureCode.RUNTIME);
                            terminationSignal.countDown();
                        });
        final ManagementServer managementServer = new ManagementServer(
                configuration,
                availability::snapshot,
                failure -> {
                    availability.fail(
                            failure instanceof ManagementServer.ManagementBindFailure
                                    ? RuntimeFailureCode.MANAGEMENT_BIND
                                    : RuntimeFailureCode.RUNTIME);
                    terminationSignal.countDown();
                });
        return new ReleaseCandidateRuntime(
                configuration, availability, protocolServer, managementServer, terminationSignal);
    }

    /** Starts recovery, sequence convergence and Protocol binding before readiness publication. */
    public void start() {
        synchronized (lifecycleMonitor) {
            if (availability.snapshot().state() != RuntimeLifecycleState.NEW) {
                throw new IllegalStateException("Release-candidate runtime has already started");
            }
            availability.markConfigurationValidated();
            availability.markStarting();
        }
        try {
            protocolServer.start();
            availability.markProtocolBound();
            managementServer.start();
        } catch (final Throwable failure) {
            availability.fail(failure instanceof ManagementServer.ManagementBindFailure
                    ? RuntimeFailureCode.MANAGEMENT_BIND
                    : RuntimeFailureCode.RUNTIME);
            try {
                final long rollbackDeadline = deadline(timeoutNanos(configuration.shutdownTimeout()));
                managementServer.shutdown(remainingDuration(rollbackDeadline));
                shutdownProtocol(rollbackDeadline);
            } catch (final Throwable cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            terminationSignal.countDown();
            throw rethrow(failure, "Release-candidate runtime failed to start");
        }
    }

    /**
     * Publishes READY after the composition root has bound every required direct child.
     *
     * <p>The operation is valid only after the Protocol and configured ManagementServer children
     * are bound; calling it earlier is rejected, so Protocol admission cannot open before the
     * complete composition is ready.</p>
     */
    public void publishReady() {
        synchronized (lifecycleMonitor) {
            if (availability.snapshot().state() != RuntimeLifecycleState.STARTING) {
                throw new IllegalStateException("Runtime is not starting");
            }
            if (configuration.managementEnabled()) {
                if (!managementServer.isBound()) {
                    throw new IllegalStateException(
                            "Management listener must be bound before readiness publication");
                }
            }
            if (protocolServer.state()
                    != com.ultralatency.matching.integration.recovery.RecoveryRuntimeState.RUNNING) {
                throw new IllegalStateException("Protocol server is not running");
            }
            availability.publishReady(configuration.recoveryMode().name());
        }
    }

    /**
     * Stops admission and closes the owned Protocol child exactly once.
     *
     * <p>The bounded drain extensions are intentionally additive and are hardened further by
     * TASK-045; this task already closes admission before delegating child shutdown.</p>
     */
    public void shutdown() {
        synchronized (lifecycleMonitor) {
            if (shutdownRequested) {
                return;
            }
            shutdownRequested = true;
        }
        final RuntimeLifecycleState current = availability.snapshot().state();
        if (current == RuntimeLifecycleState.NEW) {
            availability.markStopped();
            terminationSignal.countDown();
            return;
        }
        if (current == RuntimeLifecycleState.READY
                || current == RuntimeLifecycleState.STARTING
                || current == RuntimeLifecycleState.CONFIG_VALIDATED) {
            availability.beginStopping();
        }
        final long deadline = deadline(timeoutNanos(configuration.shutdownTimeout()));
        try {
            managementServer.shutdown(remainingDuration(deadline));
            shutdownProtocol(deadline);
            final RuntimeLifecycleState after = availability.snapshot().state();
            if (after == RuntimeLifecycleState.STOPPING
                    || after == RuntimeLifecycleState.FAILED) {
                availability.markStopped();
            }
        } catch (final Throwable failure) {
            if (availability.snapshot().failureCode() == RuntimeFailureCode.NONE) {
                availability.fail(RuntimeFailureCode.SHUTDOWN_TIMEOUT);
            }
            terminationSignal.countDown();
            throw rethrow(failure, "Release-candidate runtime shutdown failed");
        }
        terminationSignal.countDown();
    }

    /** @return immutable application configuration */
    public RuntimeConfiguration configuration() {
        return configuration;
    }

    /** @return one shared readiness and lifecycle owner */
    public RuntimeAvailability availability() {
        return availability;
    }

    /** @return the directly owned Protocol server */
    public RecoverableDurableMatchingEngineTcpServer protocolServer() {
        return protocolServer;
    }

    /** @return the directly owned bounded management child */
    public ManagementServer managementServer() {
        return managementServer;
    }

    /** @return the current immutable operational status */
    public RuntimeStatusSnapshot status() {
        return availability.snapshot();
    }

    @Override
    public void close() {
        shutdown();
    }

    /** Waits for a terminal failure or completed shutdown signal. */
    public void awaitTermination() throws InterruptedException {
        terminationSignal.await();
    }

    private void shutdownProtocol(final long deadline) {
        try {
            protocolServer.stopAdmission();
            final boolean drained = protocolServer.awaitInFlight(remainingDuration(deadline));
            if (!drained) {
                availability.fail(RuntimeFailureCode.SHUTDOWN_TIMEOUT);
            }
            protocolServer.shutdown(remainingDuration(deadline));
            if (protocolServer.state()
                    == com.ultralatency.matching.integration.recovery.RecoveryRuntimeState.FAILED
                    && availability.snapshot().failureCode() == RuntimeFailureCode.NONE) {
                availability.fail(RuntimeFailureCode.RUNTIME);
            }
            if (!drained) {
                throw new IllegalStateException("Protocol shutdown drain timed out");
            }
        } catch (final Throwable failure) {
            availability.fail(RuntimeFailureCode.SHUTDOWN_TIMEOUT);
            throw rethrow(failure, "Release-candidate runtime shutdown failed");
        }
    }

    private static long timeoutNanos(final Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Shutdown timeout must be positive");
        }
        try {
            return timeout.toNanos();
        } catch (final ArithmeticException exception) {
            throw new IllegalArgumentException("Shutdown timeout is too large", exception);
        }
    }

    private static long deadline(final long timeoutNanos) {
        try {
            return Math.addExact(System.nanoTime(), timeoutNanos);
        } catch (final ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static Duration remainingDuration(final long deadline) {
        return Duration.ofNanos(Math.max(1, deadline - System.nanoTime()));
    }

    private static RuntimeException rethrow(final Throwable failure, final String message) {
        if (failure instanceof RuntimeException exception) {
            return exception;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        return new IllegalStateException(message, failure);
    }

    private static RecoverableNetworkConfiguration networkConfiguration(
            final RuntimeConfiguration configuration) {
        final WalConfiguration wal = new WalConfiguration(
                configuration.walDirectory(),
                configuration.walSegmentSizeBytes(),
                configuration.walDurabilityMode());
        final PipelineConfiguration pipeline = new PipelineConfiguration(
                configuration.pipelineCapacity(), configuration.pipelineWaitMode());
        final DurableConfiguration durable = new DurableConfiguration(
                wal, pipeline, configuration.shutdownTimeout());
        final DurableNetworkConfiguration network = new DurableNetworkConfiguration(
                configuration.protocolBindAddress(),
                configuration.protocolPort(),
                configuration.protocolWriteLowWaterMark(),
                configuration.protocolWriteHighWaterMark(),
                durable);
        return RecoverableNetworkConfiguration.from(
                network, configuration.snapshotDirectory(), configuration.recoveryMode());
    }
}
