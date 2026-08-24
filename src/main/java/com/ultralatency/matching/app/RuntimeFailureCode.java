package com.ultralatency.matching.app;

/** Sanitized failure categories exposed by runtime status and process exit mapping. */
public enum RuntimeFailureCode {
    /** No terminal failure has been recorded. */
    NONE,
    /** Command-line or configuration validation failed. */
    CONFIG,
    /** Storage, recovery or sequence convergence failed. */
    RECOVERY,
    /** The Protocol v1 listener could not bind. */
    PROTOCOL_BIND,
    /** The management listener could not bind. */
    MANAGEMENT_BIND,
    /** A live runtime component reached a terminal failure. */
    RUNTIME,
    /** Shutdown exceeded its configured cooperative bound. */
    SHUTDOWN_TIMEOUT
}
