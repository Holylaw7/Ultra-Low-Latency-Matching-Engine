package com.ultralatency.matching.domain;

import java.util.Objects;

/**
 * The execution result for one order within a trade.
 *
 * @param tradeId trade containing this execution
 * @param orderId executed order identifier
 * @param price execution price
 * @param quantity executed quantity
 * @param remainingQuantityUnits order quantity remaining after execution
 */
public record Execution(
        TradeId tradeId,
        OrderId orderId,
        Price price,
        Quantity quantity,
        long remainingQuantityUnits) {

    /**
     * Validates the execution invariants.
     */
    public Execution {
        Objects.requireNonNull(tradeId, "tradeId");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(quantity, "quantity");
        if (remainingQuantityUnits < 0) {
            throw new IllegalArgumentException("Remaining quantity cannot be negative");
        }
    }
}
