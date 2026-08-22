package com.ultralatency.matching.integration.durable;

import com.ultralatency.matching.engine.EngineResult;
import java.util.Objects;

/**
 * Adapter port for handing one ordered engine result back to the owning live session.
 */
@FunctionalInterface
public interface DurableResultPort {

    /**
     * Observes an immutable result together with its session/command correlation.
     *
     * @param identity request and command identities
     * @param result frozen engine result
     */
    void onResult(DurableCommandIdentity identity, EngineResult result);

    /**
     * Observes a result using the identity carried by a durable command envelope.
     *
     * @param command durable command envelope
     * @param result frozen engine result
     */
    default void onResult(final DurableCommand command, final EngineResult result) {
        onResult(Objects.requireNonNull(command, "command").identity(), result);
    }
}
