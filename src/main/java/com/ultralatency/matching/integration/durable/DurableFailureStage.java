package com.ultralatency.matching.integration.durable;

/**
 * Boundary at which a durable composition becomes terminal.
 */
public enum DurableFailureStage {
    /** Startup could not establish the approved fresh-WAL/genesis boundary. */
    STARTUP,
    /** WAL write, force or segment rotation failed. */
    APPEND,
    /** A durable append could not be admitted to the bounded pipeline. */
    DURABLE_THEN_FULL,
    /** Pipeline publication or pipeline infrastructure failed. */
    PIPELINE,
    /** Matching-engine or result-handler processing failed. */
    ENGINE,
    /** Encoding the response failed. */
    RESPONSE_ENCODING,
    /** The local outbound write failed. */
    OUTBOUND_WRITE,
    /** The active session disconnected during an owned operation. */
    DISCONNECT,
    /** Graceful draining failed or exceeded its bound. */
    DRAIN
}
