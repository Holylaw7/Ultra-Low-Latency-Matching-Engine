package com.ultralatency.matching.integration.durable;

import java.util.Objects;

/**
 * Immutable outcome proving that one command was appended and synchronously forced to the WAL.
 *
 * <p>This value does not imply pipeline admission, engine application or client receipt.</p>
 *
 * @param identity request and command identities
 */
public record DurableOutcome(DurableCommandIdentity identity)
        implements DurableCommandOutcome {

    /**
     * Validates the durable identity.
     */
    public DurableOutcome {
        Objects.requireNonNull(identity, "identity");
    }

    @Override
    public DurableOutcomeStage stage() {
        return DurableOutcomeStage.DURABLE;
    }
}
