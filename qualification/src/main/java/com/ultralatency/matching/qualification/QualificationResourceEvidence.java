package com.ultralatency.matching.qualification;

import java.util.List;
import java.util.Objects;

/** Immutable resource-lifecycle and bounded heap-guard evidence. */
public record QualificationResourceEvidence(
        List<QualificationResourceSample> samples,
        List<Long> naturalPostGcHeapBytes,
        long baselineThreadCount,
        long finalThreadCount,
        List<String> baselineRuntimeThreads,
        List<String> finalRuntimeThreads,
        boolean threadBaselineRestored,
        boolean heapGuardAssessed,
        boolean heapGuardPassed) {

    /** Validates immutable resource evidence. */
    public QualificationResourceEvidence {
        samples = List.copyOf(Objects.requireNonNull(samples, "samples"));
        naturalPostGcHeapBytes = List.copyOf(
                Objects.requireNonNull(naturalPostGcHeapBytes, "naturalPostGcHeapBytes"));
        baselineRuntimeThreads = List.copyOf(
                Objects.requireNonNull(baselineRuntimeThreads, "baselineRuntimeThreads"));
        finalRuntimeThreads = List.copyOf(
                Objects.requireNonNull(finalRuntimeThreads, "finalRuntimeThreads"));
        if (baselineThreadCount < 0 || finalThreadCount < 0) {
            throw new IllegalArgumentException("thread counts must be non-negative");
        }
        if (!heapGuardAssessed && heapGuardPassed) {
            throw new IllegalArgumentException("unassessed heap guard cannot pass");
        }
    }
}
