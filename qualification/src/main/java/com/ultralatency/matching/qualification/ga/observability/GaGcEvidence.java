package com.ultralatency.matching.qualification.ga.observability;

import com.ultralatency.matching.qualification.ga.soak.GaNaturalGcSample;
import com.ultralatency.matching.qualification.ga.soak.GaSoakMatrix.Stage;
import java.util.List;
import java.util.Objects;

/** Immutable authoritative JFR GC evidence for one physical run. */
public record GaGcEvidence(
        List<GaNaturalGcSample> samples,
        boolean parsed,
        boolean authoritativeJfr,
        boolean identityBound,
        String failureCode) {

    /** Validates one GC evidence container. */
    public GaGcEvidence {
        samples = List.copyOf(Objects.requireNonNull(samples, "samples"));
        if (failureCode == null || failureCode.isBlank()) {
            throw new IllegalArgumentException("GC failure code must not be blank");
        }
        for (GaNaturalGcSample sample : samples) {
            Objects.requireNonNull(sample, "GC sample");
        }
    }

    /** Returns whether this source can be used by the formal natural-GC guard. */
    public boolean applicable() {
        return parsed && authoritativeJfr && identityBound;
    }

    /** Returns whether all samples belong to one physical execution and stage. */
    public boolean belongsTo(final String physicalExecutionId, final Stage stage) {
        Objects.requireNonNull(physicalExecutionId, "physicalExecutionId");
        Objects.requireNonNull(stage, "stage");
        return samples.stream().allMatch(sample ->
                physicalExecutionId.equals(sample.physicalExecutionId()) && stage == sample.stage());
    }

    /** Returns a valid empty Quick fixture. */
    public static GaGcEvidence quick(final String failureCode) {
        return new GaGcEvidence(List.of(), true, true, true, failureCode);
    }
}
