package com.ultralatency.matching.qualification;

/**
 * Deterministic workload profiles used by system qualification.
 */
public enum QualificationProfile {

    /** Exercises accepted, canceled and not-found lifecycle outcomes. */
    LIFECYCLE_MIX,

    /** Exercises maker-price, multi-match and partial-fill behavior. */
    CROSSING_MULTI_MATCH,

    /** Exercises a bounded number of resting price levels. */
    RESTING_DEPTH,

    /**
     * Qualification-only workload whose active order state returns to a fixed bound every
     * cycle. This is a new workload identity; the existing profile vectors remain unchanged.
     */
    MEMORY_STEADY_STATE_V1
}
