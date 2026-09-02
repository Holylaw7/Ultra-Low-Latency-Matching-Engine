package com.ultralatency.matching.network.netty.codec;

import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Side;
import com.ultralatency.matching.network.protocol.CancelOrderRequest;
import com.ultralatency.matching.network.protocol.ClientRequestId;
import com.ultralatency.matching.network.protocol.ProtocolConstants;
import com.ultralatency.matching.network.protocol.ProtocolErrorCode;
import com.ultralatency.matching.network.protocol.ProtocolRequest;
import com.ultralatency.matching.network.protocol.SubmitLimitRequest;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import java.util.List;

/**
 * Converts one complete protocol request frame into a project-owned value.
 */
public final class ProtocolRequestDecoder extends MessageToMessageDecoder<ByteBuf> {

    @Override
    protected void decode(
            final ChannelHandlerContext context,
            final ByteBuf frame,
            final List<Object> out) {
        final int index = frame.readerIndex();
        final int type = validateCommonFrame(context, frame, index);
        final ProtocolRequest request;
        try {
            request = switch (type) {
                case ProtocolConstants.SUBMIT_LIMIT_TYPE -> decodeSubmit(frame, index);
                case ProtocolConstants.CANCEL_ORDER_TYPE -> decodeCancel(frame, index);
                default -> throw new ProtocolCodecException(
                        ProtocolErrorCode.UNSUPPORTED_MESSAGE_TYPE,
                        "Message type is not a request");
            };
        } catch (final ProtocolCodecException exception) {
            throw exception;
        } catch (final IllegalArgumentException exception) {
            throw new ProtocolCodecException(ProtocolErrorCode.INVALID_FIELD, exception.getMessage());
        }
        out.add(request);
    }

    private static int validateCommonFrame(
            final ChannelHandlerContext context,
            final ByteBuf frame,
            final int index) {
        final int length = frame.readableBytes();
        if (length < ProtocolConstants.HEADER_LENGTH) {
            throw invalid("Frame is shorter than the common header");
        }
        if (frame.getInt(index + 8) != length) {
            throw invalid("Declared frame length does not match frame bytes");
        }
        if (frame.getInt(index) != ProtocolConstants.MAGIC) {
            throw new ProtocolCodecException(ProtocolErrorCode.MALFORMED_FRAME, "Invalid protocol magic");
        }
        final int version = frame.getUnsignedByte(index + 4);
        if (version != ProtocolConstants.VERSION
                && version != ProtocolConstants.PIPELINED_VERSION) {
            throw new ProtocolCodecException(
                    ProtocolErrorCode.UNSUPPORTED_VERSION,
                    "Unsupported protocol version: " + version);
        }
        final Integer selected = context.channel().attr(ProtocolVersionAttributes.VERSION).get();
        if (selected != null && selected != version) {
            throw new ProtocolCodecException(
                    ProtocolErrorCode.UNSUPPORTED_VERSION,
                    "Protocol version cannot change within a session");
        }
        context.channel().attr(ProtocolVersionAttributes.VERSION).set(version);
        if (frame.getUnsignedShort(index + 6) != 0 || frame.getInt(index + 12) != 0) {
            throw invalid("Non-zero header flags or reserved bytes");
        }
        final int type = frame.getUnsignedByte(index + 5);
        if (type != ProtocolConstants.SUBMIT_LIMIT_TYPE
                && type != ProtocolConstants.CANCEL_ORDER_TYPE) {
            throw new ProtocolCodecException(
                    ProtocolErrorCode.UNSUPPORTED_MESSAGE_TYPE,
                    "Unsupported request message type: " + type);
        }
        final int expectedLength = type == ProtocolConstants.SUBMIT_LIMIT_TYPE
                ? ProtocolConstants.SUBMIT_LIMIT_FRAME_LENGTH
                : ProtocolConstants.CANCEL_ORDER_FRAME_LENGTH;
        if (length != expectedLength) {
            throw invalid("Unexpected request frame length");
        }
        return type;
    }

    private static SubmitLimitRequest decodeSubmit(final ByteBuf frame, final int index) {
        validateReserved(frame, index + 33, 7);
        return new SubmitLimitRequest(
                ClientRequestId.of(frame.getLong(index + 16)),
                OrderId.of(frame.getLong(index + 24)),
                decodeSide(frame.getUnsignedByte(index + 32)),
                Price.of(frame.getLong(index + 40)),
                Quantity.of(frame.getLong(index + 48)));
    }

    private static CancelOrderRequest decodeCancel(final ByteBuf frame, final int index) {
        return new CancelOrderRequest(
                ClientRequestId.of(frame.getLong(index + 16)),
                OrderId.of(frame.getLong(index + 24)));
    }

    private static Side decodeSide(final int code) {
        return switch (code) {
            case 1 -> Side.BUY;
            case 2 -> Side.SELL;
            default -> throw invalid("Invalid side code: " + code);
        };
    }

    private static void validateReserved(final ByteBuf frame, final int start, final int length) {
        for (int offset = 0; offset < length; offset++) {
            if (frame.getUnsignedByte(start + offset) != 0) {
                throw invalid("Non-zero request reserved bytes");
            }
        }
    }

    private static ProtocolCodecException invalid(final String message) {
        return new ProtocolCodecException(ProtocolErrorCode.INVALID_FIELD, message);
    }
}
