package com.ultralatency.matching.engine;

import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.orderbook.OrderBook;
import java.util.Objects;

/**
 * Synchronous, caller-owned orchestration boundary for one order book.
 *
 * <p>This initial implementation establishes command lifecycle ownership and exact input
 * sequence validation. Order-book application and output translation are introduced in the next
 * approved implementation steps.</p>
 */
public final class MatchingEngine {

    private final OrderBook orderBook;
    private long lastAppliedCommandSequence;
    private long nextTradeId;
    private long nextEventSequence;
    private boolean failed;

    /**
     * Creates an empty synchronous engine with genesis counters.
     */
    public MatchingEngine() {
        orderBook = new OrderBook();
        lastAppliedCommandSequence = 0;
        nextTradeId = 1;
        nextEventSequence = 1;
        failed = false;
    }

    /**
     * Validates one command before it can be applied by the matching core.
     *
     * @param command immutable command from the upstream owner
     * @return command result after a later application step
     */
    public EngineResult process(final EngineCommand command) {
        Objects.requireNonNull(command, "command");
        if (failed) {
            throw new IllegalStateException("Matching engine is failed");
        }
        validateExactNextSequence(command.sequence());
        throw new UnsupportedOperationException("Order-book application is not implemented yet");
    }

    private void validateExactNextSequence(final Sequence sequence) {
        Objects.requireNonNull(sequence, "sequence");
        final long expectedSequence = Math.addExact(lastAppliedCommandSequence, 1);
        if (sequence.value() != expectedSequence) {
            throw new IllegalArgumentException(
                    "Expected command sequence " + expectedSequence + " but received " + sequence.value());
        }
    }
}
