package com.ultralatency.matching.qualification.ga.observability;

import com.ultralatency.matching.qualification.ga.soak.GaSoakMatrix;
import com.ultralatency.matching.qualification.ga.soak.GaSoakResourceSample;
import java.util.List;
import java.util.Objects;

/** Immutable G8 public observation set for one physical execution. */
public record GaObservabilityObservation(
        String physicalExecutionId,
        GaSoakMatrix.Stage stage,
        List<GaSoakResourceSample> resourceSamples,
        GaGcEvidence gcEvidence,
        GaJfrEvidence jfrEvidence,
        List<GaManagementEvidence> managementEvidence,
        boolean clientEvidenceComplete,
        boolean terminalEvidenceComplete,
        int exitCode,
        boolean externallyInterrupted,
        boolean undeclaredTransientFiles,
        boolean transientFilesCleanAfterShutdown,
        boolean configurationBound,
        boolean candidateBound,
        boolean controllerBound) {

    /** Validates immutable G8 observation data. */
    public GaObservabilityObservation {
        if (physicalExecutionId == null || physicalExecutionId.isBlank() || stage == null
                || resourceSamples == null || gcEvidence == null || jfrEvidence == null
                || managementEvidence == null || exitCode < 0) {
            throw new IllegalArgumentException("observability observation is outside its bounds");
        }
        Objects.requireNonNull(stage, "stage");
        resourceSamples = List.copyOf(resourceSamples);
        managementEvidence = List.copyOf(managementEvidence);
        for (GaSoakResourceSample sample : resourceSamples) {
            Objects.requireNonNull(sample, "resource sample");
        }
        for (GaManagementEvidence sample : managementEvidence) {
            Objects.requireNonNull(sample, "management evidence");
        }
    }

    /** Returns whether every management response is a complete known schema. */
    public boolean managementComplete() {
        return !managementEvidence.isEmpty() && managementEvidence.stream()
                .allMatch(item -> item.completeResponseBoundary() && item.hasRequiredFields());
    }
}
