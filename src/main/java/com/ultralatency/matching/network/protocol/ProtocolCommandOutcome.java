package com.ultralatency.matching.network.protocol;

/**
 * Command outcome values carried by a network response.
 */
public enum ProtocolCommandOutcome {
    /** Submit command accepted. */
    ACCEPTED(1),
    /** Cancel command removed an active order. */
    CANCELED(2),
    /** Cancel command found no active order. */
    NOT_FOUND(3);

    private final int code;

    ProtocolCommandOutcome(final int code) {
        this.code = code;
    }

    /**
     * Returns the wire outcome code.
     *
     * @return unsigned one-byte outcome value
     */
    public int code() {
        return code;
    }
}
