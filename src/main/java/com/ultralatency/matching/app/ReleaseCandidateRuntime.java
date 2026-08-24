package com.ultralatency.matching.app;

import com.ultralatency.matching.integration.durable.DurableConfiguration;
import com.ultralatency.matching.network.netty.durable.DurableNetworkConfiguration;
import com.ultralatency.matching.network.netty.recovery.RecoverableDurableMatchingEngineTcpServer;
import com.ultralatency.matching.network.netty.recovery.RecoverableNetworkConfiguration;
import com.ultralatency.matching.persistence.wal.WalConfiguration;
import com.ultralatency.matching.pipeline.PipelineConfiguration;
import com.ultralatency.matching.operations.ManagementServer;
import com.ultralatency.matching.operations.RuntimeAvailability;
import java.util.Objects;

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
    private boolean shutdownRequested;

    private ReleaseCandidateRuntime(
            final RuntimeConfiguration configuration,
            final RuntimeAvailability availability,
            final RecoverableDurableMatchingEngineTcpServer protocolServer,
            final ManagementServer managementServer) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.availability = Objects.requireNonNull(availability, "availability");
        this.protocolServer = Objects.requireNonNull(protocolServer, "protocolServer");
        this.managementServer = Objects.requireNonNull(managementServer, "managementServer");
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
        final RecoverableNetworkConfiguration network = networkConfiguration(configuration);
        final RecoverableDurableMatchingEngineTcpServer protocolServer =
                new RecoverableDurableMatchingEngineTcpServer(
                        network,
                        availability::isReady,
                        failure -> availability.fail(RuntimeFailureCode.RUNTIME));
        final ManagementServer managementServer = new ManagementServer(
                configuration,
                availability::snapshot,
                failure -> availability.fail(
                        failure instanceof ManagementServer.ManagementBindFailure
                                ? RuntimeFailureCode.MANAGEMENT_BIND
                                : RuntimeFailureCode.RUNTIME));
        return new ReleaseCandidateRuntime(
                configuration, availability, protocolServer, managementServer);
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
                managementServer.shutdown(configuration.shutdownTimeout());
                shutdownProtocol();
            } catch (final Throwable cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw rethrow(failure, "Release-candidate runtime failed to start");
        }
    }

    /**
     * Publishes READY after the composition root has bound every required direct child.
     *
     * <p>TASK-042 has only the Protocol child. TASK-044 will call this operation after the
     * ManagementServer is bound; calling it earlier is rejected, so Protocol admission cannot
     * open before the complete composition is ready.</p>
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
            return;
        }
        if (current == RuntimeLifecycleState.READY
                || current == RuntimeLifecycleState.STARTING
                || current == RuntimeLifecycleState.CONFIG_VALIDATED) {
            availability.beginStopping();
        }
        managementServer.shutdown(configuration.shutdownTimeout());
        shutdownProtocol();
        final RuntimeLifecycleState after = availability.snapshot().state();
        if (after == RuntimeLifecycleState.STOPPING || after == RuntimeLifecycleState.FAILED) {
            availability.markStopped();
        }
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

    private void shutdownProtocol() {
        try {
            protocolServer.stopAdmission();
            protocolServer.awaitInFlight(configuration.shutdownTimeout());
            protocolServer.shutdown(configuration.shutdownTimeout());
        } catch (final Throwable failure) {
            availability.fail(RuntimeFailureCode.SHUTDOWN_TIMEOUT);
            throw rethrow(failure, "Release-candidate runtime shutdown failed");
        }
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
