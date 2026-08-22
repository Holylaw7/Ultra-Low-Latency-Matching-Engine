package com.ultralatency.matching.persistence.snapshot;

import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.domain.Side;
import com.ultralatency.matching.engine.MatchingEngineCheckpoint;
import com.ultralatency.matching.orderbook.OrderBookCheckpoint;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.CRC32C;

/** Exact, bounded and strict codec for Snapshot v1. */
public final class SnapshotCodec {

    /** Snapshot v1 format version. */
    public static final int FORMAT_VERSION = 1;
    /** Fixed header length. */
    public static final int HEADER_LENGTH = 128;
    /** Fixed active-order record length. */
    public static final int ORDER_RECORD_LENGTH = 48;
    /** Footer CRC32C length. */
    public static final int FOOTER_LENGTH = Integer.BYTES;
    /** WAL format version bound by Snapshot v1. */
    public static final int WAL_FORMAT_VERSION = 1;
    /** Exact byte length before active-order records. */
    public static final int PAYLOAD_OFFSET = HEADER_LENGTH;
    /** Exact file magic. */
    public static final byte[] MAGIC = "ULMESNP1".getBytes(StandardCharsets.US_ASCII);

    private final SnapshotLimits limits;

    /** Creates a codec with the approved bounded defaults. */
    public SnapshotCodec() {
        this(SnapshotLimits.defaults());
    }

    /** Creates a codec with explicit decode/allocation limits. */
    public SnapshotCodec(final SnapshotLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    /** @return configured allocation and file-size limits */
    public SnapshotLimits limits() {
        return limits;
    }

    /** Encodes one immutable Snapshot v1 value. */
    public byte[] encode(final Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        final MatchingEngineCheckpoint checkpoint = snapshot.checkpoint();
        final int activeCount = checkpoint.activeOrderCount();
        final int totalLength = totalLength(activeCount);
        validateLimits(activeCount, totalLength);
        final ByteBuffer buffer = ByteBuffer.allocate(totalLength).order(ByteOrder.BIG_ENDIAN);
        buffer.put(MAGIC);
        buffer.putInt(FORMAT_VERSION);
        buffer.putInt(HEADER_LENGTH);
        buffer.putLong(totalLength);
        buffer.putLong(checkpoint.lastAppliedCommandSequence());
        buffer.putLong(checkpoint.nextTradeId());
        buffer.putLong(checkpoint.nextEventSequence());
        buffer.putInt(activeCount);
        buffer.putInt(ORDER_RECORD_LENGTH);
        buffer.putInt(WAL_FORMAT_VERSION);
        buffer.putInt(0);
        buffer.put(snapshot.walPrefixDigest());
        buffer.put(snapshot.canonicalCheckpointDigest());
        for (final OrderBookCheckpoint.RestingOrderCheckpoint order
                : checkpoint.orderBook().allOrders()) {
            putOrder(buffer, order);
        }
        putCrc(buffer.array());
        return buffer.array();
    }

    /**
     * Decodes and strictly validates one complete Snapshot v1 byte array.
     *
     * @param bytes exact file bytes
     * @return immutable decoded Snapshot
     */
    public Snapshot decode(final byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length < HEADER_LENGTH + FOOTER_LENGTH) {
            throw new SnapshotFormatException("Snapshot is shorter than its fixed header");
        }
        if (bytes.length > limits.maxSnapshotBytes()) {
            throw new SnapshotFormatException("Snapshot exceeds configured size limit");
        }
        final ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        validateMagic(buffer);
        final int version = buffer.getInt();
        if (version != FORMAT_VERSION) {
            throw new SnapshotFormatException("Unsupported Snapshot version: " + version);
        }
        final int headerLength = buffer.getInt();
        if (headerLength != HEADER_LENGTH) {
            throw new SnapshotFormatException("Invalid Snapshot header length: " + headerLength);
        }
        final long declaredTotalLength = buffer.getLong();
        if (declaredTotalLength != bytes.length
                || declaredTotalLength > limits.maxSnapshotBytes()
                || declaredTotalLength < HEADER_LENGTH + FOOTER_LENGTH) {
            throw new SnapshotFormatException("Snapshot total length does not match input");
        }
        final long checkpointSequence = buffer.getLong();
        final long nextTradeId = buffer.getLong();
        final long nextEventSequence = buffer.getLong();
        final int activeCount = buffer.getInt();
        final int recordLength = buffer.getInt();
        final int walVersion = buffer.getInt();
        final int flags = buffer.getInt();
        validateHeaderValues(
                checkpointSequence,
                nextTradeId,
                nextEventSequence,
                activeCount,
                recordLength,
                walVersion,
                flags,
                declaredTotalLength);
        final byte[] walPrefixDigest = new byte[Snapshot.SHA256_LENGTH];
        final byte[] canonicalDigest = new byte[Snapshot.SHA256_LENGTH];
        buffer.get(walPrefixDigest);
        buffer.get(canonicalDigest);
        final List<OrderBookCheckpoint.RestingOrderCheckpoint> bids = new ArrayList<>();
        final List<OrderBookCheckpoint.RestingOrderCheckpoint> asks = new ArrayList<>();
        for (int index = 0; index < activeCount; index++) {
            final OrderBookCheckpoint.RestingOrderCheckpoint order = readOrder(buffer);
            if (order.side() == Side.BUY) {
                bids.add(order);
            } else {
                asks.add(order);
            }
        }
        final long storedCrc = Integer.toUnsignedLong(buffer.getInt());
        final CRC32C crc = new CRC32C();
        crc.update(bytes, 0, bytes.length - FOOTER_LENGTH);
        if (crc.getValue() != storedCrc) {
            throw new SnapshotFormatException("Snapshot CRC32C mismatch");
        }
        final MatchingEngineCheckpoint checkpoint;
        try {
            checkpoint = new MatchingEngineCheckpoint(
                    checkpointSequence,
                    nextTradeId,
                    nextEventSequence,
                    new OrderBookCheckpoint(bids, asks));
        } catch (final RuntimeException exception) {
            throw new SnapshotFormatException("Invalid canonical checkpoint state", exception);
        }
        final Snapshot snapshot;
        try {
            snapshot = new Snapshot(checkpoint, walPrefixDigest);
        } catch (final RuntimeException exception) {
            throw new SnapshotFormatException("Invalid Snapshot value", exception);
        }
        if (!MessageDigest.isEqual(canonicalDigest, snapshot.canonicalCheckpointDigest())) {
            throw new SnapshotFormatException("Snapshot canonical checkpoint digest mismatch");
        }
        return snapshot;
    }

    private static void validateMagic(final ByteBuffer buffer) {
        for (final byte expected : MAGIC) {
            if (buffer.get() != expected) {
                throw new SnapshotFormatException("Invalid Snapshot magic");
            }
        }
    }

    private void validateHeaderValues(
            final long checkpointSequence,
            final long nextTradeId,
            final long nextEventSequence,
            final int activeCount,
            final int recordLength,
            final int walVersion,
            final int flags,
            final long declaredTotalLength) {
        if (checkpointSequence < 1 || nextTradeId < 1 || nextEventSequence < 1) {
            throw new SnapshotFormatException("Snapshot counters must be positive");
        }
        if (recordLength != ORDER_RECORD_LENGTH) {
            throw new SnapshotFormatException("Invalid Snapshot order record length");
        }
        if (walVersion != WAL_FORMAT_VERSION) {
            throw new SnapshotFormatException("Unsupported bound WAL version: " + walVersion);
        }
        if (flags != 0) {
            throw new SnapshotFormatException("Snapshot flags must be zero");
        }
        if (activeCount < 0 || activeCount > limits.maxActiveOrders()) {
            throw new SnapshotFormatException("Snapshot active-order count exceeds limits");
        }
        if (declaredTotalLength != totalLength(activeCount)) {
            throw new SnapshotFormatException("Snapshot length/count relationship is invalid");
        }
    }

    private static OrderBookCheckpoint.RestingOrderCheckpoint readOrder(
            final ByteBuffer buffer) {
        final OrderId orderId;
        final Price price;
        final Quantity originalQuantity;
        final Quantity remainingQuantity;
        final Sequence originalSequence;
        try {
            orderId = new OrderId(buffer.getLong());
            final int sideCode = Byte.toUnsignedInt(buffer.get());
            if (sideCode != 1 && sideCode != 2) {
                throw new SnapshotFormatException("Invalid Snapshot side code");
            }
            for (int index = 0; index < 7; index++) {
                if (buffer.get() != 0) {
                    throw new SnapshotFormatException("Snapshot reserved bytes must be zero");
                }
            }
            price = new Price(buffer.getLong());
            originalQuantity = new Quantity(buffer.getLong());
            remainingQuantity = new Quantity(buffer.getLong());
            originalSequence = new Sequence(buffer.getLong());
            return new OrderBookCheckpoint.RestingOrderCheckpoint(
                    orderId,
                    sideCode == 1 ? Side.BUY : Side.SELL,
                    price,
                    originalQuantity,
                    remainingQuantity,
                    originalSequence);
        } catch (final SnapshotFormatException exception) {
            throw exception;
        } catch (final RuntimeException exception) {
            throw new SnapshotFormatException("Invalid Snapshot order record", exception);
        }
    }

    private static void putOrder(
            final ByteBuffer buffer,
            final OrderBookCheckpoint.RestingOrderCheckpoint order) {
        buffer.putLong(order.orderId().value());
        buffer.put((byte) (order.side() == Side.BUY ? 1 : 2));
        buffer.put(new byte[7]);
        buffer.putLong(order.price().ticks());
        buffer.putLong(order.originalQuantity().units());
        buffer.putLong(order.remainingQuantity().units());
        buffer.putLong(order.originalCommandSequence().value());
    }

    private static void putCrc(final byte[] bytes) {
        final CRC32C crc = new CRC32C();
        crc.update(bytes, 0, bytes.length - FOOTER_LENGTH);
        ByteBuffer.wrap(bytes, bytes.length - FOOTER_LENGTH, FOOTER_LENGTH)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt((int) crc.getValue());
    }

    private static int totalLength(final int activeCount) {
        try {
            return Math.addExact(
                    Math.addExact(HEADER_LENGTH, Math.multiplyExact(activeCount, ORDER_RECORD_LENGTH)),
                    FOOTER_LENGTH);
        } catch (final ArithmeticException exception) {
            throw new SnapshotFormatException("Snapshot length overflows integer range", exception);
        }
    }

    private void validateLimits(final int activeCount, final int totalLength) {
        if (activeCount > limits.maxActiveOrders()) {
            throw new SnapshotFormatException("Snapshot active-order count exceeds limits");
        }
        if (totalLength > limits.maxSnapshotBytes()) {
            throw new SnapshotFormatException("Snapshot exceeds configured size limit");
        }
    }
}
