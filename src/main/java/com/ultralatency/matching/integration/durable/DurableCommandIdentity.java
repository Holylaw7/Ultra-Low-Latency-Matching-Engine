package com.ultralatency.matching.integration.durable;

import com.ultralatency.matching.network.protocol.ClientRequestId;
import java.util.Objects;

/**
 * Correlation value joining the two independent identities used by the live path.
 *
 * <p>The request ID belongs to the active client session. The command sequence belongs to the
 * durable coordinator. This value does not represent a ring sequence, event sequence, trade ID,
 * WAL segment ID or physical file offset.</p>
 *
 * @param requestId session-owned client request identifier
 * @param commandSequence coordinator-owned logical command sequence
 */
public record DurableCommandIdentity(
        ClientRequestId requestId,
        DurableCommandSequence commandSequence) {

    /**
     * Validates the independent identity values.
     */
    public DurableCommandIdentity {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(commandSequence, "commandSequence");
    }

    /**
     * Returns the frozen engine sequence representation.
     *
     * @return domain command sequence
     */
    public com.ultralatency.matching.domain.Sequence domainCommandSequence() {
        return commandSequence.toSequence();
    }
}
