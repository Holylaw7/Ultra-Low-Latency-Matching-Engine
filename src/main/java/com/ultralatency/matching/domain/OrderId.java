package com.ultralatency.matching.domain;

/**
 * Stable positive identifier for an order.
 *
 * @param value identifier value
 */
public record OrderId(long value) implements Comparable<OrderId> {

    /**
     * Creates an order identifier.
     */
    public OrderId {
        if (value <= 0) {
            throw new IllegalArgumentException("OrderId must be positive");
        }
    }

    /**
     * Creates an order identifier from its primitive value.
     *
     * @param value identifier value
     * @return validated order identifier
     */
    public static OrderId of(final long value) {
        return new OrderId(value);
    }

    @Override
    public int compareTo(final OrderId other) {
        return Long.compare(value, other.value);
    }
}
