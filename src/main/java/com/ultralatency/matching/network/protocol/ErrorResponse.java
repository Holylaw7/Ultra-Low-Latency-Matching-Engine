package com.ultralatency.matching.network.protocol;

import java.util.Objects;

/**
 * Bounded protocol error response.
 *
 * @param requestId positive request ID, or zero when unavailable
 * @param errorCode protocol error code
 */
public record ErrorResponse(long requestId, ProtocolErrorCode errorCode)
        implements ProtocolResponse {

    /**
     * Validates error response fields.
     */
    public ErrorResponse {
        Objects.requireNonNull(errorCode, "errorCode");
        if (requestId < 0) {
            throw new IllegalArgumentException("Error request ID must be zero or positive");
        }
    }
}
