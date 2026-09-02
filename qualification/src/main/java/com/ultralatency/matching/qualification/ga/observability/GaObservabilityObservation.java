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
        return managementEvidence.stream()
                .allMatch(item -> item.completeResponseBoundary() && item.hasRequiredFields())
                && managementEvidence.stream().anyMatch(
                item -> item.kind() == GaManagementEvidence.Kind.STATUS)
                && managementEvidence.stream().anyMatch(
                item -> item.kind() == GaManagementEvidence.Kind.METRICS);
    }

    /** Returns whether all management counters are monotonic across the observed sequence. */
    public boolean managementCountersNonRegressing() {
        GaManagementEvidence previous = null;
        for (GaManagementEvidence current : managementEvidence) {
            if (previous != null && !current.nonRegressingCountersFrom(previous)) {
                return false;
            }
            previous = current;
        }
        return !managementEvidence.isEmpty();
    }

    /** Returns whether observed STATUS/METRICS states follow the runtime lifecycle graph. */
    public boolean managementStateTransitionsValid() {
        GaManagementEvidence previous = null;
        for (GaManagementEvidence current : managementEvidence) {
            if (!current.hasValidStateSemantics()) {
                return false;
            }
            if (previous != null && !current.stateTransitionValidFrom(previous)) {
                return false;
            }
            previous = current;
        }
        return !managementEvidence.isEmpty();
    }

    /** Returns whether management schema, counters and lifecycle semantics are all valid. */
    public boolean managementSemanticsComplete() {
        return managementComplete() && managementCountersNonRegressing()
                && managementStateTransitionsValid();
    }
}
