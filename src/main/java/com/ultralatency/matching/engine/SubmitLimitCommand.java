package com.ultralatency.matching.engine;

import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.domain.Side;
import java.util.Objects;

/**
 * Immutable intent to submit one limit order.
 *
 * <p>This command deliberately contains value types rather than a mutable {@code Order}. The
 * matching engine will later construct the internal order state after command validation.</p>
 *
 * @param sequence upstream command sequence
 * @param orderId requested order identifier
 * @param side requested order side
 * @param price requested limit price
 * @param quantity requested order quantity
 */
public record SubmitLimitCommand(
        Sequence sequence,
        OrderId orderId,
        Side side,
        Price price,
        Quantity quantity) implements EngineCommand {

    /**
     * Validates required command values.
     */
    public SubmitLimitCommand {
        Objects.requireNonNull(sequence, "sequence");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(quantity, "quantity");
    }
}
