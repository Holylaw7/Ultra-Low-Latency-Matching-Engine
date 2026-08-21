package com.ultralatency.matching.network.netty.codec;

import com.ultralatency.matching.network.protocol.ProtocolConstants;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import java.nio.ByteOrder;

/**
 * Fail-fast big-endian length-field framing for protocol v1.
 */
public final class ProtocolFrameDecoder extends LengthFieldBasedFrameDecoder {

    /**
     * Creates the exact ADR-0014 framing configuration.
     */
    public ProtocolFrameDecoder() {
        super(
                ByteOrder.BIG_ENDIAN,
                ProtocolConstants.MAX_FRAME_LENGTH,
                8,
                Integer.BYTES,
                -12,
                0,
                true);
    }
}
