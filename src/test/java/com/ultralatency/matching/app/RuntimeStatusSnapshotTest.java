package com.ultralatency.matching.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RuntimeStatusSnapshotTest {

    @Test
    void initialSnapshotUsesStableSchema() {
        final RuntimeStatusSnapshot snapshot = RuntimeStatusSnapshot.initial();

        assertEquals(1, snapshot.schemaVersion());
        assertEquals(RuntimeLifecycleState.NEW, snapshot.state());
        assertEquals(RuntimeFailureCode.NONE, snapshot.failureCode());
        assertEquals("UNSET", snapshot.recoveryMode());
    }

    @Test
    void rejectsInconsistentReadyAndFailureStates() {
        assertThrows(IllegalArgumentException.class, () -> new RuntimeStatusSnapshot(
                1,
                RuntimeLifecycleState.READY,
                true,
                true,
                RuntimeFailureCode.NONE,
                false,
                "PURE_WAL",
                0,
                0,
                0));
        assertThrows(IllegalArgumentException.class, () -> new RuntimeStatusSnapshot(
                1,
                RuntimeLifecycleState.FAILED,
                false,
                false,
                RuntimeFailureCode.NONE,
                false,
                "PURE_WAL",
                0,
                0,
                0));
    }
}
