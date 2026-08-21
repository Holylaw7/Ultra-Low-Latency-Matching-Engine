package com.ultralatency.matching.network.protocol;

/**
 * Version-one message type codes.
 */
public enum ProtocolMessageType {
    /** Limit-order request. */
    SUBMIT_LIMIT(ProtocolConstants.SUBMIT_LIMIT_TYPE),
    /** Cancel-order request. */
    CANCEL_ORDER(ProtocolConstants.CANCEL_ORDER_TYPE),
    /** Command outcome response. */
    COMMAND_RESULT(ProtocolConstants.COMMAND_RESULT_TYPE),
    /** One ordered match response. */
    MATCH_RESULT(ProtocolConstants.MATCH_RESULT_TYPE),
    /** Protocol or server error response. */
    ERROR(ProtocolConstants.ERROR_TYPE);

    private final int code;

    ProtocolMessageType(final int code) {
        this.code = code;
    }

    /**
     * Returns the wire code.
     *
     * @return unsigned one-byte code
     */
    public int code() {
        return code;
    }
}
