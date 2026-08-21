package com.ultralatency.matching.engine;

import com.ultralatency.matching.domain.EventSequence;
import com.ultralatency.matching.domain.Execution;
import com.ultralatency.matching.domain.Trade;
import java.util.Objects;

/**
 * One immutable aggregate emitted for a single match.
 *
 * <p>The aggregate owns one output event sequence and one trade with its maker and taker
 * executions. It contains no publication or persistence behavior.</p>
 *
 * @param eventSequence output sequence allocated by the matching engine
 * @param trade matched trade
 * @param makerExecution execution against the resting maker order
 * @param takerExecution execution against the incoming taker order
 */
public record MatchResult(
        EventSequence eventSequence,
        Trade trade,
        Execution makerExecution,
        Execution takerExecution) {

    /**
     * Validates aggregate identity and execution mapping invariants.
     */
    public MatchResult {
        Objects.requireNonNull(eventSequence, "eventSequence");
        Objects.requireNonNull(trade, "trade");
        Objects.requireNonNull(makerExecution, "makerExecution");
        Objects.requireNonNull(takerExecution, "takerExecution");
        if (!eventSequence.equals(trade.eventSequence())) {
            throw new IllegalArgumentException("Match result and trade event sequences must match");
        }
        validateExecution(makerExecution, trade, trade.makerOrderId(), "maker");
        validateExecution(takerExecution, trade, trade.takerOrderId(), "taker");
    }

    private static void validateExecution(
            final Execution execution,
            final Trade trade,
            final com.ultralatency.matching.domain.OrderId expectedOrderId,
            final String role) {
        if (!execution.tradeId().equals(trade.tradeId())) {
            throw new IllegalArgumentException(role + " execution trade identifier must match");
        }
        if (!execution.orderId().equals(expectedOrderId)) {
            throw new IllegalArgumentException(role + " execution order identifier must match");
        }
        if (!execution.price().equals(trade.price())) {
            throw new IllegalArgumentException(role + " execution price must match");
        }
        if (!execution.quantity().equals(trade.quantity())) {
            throw new IllegalArgumentException(role + " execution quantity must match");
        }
    }
}
