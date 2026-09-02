package com.ultralatency.matching.network.netty.codec;

import com.ultralatency.matching.network.protocol.CommandResultResponse;
import com.ultralatency.matching.network.protocol.ErrorResponse;
import com.ultralatency.matching.network.protocol.MatchResultResponse;
import com.ultralatency.matching.network.protocol.ProtocolConstants;
import com.ultralatency.matching.network.protocol.ProtocolResponse;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * Encodes project-owned responses into bounded protocol frames.
 */
public final class ProtocolResponseEncoder extends MessageToByteEncoder<ProtocolResponse> {

    @Override
    protected void encode(
            final ChannelHandlerContext context,
            final ProtocolResponse response,
            final ByteBuf out) {
        if (response instanceof CommandResultResponse command) {
            encodeCommandResult(context, command, out);
        } else if (response instanceof MatchResultResponse match) {
            encodeMatchResult(context, match, out);
        } else if (response instanceof ErrorResponse error) {
            encodeError(context, error, out);
        } else {
            throw new EncoderException("Unsupported protocol response: " + response.getClass());
        }
    }

    private static void encodeCommandResult(
            final ChannelHandlerContext context,
            final CommandResultResponse response,
            final ByteBuf out) {
        ProtocolWire.writeHeader(
                out,
                ProtocolVersionAttributes.version(context.channel()),
                ProtocolConstants.COMMAND_RESULT_TYPE,
                ProtocolConstants.COMMAND_RESULT_FRAME_LENGTH);
        out.writeLong(response.requestId().value());
        out.writeLong(response.commandSequence().value());
        out.writeByte(response.outcome().code());
        out.writeZero(3);
        out.writeInt(response.matchCount());
    }

    private static void encodeMatchResult(
            final ChannelHandlerContext context,
            final MatchResultResponse response,
            final ByteBuf out) {
        ProtocolWire.writeHeader(
                out,
                ProtocolVersionAttributes.version(context.channel()),
                ProtocolConstants.MATCH_RESULT_TYPE,
                ProtocolConstants.MATCH_RESULT_FRAME_LENGTH);
        out.writeLong(response.requestId().value());
        out.writeLong(response.commandSequence().value());
        out.writeInt(response.matchIndex());
        out.writeInt(response.totalMatchCount());
        out.writeLong(response.eventSequence().value());
        out.writeLong(response.tradeId().value());
        out.writeLong(response.price().ticks());
        out.writeLong(response.quantity().units());
        out.writeLong(response.makerOrderId().value());
        out.writeLong(response.makerRemainingQuantityUnits());
        out.writeLong(response.takerOrderId().value());
        out.writeLong(response.takerRemainingQuantityUnits());
    }

    private static void encodeError(
            final ChannelHandlerContext context,
            final ErrorResponse response,
            final ByteBuf out) {
        ProtocolWire.writeHeader(
                out,
                ProtocolVersionAttributes.version(context.channel()),
                ProtocolConstants.ERROR_TYPE,
                ProtocolConstants.ERROR_FRAME_LENGTH);
        out.writeLong(response.requestId());
        out.writeShort(response.errorCode().code());
        out.writeZero(6);
    }
}
