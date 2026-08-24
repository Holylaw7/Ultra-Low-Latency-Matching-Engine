package com.ultralatency.matching.qualification;

import com.ultralatency.matching.engine.CancelOrderCommand;
import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.engine.SubmitLimitCommand;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Reconstructs the bounded public order-state observation from Protocol v1 exchanges.
 *
 * <p>This tracker deliberately consumes only the command sent through the public boundary and
 * the response returned by that boundary. It does not access an engine, order book, coordinator
 * or recovery object. Its purpose is to make the memory-lane active-order bound observable over
 * the same public path used by qualification.</p>
 */
final class QualificationPublicStateTracker {

    private final Map<Long, Long> activeOrderQuantities = new HashMap<>();
    private int maximumActiveOrderCount;
    private boolean finished;

    /** Records one completed public request/response exchange. */
    void accept(final EngineCommand command, final QualificationExchange exchange) {
        if (finished) {
            throw new IllegalStateException("public state tracker is already finished");
        }
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(exchange, "exchange");
        if (command.sequence().value() != exchange.commandSequence()) {
            throw new IllegalArgumentException("public exchange sequence does not match command");
        }
        if (command instanceof SubmitLimitCommand submit) {
            applySubmit(submit, exchange);
        } else if (command instanceof CancelOrderCommand cancel) {
            applyCancel(cancel, exchange);
        } else {
            throw new IllegalArgumentException("unsupported public qualification command");
        }
        maximumActiveOrderCount = Math.max(
                maximumActiveOrderCount, activeOrderQuantities.size());
    }

    /** Finishes the observation and returns bounded state evidence. */
    Summary finish() {
        if (finished) {
            throw new IllegalStateException("public state tracker is already finished");
        }
        finished = true;
        return new Summary(maximumActiveOrderCount, activeOrderQuantities.size());
    }

    private void applySubmit(
            final SubmitLimitCommand submit,
            final QualificationExchange exchange) {
        if (exchange.outcomeCode() != 1) {
            throw new IllegalStateException("submit did not return ACCEPTED");
        }
        if (activeOrderQuantities.containsKey(submit.orderId().value())) {
            throw new IllegalStateException("submit reused an active order id");
        }
        long remaining = submit.quantity().units();
        for (final QualificationMatch match : exchange.matches()) {
            if (match.takerOrderId() != submit.orderId().value()) {
                throw new IllegalStateException("match taker does not match submitted order");
            }
            decrementMaker(match.makerOrderId(), match.quantity());
            remaining -= match.quantity();
            if (remaining < 0) {
                throw new IllegalStateException("public matches exceed submitted quantity");
            }
        }
        if (remaining > 0) {
            activeOrderQuantities.put(submit.orderId().value(), remaining);
        }
    }

    private void applyCancel(
            final CancelOrderCommand cancel,
            final QualificationExchange exchange) {
        if (exchange.outcomeCode() == 2) {
            if (activeOrderQuantities.remove(cancel.orderId().value()) == null) {
                throw new IllegalStateException("canceled order was not publicly active");
            }
        } else if (exchange.outcomeCode() != 3) {
            throw new IllegalStateException("cancel returned unsupported outcome");
        }
    }

    private void decrementMaker(final long orderId, final long quantity) {
        final Long remaining = activeOrderQuantities.get(orderId);
        if (remaining == null || quantity > remaining) {
            throw new IllegalStateException("match references an unknown or overfilled maker");
        }
        if (remaining == quantity) {
            activeOrderQuantities.remove(orderId);
        } else {
            activeOrderQuantities.put(orderId, remaining - quantity);
        }
    }

    /** Immutable public-boundary state summary. */
    record Summary(int maximumActiveOrderCount, int finalActiveOrderCount) {

        Summary {
            if (maximumActiveOrderCount < 0 || finalActiveOrderCount < 0) {
                throw new IllegalArgumentException("public active-order counts must be non-negative");
            }
            if (finalActiveOrderCount > maximumActiveOrderCount) {
                throw new IllegalArgumentException("final count cannot exceed maximum count");
            }
        }

        boolean boundPassed() {
            return maximumActiveOrderCount
                    <= QualificationWorkloadV1.MEMORY_STEADY_STATE_MAX_ACTIVE_ORDERS;
        }
    }
}
