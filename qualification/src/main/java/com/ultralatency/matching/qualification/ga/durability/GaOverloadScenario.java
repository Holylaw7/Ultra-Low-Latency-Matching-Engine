package com.ultralatency.matching.qualification.ga.durability;

/** Public-boundary overload probes required by G7. */
public enum GaOverloadScenario {

    /** A second TCP session is rejected without a second admission path. */
    SECOND_SESSION,
    /** Coalesced requests do not gain a second in-flight admission. */
    PIPELINED_REQUEST,
    /** An oversized or malformed frame is rejected within the protocol bound. */
    FRAME_BOUND,
    /** A full bounded pipeline is terminal and never silently retried. */
    PIPELINE_FULL,
    /** Management request saturation remains bounded. */
    MANAGEMENT_BOUND,
    /** Durable-FULL does not create an unbounded retry/producer path. */
    DURABLE_FULL,
    /** Resource and temporary-file bounds remain finite. */
    RESOURCE_BOUND
}
