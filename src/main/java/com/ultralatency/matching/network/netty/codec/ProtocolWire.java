package com.ultralatency.matching.network.netty.codec;

import com.ultralatency.matching.network.protocol.ProtocolConstants;
import io.netty.buffer.ByteBuf;

/**
 * Package-local wire helpers shared by request and response codecs.
 */
final class ProtocolWire {

    private ProtocolWire() {
        // Utility class.
    }

    static void writeHeader(
            final ByteBuf out,
            final int messageType,
            final int frameLength) {
        writeHeader(out, ProtocolConstants.VERSION, messageType, frameLength);
    }

    static void writeHeader(
            final ByteBuf out,
            final int version,
            final int messageType,
            final int frameLength) {
        out.writeInt(ProtocolConstants.MAGIC);
        out.writeByte(version);
        out.writeByte(messageType);
        out.writeShort(0);
        out.writeInt(frameLength);
        out.writeInt(0);
    }

    static void validateHeader(final ByteBuf frame, final int index, final int expectedLength) {
        validateHeader(frame, index, expectedLength, ProtocolConstants.VERSION);
    }

    static void validateHeader(
            final ByteBuf frame,
            final int index,
            final int expectedLength,
            final int expectedVersion) {
        if (frame.readableBytes() != expectedLength) {
            throw new ProtocolCodecException(
                    com.ultralatency.matching.network.protocol.ProtocolErrorCode.MALFORMED_FRAME,
                    "Unexpected frame length");
        }
        if (frame.getInt(index) != ProtocolConstants.MAGIC) {
            throw new ProtocolCodecException(
                    com.ultralatency.matching.network.protocol.ProtocolErrorCode.MALFORMED_FRAME,
                    "Invalid protocol magic");
        }
        if (frame.getUnsignedByte(index + 4) != expectedVersion) {
            throw new ProtocolCodecException(
                    com.ultralatency.matching.network.protocol.ProtocolErrorCode.UNSUPPORTED_VERSION,
                    "Unexpected protocol version");
        }
        if (frame.getUnsignedShort(index + 6) != 0 || frame.getInt(index + 12) != 0) {
            throw new ProtocolCodecException(
                    com.ultralatency.matching.network.protocol.ProtocolErrorCode.INVALID_FIELD,
                    "Non-zero header flags or reserved bytes");
        }
        if (frame.getInt(index + 8) != expectedLength) {
            throw new ProtocolCodecException(
                    com.ultralatency.matching.network.protocol.ProtocolErrorCode.MALFORMED_FRAME,
                    "Declared frame length does not match payload");
        }
    }
}
