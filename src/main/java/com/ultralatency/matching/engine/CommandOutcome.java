package com.ultralatency.matching.engine;

/**
 * Observable result of applying one command.
 */
public enum CommandOutcome {
    /** A submit command was accepted. */
    ACCEPTED,
    /** A cancel command removed an active order. */
    CANCELED,
    /** A cancel command referenced no active order. */
    NOT_FOUND
}
