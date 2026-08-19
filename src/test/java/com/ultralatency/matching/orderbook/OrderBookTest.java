package com.ultralatency.matching.orderbook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ultralatency.matching.domain.Order;
import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.OrderStatus;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.domain.Side;

class OrderBookTest {

    @Test
    void aggregatesBothSidesAndExposesBestPricesAndActiveOrders() {
        final OrderBook book = new OrderBook();
        final Order bid = buy(1, 101, 10, 1);
        final Order ask = sell(2, 100, 20, 2);

        book.add(bid);
        book.add(ask);

        assertEquals(Optional.of(new Price(101)), book.bestBid());
        assertEquals(Optional.of(new Price(100)), book.bestAsk());
        assertEquals(2, book.activeOrderCount());
        assertEquals(1, book.bidPriceLevelCount());
        assertEquals(1, book.askPriceLevelCount());
        assertEquals(Optional.of(bid), book.activeOrder(new OrderId(1)));
        assertEquals(Optional.of(ask), book.activeOrder(new OrderId(2)));
        assertEquals(
                book.activeNode(new OrderId(1)).orElseThrow(),
                book.bidLevelAt(new Price(101)).orElseThrow().head());
    }

    @Test
    void cancelUnlinksNodeRemovesIndexAndCleansEmptyLevel() {
        final OrderBook book = new OrderBook();
        final Order first = buy(1, 101, 10, 1);
        final Order second = buy(2, 100, 5, 2);

        book.add(first);
        book.add(second);

        assertTrue(book.cancel(new OrderId(1)));
        assertEquals(OrderStatus.CANCELED, first.status());
        assertEquals(1, book.activeOrderCount());
        assertEquals(Optional.empty(), book.activeOrder(new OrderId(1)));
        assertEquals(Optional.of(new Price(100)), book.bestBid());
        assertTrue(book.bidLevelAt(new Price(101)).isEmpty());
        assertFalse(
                book.activeNode(new OrderId(1)).isPresent());

        assertFalse(book.cancel(new OrderId(1)));
        assertEquals(1, book.activeOrderCount());
        assertEquals(Optional.of(second), book.activeOrder(new OrderId(2)));
    }

    @Test
    void rejectsDuplicateActiveIdButAllowsReuseAfterCancellation() {
        final OrderBook book = new OrderBook();
        final Order original = buy(1, 100, 10, 1);
        final Order duplicate = buy(1, 101, 20, 2);

        book.add(original);

        assertThrows(
                IllegalArgumentException.class,
                () -> book.add(duplicate));
        assertEquals(1, book.activeOrderCount());
        assertEquals(Optional.of(new Price(100)), book.bestBid());

        assertTrue(book.cancel(new OrderId(1)));
        book.add(duplicate);
        assertEquals(1, book.activeOrderCount());
        assertEquals(Optional.of(new Price(101)), book.bestBid());
        assertEquals(Optional.of(duplicate), book.activeOrder(new OrderId(1)));
    }

    @Test
    void partialExecutionKeepsIndexAndFullExecutionRemovesIt() {
        final OrderBook book = new OrderBook();
        final Order order = buy(1, 100, 10, 1);

        book.add(order);
        book.applyExecution(new OrderId(1), new Quantity(4));

        assertEquals(OrderStatus.PARTIALLY_FILLED, order.status());
        assertEquals(6, order.remainingQuantityUnits());
        assertEquals(6, book.bidLevelAt(new Price(100))
                .orElseThrow().totalQuantityUnits());
        assertEquals(Optional.of(order), book.activeOrder(new OrderId(1)));
        assertTrue(book.activeNode(new OrderId(1)).orElseThrow().isLinked());

        book.applyExecution(new OrderId(1), new Quantity(6));

        assertEquals(OrderStatus.FILLED, order.status());
        assertEquals(0, book.activeOrderCount());
        assertEquals(Optional.empty(), book.activeOrder(new OrderId(1)));
        assertEquals(Optional.empty(), book.bidLevelAt(new Price(100)));
        assertEquals(Optional.empty(), book.bestBid());
    }

    @Test
    void rejectsInvalidOrdersBeforeTheyEnterEitherSide() {
        final OrderBook book = new OrderBook();
        final Order market = Order.market(
                new OrderId(1),
                Side.BUY,
                new Quantity(1),
                new Sequence(1));
        final Order canceled = buy(2, 100, 1, 2);
        canceled.cancel();

        assertThrows(
                IllegalArgumentException.class,
                () -> book.add(market));
        assertThrows(
                IllegalStateException.class,
                () -> book.add(canceled));
        assertEquals(0, book.activeOrderCount());
        assertEquals(Optional.empty(), book.bestBid());
        assertEquals(Optional.empty(), book.bestAsk());
    }

    @Test
    void cancelAndExecutionOfOneSideDoNotAffectTheOtherSide() {
        final OrderBook book = new OrderBook();
        final Order bid = buy(1, 99, 10, 1);
        final Order ask = sell(2, 101, 20, 2);

        book.add(bid);
        book.add(ask);
        assertSame(
                ask,
                book.activeOrder(new OrderId(2)).orElseThrow());

        book.applyExecution(new OrderId(1), new Quantity(5));
        assertEquals(Optional.of(new Price(99)), book.bestBid());
        assertEquals(Optional.of(new Price(101)), book.bestAsk());
        assertEquals(5, book.bidLevelAt(new Price(99))
                .orElseThrow().totalQuantityUnits());
        assertEquals(20, book.askLevelAt(new Price(101))
                .orElseThrow().totalQuantityUnits());

        assertTrue(book.cancel(new OrderId(2)));
        assertEquals(Optional.of(new Price(99)), book.bestBid());
        assertEquals(Optional.empty(), book.bestAsk());
        assertEquals(1, book.activeOrderCount());
    }

    private static Order buy(
            final long id,
            final long price,
            final long quantity,
            final long sequence) {
        return Order.limit(
                new OrderId(id),
                Side.BUY,
                new Price(price),
                new Quantity(quantity),
                new Sequence(sequence));
    }

    private static Order sell(
            final long id,
            final long price,
            final long quantity,
            final long sequence) {
        return Order.limit(
                new OrderId(id),
                Side.SELL,
                new Price(price),
                new Quantity(quantity),
                new Sequence(sequence));
    }
}
