package com.ultralatency.matching.qualification.ga.soak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Deterministic tests for the qualification-only absolute-deadline hybrid waiter. */
class PacingDeadlineWaiterTest {

    private static final long SLOT_NANOS = 5_000_000L;

    @Test
    void expiredDeadlineReturnsWithoutWaiting() {
        final FakeTime time = new FakeTime(10_000_000L, 1_000_000L, 100_000L);
        final PacingDeadlineWaiter waiter = time.waiter(1_000_000L);

        assertTrue(waiter.awaitUntil(10_000_000L));
        assertEquals(0, time.parkRequests.size());
        assertEquals(0, time.spinCalls);
    }

    @Test
    void farDeadlineUsesBoundedParkPhase() {
        final FakeTime time = new FakeTime(0L, 1_000_000L, 100_000L);
        final PacingDeadlineWaiter waiter = time.waiter(1_000_000L);

        assertTrue(waiter.awaitUntil(20_000_000L));
        assertTrue(time.parkRequests.size() > 0);
        assertTrue(time.parkRequests.stream().allMatch(value -> value <= 1_000_000L));
        assertTrue(time.spinCalls > 0);
        assertTrue(time.now >= 20_000_000L);
    }

    @Test
    void nearDeadlineUsesPrecisionPhaseWithoutParking() {
        final FakeTime time = new FakeTime(4_000_000L, 1_000_000L, 100_000L);
        final PacingDeadlineWaiter waiter = time.waiter(1_000_000L);

        assertTrue(waiter.awaitUntil(5_000_000L));
        assertEquals(0, time.parkRequests.size());
        assertTrue(time.spinCalls > 0);
        assertTrue(time.now >= 5_000_000L);
    }

    @Test
    void earlyParkWakeReusesTheSameAbsoluteDeadline() {
        final FakeTime time = new FakeTime(0L, 400_000L, 100_000L);
        final PacingDeadlineWaiter waiter = time.waiter(1_000_000L);

        assertTrue(waiter.awaitUntil(20_000_000L));
        assertTrue(time.parkRequests.size() > 1);
        assertTrue(time.now >= 20_000_000L);
        assertTrue(time.now < 21_000_000L);
    }

    @Test
    void slightWakeOvershootCanStillLandInsideTheActiveSlot() {
        final FakeTime time = new FakeTime(0L, 5_000_000L, 6_000_000L);
        final PacingDeadlineWaiter waiter = time.waiter(3_000_000L);

        assertTrue(waiter.awaitUntil(SLOT_NANOS));
        assertTrue(time.now >= SLOT_NANOS);
        assertTrue(time.now < 2L * SLOT_NANOS);
    }

    @Test
    void overshootPastSlotEndIsNotHiddenOrRebased() {
        final FakeTime time = new FakeTime(0L, 5_000_000L, 12_000_000L);
        final PacingDeadlineWaiter waiter = time.waiter(3_000_000L);

        assertTrue(waiter.awaitUntil(SLOT_NANOS));
        assertTrue(time.now >= 2L * SLOT_NANOS);
    }

    @Test
    void absoluteDeadlinesDoNotAccumulateRelativeDrift() {
        final FakeTime time = new FakeTime(0L, 1_000_000L, 100_000L);
        final PacingDeadlineWaiter waiter = time.waiter(1_000_000L);

        for (int ordinal = 0; ordinal < 8; ordinal++) {
            final long deadline = ordinal * SLOT_NANOS;
            assertTrue(waiter.awaitUntil(deadline));
            assertTrue(time.now >= deadline);
        }
        assertTrue(time.now < 8L * SLOT_NANOS + 1_000_000L);
    }

    @Test
    void interruptedWaitReportsInterruption() {
        final FakeTime time = new FakeTime(0L, 1_000_000L, 100_000L);
        final PacingDeadlineWaiter waiter = time.waiter(1_000_000L);
        Thread.currentThread().interrupt();
        try {
            assertFalse(waiter.awaitUntil(20_000_000L));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void realTimerSmokeReachesADeadline() {
        final PacingDeadlineWaiter waiter = new PacingDeadlineWaiter(1_000_000L);
        final long deadline = System.nanoTime() + 1_000_000L;

        assertTrue(waiter.awaitUntil(deadline));
    }

    private static final class FakeTime {

        private long now;
        private final long parkAdvanceNanos;
        private final long spinAdvanceNanos;
        private final List<Long> parkRequests = new ArrayList<>();
        private int spinCalls;

        private FakeTime(
                final long initialNanos,
                final long parkAdvance,
                final long spinAdvance) {
            now = initialNanos;
            parkAdvanceNanos = parkAdvance;
            spinAdvanceNanos = spinAdvance;
        }

        private PacingDeadlineWaiter waiter(final long precisionWindowNanos) {
            return new PacingDeadlineWaiter(
                    () -> now,
                    this::park,
                    this::spin,
                    precisionWindowNanos);
        }

        private void park(final long requestedNanos) {
            parkRequests.add(requestedNanos);
            now += Math.min(requestedNanos, parkAdvanceNanos);
        }

        private void spin() {
            spinCalls++;
            now += spinAdvanceNanos;
        }
    }
}
