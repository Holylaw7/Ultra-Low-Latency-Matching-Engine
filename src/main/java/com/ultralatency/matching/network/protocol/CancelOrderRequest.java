package com.ultralatency.matching.network.protocol;

import com.ultralatency.matching.domain.OrderId;
import java.util.Objects;

/**
 * Project-owned cancel-order request decoded from protocol v1.
 *
 * @param requestId transport correlation identifier
 * @param orderId order identifier to cancel
 */
public record CancelOrderRequest(ClientRequestId requestId, OrderId orderId)
        implements ProtocolRequest {

    /**
     * Validates required request values.
     */
    public CancelOrderRequest {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(orderId, "orderId");
    }
}
