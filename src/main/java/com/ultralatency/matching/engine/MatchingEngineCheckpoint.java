package com.ultralatency.matching.engine;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

import com.ultralatency.matching.domain.Side;
import com.ultralatency.matching.orderbook.OrderBookCheckpoint;

/**
 * Immutable checkpoint of the observable state required to resume one engine.
 *
 * @param lastAppliedCommandSequence last applied input sequence, or zero at genesis
 * @param nextTradeId next TradeId owned by the engine
 * @param nextEventSequence next EventSequence owned by the engine
 * @param orderBook canonical active order-book state
 */
public record MatchingEngineCheckpoint(
        long lastAppliedCommandSequence,
        long nextTradeId,
        long nextEventSequence,
        OrderBookCheckpoint orderBook) {

    /**
     * Validates checkpoint counters and state.
     */
    public MatchingEngineCheckpoint {
        if (lastAppliedCommandSequence < 0) {
            throw new IllegalArgumentException("Last applied command sequence must not be negative");
        }
        if (nextTradeId <= 0) {
            throw new IllegalArgumentException("Next TradeId must be positive");
        }
        if (nextEventSequence <= 0) {
            throw new IllegalArgumentException("Next EventSequence must be positive");
        }
        orderBook = Objects.requireNonNull(orderBook, "orderBook");
        orderBook.allOrders().forEach(order -> {
            if (order.originalCommandSequence().value() > lastAppliedCommandSequence) {
                throw new IllegalArgumentException(
                        "Active order sequence is beyond the checkpoint sequence");
            }
        });
    }

    /**
     * Returns the number of active orders in the checkpoint.
     *
     * @return active order count
     */
    public int activeOrderCount() {
        return orderBook.activeOrderCount();
    }

    /**
     * Computes the canonical counter-sensitive checkpoint digest.
     *
     * <p>The descriptor is 32 bytes in big-endian order, followed by the exact
     * canonical 48-byte order records used by Snapshot v1. The returned array
     * is newly allocated on every call.</p>
     *
     * @return raw SHA-256 digest
     */
    public byte[] canonicalCheckpointDigest() {
        final MessageDigest digest = sha256();
        final ByteBuffer descriptor = ByteBuffer.allocate(32).order(ByteOrder.BIG_ENDIAN);
        descriptor.putLong(lastAppliedCommandSequence)
                .putLong(nextTradeId)
                .putLong(nextEventSequence)
                .putInt(activeOrderCount())
                .putInt(48);
        digest.update(descriptor.array());
        orderBook.allOrders().forEach(order -> digest.update(canonicalOrderBytes(order)));
        return digest.digest();
    }

    private static byte[] canonicalOrderBytes(
            final OrderBookCheckpoint.RestingOrderCheckpoint order) {
        final ByteBuffer buffer = ByteBuffer.allocate(48).order(ByteOrder.BIG_ENDIAN);
        buffer.putLong(order.orderId().value());
        buffer.put((byte) (order.side() == Side.BUY ? 1 : 2));
        buffer.put(new byte[7]);
        buffer.putLong(order.price().ticks());
        buffer.putLong(order.originalQuantity().units());
        buffer.putLong(order.remainingQuantity().units());
        buffer.putLong(order.originalCommandSequence().value());
        return buffer.array();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException exception) {
            throw new AssertionError("JDK must provide SHA-256", exception);
        }
    }
}
