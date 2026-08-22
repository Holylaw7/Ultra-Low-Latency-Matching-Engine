package com.ultralatency.matching.integration.durable;

import java.util.Objects;

/**
 * Signals that the live durable coordinator entered its terminal failure state.
 *
 * <p>The retained {@link DurableTerminalFailure} is exposed so a caller can distinguish an
 * append failure from a durable-then-full integration failure without changing the frozen WAL or
 * pipeline APIs.</p>
 */
public final class DurableTerminalException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    private final transient DurableTerminalFailure failure;

    /**
     * Creates an exception for the first terminal failure.
     *
     * @param failure retained terminal failure
     */
    public DurableTerminalException(final DurableTerminalFailure failure) {
        super(message(Objects.requireNonNull(failure, "failure")), failure.cause());
        this.failure = failure;
    }

    /**
     * Returns the immutable failure descriptor retained by the coordinator.
     *
     * @return first terminal failure
     */
    public DurableTerminalFailure failure() {
        return failure;
    }

    private static String message(final DurableTerminalFailure failure) {
        return "Durable coordinator failed at " + failure.stage();
    }
}
