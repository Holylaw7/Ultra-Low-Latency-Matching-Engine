package com.ultralatency.matching.engine;

import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Sequence;
import java.util.Objects;

/**
 * Immutable intent to cancel an active order.
 *
 * @param sequence upstream command sequence
 * @param orderId identifier of the order to cancel
 */
public record CancelOrderCommand(Sequence sequence, OrderId orderId) implements EngineCommand {

    /**
     * Validates required command values.
     */
    public CancelOrderCommand {
        Objects.requireNonNull(sequence, "sequence");
        Objects.requireNonNull(orderId, "orderId");
    }
}
