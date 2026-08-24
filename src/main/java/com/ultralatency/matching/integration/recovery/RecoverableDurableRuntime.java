package com.ultralatency.matching.integration.recovery;

import com.ultralatency.matching.integration.durable.DurableCommandCoordinator;
import com.ultralatency.matching.integration.durable.DurableCommandSequence;
import com.ultralatency.matching.integration.durable.DurableConfiguration;
import com.ultralatency.matching.integration.durable.DurableFailureStage;
import com.ultralatency.matching.integration.durable.DurableLifecycleState;
import com.ultralatency.matching.integration.durable.DurableTerminalFailure;
import com.ultralatency.matching.persistence.snapshot.RecoveryLease;
import com.ultralatency.matching.persistence.wal.CommandWalWriter;
import com.ultralatency.matching.pipeline.EngineResultHandler;
import com.ultralatency.matching.pipeline.MatchingEnginePipeline;
import com.ultralatency.matching.pipeline.PipelineState;
import com.ultralatency.matching.recovery.online.RecoveryPlanner;
import com.ultralatency.matching.recovery.online.RecoveryResult;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Listener-independent recovered durable runtime composition.
 *
 * <p>The runtime performs strict recovery before constructing the recovered-engine Pipeline and
 * seeded coordinator. It owns those resources until shutdown; a network adapter may bind a
 * listener only after this runtime reaches {@link RecoveryRuntimeState#RUNNING}.</p>
 */
public final class RecoverableDurableRuntime implements AutoCloseable {

    private final Object lifecycleMonitor = new Object();
    private final RecoveryRuntimeConfiguration configuration;
    private final EngineResultHandler resultHandler;
    private final Consumer<DurableTerminalFailure> failureObserver;
    private RecoveryRuntimeState state = RecoveryRuntimeState.NEW;
    private Throwable failureCause;
    private RecoveryResult recoveryResult;
    private RecoveryLease recoveryLease;
    private CommandWalWriter walWriter;
    private MatchingEnginePipeline pipeline;
    private DurableCommandCoordinator coordinator;
    private boolean failureNotified;

    /**
     * Creates a recovered runtime with an in-memory result handler and no-op failure observer.
     *
     * @param configuration explicit recovery configuration
     * @param resultHandler live engine-result handoff
     */
    public RecoverableDurableRuntime(
            final RecoveryRuntimeConfiguration configuration,
            final EngineResultHandler resultHandler) {
        this(configuration, resultHandler, failure -> { });
    }

    /**
     * Creates a recovered runtime with an external first-failure observer.
     *
     * @param configuration explicit recovery configuration
     * @param resultHandler live engine-result handoff
     * @param failureObserver first terminal failure observer
     */
    public RecoverableDurableRuntime(
            final RecoveryRuntimeConfiguration configuration,
            final EngineResultHandler resultHandler,
            final Consumer<DurableTerminalFailure> failureObserver) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.resultHandler = Objects.requireNonNull(resultHandler, "resultHandler");
        this.failureObserver = Objects.requireNonNull(failureObserver, "failureObserver");
    }

    /** Starts strict recovery, then the recovered Pipeline and coordinator. */
    public void start() {
        synchronized (lifecycleMonitor) {
            requireState(RecoveryRuntimeState.NEW, "start");
            state = RecoveryRuntimeState.RECOVERING;
        }
        try {
            final DurableConfiguration durable = configuration.durableConfiguration();
            recoveryLease = RecoveryLease.acquire(durable.walConfiguration().directory());
            recoveryResult = RecoveryPlanner.create(
                    durable.walConfiguration(),
                    configuration.snapshotDirectory())
                    .recover(configuration.recoveryMode(), recoveryLease);
            synchronized (lifecycleMonitor) {
                state = RecoveryRuntimeState.RECOVERED;
            }
            walWriter = CommandWalWriter.open(durable.walConfiguration());
            if (walWriter.nextCommandSequence() != recoveryResult.nextCommandSequence()) {
                throw new IllegalStateException("WAL writer sequence does not converge with recovery");
            }
            synchronized (lifecycleMonitor) {
                state = RecoveryRuntimeState.STARTING;
            }
            pipeline = new MatchingEnginePipeline(
                    durable.pipelineConfiguration(),
                    recoveryResult.engine(),
                    resultHandler,
                    this::onPipelineFailure);
            coordinator = new DurableCommandCoordinator(
                    walWriter::append,
                    pipeline::tryPublish,
                    new DurableCommandSequence(recoveryResult.nextCommandSequence()),
                    this::onCoordinatorFailure);
            pipeline.start();
            coordinator.start();
            synchronized (lifecycleMonitor) {
                state = RecoveryRuntimeState.RUNNING;
            }
        } catch (final Throwable failure) {
            failTerminal(DurableFailureStage.STARTUP, failure);
            throw rethrow(failure, "Recovered durable runtime failed to start");
        }
    }

    /** Stops the runtime and releases its lease and owned resources. */
    public RecoveryRuntimeState shutdown() {
        return shutdown(configuration.durableConfiguration().shutdownTimeout());
    }

    /**
     * Stops the runtime using one cooperative deadline for Pipeline, storage and lease close.
     *
     * @param timeout maximum total shutdown duration
     * @return final runtime lifecycle state
     */
    public RecoveryRuntimeState shutdown(final Duration timeout) {
        final long deadline = deadline(timeoutNanos(timeout));
        MatchingEnginePipeline currentPipeline = null;
        DurableCommandCoordinator currentCoordinator = null;
        CommandWalWriter currentWriter = null;
        RecoveryLease currentLease = null;
        boolean terminal = false;
        synchronized (lifecycleMonitor) {
            if (state == RecoveryRuntimeState.STOPPED) {
                return state;
            }
            if (state == RecoveryRuntimeState.FAILED) {
                currentPipeline = pipeline;
                currentCoordinator = coordinator;
                currentWriter = walWriter;
                currentLease = recoveryLease;
                pipeline = null;
                coordinator = null;
                walWriter = null;
                recoveryLease = null;
                terminal = true;
            } else if (state == RecoveryRuntimeState.NEW) {
                state = RecoveryRuntimeState.STOPPED;
                return state;
            } else {
                currentPipeline = pipeline;
                currentCoordinator = coordinator;
                currentWriter = walWriter;
                currentLease = recoveryLease;
                pipeline = null;
                coordinator = null;
                walWriter = null;
                recoveryLease = null;
                state = RecoveryRuntimeState.STOPPED;
            }
        }
        final boolean closed = closeResources(
                currentPipeline, currentCoordinator, currentWriter, currentLease, deadline);
        if (!closed && !terminal) {
            synchronized (lifecycleMonitor) {
                if (failureCause == null) {
                    failureCause = new IllegalStateException("Recovered runtime shutdown failed");
                }
                state = RecoveryRuntimeState.FAILED;
            }
        }
        synchronized (lifecycleMonitor) {
            return state;
        }
    }

    /** @return current runtime lifecycle state */
    public RecoveryRuntimeState state() {
        synchronized (lifecycleMonitor) {
            return state;
        }
    }

    /** @return first runtime failure, if terminal */
    public Optional<Throwable> failureCause() {
        synchronized (lifecycleMonitor) {
            return Optional.ofNullable(failureCause);
        }
    }

    /** @return validated offline recovery result after recovery begins */
    public RecoveryResult recoveryResult() {
        synchronized (lifecycleMonitor) {
            return requireRuntime(recoveryResult, "recovery result");
        }
    }

    /** @return recovered-engine Pipeline after startup */
    public MatchingEnginePipeline pipeline() {
        synchronized (lifecycleMonitor) {
            return requireRuntime(pipeline, "pipeline");
        }
    }

    /** @return seeded durable coordinator after startup */
    public DurableCommandCoordinator coordinator() {
        synchronized (lifecycleMonitor) {
            return requireRuntime(coordinator, "coordinator");
        }
    }

    /**
     * Propagates a runtime-owned terminal failure to the same first-cause boundary.
     *
     * @param stage failure boundary
     * @param cause original failure
     */
    public void fail(final DurableFailureStage stage, final Throwable cause) {
        failTerminal(stage, cause);
    }

    @Override
    public void close() {
        shutdown();
    }

    private void onPipelineFailure(final Throwable failure) {
        failTerminal(DurableFailureStage.PIPELINE, failure);
    }

    private void onCoordinatorFailure(final DurableTerminalFailure failure) {
        failTerminal(failure.stage(), failure.cause());
    }

    private void failTerminal(
            final DurableFailureStage stage,
            final Throwable cause) {
        final DurableTerminalFailure retained;
        boolean notifyFailure = false;
        synchronized (lifecycleMonitor) {
            if (failureCause != null || state == RecoveryRuntimeState.STOPPED) {
                return;
            }
            failureCause = Objects.requireNonNull(cause, "cause");
            state = RecoveryRuntimeState.FAILED;
            retained = new DurableTerminalFailure(
                    Objects.requireNonNull(stage, "stage"), failureCause);
            if (!failureNotified) {
                failureNotified = true;
                notifyFailure = true;
            }
        }
        if (notifyFailure) {
            try {
                failureObserver.accept(retained);
            } catch (final Throwable ignored) {
                // An observer cannot replace the first runtime failure.
            }
        }
        releaseResources();
    }

    private void releaseResources() {
        final MatchingEnginePipeline currentPipeline;
        final DurableCommandCoordinator currentCoordinator;
        final CommandWalWriter currentWriter;
        final RecoveryLease currentLease;
        synchronized (lifecycleMonitor) {
            currentPipeline = pipeline;
            currentCoordinator = coordinator;
            currentWriter = walWriter;
            currentLease = recoveryLease;
            pipeline = null;
            coordinator = null;
            walWriter = null;
            recoveryLease = null;
        }
        closeResources(
                currentPipeline,
                currentCoordinator,
                currentWriter,
                currentLease,
                deadline(configuration.durableConfiguration().shutdownTimeout()));
    }

    private static boolean closeResources(
            final MatchingEnginePipeline pipeline,
            final DurableCommandCoordinator coordinator,
            final CommandWalWriter writer,
            final RecoveryLease lease,
            final long deadline) {
        boolean clean = true;
        if (coordinator != null && coordinator.state() == DurableLifecycleState.RUNNING) {
            if (coordinator.shutdown() != DurableLifecycleState.STOPPED) {
                clean = false;
            }
        }
        if (pipeline != null
                && (pipeline.state() == PipelineState.RUNNING
                || pipeline.state() == PipelineState.DRAINING)) {
            if (pipeline.shutdown(Duration.ofNanos(remaining(deadline))) != PipelineState.STOPPED) {
                clean = false;
            }
        }
        if (writer != null) {
            try {
                writer.close();
            } catch (final IOException ignored) {
                // The first runtime failure remains authoritative.
                clean = false;
            }
        }
        if (lease != null) {
            try {
                lease.close();
            } catch (final IOException ignored) {
                // The first runtime failure remains authoritative.
                clean = false;
            }
        }
        return clean;
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

    private static long deadline(final Duration timeout) {
        return deadline(timeoutNanos(timeout));
    }

    private static long deadline(final long timeoutNanos) {
        try {
            return Math.addExact(System.nanoTime(), timeoutNanos);
        } catch (final ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static long remaining(final long deadline) {
        return Math.max(1, deadline - System.nanoTime());
    }

    private void requireState(final RecoveryRuntimeState expected, final String operation) {
        if (state != expected) {
            throw new IllegalStateException(
                    "Cannot " + operation + " while recovery runtime is " + state);
        }
    }

    private static <T> T requireRuntime(final T value, final String name) {
        if (value == null) {
            throw new IllegalStateException(name + " is not available");
        }
        return value;
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
}
