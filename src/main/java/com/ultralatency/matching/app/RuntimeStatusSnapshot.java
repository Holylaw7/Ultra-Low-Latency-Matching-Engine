package com.ultralatency.matching.app;

import java.util.Objects;

/** Immutable, bounded operational status exposed by the runtime boundary. */
public record RuntimeStatusSnapshot(
        int schemaVersion,
        RuntimeLifecycleState state,
        boolean live,
        boolean ready,
        RuntimeFailureCode failureCode,
        boolean protocolBound,
        String recoveryMode,
        long acceptedCommands,
        long terminalFailures,
        long uptimeMillis) {

    /** Version of the status schema frozen by ADR-0018. */
    public static final int SCHEMA_VERSION = 1;

    /** Creates and validates an immutable status value. */
    public RuntimeStatusSnapshot {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported runtime status schema version");
        }
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(failureCode, "failureCode");
        Objects.requireNonNull(recoveryMode, "recoveryMode");
        if (recoveryMode.isBlank()) {
            throw new IllegalArgumentException("recoveryMode must not be blank");
        }
        if (acceptedCommands < 0 || terminalFailures < 0 || uptimeMillis < 0) {
            throw new IllegalArgumentException("Runtime status counters must not be negative");
        }
        if (ready && (!live || !protocolBound || state != RuntimeLifecycleState.READY)) {
            throw new IllegalArgumentException("Ready status requires a live bound Protocol");
        }
        if (failureCode != RuntimeFailureCode.NONE && state != RuntimeLifecycleState.FAILED) {
            throw new IllegalArgumentException("Failure code requires FAILED state");
        }
        if (state == RuntimeLifecycleState.FAILED && failureCode == RuntimeFailureCode.NONE) {
            throw new IllegalArgumentException("FAILED state requires a failure code");
        }
    }

    /** @return the initial status before configuration is accepted */
    public static RuntimeStatusSnapshot initial() {
        return new RuntimeStatusSnapshot(
                SCHEMA_VERSION,
                RuntimeLifecycleState.NEW,
                false,
                false,
                RuntimeFailureCode.NONE,
                false,
                "UNSET",
                0,
                0,
                0);
    }
}
