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

class AskBookTest {

    @Test
    void ordersPricesFromLowestToHighestAndKeepsSamePriceFifo() {
        final AskBook book = new AskBook();
        final OrderNode high = book.add(sell(1, 102, 10, 1));
        final OrderNode bestFirst = book.add(sell(2, 100, 20, 2));
        final OrderNode bestSecond = book.add(sell(3, 100, 30, 3));

        assertEquals(Optional.of(new Price(100)), book.bestPrice());
        assertEquals(2, book.priceLevelCount());
        assertSame(bestFirst, book.bestLevel().orElseThrow().head());
        assertSame(bestSecond, bestFirst.next());
        assertSame(high, book.levelAt(new Price(102)).orElseThrow().head());
    }

    @Test
    void removesNonBestAndBestLevelsWithoutLeavingStalePrices() {
        final AskBook book = new AskBook();
        final OrderNode best = book.add(sell(1, 100, 10, 1));
        final OrderNode middle = book.add(sell(2, 101, 5, 2));
        final OrderNode high = book.add(sell(3, 102, 5, 3));

        assertTrue(book.cancel(middle));
        assertEquals(Optional.of(new Price(100)), book.bestPrice());
        assertEquals(2, book.priceLevelCount());
        assertTrue(book.levelAt(new Price(101)).isEmpty());

        assertTrue(book.cancel(best));
        assertEquals(Optional.of(new Price(102)), book.bestPrice());
        assertSame(high, book.bestLevel().orElseThrow().head());

        assertTrue(book.cancel(high));
        assertEquals(Optional.empty(), book.bestPrice());
        assertTrue(book.isEmpty());
    }

    @Test
    void rejectsWrongSideMarketAndForeignCancellation() {
        final AskBook book = new AskBook();
        final BidBook bidBook = new BidBook();
        final Order buy = Order.limit(
                new OrderId(1),
                Side.BUY,
                new Price(100),
                new Quantity(1),
                new Sequence(1));
        final Order market = Order.market(
                new OrderId(2),
                Side.SELL,
                new Quantity(1),
                new Sequence(2));
        final OrderNode foreignNode = bidBook.add(buy);

        assertThrows(IllegalArgumentException.class, () -> book.add(buy));
        assertThrows(IllegalArgumentException.class, () -> book.add(market));
        assertFalse(book.cancel(foreignNode));
        assertTrue(bidBook.cancel(foreignNode));
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
