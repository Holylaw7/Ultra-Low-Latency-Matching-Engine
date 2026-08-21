package com.ultralatency.matching.network.protocol;

/**
 * Strict protocol and gateway error codes.
 */
public enum ProtocolErrorCode {
    /** Frame cannot be parsed safely. */
    MALFORMED_FRAME(1),
    /** Unsupported protocol version. */
    UNSUPPORTED_VERSION(2),
    /** Unsupported message type. */
    UNSUPPORTED_MESSAGE_TYPE(3),
    /** A field violates the v1 domain or reserved-byte rules. */
    INVALID_FIELD(4),
    /** Request identifier is not the exact next session identifier. */
    UNEXPECTED_REQUEST_ID(5),
    /** Bounded pipeline has no available slot. */
    BACKPRESSURE_FULL(6),
    /** The single session is already owned by another client. */
    SERVER_BUSY(7),
    /** Terminal server failure. */
    TERMINAL_SERVER_FAILURE(8);

    private final int code;

    ProtocolErrorCode(final int code) {
        this.code = code;
    }

    /**
     * Returns the wire error code.
     *
     * @return unsigned two-byte error code value
     */
    public int code() {
        return code;
    }
}
