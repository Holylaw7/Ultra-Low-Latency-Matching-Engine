package com.ultralatency.matching.domain;

/**
 * Stable positive identifier for a trade.
 *
 * @param value identifier value
 */
public record TradeId(long value) implements Comparable<TradeId> {

    /**
     * Creates a trade identifier.
     */
    public TradeId {
        if (value <= 0) {
            throw new IllegalArgumentException("TradeId must be positive");
        }
    }

    /**
     * Creates a trade identifier from its primitive value.
     *
     * @param value identifier value
     * @return validated trade identifier
     */
    public static TradeId of(final long value) {
        return new TradeId(value);
    }

    @Override
    public int compareTo(final TradeId other) {
        return Long.compare(value, other.value);
    }
}
