package com.ultralatency.matching.integration.durable;

import java.util.Objects;

/**
 * Immutable description of the first terminal failure retained by a live durable service.
 *
 * @param stage failure boundary
 * @param cause original failure cause
 */
public record DurableTerminalFailure(DurableFailureStage stage, Throwable cause) {

    /**
     * Validates the terminal failure value.
     */
    public DurableTerminalFailure {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(cause, "cause");
    }
}
