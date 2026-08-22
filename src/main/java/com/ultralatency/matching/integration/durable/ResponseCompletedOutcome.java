package com.ultralatency.matching.integration.durable;

import com.ultralatency.matching.engine.EngineResult;
import java.util.Objects;

/**
 * Immutable outcome proving local response encoding and outbound write completion.
 *
 * <p>Local write completion is not proof of client receipt and does not add a durable-ACK frame
 * to Protocol v1.</p>
 *
 * @param liveAccepted preceding live-accepted outcome
 * @param result immutable engine result correlated with the command sequence
 */
public record ResponseCompletedOutcome(
        LiveAcceptedOutcome liveAccepted,
        EngineResult result) implements DurableCommandOutcome {

    /**
     * Validates milestone ordering and result correlation.
     */
    public ResponseCompletedOutcome {
        Objects.requireNonNull(liveAccepted, "liveAccepted");
        Objects.requireNonNull(result, "result");
        if (!liveAccepted.commandSequence().toSequence().equals(result.commandSequence())) {
            throw new IllegalArgumentException(
                    "Response result sequence must match the live command sequence");
        }
    }

    @Override
    public DurableOutcomeStage stage() {
        return DurableOutcomeStage.RESPONSE_COMPLETED;
    }

    @Override
    public DurableCommandIdentity identity() {
        return liveAccepted.identity();
    }
}
