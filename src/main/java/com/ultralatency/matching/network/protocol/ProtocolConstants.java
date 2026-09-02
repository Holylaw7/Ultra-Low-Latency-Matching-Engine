package com.ultralatency.matching.network.protocol;

/**
 * Wire constants for the binary network protocol.
 */
public final class ProtocolConstants {

    /** Protocol magic bytes represented as a big-endian integer. */
    public static final int MAGIC = 0x554C4D45;

    /** Existing protocol version with one active request in flight. */
    public static final int VERSION = 1;

    /** Explicit pipelined protocol version. */
    public static final int PIPELINED_VERSION = 2;

    /** Default bounded v2 request window. */
    public static final int DEFAULT_PIPELINED_MAX_IN_FLIGHT = 64;

    /** Hard upper bound for a v2 request window. */
    public static final int MAX_PIPELINED_IN_FLIGHT = 1024;

    /** Common header length in bytes. */
    public static final int HEADER_LENGTH = 16;

    /** Maximum supported frame length. */
    public static final int MAX_FRAME_LENGTH = 104;

    /** Submit-limit request frame length. */
    public static final int SUBMIT_LIMIT_FRAME_LENGTH = 56;

    /** Cancel-order request frame length. */
    public static final int CANCEL_ORDER_FRAME_LENGTH = 32;

    /** Command-result response frame length. */
    public static final int COMMAND_RESULT_FRAME_LENGTH = 40;

    /** Match-result response frame length. */
    public static final int MATCH_RESULT_FRAME_LENGTH = 104;

    /** Error response frame length. */
    public static final int ERROR_FRAME_LENGTH = 32;

    /** Submit-limit message code. */
    public static final int SUBMIT_LIMIT_TYPE = 0x01;

    /** Cancel-order message code. */
    public static final int CANCEL_ORDER_TYPE = 0x02;

    /** Command-result message code. */
    public static final int COMMAND_RESULT_TYPE = 0x81;

    /** Match-result message code. */
    public static final int MATCH_RESULT_TYPE = 0x82;

    /** Error message code. */
    public static final int ERROR_TYPE = 0xE0;

    private ProtocolConstants() {
        // Constants only.
    }
}
