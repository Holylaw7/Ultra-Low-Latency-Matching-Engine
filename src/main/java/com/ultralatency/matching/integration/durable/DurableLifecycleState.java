package com.ultralatency.matching.integration.durable;

/**
 * Lifecycle states for the live durable composition.
 */
public enum DurableLifecycleState {
    /** No durable runtime resources have been started. */
    NEW,
    /** The composition admits one live command producer. */
    RUNNING,
    /** New admission is closed while accepted work drains. */
    DRAINING,
    /** The composition drained and stopped normally. */
    STOPPED,
    /** A terminal durable, pipeline, response or drain failure occurred. */
    FAILED
}
