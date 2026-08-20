package com.ultralatency.matching.domain;

/**
 * Positive sequence assigned by the matching engine to an emitted match result.
 *
 * <p>Allocation ownership belongs exclusively to the matching engine. This value object
 * validates, orders, and provides checked arithmetic for an already assigned value.</p>
 *
 * @param value output event sequence value
 */
public record EventSequence(long value) implements Comparable<EventSequence> {

    /**
     * Creates a validated event sequence.
     */
    public EventSequence {
        if (value <= 0) {
            throw new IllegalArgumentException("Event sequence must be positive");
        }
    }

    /**
     * Creates an event sequence from its primitive value.
     *
     * @param value output event sequence value
     * @return validated event sequence
     */
    public static EventSequence of(final long value) {
        return new EventSequence(value);
    }

    /**
     * Returns the next event sequence using checked arithmetic.
     *
     * @return next event sequence
     * @throws ArithmeticException when the sequence is exhausted
     */
    public EventSequence next() {
        if (value == Long.MAX_VALUE) {
            throw new ArithmeticException("Event sequence overflow");
        }
        return new EventSequence(value + 1);
    }

    @Override
    public int compareTo(final EventSequence other) {
        return Long.compare(value, other.value);
    }
}
