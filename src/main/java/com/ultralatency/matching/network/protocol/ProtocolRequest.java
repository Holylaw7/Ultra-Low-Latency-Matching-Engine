package com.ultralatency.matching.network.protocol;

/**
 * Project-owned inbound protocol request without a command sequence.
 */
public sealed interface ProtocolRequest permits SubmitLimitRequest, CancelOrderRequest {

    /**
     * Returns the transport correlation identifier.
     *
     * @return client request identifier
     */
    ClientRequestId requestId();
}
