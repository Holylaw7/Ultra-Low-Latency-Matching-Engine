package com.ultralatency.matching.network.protocol;

import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Side;
import java.util.Objects;

/**
 * Project-owned limit-order request decoded from protocol v1.
 *
 * @param requestId transport correlation identifier
 * @param orderId requested order identifier
 * @param side order side
 * @param price limit price
 * @param quantity order quantity
 */
public record SubmitLimitRequest(
        ClientRequestId requestId,
        OrderId orderId,
        Side side,
        Price price,
        Quantity quantity) implements ProtocolRequest {

    /**
     * Validates required request values.
     */
    public SubmitLimitRequest {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(quantity, "quantity");
    }
}
