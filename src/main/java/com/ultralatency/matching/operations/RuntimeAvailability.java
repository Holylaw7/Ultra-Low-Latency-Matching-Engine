package com.ultralatency.matching.operations;

import com.ultralatency.matching.app.RuntimeFailureCode;
import com.ultralatency.matching.app.RuntimeLifecycleState;
import com.ultralatency.matching.app.RuntimeStatusSnapshot;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** Thread-safe lifecycle owner for immutable operational availability snapshots. */
public final class RuntimeAvailability {

    private final Object monitor = new Object();
    private final AtomicLong acceptedCommands = new AtomicLong();
    private final AtomicLong terminalFailures = new AtomicLong();
    private final long startedAtNanos = System.nanoTime();
    private RuntimeLifecycleState state = RuntimeLifecycleState.NEW;
    private RuntimeFailureCode failureCode = RuntimeFailureCode.NONE;
    private boolean protocolBound;
    private String recoveryMode = "UNSET";

    /** @return whether new Protocol admission is currently allowed */
    public boolean isReady() {
        synchronized (monitor) {
            return state == RuntimeLifecycleState.READY;
        }
    }

    /** Marks configuration as accepted. */
    public void markConfigurationValidated() {
        transition(RuntimeLifecycleState.CONFIG_VALIDATED, RuntimeFailureCode.NONE, false);
    }

    /** Marks runtime startup as in progress. */
    public void markStarting() {
        transition(RuntimeLifecycleState.STARTING, RuntimeFailureCode.NONE, false);
    }

    /** Publishes the only state in which admission is allowed. */
    public void publishReady(final String recoveryMode) {
        Objects.requireNonNull(recoveryMode, "recoveryMode");
        synchronized (monitor) {
            requireTransition(RuntimeLifecycleState.READY);
            this.recoveryMode = recoveryMode;
            this.protocolBound = true;
            this.state = RuntimeLifecycleState.READY;
        }
    }

    /** Marks shutdown admission closed. */
    public void beginStopping() {
        transition(RuntimeLifecycleState.STOPPING, RuntimeFailureCode.NONE, protocolBound);
    }

    /** Records a clean resource shutdown. */
    public void markStopped() {
        synchronized (monitor) {
            requireTransition(RuntimeLifecycleState.STOPPED);
            state = RuntimeLifecycleState.STOPPED;
            protocolBound = false;
        }
    }

    /** Records the first sanitized terminal failure and closes admission. */
    public void fail(final RuntimeFailureCode code) {
        if (code == null || code == RuntimeFailureCode.NONE) {
            throw new IllegalArgumentException("A terminal failure code is required");
        }
        synchronized (monitor) {
            if (state == RuntimeLifecycleState.FAILED || state == RuntimeLifecycleState.STOPPED) {
                return;
            }
            failureCode = code;
            state = RuntimeLifecycleState.FAILED;
            protocolBound = false;
            increment(terminalFailures);
        }
    }

    /** Records one durably accepted command without exposing command data. */
    public void recordAcceptedCommand() {
        increment(acceptedCommands);
    }

    /** Returns an immutable status snapshot suitable for management serialization. */
    public RuntimeStatusSnapshot snapshot() {
        synchronized (monitor) {
            final boolean live = state == RuntimeLifecycleState.STARTING
                    || state == RuntimeLifecycleState.READY
                    || state == RuntimeLifecycleState.STOPPING;
            final long uptimeMillis = Math.max(0, (System.nanoTime() - startedAtNanos) / 1_000_000);
            return new RuntimeStatusSnapshot(
                    RuntimeStatusSnapshot.SCHEMA_VERSION,
                    state,
                    live,
                    state == RuntimeLifecycleState.READY,
                    failureCode,
                    protocolBound,
                    recoveryMode,
                    acceptedCommands.get(),
                    terminalFailures.get(),
                    uptimeMillis);
        }
    }

    private void transition(
            final RuntimeLifecycleState next,
            final RuntimeFailureCode code,
            final boolean bound) {
        synchronized (monitor) {
            requireTransition(next);
            state = next;
            failureCode = code;
            protocolBound = bound;
        }
    }

    private void requireTransition(final RuntimeLifecycleState next) {
        final boolean valid = switch (state) {
            case NEW -> next == RuntimeLifecycleState.CONFIG_VALIDATED
                    || next == RuntimeLifecycleState.FAILED;
            case CONFIG_VALIDATED -> next == RuntimeLifecycleState.STARTING
                    || next == RuntimeLifecycleState.FAILED;
            case STARTING -> next == RuntimeLifecycleState.READY
                    || next == RuntimeLifecycleState.FAILED;
            case READY -> next == RuntimeLifecycleState.STOPPING
                    || next == RuntimeLifecycleState.FAILED;
            case STOPPING -> next == RuntimeLifecycleState.STOPPED
                    || next == RuntimeLifecycleState.FAILED;
            case FAILED -> next == RuntimeLifecycleState.STOPPED;
            case STOPPED -> false;
        };
        if (!valid) {
            throw new IllegalStateException("Invalid runtime transition " + state + " -> " + next);
        }
    }

    private static void increment(final AtomicLong counter) {
        counter.updateAndGet(value -> value == Long.MAX_VALUE ? value : value + 1);
    }
}
