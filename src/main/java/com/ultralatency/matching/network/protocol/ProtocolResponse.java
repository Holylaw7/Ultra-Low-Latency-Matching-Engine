package com.ultralatency.matching.network.protocol;

/**
 * Project-owned outbound protocol response.
 */
public sealed interface ProtocolResponse
        permits CommandResultResponse, MatchResultResponse, ErrorResponse {
}
