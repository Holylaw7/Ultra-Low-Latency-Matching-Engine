package com.ultralatency.matching.persistence.wal;

import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.domain.Side;
import com.ultralatency.matching.engine.CancelOrderCommand;
import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.engine.SubmitLimitCommand;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.zip.CRC32C;

/**
 * Exact version-1 binary codec for project-owned engine commands.
 *
 * <p>The codec is deliberately independent of files, channels and engine
 * execution. All multi-byte values use big-endian byte order. CRC32C covers
 * the record body after the length field and before the checksum field.</p>
 */
public final class WalCommandCodec {

    /** Current persisted format version. */
    public static final int FORMAT_VERSION = 1;

    /** Exact segment header length. */
    public static final int SEGMENT_HEADER_LENGTH = 32;

    /** Maximum record length accepted before buffer allocation. */
    public static final int MAX_RECORD_LENGTH = 4096;

    /** Minimum supported record length, including envelope and checksum. */
    public static final int MIN_RECORD_LENGTH = 28;

    /** Minimum segment size that can contain the largest supported record. */
    public static final int MIN_SEGMENT_SIZE_BYTES = SEGMENT_HEADER_LENGTH + MAX_RECORD_LENGTH;

    private static final int RECORD_VERSION_OFFSET = Integer.BYTES;
    private static final int RECORD_TYPE_OFFSET = RECORD_VERSION_OFFSET + Byte.BYTES;
    private static final int RECORD_FLAGS_OFFSET = RECORD_TYPE_OFFSET + Byte.BYTES;
    private static final int RECORD_SEQUENCE_OFFSET = RECORD_FLAGS_OFFSET + Short.BYTES;
    private static final int RECORD_ORDER_ID_OFFSET = RECORD_SEQUENCE_OFFSET + Long.BYTES;
    private static final int RECORD_BODY_OFFSET = Integer.BYTES;
    private static final int RECORD_CHECKSUM_LENGTH = Integer.BYTES;
    private static final int SUBMIT_RECORD_LENGTH = 52;
    private static final int CANCEL_RECORD_LENGTH = 28;
    private static final byte SUBMIT_LIMIT_TYPE = 1;
    private static final byte CANCEL_ORDER_TYPE = 2;
    private static final byte BUY_SIDE = 1;
    private static final byte SELL_SIDE = 2;
    private static final byte[] SEGMENT_MAGIC = "ULMEWAL1".getBytes(StandardCharsets.US_ASCII);

    /**
     * Encodes a segment header.
     *
     * @param segmentId positive physical segment identifier
     * @param firstCommandSequence first logical command sequence
     * @return exact 32-byte header
     */
    public byte[] encodeSegmentHeader(
            final long segmentId,
            final Sequence firstCommandSequence) {
        final WalSegmentHeader header = new WalSegmentHeader(
                FORMAT_VERSION,
                segmentId,
                firstCommandSequence);
        final ByteBuffer buffer = ByteBuffer.allocate(SEGMENT_HEADER_LENGTH)
                .order(ByteOrder.BIG_ENDIAN);
        buffer.put(SEGMENT_MAGIC);
        buffer.putInt(header.version());
        buffer.putInt(SEGMENT_HEADER_LENGTH);
        buffer.putLong(header.segmentId());
        buffer.putLong(header.firstCommandSequence().value());
        return buffer.array();
    }

    /**
     * Decodes and validates a segment header.
     *
     * @param bytes exact header bytes
     * @return decoded header
     */
    public WalSegmentHeader decodeSegmentHeader(final byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length != SEGMENT_HEADER_LENGTH) {
            throw new WalFormatException("Segment header must be exactly 32 bytes");
        }
        final ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        for (final byte expected : SEGMENT_MAGIC) {
            if (buffer.get() != expected) {
                throw new WalFormatException("Invalid WAL segment magic");
            }
        }
        final int version = buffer.getInt();
        if (version != FORMAT_VERSION) {
            throw new WalFormatException("Unsupported WAL format version: " + version);
        }
        final int headerLength = buffer.getInt();
        if (headerLength != SEGMENT_HEADER_LENGTH) {
            throw new WalFormatException("Invalid WAL segment header length: " + headerLength);
        }
        try {
            return new WalSegmentHeader(version, buffer.getLong(), new Sequence(buffer.getLong()));
        } catch (final RuntimeException exception) {
            throw new WalFormatException("Invalid WAL segment header value", exception);
        }
    }

    /**
     * Encodes one supported engine command.
     *
     * @param command immutable engine command
     * @return exact version-1 record bytes
     */
    public byte[] encodeRecord(final EngineCommand command) {
        Objects.requireNonNull(command, "command");
        if (command instanceof SubmitLimitCommand submit) {
            return encodeSubmitLimit(submit);
        }
        if (command instanceof CancelOrderCommand cancel) {
            return encodeCancel(cancel);
        }
        throw new WalFormatException("Unsupported engine command: " + command.getClass());
    }

    /**
     * Decodes and validates one complete version-1 record.
     *
     * @param bytes complete record bytes including length and CRC32C
     * @return reconstructed immutable engine command
     */
    public EngineCommand decodeRecord(final byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length < Integer.BYTES) {
            throw new WalFormatException("WAL record is shorter than its length field");
        }
        final ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        final int declaredLength = header.getInt();
        if (declaredLength != bytes.length) {
            throw new WalFormatException(
                    "WAL record length mismatch: declared " + declaredLength
                            + ", actual " + bytes.length);
        }
        if (declaredLength < MIN_RECORD_LENGTH || declaredLength > MAX_RECORD_LENGTH) {
            throw new WalFormatException("WAL record length is outside supported bounds");
        }
        final int bodyLength = declaredLength - Integer.BYTES - RECORD_CHECKSUM_LENGTH;
        final int storedChecksumOffset = declaredLength - RECORD_CHECKSUM_LENGTH;
        final long storedChecksum = Integer.toUnsignedLong(header.getInt(storedChecksumOffset));
        final CRC32C checksum = new CRC32C();
        checksum.update(bytes, RECORD_BODY_OFFSET, bodyLength);
        if (checksum.getValue() != storedChecksum) {
            throw new WalFormatException("WAL record CRC32C mismatch");
        }
        final ByteBuffer body = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        body.position(RECORD_VERSION_OFFSET);
        final int recordVersion = Byte.toUnsignedInt(body.get());
        if (recordVersion != FORMAT_VERSION) {
            throw new WalFormatException("Unsupported WAL record version: " + recordVersion);
        }
        final int type = Byte.toUnsignedInt(body.get());
        final int flags = Short.toUnsignedInt(body.getShort());
        if (flags != 0) {
            throw new WalFormatException("WAL record flags must be zero");
        }
        final Sequence sequence = sequence(body.getLong());
        final OrderId orderId = orderId(body.getLong());
        return switch (type) {
            case SUBMIT_LIMIT_TYPE -> decodeSubmitLimit(bytes, body, declaredLength, sequence, orderId);
            case CANCEL_ORDER_TYPE -> decodeCancel(declaredLength, sequence, orderId);
            default -> throw new WalFormatException("Unsupported WAL command type: " + type);
        };
    }

    private static byte[] encodeSubmitLimit(final SubmitLimitCommand command) {
        final ByteBuffer buffer = recordBuffer(SUBMIT_RECORD_LENGTH);
        buffer.put((byte) FORMAT_VERSION);
        buffer.put(SUBMIT_LIMIT_TYPE);
        buffer.putShort((short) 0);
        buffer.putLong(command.sequence().value());
        buffer.putLong(command.orderId().value());
        buffer.put(sideCode(command.side()));
        buffer.put(new byte[7]);
        buffer.putLong(command.price().ticks());
        buffer.putLong(command.quantity().units());
        finishRecord(buffer);
        return buffer.array();
    }

    private static byte[] encodeCancel(final CancelOrderCommand command) {
        final ByteBuffer buffer = recordBuffer(CANCEL_RECORD_LENGTH);
        buffer.put((byte) FORMAT_VERSION);
        buffer.put(CANCEL_ORDER_TYPE);
        buffer.putShort((short) 0);
        buffer.putLong(command.sequence().value());
        buffer.putLong(command.orderId().value());
        finishRecord(buffer);
        return buffer.array();
    }

    private static ByteBuffer recordBuffer(final int recordLength) {
        final ByteBuffer buffer = ByteBuffer.allocate(recordLength).order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(recordLength);
        return buffer;
    }

    private static void finishRecord(final ByteBuffer buffer) {
        final byte[] bytes = buffer.array();
        final int checksumOffset = bytes.length - RECORD_CHECKSUM_LENGTH;
        final CRC32C checksum = new CRC32C();
        checksum.update(bytes, RECORD_BODY_OFFSET, checksumOffset - RECORD_BODY_OFFSET);
        buffer.putInt((int) checksum.getValue());
    }

    private static SubmitLimitCommand decodeSubmitLimit(
            final byte[] bytes,
            final ByteBuffer body,
            final int declaredLength,
            final Sequence sequence,
            final OrderId orderId) {
        if (declaredLength != SUBMIT_RECORD_LENGTH) {
            throw new WalFormatException("Invalid SUBMIT_LIMIT record length: " + declaredLength);
        }
        final int side = Byte.toUnsignedInt(body.get());
        for (int index = 0; index < 7; index++) {
            if (body.get() != 0) {
                throw new WalFormatException("WAL submit reserved bytes must be zero");
            }
        }
        final Side decodedSide = switch (side) {
            case BUY_SIDE -> Side.BUY;
            case SELL_SIDE -> Side.SELL;
            default -> throw new WalFormatException("Unsupported WAL side code: " + side);
        };
        try {
            return new SubmitLimitCommand(
                    sequence,
                    orderId,
                    decodedSide,
                    new Price(body.getLong()),
                    new Quantity(body.getLong()));
        } catch (final RuntimeException exception) {
            throw new WalFormatException("Invalid SUBMIT_LIMIT domain value", exception);
        }
    }

    private static CancelOrderCommand decodeCancel(
            final int declaredLength,
            final Sequence sequence,
            final OrderId orderId) {
        if (declaredLength != CANCEL_RECORD_LENGTH) {
            throw new WalFormatException("Invalid CANCEL_ORDER record length: " + declaredLength);
        }
        return new CancelOrderCommand(sequence, orderId);
    }

    private static Sequence sequence(final long value) {
        try {
            return new Sequence(value);
        } catch (final RuntimeException exception) {
            throw new WalFormatException("Invalid WAL command sequence", exception);
        }
    }

    private static OrderId orderId(final long value) {
        try {
            return new OrderId(value);
        } catch (final RuntimeException exception) {
            throw new WalFormatException("Invalid WAL order id", exception);
        }
    }

    private static byte sideCode(final Side side) {
        return switch (Objects.requireNonNull(side, "side")) {
            case BUY -> BUY_SIDE;
            case SELL -> SELL_SIDE;
        };
    }
}
