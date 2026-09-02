package com.ultralatency.matching.network.netty.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ultralatency.matching.domain.EventSequence;
import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.domain.Side;
import com.ultralatency.matching.domain.TradeId;
import com.ultralatency.matching.network.protocol.CancelOrderRequest;
import com.ultralatency.matching.network.protocol.ClientRequestId;
import com.ultralatency.matching.network.protocol.CommandResultResponse;
import com.ultralatency.matching.network.protocol.ErrorResponse;
import com.ultralatency.matching.network.protocol.MatchResultResponse;
import com.ultralatency.matching.network.protocol.ProtocolCommandOutcome;
import com.ultralatency.matching.network.protocol.ProtocolConstants;
import com.ultralatency.matching.network.protocol.ProtocolErrorCode;
import com.ultralatency.matching.network.protocol.ProtocolRequest;
import com.ultralatency.matching.network.protocol.ProtocolResponse;
import com.ultralatency.matching.network.protocol.SubmitLimitRequest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.TooLongFrameException;
import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class ProtocolCodecTest {

    private static final String SUBMIT_HEX =
            "554c4d45010100000000003800000000"
                    + "0000000000000007"
                    + "000000000000002a"
                    + "0100000000000000"
                    + "0000000000000064"
                    + "0000000000000003";
    private static final String CANCEL_HEX =
            "554c4d45010200000000002000000000"
                    + "0000000000000007"
                    + "000000000000002a";
    private static final String COMMAND_RESULT_HEX =
            "554c4d45018100000000002800000000"
                    + "0000000000000007"
                    + "0000000000000009"
                    + "0100000000000002";
    private static final String MATCH_RESULT_HEX =
            "554c4d45018200000000006800000000"
                    + "0000000000000007"
                    + "0000000000000009"
                    + "0000000000000001"
                    + "000000000000000b"
                    + "000000000000000c"
                    + "0000000000000064"
                    + "0000000000000003"
                    + "0000000000000002"
                    + "0000000000000000"
                    + "0000000000000007"
                    + "0000000000000004";
    private static final String ERROR_HEX =
            "554c4d4501e000000000002000000000"
                    + "0000000000000000"
                    + "0006"
                    + "000000000000";

    @Test
    void encodesAndDecodesSubmitLimitGoldenBytes() {
        final SubmitLimitRequest request = new SubmitLimitRequest(
                ClientRequestId.of(7),
                OrderId.of(42),
                Side.BUY,
                Price.of(100),
                Quantity.of(3));
        final byte[] encoded = encodeRequest(request);

        assertEquals(SUBMIT_HEX, HexFormat.of().formatHex(encoded));
        assertEquals(request, decodeRequest(encoded));
    }

    @Test
    void encodesAndDecodesCancelGoldenBytes() {
        final CancelOrderRequest request = new CancelOrderRequest(
                ClientRequestId.of(7), OrderId.of(42));
        final byte[] encoded = encodeRequest(request);

        assertEquals(CANCEL_HEX, HexFormat.of().formatHex(encoded));
        assertEquals(request, decodeRequest(encoded));
    }

    @Test
    void explicitV2RequestEncodingRetainsTheSamePayloadSemantics() {
        final CancelOrderRequest request = new CancelOrderRequest(
                ClientRequestId.of(7), OrderId.of(42));

        final byte[] encoded = encodeRequest(request, ProtocolConstants.PIPELINED_VERSION);

        assertEquals(ProtocolConstants.PIPELINED_VERSION, encoded[4] & 0xFF);
        assertEquals(request, decodeRequest(encoded));
    }

    @Test
    void encodesExactResponseGoldenBytes() {
        final ProtocolResponse command = new CommandResultResponse(
                ClientRequestId.of(7),
                Sequence.of(9),
                ProtocolCommandOutcome.ACCEPTED,
                2);
        final ProtocolResponse match = new MatchResultResponse(
                ClientRequestId.of(7),
                Sequence.of(9),
                0,
                1,
                EventSequence.of(11),
                TradeId.of(12),
                Price.of(100),
                Quantity.of(3),
                OrderId.of(2),
                0,
                OrderId.of(7),
                4);
        final ProtocolResponse error = new ErrorResponse(0, ProtocolErrorCode.BACKPRESSURE_FULL);

        assertEquals(COMMAND_RESULT_HEX, HexFormat.of().formatHex(encodeResponse(command)));
        assertEquals(MATCH_RESULT_HEX, HexFormat.of().formatHex(encodeResponse(match)));
        assertEquals(ERROR_HEX, HexFormat.of().formatHex(encodeResponse(error)));
    }

    @Test
    void responseEncoderUsesTheSelectedProtocolVersion() {
        final CommandResultResponse response = new CommandResultResponse(
                ClientRequestId.of(7),
                Sequence.of(9),
                ProtocolCommandOutcome.ACCEPTED,
                0);
        final EmbeddedChannel channel = new EmbeddedChannel(new ProtocolResponseEncoder());
        channel.attr(ProtocolVersionAttributes.VERSION).set(ProtocolConstants.PIPELINED_VERSION);
        channel.writeOutbound(response);
        final ByteBuf encoded = channel.readOutbound();
        try {
            assertEquals(ProtocolConstants.PIPELINED_VERSION, encoded.getUnsignedByte(4));
        } finally {
            encoded.release();
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void acceptsEverySingleFrameFragmentationAndCoalescedFramesInOrder() {
        final byte[] submit = HexFormat.of().parseHex(SUBMIT_HEX);
        final byte[] cancel = HexFormat.of().parseHex(CANCEL_HEX);
        final EmbeddedChannel fragmented = new EmbeddedChannel(
                new ProtocolFrameDecoder(), new ProtocolRequestDecoder());
        for (final byte value : submit) {
            fragmented.writeInbound(Unpooled.wrappedBuffer(new byte[] {value}));
        }
        assertEquals(
                new SubmitLimitRequest(
                        ClientRequestId.of(7),
                        OrderId.of(42),
                        Side.BUY,
                        Price.of(100),
                        Quantity.of(3)),
                fragmented.readInbound());
        fragmented.finishAndReleaseAll();

        final EmbeddedChannel coalesced = new EmbeddedChannel(
                new ProtocolFrameDecoder(), new ProtocolRequestDecoder());
        final byte[] both = Arrays.copyOf(submit, submit.length + cancel.length);
        System.arraycopy(cancel, 0, both, submit.length, cancel.length);
        coalesced.writeInbound(Unpooled.wrappedBuffer(both));
        assertInstanceOf(SubmitLimitRequest.class, coalesced.readInbound());
        assertInstanceOf(CancelOrderRequest.class, coalesced.readInbound());
        coalesced.finishAndReleaseAll();
    }

    @Test
    void protocolVersionCannotChangeWithinOneSession() {
        final CancelOrderRequest request = new CancelOrderRequest(
                ClientRequestId.of(7), OrderId.of(42));
        final byte[] v2 = encodeRequest(request, ProtocolConstants.PIPELINED_VERSION);
        final byte[] v1 = encodeRequest(request, ProtocolConstants.VERSION);
        final EmbeddedChannel channel = new EmbeddedChannel(
                new ProtocolFrameDecoder(), new ProtocolRequestDecoder());
        try {
            channel.writeInbound(Unpooled.wrappedBuffer(v2));
            assertEquals(request, channel.readInbound());
            assertThrows(
                    ProtocolCodecException.class,
                    () -> channel.writeInbound(Unpooled.wrappedBuffer(v1)));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void rejectsInvalidHeaderAndNumericFields() {
        final byte[] submit = HexFormat.of().parseHex(SUBMIT_HEX);
        final byte[] invalidMagic = submit.clone();
        invalidMagic[0] = 0;
        assertThrows(ProtocolCodecException.class, () -> decodeRequest(invalidMagic));

        final byte[] invalidVersion = submit.clone();
        invalidVersion[4] = 3;
        assertThrows(ProtocolCodecException.class, () -> decodeRequest(invalidVersion));

        final byte[] invalidType = submit.clone();
        invalidType[5] = (byte) 0x81;
        assertThrows(ProtocolCodecException.class, () -> decodeRequest(invalidType));

        final byte[] invalidFlags = submit.clone();
        invalidFlags[7] = 1;
        assertThrows(ProtocolCodecException.class, () -> decodeRequest(invalidFlags));

        final byte[] invalidSide = submit.clone();
        invalidSide[32] = 3;
        assertThrows(ProtocolCodecException.class, () -> decodeRequest(invalidSide));

        final byte[] invalidReserved = submit.clone();
        invalidReserved[33] = 1;
        assertThrows(ProtocolCodecException.class, () -> decodeRequest(invalidReserved));

        final byte[] invalidRequestId = submit.clone();
        Arrays.fill(invalidRequestId, 16, 24, (byte) 0);
        assertThrows(ProtocolCodecException.class, () -> decodeRequest(invalidRequestId));

        final byte[] invalidPrice = submit.clone();
        Arrays.fill(invalidPrice, 40, 48, (byte) 0);
        assertThrows(ProtocolCodecException.class, () -> decodeRequest(invalidPrice));
    }

    @Test
    void rejectsOverlongFramesBeforeRequestDecode() {
        final byte[] overlong = new byte[105];
        final ByteBuf frame = Unpooled.wrappedBuffer(overlong);
        frame.setInt(0, 0x554C4D45);
        frame.setByte(4, 1);
        frame.setByte(5, 1);
        frame.setInt(8, 105);
        final EmbeddedChannel channel = new EmbeddedChannel(
                new ProtocolFrameDecoder(), new ProtocolRequestDecoder());
        assertThrows(TooLongFrameException.class, () -> channel.writeInbound(frame));
        channel.finishAndReleaseAll();
    }

    private static byte[] encodeRequest(final ProtocolRequest request) {
        return encodeRequest(request, ProtocolConstants.VERSION);
    }

    private static byte[] encodeRequest(
            final ProtocolRequest request,
            final int protocolVersion) {
        final EmbeddedChannel channel = new EmbeddedChannel(new ProtocolRequestEncoder(protocolVersion));
        channel.writeOutbound(request);
        final ByteBuf encoded = channel.readOutbound();
        try {
            final byte[] bytes = new byte[encoded.readableBytes()];
            encoded.getBytes(encoded.readerIndex(), bytes);
            return bytes;
        } finally {
            encoded.release();
            channel.finishAndReleaseAll();
        }
    }

    private static byte[] encodeResponse(final ProtocolResponse response) {
        final EmbeddedChannel channel = new EmbeddedChannel(new ProtocolResponseEncoder());
        channel.writeOutbound(response);
        final ByteBuf encoded = channel.readOutbound();
        try {
            final byte[] bytes = new byte[encoded.readableBytes()];
            encoded.getBytes(encoded.readerIndex(), bytes);
            return bytes;
        } finally {
            encoded.release();
            channel.finishAndReleaseAll();
        }
    }

    private static ProtocolRequest decodeRequest(final byte[] bytes) {
        final EmbeddedChannel channel = new EmbeddedChannel(
                new ProtocolFrameDecoder(), new ProtocolRequestDecoder());
        try {
            channel.writeInbound(Unpooled.wrappedBuffer(bytes));
            return channel.readInbound();
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
