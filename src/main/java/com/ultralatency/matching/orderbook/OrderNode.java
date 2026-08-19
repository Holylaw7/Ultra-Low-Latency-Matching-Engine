package com.ultralatency.matching.orderbook;

import java.util.Objects;

import com.ultralatency.matching.domain.Order;

/**
 * Intrusive queue node for one live resting order.
 */
final class OrderNode {

    private final Order order;
    private PriceLevel owner;
    private OrderNode previous;
    private OrderNode next;

    OrderNode(final Order order) {
        this.order = Objects.requireNonNull(order, "order");
    }

    Order order() {
        return order;
    }

    PriceLevel owner() {
        return owner;
    }

    OrderNode previous() {
        return previous;
    }

    OrderNode next() {
        return next;
    }

    boolean isLinked() {
        return owner != null;
    }

    boolean belongsTo(final PriceLevel priceLevel) {
        return priceLevel != null && owner == priceLevel;
    }

    void attach(final PriceLevel priceLevel) {
        Objects.requireNonNull(priceLevel, "priceLevel");
        if (isLinked() || previous != null || next != null) {
            throw new IllegalStateException("OrderNode is already linked");
        }
        owner = priceLevel;
    }

    void detach(final PriceLevel priceLevel) {
        Objects.requireNonNull(priceLevel, "priceLevel");
        if (!belongsTo(priceLevel)) {
            throw new IllegalArgumentException("OrderNode belongs to another price level");
        }
        owner = null;
        previous = null;
        next = null;
    }

    void previous(final OrderNode node) {
        previous = node;
    }

    void next(final OrderNode node) {
        next = node;
    }
}
