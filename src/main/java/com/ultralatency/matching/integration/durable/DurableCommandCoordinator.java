package com.ultralatency.matching.integration.durable;

import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.network.protocol.ClientRequestId;
import com.ultralatency.matching.pipeline.PipelinePublishOutcome;
import java.util.Objects;
import java.util.Optional;

/**
 * Synchronous WAL-before-pipeline coordinator for the Phase 7 live durable boundary.
 *
 * <p>The caller thread owns one admission path. A command is constructed with the coordinator's
 * candidate sequence, appended through the durability port, and published only after append
 * returns successfully. A publish failure after a durable append is terminal and is never mapped
 * back to the retryable legacy {@code BACKPRESSURE_FULL} behavior.</p>
 */
public final class DurableCommandCoordinator implements DurableCommandCoordinatorPort {

    private final Object lifecycleMonitor = new Object();
    private final DurableAppendPort appendPort;
    private final DurablePublishPort publishPort;
    private final DurableFailurePort failurePort;
    private DurableLifecycleState state = DurableLifecycleState.NEW;
    private DurableCommandSequence nextCommandSequence = new DurableCommandSequence(1);
    private DurableTerminalFailure terminalFailure;
    private Thread producerThread;
    private boolean failureNotified;

    /**
     * Creates a coordinator with a no-op terminal failure observer.
     *
     * @param appendPort synchronous append/force adapter
     * @param publishPort non-blocking pipeline publication adapter
     */
    public DurableCommandCoordinator(
            final DurableAppendPort appendPort,
            final DurablePublishPort publishPort) {
        this(appendPort, publishPort, new DurableCommandSequence(1), failure -> { });
    }

    /**
     * Creates a coordinator seeded at a validated recovered next sequence.
     *
     * @param appendPort synchronous append/force adapter
     * @param publishPort non-blocking pipeline publication adapter
     * @param nextCommandSequence next sequence after the recovered WAL end
     */
    public DurableCommandCoordinator(
            final DurableAppendPort appendPort,
            final DurablePublishPort publishPort,
            final DurableCommandSequence nextCommandSequence) {
        this(appendPort, publishPort, nextCommandSequence, failure -> { });
    }

    /**
     * Creates a coordinator with an observer for its first terminal failure.
     *
     * @param appendPort synchronous append/force adapter
     * @param publishPort non-blocking pipeline publication adapter
     * @param failurePort first-failure observer
     */
    public DurableCommandCoordinator(
            final DurableAppendPort appendPort,
            final DurablePublishPort publishPort,
            final DurableFailurePort failurePort) {
        this(appendPort, publishPort, new DurableCommandSequence(1), failurePort);
    }

    /**
     * Creates a coordinator with a recovered next sequence and first-failure observer.
     *
     * @param appendPort synchronous append/force adapter
     * @param publishPort non-blocking pipeline publication adapter
     * @param nextCommandSequence next sequence after the recovered WAL end
     * @param failurePort first-failure observer
     */
    public DurableCommandCoordinator(
            final DurableAppendPort appendPort,
            final DurablePublishPort publishPort,
            final DurableCommandSequence nextCommandSequence,
            final DurableFailurePort failurePort) {
        this.appendPort = Objects.requireNonNull(appendPort, "appendPort");
        this.publishPort = Objects.requireNonNull(publishPort, "publishPort");
        this.nextCommandSequence = Objects.requireNonNull(
                nextCommandSequence, "nextCommandSequence");
        this.failurePort = Objects.requireNonNull(failurePort, "failurePort");
    }

    /**
     * Starts the coordinator without allocating a thread or queue.
     *
     * @throws IllegalStateException when the coordinator is not new
     */
    public void start() {
        synchronized (lifecycleMonitor) {
            requireState(DurableLifecycleState.NEW, "start");
            state = DurableLifecycleState.RUNNING;
        }
    }

    /**
     * Admits one command in the owning caller thread.
     *
     * @param requestId session-owned request identity
     * @param commandFactory factory that must use the supplied candidate sequence
     * @return live-accepted outcome after append and publication both succeed
     * @throws DurableTerminalException when append, publication or terminal lifecycle fails
     * @throws IllegalArgumentException when the factory violates the sequence contract
     */
    @Override
    public LiveAcceptedOutcome accept(
            final ClientRequestId requestId,
            final DurableCommandFactory commandFactory) {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(commandFactory, "commandFactory");
        synchronized (lifecycleMonitor) {
            requireRunning();
            claimProducerThread();
            final DurableCommandSequence candidateSequence = nextCommandSequence;
            final EngineCommand command = Objects.requireNonNull(
                    commandFactory.create(candidateSequence), "commandFactory result");
            if (!candidateSequence.toSequence().equals(command.sequence())) {
                throw new IllegalArgumentException(
                        "Command factory must preserve the coordinator sequence");
            }
            final DurableCommandIdentity identity = new DurableCommandIdentity(
                    requestId, candidateSequence);
            final DurableCommand durableCommand = new DurableCommand(identity, command);

            append(durableCommand);
            advanceSequenceAfterDurableAppend();
            final DurableOutcome durable = new DurableOutcome(identity);
            return publish(durableCommand, durable);
        }
    }

    /**
     * Stops new admission and completes the synchronous drain immediately.
     *
     * @return stopped or already terminal lifecycle state
     */
    public DurableLifecycleState shutdown() {
        synchronized (lifecycleMonitor) {
            if (state == DurableLifecycleState.STOPPED || state == DurableLifecycleState.FAILED) {
                return state;
            }
            requireState(DurableLifecycleState.RUNNING, "shutdown");
            state = DurableLifecycleState.DRAINING;
            state = DurableLifecycleState.STOPPED;
            return state;
        }
    }

    /**
     * Returns the next command sequence candidate owned by this coordinator.
     *
     * @return next logical command sequence
     */
    public DurableCommandSequence nextCommandSequence() {
        synchronized (lifecycleMonitor) {
            return nextCommandSequence;
        }
    }

    /**
     * Returns the current lifecycle state.
     *
     * @return current lifecycle state
     */
    @Override
    public DurableLifecycleState state() {
        synchronized (lifecycleMonitor) {
            return state;
        }
    }

    /**
     * Returns the first terminal failure, if any.
     *
     * @return retained first terminal failure
     */
    @Override
    public Optional<DurableTerminalFailure> terminalFailure() {
        synchronized (lifecycleMonitor) {
            return Optional.ofNullable(terminalFailure);
        }
    }

    /**
     * Transitions the coordinator to its first terminal failure.
     *
     * <p>This is used by the surrounding Phase 7 runtime composition when a failure occurs after
     * the coordinator has already returned a durable command, such as a local outbound write
     * failure. It does not retry, rewind or create a second command sequence.</p>
     *
     * @param stage terminal failure boundary
     * @param cause original failure
     */
    public void fail(
            final DurableFailureStage stage,
            final Throwable cause) {
        terminal(
                Objects.requireNonNull(stage, "stage"),
                Objects.requireNonNull(cause, "cause"));
    }

    private void append(final DurableCommand command) {
        try {
            appendPort.append(command);
        } catch (final Throwable failure) {
            throw terminal(DurableFailureStage.APPEND, failure);
        }
    }

    private LiveAcceptedOutcome publish(
            final DurableCommand command,
            final DurableOutcome durable) {
        final PipelinePublishOutcome outcome;
        try {
            outcome = Objects.requireNonNull(
                    publishPort.tryPublish(command), "publishPort outcome");
        } catch (final Throwable failure) {
            throw terminal(DurableFailureStage.PIPELINE, failure);
        }
        if (outcome == PipelinePublishOutcome.FULL) {
            throw terminal(
                    DurableFailureStage.DURABLE_THEN_FULL,
                    new IllegalStateException("Pipeline FULL after durable append"));
        }
        return new LiveAcceptedOutcome(durable);
    }

    private void advanceSequenceAfterDurableAppend() {
        try {
            nextCommandSequence = nextCommandSequence.next();
        } catch (final ArithmeticException failure) {
            throw terminal(DurableFailureStage.APPEND, failure);
        }
    }

    private void claimProducerThread() {
        final Thread currentThread = Thread.currentThread();
        if (producerThread == null) {
            producerThread = currentThread;
        } else if (producerThread != currentThread) {
            throw terminal(
                    DurableFailureStage.PIPELINE,
                    new IllegalStateException("Durable coordinator supports one producer thread"));
        }
    }

    private void requireRunning() {
        if (state == DurableLifecycleState.FAILED && terminalFailure != null) {
            throw new DurableTerminalException(terminalFailure);
        }
        requireState(DurableLifecycleState.RUNNING, "accept");
    }

    private void requireState(final DurableLifecycleState expected, final String operation) {
        if (state != expected) {
            throw new IllegalStateException(
                    "Cannot " + operation + " while coordinator is " + state);
        }
    }

    private DurableTerminalException terminal(
            final DurableFailureStage stage,
            final Throwable cause) {
        final DurableTerminalFailure retained;
        boolean notifyFailure = false;
        synchronized (lifecycleMonitor) {
            if (terminalFailure == null) {
                terminalFailure = new DurableTerminalFailure(stage, cause);
                state = DurableLifecycleState.FAILED;
                retained = terminalFailure;
                if (!failureNotified) {
                    failureNotified = true;
                    notifyFailure = true;
                }
            } else {
                retained = terminalFailure;
            }
        }
        if (notifyFailure) {
            try {
                failurePort.onFailure(retained);
            } catch (final Throwable ignored) {
                // The observer cannot replace the first cause or restart the coordinator.
            }
        }
        return new DurableTerminalException(retained);
    }
}
