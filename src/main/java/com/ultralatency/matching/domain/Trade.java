package com.ultralatency.matching.domain;

import java.util.Objects;

/**
 * One deterministic match between a maker and a taker order.
 *
 * @param tradeId stable trade identifier
 * @param sequence logical sequence at which the trade was emitted
 * @param price execution price
 * @param quantity executed quantity
 * @param makerOrderId resting order identifier
 * @param takerOrderId incoming order identifier
 */
public record Trade(
        TradeId tradeId,
        Sequence sequence,
        Price price,
        Quantity quantity,
        OrderId makerOrderId,
        OrderId takerOrderId) {

    /**
     * Validates the trade invariants.
     */
    public Trade {
        Objects.requireNonNull(tradeId, "tradeId");
        Objects.requireNonNull(sequence, "sequence");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(makerOrderId, "makerOrderId");
        Objects.requireNonNull(takerOrderId, "takerOrderId");
        if (makerOrderId.equals(takerOrderId)) {
            throw new IllegalArgumentException("Maker and taker orders must differ");
        }
    }
}
