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
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.domain.Side;

class BidBookTest {

    @Test
    void ordersPricesFromHighestToLowestAndKeepsSamePriceFifo() {
        final BidBook book = new BidBook();
        final OrderNode low = book.add(buy(1, 99, 10, 1));
        final OrderNode bestFirst = book.add(buy(2, 101, 20, 2));
        final OrderNode bestSecond = book.add(buy(3, 101, 30, 3));

        assertEquals(Optional.of(new Price(101)), book.bestPrice());
        assertEquals(2, book.priceLevelCount());
        assertSame(bestFirst, book.bestLevel().orElseThrow().head());
        assertSame(bestSecond, bestFirst.next());
        assertSame(low, book.levelAt(new Price(99)).orElseThrow().head());
    }

    @Test
    void removesEmptyLevelsAndRefreshesBestBidAfterCancelAndFill() {
        final BidBook book = new BidBook();
        final OrderNode best = book.add(buy(1, 101, 10, 1));
        final OrderNode next = book.add(buy(2, 100, 5, 2));

        assertTrue(book.cancel(best));
        assertEquals(Optional.of(new Price(100)), book.bestPrice());
        assertEquals(1, book.priceLevelCount());
        assertTrue(book.levelAt(new Price(101)).isEmpty());

        book.applyExecution(next, new Quantity(5));
        assertTrue(book.isEmpty());
        assertEquals(Optional.empty(), book.bestPrice());
        assertEquals(0, book.priceLevelCount());
    }

    @Test
    void rejectsWrongSideMarketAndTerminalOrders() {
        final BidBook book = new BidBook();
        final Order sell = Order.limit(
                new OrderId(1),
                Side.SELL,
                new Price(100),
                new Quantity(1),
                new Sequence(1));
        final Order market = Order.market(
                new OrderId(2),
                Side.BUY,
                new Quantity(1),
                new Sequence(2));
        final Order canceled = buy(3, 100, 1, 3);
        canceled.cancel();

        assertThrows(IllegalArgumentException.class, () -> book.add(sell));
        assertThrows(IllegalArgumentException.class, () -> book.add(market));
        assertThrows(IllegalStateException.class, () -> book.add(canceled));
        assertTrue(book.isEmpty());
    }

    @Test
    void foreignAndRepeatedCancellationAreNoOps() {
        final BidBook book = new BidBook();
        final BidBook otherBook = new BidBook();
        final OrderNode node = book.add(buy(1, 100, 1, 1));

        assertFalse(otherBook.cancel(node));
        assertTrue(book.cancel(node));
        assertFalse(book.cancel(node));
        assertEquals(Optional.empty(), book.bestPrice());
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
}
