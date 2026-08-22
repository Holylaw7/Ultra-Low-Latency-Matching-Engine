package com.ultralatency.matching.integration.durable;

import com.ultralatency.matching.domain.Sequence;
import java.util.Objects;

/**
 * Coordinator-owned logical command sequence for the live durable boundary.
 *
 * <p>This wrapper keeps the coordinator's candidate sequence distinct from request IDs, ring
 * positions, output event sequences, trade IDs and physical WAL positions.  It converts to the
 * frozen domain {@link Sequence} only when an engine command is constructed.</p>
 *
 * @param value positive logical command sequence
 */
public record DurableCommandSequence(long value)
        implements Comparable<DurableCommandSequence> {

    /**
     * Validates a logical command sequence.
     */
    public DurableCommandSequence {
        if (value <= 0) {
            throw new IllegalArgumentException("Durable command sequence must be positive");
        }
    }

    /**
     * Creates a durable sequence from a primitive value.
     *
     * @param value positive sequence value
     * @return validated durable sequence
     */
    public static DurableCommandSequence of(final long value) {
        return new DurableCommandSequence(value);
    }

    /**
     * Creates a durable sequence from the frozen engine sequence at an adapter boundary.
     *
     * @param sequence validated domain sequence
     */
    public DurableCommandSequence(final Sequence sequence) {
        this(Objects.requireNonNull(sequence, "sequence").value());
    }

    /**
     * Returns the next candidate sequence.
     *
     * @return next sequence
     * @throws ArithmeticException when the sequence is exhausted
     */
    public DurableCommandSequence next() {
        if (value == Long.MAX_VALUE) {
            throw new ArithmeticException("Durable command sequence overflow");
        }
        return new DurableCommandSequence(value + 1);
    }

    /**
     * Converts this coordinator value to the frozen engine sequence type.
     *
     * @return domain command sequence with the same value
     */
    public Sequence toSequence() {
        return new Sequence(value);
    }

    /**
     * Alias used by adapters that describe the conversion as a domain sequence.
     *
     * @return domain command sequence with the same value
     */
    public Sequence sequence() {
        return toSequence();
    }

    @Override
    public int compareTo(final DurableCommandSequence other) {
        return Long.compare(value, Objects.requireNonNull(other, "other").value);
    }
}
