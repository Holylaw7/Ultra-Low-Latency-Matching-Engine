package com.ultralatency.matching.qualification;

import java.time.Instant;

/** One non-invasive process resource observation. */
public record QualificationResourceSample(
        Instant timestamp,
        long liveThreadCount,
        long peakThreadCount,
        long totalGcCollections,
        long totalGcTimeMillis,
        long heapUsedBytes,
        Long naturalPostGcHeapBytes) {

    /** Validates one resource observation. */
    public QualificationResourceSample {
        if (timestamp == null) {
            throw new NullPointerException("timestamp");
        }
        if (liveThreadCount < 0 || peakThreadCount < 0 || totalGcCollections < 0
                || totalGcTimeMillis < 0 || heapUsedBytes < 0
                || (naturalPostGcHeapBytes != null && naturalPostGcHeapBytes < 0)) {
            throw new IllegalArgumentException("resource values must be non-negative");
        }
    }
}
