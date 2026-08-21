package com.ultralatency.matching.pipeline;

/**
 * Result of a non-blocking command admission attempt.
 */
public enum PipelinePublishOutcome {
    /** The command was placed into an in-memory pipeline slot. */
    ACCEPTED,
    /** No slot was available; the command was not consumed or mutated. */
    FULL
}
