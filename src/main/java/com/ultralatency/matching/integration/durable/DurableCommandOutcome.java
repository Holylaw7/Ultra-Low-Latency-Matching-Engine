package com.ultralatency.matching.integration.durable;

import com.ultralatency.matching.network.protocol.ClientRequestId;

/**
 * Common immutable boundary for the three distinct live-command milestones.
 */
public sealed interface DurableCommandOutcome
        permits DurableOutcome, LiveAcceptedOutcome, ResponseCompletedOutcome {

    /**
     * Returns the milestone represented by this value.
     *
     * @return outcome stage
     */
    DurableOutcomeStage stage();

    /**
     * Returns the stable request/command identity.
     *
     * @return durable command identity
     */
    DurableCommandIdentity identity();

    /**
     * Convenience access to the session request identity.
     *
     * @return request ID
     */
    default ClientRequestId requestId() {
        return identity().requestId();
    }

    /**
     * Convenience access to the coordinator command sequence.
     *
     * @return command sequence
     */
    default DurableCommandSequence commandSequence() {
        return identity().commandSequence();
    }

    /**
     * Convenience conversion for the frozen engine and protocol adapters.
     *
     * @return domain command sequence
     */
    default com.ultralatency.matching.domain.Sequence domainCommandSequence() {
        return commandSequence().toSequence();
    }
}
