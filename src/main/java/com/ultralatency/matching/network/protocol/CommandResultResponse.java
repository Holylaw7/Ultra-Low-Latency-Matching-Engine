package com.ultralatency.matching.network.protocol;

import com.ultralatency.matching.domain.Sequence;
import java.util.Objects;

/**
 * Ordered command outcome response.
 *
 * @param requestId client request correlation identifier
 * @param commandSequence applied engine command sequence
 * @param outcome command outcome code
 * @param matchCount number of following match frames
 */
public record CommandResultResponse(
        ClientRequestId requestId,
        Sequence commandSequence,
        ProtocolCommandOutcome outcome,
        int matchCount) implements ProtocolResponse {

    /**
     * Validates response fields.
     */
    public CommandResultResponse {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(commandSequence, "commandSequence");
        Objects.requireNonNull(outcome, "outcome");
        if (matchCount < 0) {
            throw new IllegalArgumentException("Match count must not be negative");
        }
    }
}
