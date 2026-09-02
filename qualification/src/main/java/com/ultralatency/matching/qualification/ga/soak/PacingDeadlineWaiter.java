package com.ultralatency.matching.qualification.ga.soak;

import java.util.Objects;
import java.util.concurrent.locks.LockSupport;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/**
 * Qualification-only hybrid waiter for an absolute monotonic deadline.
 *
 * <p>The waiter deliberately leaves a bounded precision phase before the deadline.  A park is
 * never allowed to consume that phase, and the park quantum is bounded by the same configured
 * precision window.  This keeps the policy independent of wall-clock time and avoids treating a
 * normal park overshoot as a new deadline.</p>
 */
public final class PacingDeadlineWaiter {

    /** Candidate precision windows authorized by the pacing calibration gate. */
    public static final long[] CALIBRATION_WINDOWS_NANOS = {
        500_000L,
        1_000_000L,
        2_000_000L,
        3_000_000L
    };

    /** Selected default until the bounded local calibration chooses a smaller passing window. */
    public static final long DEFAULT_PRECISION_WINDOW_NANOS = 3_000_000L;

    private final LongSupplier clock;
    private final LongConsumer parker;
    private final Runnable precisionWait;
    private final long precisionWindowNanos;

    /** Creates the system waiter used by the Quick scheduler. */
    public PacingDeadlineWaiter() {
        this(System::nanoTime, LockSupport::parkNanos, Thread::onSpinWait,
                DEFAULT_PRECISION_WINDOW_NANOS);
    }

    /** Creates a system waiter with an explicitly selected calibration window. */
    public PacingDeadlineWaiter(final long precisionWindowNanos) {
        this(System::nanoTime, LockSupport::parkNanos, Thread::onSpinWait,
                precisionWindowNanos);
    }

    PacingDeadlineWaiter(
            final LongSupplier clock,
            final LongConsumer parker,
            final Runnable precisionWait,
            final long precisionWindowNanos) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.parker = Objects.requireNonNull(parker, "parker");
        this.precisionWait = Objects.requireNonNull(precisionWait, "precisionWait");
        if (precisionWindowNanos <= 0L || precisionWindowNanos > 5_000_000L) {
            throw new IllegalArgumentException("precision window must be in (0, 5ms]");
        }
        this.precisionWindowNanos = precisionWindowNanos;
    }

    /** @return the selected bounded precision window in nanoseconds */
    public long precisionWindowNanos() {
        return precisionWindowNanos;
    }

    /**
     * Waits until the supplied absolute monotonic deadline.
     *
     * @return {@code true} when the deadline was reached, or {@code false} when interrupted
     */
    public boolean awaitUntil(final long deadlineNanos) {
        final long guardNanos = Math.multiplyExact(precisionWindowNanos, 2L);
        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                return false;
            }
            final long remaining = deadlineNanos - clock.getAsLong();
            if (remaining <= 0L) {
                return true;
            }
            if (remaining > guardNanos) {
                final long parkNanos = Math.min(remaining - guardNanos, precisionWindowNanos);
                parker.accept(parkNanos);
            } else {
                precisionWait.run();
            }
        }
    }
}
