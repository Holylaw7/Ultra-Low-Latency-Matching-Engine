package com.ultralatency.matching.engine;

import com.ultralatency.matching.domain.EventSequence;
import com.ultralatency.matching.domain.Execution;
import com.ultralatency.matching.domain.Order;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.domain.Trade;
import com.ultralatency.matching.domain.TradeId;
import com.ultralatency.matching.orderbook.MatchFragment;
import com.ultralatency.matching.orderbook.OrderBook;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Synchronous, caller-owned orchestration boundary for one order book.
 *
 * <p>The engine owns command sequencing, output identifiers, structural application and immutable
 * result translation. It contains no queue, thread, callback, I/O or publication behavior.</p>
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
        preflightOutputCapacity();
        try {
            final List<MatchFragment> fragments = orderBook.matchLimit(incoming);
            return complete(
                    command.sequence(), CommandOutcome.ACCEPTED, translateFragments(fragments));
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
            return complete(command.sequence(), outcome, List.of());
        } catch (final RuntimeException exception) {
            failed = true;
            throw exception;
        }
    }

    private void preflightOutputCapacity() {
        final long fragmentUpperBound = orderBook.activeOrderCount();
        verifyCounterCapacity(nextTradeId, fragmentUpperBound, "TradeId");
        verifyCounterCapacity(nextEventSequence, fragmentUpperBound, "EventSequence");
    }

    private static void verifyCounterCapacity(
            final long nextValue,
            final long fragmentUpperBound,
            final String counterName) {
        try {
            Math.addExact(nextValue, fragmentUpperBound);
        } catch (final ArithmeticException exception) {
            throw new ArithmeticException(counterName + " capacity is exhausted");
        }
    }

    private List<MatchResult> translateFragments(final List<MatchFragment> fragments) {
        final List<MatchResult> matches = new ArrayList<>(fragments.size());
        for (final MatchFragment fragment : fragments) {
            final TradeId tradeId = new TradeId(nextTradeId);
            final EventSequence eventSequence = new EventSequence(nextEventSequence);
            final Trade trade = new Trade(
                    tradeId,
                    eventSequence,
                    fragment.price(),
                    fragment.quantity(),
                    fragment.makerOrderId(),
                    fragment.takerOrderId());
            final Execution makerExecution = new Execution(
                    tradeId,
                    fragment.makerOrderId(),
                    fragment.price(),
                    fragment.quantity(),
                    fragment.makerRemainingQuantityUnits());
            final Execution takerExecution = new Execution(
                    tradeId,
                    fragment.takerOrderId(),
                    fragment.price(),
                    fragment.quantity(),
                    fragment.takerRemainingQuantityUnits());
            matches.add(new MatchResult(eventSequence, trade, makerExecution, takerExecution));
            nextTradeId = Math.addExact(nextTradeId, 1);
            nextEventSequence = Math.addExact(nextEventSequence, 1);
        }
        return matches;
    }

    private EngineResult complete(
            final Sequence sequence,
            final CommandOutcome outcome,
            final List<MatchResult> matches) {
        final EngineResult result = new EngineResult(sequence, outcome, matches);
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
