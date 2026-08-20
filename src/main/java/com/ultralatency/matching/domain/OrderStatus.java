package com.ultralatency.matching.domain;

/**
 * Lifecycle states of an order.
 */
public enum OrderStatus {
    NEW,
    PARTIALLY_FILLED,
    FILLED,
    CANCELED;

    /**
     * Returns whether the order can accept another execution or cancellation.
     *
     * @return true for active order states
     */
    public boolean isActive() {
        return this == NEW || this == PARTIALLY_FILLED;
    }

    /**
     * Returns whether the order can no longer be filled.
     *
     * @return true for terminal order states
     */
    public boolean isTerminal() {
        return !isActive();
    }
}
