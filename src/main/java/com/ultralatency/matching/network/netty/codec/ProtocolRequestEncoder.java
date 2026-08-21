package com.ultralatency.matching.network.netty.codec;

import com.ultralatency.matching.network.protocol.CancelOrderRequest;
import com.ultralatency.matching.network.protocol.ProtocolConstants;
import com.ultralatency.matching.network.protocol.ProtocolRequest;
import com.ultralatency.matching.network.protocol.SubmitLimitRequest;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * Encodes project-owned requests into exact protocol v1 frames for fixtures and loopback clients.
 */
public final class ProtocolRequestEncoder extends MessageToByteEncoder<ProtocolRequest> {

    @Override
    protected void encode(
            final ChannelHandlerContext context,
            final ProtocolRequest request,
            final ByteBuf out) {
        if (request instanceof SubmitLimitRequest submit) {
            ProtocolWire.writeHeader(
                    out,
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
                    ProtocolConstants.CANCEL_ORDER_TYPE,
                    ProtocolConstants.CANCEL_ORDER_FRAME_LENGTH);
            out.writeLong(cancel.requestId().value());
            out.writeLong(cancel.orderId().value());
        } else {
            throw new EncoderException("Unsupported protocol request: " + request.getClass());
        }
    }
}
