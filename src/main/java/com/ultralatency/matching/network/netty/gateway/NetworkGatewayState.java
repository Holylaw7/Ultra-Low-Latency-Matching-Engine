package com.ultralatency.matching.network.netty.gateway;

/**
 * Lifecycle states for the Phase 6 single-session network gateway.
 */
public enum NetworkGatewayState {
    /** No gateway resources have been allocated. */
    NEW,
    /** The gateway accepts one client session and requests. */
    RUNNING,
    /** New requests are stopped while existing resources close. */
    DRAINING,
    /** Gateway resources closed normally. */
    STOPPED,
    /** A terminal network, pipeline or outbound failure occurred. */
    FAILED
}
