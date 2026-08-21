package com.ultralatency.matching.recovery;

import com.ultralatency.matching.engine.EngineResult;
import java.util.List;
import java.util.Objects;

/** Immutable ordered observable result transcript for one offline replay. */
public record ReplayTranscript(List<EngineResult> results, String sha256DigestHex) {

    /** Validates and snapshots transcript values. */
    public ReplayTranscript {
        results = List.copyOf(Objects.requireNonNull(results, "results"));
        Objects.requireNonNull(sha256DigestHex, "sha256DigestHex");
        if (sha256DigestHex.length() != 64) {
            throw new IllegalArgumentException("SHA-256 digest must contain 64 hex characters");
        }
    }
}
