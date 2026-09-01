package com.ultralatency.matching.qualification.ga.soak;

import java.util.Objects;

/** One complete natural JFR GC cycle and its After-GC heap summary. */
public record GaNaturalGcSample(
        String physicalExecutionId,
        GaSoakMatrix.Stage stage,
        long sequence,
        long monotonicNanos,
        long gcId,
        long afterGcHeapBytes,
        boolean completeCycle) {

    /** Validates immutable natural-GC evidence. */
    public GaNaturalGcSample {
        if (physicalExecutionId == null || physicalExecutionId.isBlank()
                || stage == null || sequence < 0 || monotonicNanos < 0 || gcId < 0
                || afterGcHeapBytes < 0) {
            throw new IllegalArgumentException("natural GC sample is outside its bounds");
        }
        Objects.requireNonNull(stage, "stage");
    }

    /** Convenience constructor for one complete sample. */
    public GaNaturalGcSample(
            final String physicalExecutionId,
            final GaSoakMatrix.Stage stage,
            final long sequence,
            final long monotonicNanos,
            final long afterGcHeapBytes) {
        this(physicalExecutionId, stage, sequence, monotonicNanos, sequence,
                afterGcHeapBytes, true);
    }
}
