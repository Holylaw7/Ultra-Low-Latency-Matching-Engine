package com.ultralatency.matching.recovery;

import com.ultralatency.matching.engine.EngineResult;
import java.util.List;
import java.util.Objects;

/** Immutable result of replaying a prefix and applying a public API probe suffix. */
public record ReplayProbeResult(
        ReplayTranscript transcript,
        List<EngineResult> probeResults) {

    /** Validates and snapshots probe values. */
    public ReplayProbeResult {
        Objects.requireNonNull(transcript, "transcript");
        probeResults = List.copyOf(Objects.requireNonNull(probeResults, "probeResults"));
    }
}
