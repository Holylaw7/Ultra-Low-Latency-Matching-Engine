package com.ultralatency.matching.network.netty.codec;

import com.ultralatency.matching.network.protocol.CancelOrderRequest;
import com.ultralatency.matching.network.protocol.ProtocolConstants;
import com.ultralatency.matching.network.protocol.ProtocolRequest;
import com.ultralatency.matching.network.protocol.SubmitLimitRequest;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.MessageToByteEncoder;

/** Encodes project-owned requests into exact versioned protocol frames. */
public final class ProtocolRequestEncoder extends MessageToByteEncoder<ProtocolRequest> {

    private final int protocolVersion;

    /** Creates the backwards-compatible Protocol v1 encoder. */
    public ProtocolRequestEncoder() {
        this(ProtocolConstants.VERSION);
    }

    /**
     * Creates an encoder for an explicit protocol version.
     *
     * @param protocolVersion supported protocol version
     */
    public ProtocolRequestEncoder(final int protocolVersion) {
        if (protocolVersion != ProtocolConstants.VERSION
                && protocolVersion != ProtocolConstants.PIPELINED_VERSION) {
            throw new IllegalArgumentException("Unsupported protocol version: " + protocolVersion);
        }
        this.protocolVersion = protocolVersion;
    }

    @Override
    protected void encode(
            final ChannelHandlerContext context,
            final ProtocolRequest request,
            final ByteBuf out) {
        if (request instanceof SubmitLimitRequest submit) {
            ProtocolWire.writeHeader(
                    out,
                    protocolVersion,
                    ProtocolConstants.SUBMIT_LIMIT_TYPE,
                    ProtocolConstants.SUBMIT_LIMIT_FRAME_LENGTH);
            out.writeLong(submit.requestId().value());
            out.writeLong(submit.orderId().value());
            out.writeByte(submit.side() == com.ultralatency.matching.domain.Side.BUY ? 1 : 2);
            out.writeZero(7);
            out.writeLong(submit.price().ticks());
            out.writeLong(submit.quantity().units());
        } else if (request instanceof CancelOrderRequest cancel) {
            ProtocolWire.writeHeader(
                    out,
                    protocolVersion,
                    ProtocolConstants.CANCEL_ORDER_TYPE,
                    ProtocolConstants.CANCEL_ORDER_FRAME_LENGTH);
            out.writeLong(cancel.requestId().value());
            out.writeLong(cancel.orderId().value());
        } else {
            throw new EncoderException("Unsupported protocol request: " + request.getClass());
        }
    }
}
