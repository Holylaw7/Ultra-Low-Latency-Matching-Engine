package com.ultralatency.matching.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.app.RuntimeFailureCode;
import com.ultralatency.matching.app.RuntimeLifecycleState;
import org.junit.jupiter.api.Test;

class RuntimeAvailabilityTest {

    @Test
    void publishesReadyOnlyAfterTheApprovedLifecycle() {
        final RuntimeAvailability availability = new RuntimeAvailability();

        assertFalse(availability.isReady());
        availability.markConfigurationValidated();
        availability.markStarting();
        availability.markProtocolBound();
        availability.publishReady("PURE_WAL");
        availability.recordAcceptedCommand();

        assertTrue(availability.isReady());
        assertEquals(RuntimeLifecycleState.READY, availability.snapshot().state());
        assertEquals(1, availability.snapshot().acceptedCommands());
        assertTrue(availability.snapshot().live());
    }

    @Test
    void rejectsSkippedTransitionsAndRetainsFirstFailure() {
        final RuntimeAvailability availability = new RuntimeAvailability();

        assertThrows(IllegalStateException.class, () -> availability.publishReady("PURE_WAL"));
        availability.fail(RuntimeFailureCode.CONFIG);
        availability.fail(RuntimeFailureCode.RUNTIME);

        assertEquals(RuntimeLifecycleState.FAILED, availability.snapshot().state());
        assertEquals(RuntimeFailureCode.CONFIG, availability.snapshot().failureCode());
        assertEquals(1, availability.snapshot().terminalFailures());
        assertFalse(availability.isReady());
    }

    @Test
    void supportsBoundedStoppingAndCleanStoppedState() {
        final RuntimeAvailability availability = new RuntimeAvailability();
        availability.markConfigurationValidated();
        availability.markStarting();
        availability.markProtocolBound();
        availability.publishReady("SNAPSHOT_THEN_WAL");
        availability.beginStopping();
        availability.markStopped();

        assertEquals(RuntimeLifecycleState.STOPPED, availability.snapshot().state());
        assertFalse(availability.snapshot().live());
        assertFalse(availability.snapshot().ready());
        assertFalse(availability.snapshot().protocolBound());
    }

    @Test
    void supportsStoppingBeforeReadyAndCloseBeforeStart() {
        final RuntimeAvailability unstarted = new RuntimeAvailability();
        unstarted.markStopped();
        assertEquals(RuntimeLifecycleState.STOPPED, unstarted.snapshot().state());

        final RuntimeAvailability starting = new RuntimeAvailability();
        starting.markConfigurationValidated();
        starting.markStarting();
        starting.beginStopping();
        starting.markStopped();
        assertEquals(RuntimeLifecycleState.STOPPED, starting.snapshot().state());
    }
}
