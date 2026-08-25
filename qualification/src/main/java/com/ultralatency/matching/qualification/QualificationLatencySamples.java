package com.ultralatency.matching.qualification;

import java.util.Arrays;

/** Bounded in-memory latency accumulator for one characterization evidence unit. */
public final class QualificationLatencySamples {

    private long[] values = new long[256];
    private int size;

    /** Adds one non-negative nanosecond observation. */
    public void add(final long nanos) {
        if (nanos < 0) {
            throw new IllegalArgumentException("latency must be non-negative");
        }
        if (size == values.length) {
            values = Arrays.copyOf(values, Math.multiplyExact(values.length, 2));
        }
        values[size++] = nanos;
    }

    /** Returns the current observations as an immutable snapshot array. */
    public long[] toArray() {
        return Arrays.copyOf(values, size);
    }

    /** Returns a deterministic percentile summary. */
    public QualificationPercentiles.Summary summarize() {
        return QualificationPercentiles.summarize(toArray());
    }

    /** @return number of observations accumulated */
    public int size() {
        return size;
    }
}
