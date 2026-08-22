package com.ultralatency.matching.orderbook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.domain.Side;

class OrderBookCheckpointTest {

    @Test
    void capturesAndRestoresCanonicalPriceTimeState() {
        final OrderBook book = new OrderBook();
        book.add(order(1, Side.BUY, 101, 10, 1));
        book.add(order(2, Side.BUY, 101, 20, 2));
        book.add(order(3, Side.BUY, 100, 30, 3));
        book.add(order(4, Side.SELL, 103, 40, 4));
        book.add(order(5, Side.SELL, 103, 50, 5));
        book.applyExecution(new OrderId(1), new Quantity(4));

        final OrderBookCheckpoint checkpoint = book.checkpoint();

        assertEquals(
                List.of(new OrderId(1), new OrderId(2), new OrderId(3)),
                checkpoint.bidOrders().stream().map(OrderBookCheckpoint.RestingOrderCheckpoint::orderId)
                        .toList());
        assertEquals(
                List.of(new OrderId(4), new OrderId(5)),
                checkpoint.askOrders().stream().map(OrderBookCheckpoint.RestingOrderCheckpoint::orderId)
                        .toList());
        assertEquals(6, checkpoint.bidOrders().getFirst().remainingQuantity().units());

        final OrderBook restored = OrderBook.fromCheckpoint(checkpoint);

        assertEquals(checkpoint, restored.checkpoint());
        assertEquals(5, restored.activeOrderCount());
        assertEquals(6, restored.activeOrder(new OrderId(1)).orElseThrow().remainingQuantityUnits());
        assertEquals(
                new OrderId(1),
                restored.bidLevelAt(new Price(101)).orElseThrow().head().order().orderId());
        assertEquals(
                new OrderId(2),
                restored.bidLevelAt(new Price(101)).orElseThrow().tail().order().orderId());
    }

    @Test
    void rejectsNonCanonicalOrderingAndDuplicateIdentifiers() {
        final OrderBookCheckpoint.RestingOrderCheckpoint first =
                checkpointOrder(1, Side.BUY, 100, 1, 1);
        final OrderBookCheckpoint.RestingOrderCheckpoint second =
                checkpointOrder(2, Side.BUY, 101, 1, 2);
        assertThrows(
                IllegalArgumentException.class,
                () -> new OrderBookCheckpoint(List.of(first, second), List.of()));

        final OrderBookCheckpoint.RestingOrderCheckpoint duplicate =
                checkpointOrder(1, Side.SELL, 102, 1, 3);
        assertThrows(
                IllegalArgumentException.class,
                () -> new OrderBookCheckpoint(List.of(first), List.of(duplicate)));
    }

    @Test
    void rejectsRemainingQuantityGreaterThanOriginalQuantity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> checkpointOrder(1, Side.BUY, 100, 10, 1, 11));
    }

    private static OrderBookCheckpoint.RestingOrderCheckpoint checkpointOrder(
            final long id,
            final Side side,
            final long price,
            final long remaining,
            final long sequence) {
        return checkpointOrder(id, side, price, remaining, sequence, remaining);
    }

    private static OrderBookCheckpoint.RestingOrderCheckpoint checkpointOrder(
            final long id,
            final Side side,
            final long price,
            final long original,
            final long sequence,
            final long remaining) {
        return new OrderBookCheckpoint.RestingOrderCheckpoint(
                new OrderId(id),
                side,
                new Price(price),
                new Quantity(original),
                new Quantity(remaining),
                new Sequence(sequence));
    }

    private static com.ultralatency.matching.domain.Order order(
            final long id,
            final Side side,
            final long price,
            final long quantity,
            final long sequence) {
        return com.ultralatency.matching.domain.Order.limit(
                new OrderId(id),
                side,
                new Price(price),
                new Quantity(quantity),
                new Sequence(sequence));
    }
}
