package com.ultralatency.matching.integration.durable;

import java.util.Objects;

/**
 * Immutable outcome proving that a durable command was accepted by the live pipeline.
 *
 * <p>It is deliberately a different type from {@link DurableOutcome}; pipeline acceptance is not
 * a second durability action and it is not a response acknowledgement.</p>
 *
 * @param durable preceding durable append outcome
 */
public record LiveAcceptedOutcome(DurableOutcome durable)
        implements DurableCommandOutcome {

    /**
     * Validates the preceding durable milestone.
     */
    public LiveAcceptedOutcome {
        Objects.requireNonNull(durable, "durable");
    }

    @Override
    public DurableOutcomeStage stage() {
        return DurableOutcomeStage.LIVE_ACCEPTED;
    }

    @Override
    public DurableCommandIdentity identity() {
        return durable.identity();
    }
}
