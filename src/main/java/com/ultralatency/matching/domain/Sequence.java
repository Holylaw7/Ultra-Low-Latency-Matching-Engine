package com.ultralatency.matching.domain;

/**
 * Positive logical sequence assigned to an input event.
 *
 * @param value logical sequence value
 */
public record Sequence(long value) implements Comparable<Sequence> {

    /**
     * Creates a validated sequence.
     */
    public Sequence {
        if (value <= 0) {
            throw new IllegalArgumentException("Sequence must be positive");
        }
    }

    /**
     * Creates a sequence from its primitive value.
     *
     * @param value logical sequence value
     * @return validated sequence
     */
    public static Sequence of(final long value) {
        return new Sequence(value);
    }

    /**
     * Returns the next logical sequence.
     *
     * @return next sequence
     * @throws ArithmeticException when the sequence is exhausted
     */
    public Sequence next() {
        if (value == Long.MAX_VALUE) {
            throw new ArithmeticException("Sequence overflow");
        }
        return new Sequence(value + 1);
    }

    @Override
    public int compareTo(final Sequence other) {
        return Long.compare(value, other.value);
    }
}
