package com.ultralatency.matching.network.netty.codec;

import com.ultralatency.matching.network.protocol.ProtocolErrorCode;
import io.netty.handler.codec.DecoderException;

/**
 * Strict protocol decoding failure with its v1 error classification.
 */
public final class ProtocolCodecException extends DecoderException {

    private static final long serialVersionUID = 1L;

    private final ProtocolErrorCode errorCode;

    /**
     * Creates a classified protocol failure.
     *
     * @param errorCode protocol error code
     * @param message failure detail
     */
    public ProtocolCodecException(final ProtocolErrorCode errorCode, final String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Returns the protocol error classification.
     *
     * @return error code
     */
    public ProtocolErrorCode errorCode() {
        return errorCode;
    }
}
