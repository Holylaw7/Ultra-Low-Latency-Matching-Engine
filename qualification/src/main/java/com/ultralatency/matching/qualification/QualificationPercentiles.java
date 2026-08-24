package com.ultralatency.matching.qualification;

import java.util.Arrays;
import java.util.Objects;

/** Deterministic nearest-rank percentiles for qualification evidence. */
public final class QualificationPercentiles {

    private QualificationPercentiles() {
    }

    /** Immutable distribution summary retaining the raw sample count and tail values. */
    public record Summary(
            int count,
            long p50Nanos,
            long p95Nanos,
            long p99Nanos,
            long p999Nanos,
            long maxNanos) {

        public Summary {
            if (count < 0 || p50Nanos < 0 || p95Nanos < 0 || p99Nanos < 0
                    || p999Nanos < 0 || maxNanos < 0) {
                throw new IllegalArgumentException("invalid percentile summary");
            }
        }

        /** Appends stable key/value fields to an evidence document. */
        public void appendTo(final StringBuilder output, final String prefix) {
            Objects.requireNonNull(output, "output");
            Objects.requireNonNull(prefix, "prefix");
            output.append(prefix).append(".count=").append(count).append('\n')
                    .append(prefix).append(".p50Nanos=").append(p50Nanos).append('\n')
                    .append(prefix).append(".p95Nanos=").append(p95Nanos).append('\n')
                    .append(prefix).append(".p99Nanos=").append(p99Nanos).append('\n')
                    .append(prefix).append(".p999Nanos=").append(p999Nanos).append('\n')
                    .append(prefix).append(".maxNanos=").append(maxNanos).append('\n');
        }
    }

    /** Computes nearest-rank P50/P95/P99/P99.9/max from nanosecond samples. */
    public static Summary summarize(final long[] samples) {
        Objects.requireNonNull(samples, "samples");
        if (samples.length == 0) {
            return new Summary(0, 0, 0, 0, 0, 0);
        }
        final long[] sorted = samples.clone();
        for (final long sample : sorted) {
            if (sample < 0) {
                throw new IllegalArgumentException("latency samples must be non-negative");
            }
        }
        Arrays.sort(sorted);
        return new Summary(
                sorted.length,
                nearestRank(sorted, 0.50),
                nearestRank(sorted, 0.95),
                nearestRank(sorted, 0.99),
                nearestRank(sorted, 0.999),
                sorted[sorted.length - 1]);
    }

    private static long nearestRank(final long[] sorted, final double percentile) {
        final int rank = Math.max(1, (int) Math.ceil(percentile * sorted.length));
        return sorted[rank - 1];
    }
}
