package com.ultralatency.matching.engine;

import com.ultralatency.matching.domain.Sequence;
import java.util.List;
import java.util.Objects;

/**
 * Immutable result boundary for one future matching-engine command application.
 *
 * <p>This type carries data only. It performs no callbacks, I/O, event publication, or state
 * mutation.</p>
 *
 * @param commandSequence applied upstream command sequence
 * @param outcome observable command outcome
 * @param matches immutable ordered match aggregates
 */
public record EngineResult(
        Sequence commandSequence,
        CommandOutcome outcome,
        List<MatchResult> matches) {

    /**
     * Validates required fields and snapshots the ordered matches.
     */
    public EngineResult {
        Objects.requireNonNull(commandSequence, "commandSequence");
        Objects.requireNonNull(outcome, "outcome");
        matches = List.copyOf(Objects.requireNonNull(matches, "matches"));
    }
}
