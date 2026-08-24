package com.ultralatency.matching.qualification;

import java.util.Objects;

/** Immutable output of one public-boundary qualification run. */
public record QualificationRun(
        QualificationManifest manifest,
        QualificationResult result,
        int restartCycles) {

    /** Creates a validated run result. */
    public QualificationRun {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(result, "result");
        if (restartCycles <= 0) {
            throw new IllegalArgumentException("restartCycles must be positive");
        }
    }
}
