package com.ultralatency.matching.network.netty.codec;

import com.ultralatency.matching.network.protocol.ProtocolConstants;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

/**
 * Channel-local protocol version selected by the first decoded request.
 *
 * <p>The attribute deliberately lives in the Netty codec package so Netty types do not leak into
 * the project-owned protocol value types. A session cannot switch versions after its first valid
 * request.</p>
 */
public final class ProtocolVersionAttributes {

    /** Channel attribute containing the negotiated integer protocol version. */
    public static final AttributeKey<Integer> VERSION =
            AttributeKey.valueOf("ulme.protocol.version");

    private ProtocolVersionAttributes() {
        // Utility class.
    }

    /** Returns the channel version, defaulting to the original v1 semantics. */
    public static int version(final Channel channel) {
        final Integer value = channel.attr(VERSION).get();
        if (value == null) {
            return ProtocolConstants.VERSION;
        }
        if (value != ProtocolConstants.VERSION && value != ProtocolConstants.PIPELINED_VERSION) {
            throw new IllegalStateException("Unsupported channel protocol version: " + value);
        }
        return value;
    }

    /** Returns whether the channel selected the explicit bounded-pipelining version. */
    public static boolean isPipelined(final Channel channel) {
        return version(channel)
                == ProtocolConstants.PIPELINED_VERSION;
    }
}
