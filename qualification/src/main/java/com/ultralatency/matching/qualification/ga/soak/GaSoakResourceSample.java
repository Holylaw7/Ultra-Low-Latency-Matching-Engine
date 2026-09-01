package com.ultralatency.matching.qualification.ga.soak;

import java.util.Objects;

/** One immutable, run-owned resource sample ordered by a monotonic clock. */
public record GaSoakResourceSample(
        String physicalExecutionId,
        GaSoakMatrix.Stage stage,
        long sequence,
        long monotonicNanos,
        long threadCount,
        long transientFileCount,
        long transientFileBytes,
        long heapUsedBytes,
        Long postGcHeapBytes) {

    /** Validates a resource sample before it reaches a Gate evaluator. */
    public GaSoakResourceSample {
        requireText(physicalExecutionId, "physicalExecutionId");
        Objects.requireNonNull(stage, "stage");
        if (sequence < 0 || monotonicNanos < 0 || threadCount < 0
                || transientFileCount < 0 || transientFileBytes < 0 || heapUsedBytes < 0
                || postGcHeapBytes != null && postGcHeapBytes < 0) {
            throw new IllegalArgumentException("resource sample is outside its bounds");
        }
    }

    /** Convenience constructor for tests which do not exercise post-GC data. */
    public GaSoakResourceSample(
            final String physicalExecutionId,
            final GaSoakMatrix.Stage stage,
            final long sequence,
            final long monotonicNanos,
            final long threadCount,
            final long transientFileCount,
            final long transientFileBytes) {
        this(physicalExecutionId, stage, sequence, monotonicNanos, threadCount,
                transientFileCount, transientFileBytes, 0L, null);
    }

    private static void requireText(final String value, final String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
