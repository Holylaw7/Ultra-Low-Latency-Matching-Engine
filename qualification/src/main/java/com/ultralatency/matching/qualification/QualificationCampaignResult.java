package com.ultralatency.matching.qualification;

import java.util.List;
import java.util.Objects;

/** Immutable result of campaign-level heap-stability evaluation. */
public record QualificationCampaignResult(
        boolean passed,
        int qualifyingRunCount,
        int cumulativeNaturalPostGcSamples,
        List<String> failures) {

    /** Validates immutable campaign evaluation output. */
    public QualificationCampaignResult {
        if (qualifyingRunCount < 0 || cumulativeNaturalPostGcSamples < 0) {
            throw new IllegalArgumentException("campaign counts must be non-negative");
        }
        failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
        if (passed && !failures.isEmpty()) {
            throw new IllegalArgumentException("a passing campaign cannot have failures");
        }
    }
}
