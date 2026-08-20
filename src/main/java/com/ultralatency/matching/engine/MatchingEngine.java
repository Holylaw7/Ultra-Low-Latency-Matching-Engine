package com.ultralatency.matching.engine;

import com.ultralatency.matching.domain.Order;
import com.ultralatency.matching.orderbook.MatchFragment;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.orderbook.OrderBook;
import java.util.List;
import java.util.Objects;

/**
 * Synchronous, caller-owned orchestration boundary for one order book.
 *
 * <p>The engine owns command sequencing and delegates structural mutation to its private frozen
 * order book. Match-result translation is completed separately from structural application.</p>
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
     * Applies one command synchronously.
     *
     * @param command immutable command from the upstream owner
     * @return immutable command result
     */
    public EngineResult process(final EngineCommand command) {
        Objects.requireNonNull(command, "command");
        if (failed) {
            throw new IllegalStateException("Matching engine is failed");
        }
        validateExactNextSequence(command.sequence());
        if (command instanceof SubmitLimitCommand submit) {
            return processSubmit(submit);
        }
        if (command instanceof CancelOrderCommand cancel) {
            return processCancel(cancel);
        }
        throw new IllegalArgumentException("Unsupported engine command: " + command.getClass());
    }

    private EngineResult processSubmit(final SubmitLimitCommand command) {
        if (orderBook.activeOrder(command.orderId()).isPresent()) {
            throw new IllegalArgumentException("Order identifier is already active");
        }
        final Order incoming = Order.limit(
                command.orderId(),
                command.side(),
                command.price(),
                command.quantity(),
                command.sequence());
        try {
            final List<MatchFragment> fragments = orderBook.matchLimit(incoming);
            if (!fragments.isEmpty()) {
                failed = true;
                throw new UnsupportedOperationException("Match-result translation is not implemented yet");
            }
            return complete(command.sequence(), CommandOutcome.ACCEPTED);
        } catch (final RuntimeException exception) {
            failed = true;
            throw exception;
        }
    }

    private EngineResult processCancel(final CancelOrderCommand command) {
        try {
            final CommandOutcome outcome = orderBook.cancel(command.orderId())
                    ? CommandOutcome.CANCELED
                    : CommandOutcome.NOT_FOUND;
            return complete(command.sequence(), outcome);
        } catch (final RuntimeException exception) {
            failed = true;
            throw exception;
        }
    }

    private EngineResult complete(final Sequence sequence, final CommandOutcome outcome) {
        final EngineResult result = new EngineResult(sequence, outcome, List.of());
        lastAppliedCommandSequence = sequence.value();
        return result;
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
