package com.ultralatency.matching.domain;

/**
 * Positive quantity expressed in the configured minimum unit.
 *
 * @param units quantity in the minimum unit
 */
public record Quantity(long units) implements Comparable<Quantity> {

    /**
     * Creates a validated quantity.
     */
    public Quantity {
        if (units <= 0) {
            throw new IllegalArgumentException("Quantity units must be positive");
        }
    }

    /**
     * Creates a quantity from its primitive value.
     *
     * @param units quantity in the minimum unit
     * @return validated quantity
     */
    public static Quantity of(final long units) {
        return new Quantity(units);
    }

    @Override
    public int compareTo(final Quantity other) {
        return Long.compare(units, other.units);
    }
}
