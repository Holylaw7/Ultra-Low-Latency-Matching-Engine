package com.ultralatency.matching.orderbook;

import java.util.Objects;

import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;

/**
 * Immutable structural result for one maker/taker fill.
 *
 * @param makerOrderId resting order identifier
 * @param takerOrderId incoming order identifier
 * @param price resting maker price
 * @param quantity executed quantity
 * @param makerRemainingQuantityUnits maker remainder after this fill
 * @param takerRemainingQuantityUnits taker remainder after this fill
 */
public record MatchFragment(
        OrderId makerOrderId,
        OrderId takerOrderId,
        Price price,
        Quantity quantity,
        long makerRemainingQuantityUnits,
        long takerRemainingQuantityUnits) {

    /**
     * Creates a validated immutable match fragment.
     */
    public MatchFragment {
        Objects.requireNonNull(makerOrderId, "makerOrderId");
        Objects.requireNonNull(takerOrderId, "takerOrderId");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(quantity, "quantity");
        if (makerRemainingQuantityUnits < 0) {
            throw new IllegalArgumentException(
                    "Maker remaining quantity cannot be negative");
        }
        if (takerRemainingQuantityUnits < 0) {
            throw new IllegalArgumentException(
                    "Taker remaining quantity cannot be negative");
        }
    }
}
