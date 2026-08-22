package com.ultralatency.matching.engine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.domain.Side;
import com.ultralatency.matching.orderbook.OrderBookCheckpoint;

class MatchingEngineCheckpointTest {

    @Test
    void restorePreservesCountersBookStateAndNextResult() {
        final MatchingEngine original = new MatchingEngine();
        original.process(submit(1, 10, Side.SELL, 100, 10));
        original.process(submit(2, 11, Side.SELL, 100, 20));
        original.process(submit(3, 12, Side.BUY, 100, 15));

        final MatchingEngineCheckpoint checkpoint = original.checkpoint();
        final MatchingEngine restored = MatchingEngine.fromCheckpoint(checkpoint);

        assertEquals(checkpoint, restored.checkpoint());
        assertArrayEquals(
                checkpoint.canonicalCheckpointDigest(),
                restored.checkpoint().canonicalCheckpointDigest());

        final EngineResult originalNext = original.process(submit(4, 13, Side.BUY, 100, 1));
        final EngineResult restoredNext = restored.process(submit(4, 13, Side.BUY, 100, 1));

        assertEquals(originalNext, restoredNext);
        assertEquals(3, checkpoint.nextTradeId());
        assertEquals(3, checkpoint.nextEventSequence());
        assertEquals(14, restored.checkpoint().orderBook().askOrders().getFirst()
                .remainingQuantity().units());
    }

    @Test
    void checkpointDigestIncludesCountersAndCanonicalState() {
        final MatchingEngineCheckpoint first = new MatchingEngine().checkpoint();
        final MatchingEngineCheckpoint second = new MatchingEngineCheckpoint(
                0,
                2,
                1,
                new OrderBookCheckpoint(java.util.List.of(), java.util.List.of()));

        assertEquals(first.orderBook(), second.orderBook());
        assertFalse(Arrays.equals(
                first.canonicalCheckpointDigest(),
                second.canonicalCheckpointDigest()));
    }

    @Test
    void rejectsOrderStateBeyondCheckpointSequence() {
        final OrderBookCheckpoint.RestingOrderCheckpoint order =
                new OrderBookCheckpoint.RestingOrderCheckpoint(
                        new OrderId(1),
                        Side.BUY,
                        new Price(100),
                        new Quantity(10),
                        new Quantity(10),
                        new Sequence(2));
        final OrderBookCheckpoint orderBook = new OrderBookCheckpoint(
                java.util.List.of(order),
                java.util.List.of());

        assertThrows(
                IllegalArgumentException.class,
                () -> new MatchingEngineCheckpoint(1, 1, 1, orderBook));
    }

    private static SubmitLimitCommand submit(
            final long sequence,
            final long orderId,
            final Side side,
            final long price,
            final long quantity) {
        return new SubmitLimitCommand(
                new Sequence(sequence),
                new OrderId(orderId),
                side,
                new Price(price),
                new Quantity(quantity));
    }
}
