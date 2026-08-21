package com.ultralatency.matching.pipeline;

/**
 * Lifecycle states reserved for the Phase 4 pipeline facade.
 */
public enum PipelineState {
    /** No consumer resources have been started. */
    NEW,
    /** The pipeline accepts commands and processes them asynchronously. */
    RUNNING,
    /** New publication is closed while accepted commands drain. */
    DRAINING,
    /** The pipeline drained and stopped normally. */
    STOPPED,
    /** A terminal infrastructure or processing failure occurred. */
    FAILED
}
