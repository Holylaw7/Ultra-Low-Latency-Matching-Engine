package com.ultralatency.matching.qualification;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/** One independently evaluated Full Qualification run in a campaign. */
public record QualificationCampaignRun(
        String runId,
        QualificationFullConfiguration configuration,
        Duration elapsed,
        long acceptedCommands,
        boolean listenerRebound,
        boolean recoveryLeaseReacquired,
        boolean inventoryStable,
        QualificationResourceEvidence resourceEvidence,
        String baselineTag,
        Map<String, String> environment) {

    /** Validates immutable campaign input. */
    public QualificationCampaignRun {
        requireText(runId, "runId");
        Objects.requireNonNull(configuration, "configuration");
        if (configuration.lane() != QualificationLane.FULL) {
            throw new IllegalArgumentException("campaign runs must use the FULL lane");
        }
        Objects.requireNonNull(elapsed, "elapsed");
        if (elapsed.isNegative()) {
            throw new IllegalArgumentException("elapsed must not be negative");
        }
        if (acceptedCommands < 0) {
            throw new IllegalArgumentException("acceptedCommands must not be negative");
        }
        Objects.requireNonNull(resourceEvidence, "resourceEvidence");
        requireText(baselineTag, "baselineTag");
        environment = Map.copyOf(Objects.requireNonNull(environment, "environment"));
    }

    /** Adapts one completed runner result without changing its raw evidence. */
    public static QualificationCampaignRun from(
            final QualificationFullConfiguration configuration,
            final QualificationFullRun run) {
        Objects.requireNonNull(run, "run");
        final QualificationManifest manifest = run.qualificationRun().manifest();
        return new QualificationCampaignRun(
                manifest.runId(),
                configuration,
                run.elapsed(),
                run.qualificationRun().result().acceptedCommands(),
                run.listenerRebound(),
                run.recoveryLeaseReacquired(),
                run.inventoryStable(),
                run.resourceEvidence(),
                manifest.baselineTag(),
                manifest.environment());
    }

    /** Adapts a run after recalculating its resource evidence from raw samples. */
    public static QualificationCampaignRun from(
            final QualificationFullConfiguration configuration,
            final QualificationFullRun run,
            final QualificationResourceEvidence recalculatedEvidence) {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(recalculatedEvidence, "recalculatedEvidence");
        final QualificationManifest manifest = run.qualificationRun().manifest();
        return new QualificationCampaignRun(
                manifest.runId(),
                configuration,
                run.elapsed(),
                run.qualificationRun().result().acceptedCommands(),
                run.listenerRebound(),
                run.recoveryLeaseReacquired(),
                run.inventoryStable(),
                recalculatedEvidence,
                manifest.baselineTag(),
                manifest.environment());
    }

    private static void requireText(final String value, final String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
