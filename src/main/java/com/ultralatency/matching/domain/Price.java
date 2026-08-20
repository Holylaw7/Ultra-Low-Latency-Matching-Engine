package com.ultralatency.matching.domain;

/**
 * Fixed-scale integer price expressed in ticks.
 *
 * @param ticks encoded price in the configured minimum tick unit
 */
public record Price(long ticks) implements Comparable<Price> {

    /**
     * Creates a validated price.
     */
    public Price {
        if (ticks <= 0) {
            throw new IllegalArgumentException("Price ticks must be positive");
        }
    }

    /**
     * Creates a price from its encoded tick value.
     *
     * @param ticks encoded price
     * @return validated price
     */
    public static Price of(final long ticks) {
        return new Price(ticks);
    }

    @Override
    public int compareTo(final Price other) {
        return Long.compare(ticks, other.ticks);
    }
}
