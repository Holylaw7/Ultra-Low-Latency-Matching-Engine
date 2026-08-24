package com.ultralatency.matching.qualification;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Immutable aggregate result of one TASK-038 restart/termination campaign. */
public record QualificationRestartCampaignResult(
        boolean success,
        QualificationResult result,
        List<QualificationRestartCycle> cycles,
        Path artifactDirectory,
        String summarySha256) {

    /** Creates a validated campaign result. */
    public QualificationRestartCampaignResult {
        Objects.requireNonNull(result, "result");
        cycles = List.copyOf(Objects.requireNonNull(cycles, "cycles"));
        if (cycles.isEmpty()) {
            throw new IllegalArgumentException("campaign must contain cycles");
        }
        Objects.requireNonNull(artifactDirectory, "artifactDirectory");
        Objects.requireNonNull(summarySha256, "summarySha256");
        if (!summarySha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("summarySha256 must be lowercase SHA-256");
        }
        if (success != result.success()) {
            throw new IllegalArgumentException("campaign and result success must converge");
        }
    }

    /** Returns the number of graceful process cycles. */
    public long gracefulRestartCycles() {
        return cycles.stream()
                .filter(cycle -> cycle.mode() == QualificationRestartMode.GRACEFUL_RESTART)
                .count();
    }

    /** Returns the number of forced process cycles. */
    public long forcedTerminationCycles() {
        return cycles.stream()
                .filter(cycle -> cycle.mode() == QualificationRestartMode.FORCED_TERMINATION)
                .count();
    }
}
