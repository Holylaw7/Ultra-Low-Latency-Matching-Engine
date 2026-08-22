package com.ultralatency.matching.integration.durable;

/**
 * Monotonic externally observable milestones for one live command.
 */
public enum DurableOutcomeStage {
    /** WAL append and the required synchronous force completed successfully. */
    DURABLE,
    /** A durable command was admitted to the bounded live pipeline. */
    LIVE_ACCEPTED,
    /** Encoding and the local outbound write completed successfully. */
    RESPONSE_COMPLETED
}
