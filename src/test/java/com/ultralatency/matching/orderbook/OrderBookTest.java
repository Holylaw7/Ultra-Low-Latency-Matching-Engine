package com.ultralatency.matching.orderbook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
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

    @Test
    void emptyOppositeSideRestsIncomingLimitWithoutFragments() {
        final OrderBook book = new OrderBook();
        final Order incoming = buy(1, 100, 10, 1);

        assertEquals(List.of(), book.matchLimit(incoming));
        assertEquals(OrderStatus.NEW, incoming.status());
        assertEquals(Optional.of(incoming), book.activeOrder(new OrderId(1)));
        assertEquals(Optional.of(new Price(100)), book.bestBid());
        assertEquals(1, book.activeOrderCount());
    }

    @Test
    void nonCrossingIncomingLimitRestsOnItsOwnSide() {
        final OrderBook book = new OrderBook();
        final Order maker = sell(1, 101, 10, 1);
        final Order incoming = buy(2, 100, 20, 2);
        book.add(maker);

        assertEquals(List.of(), book.matchLimit(incoming));
        assertEquals(OrderStatus.NEW, incoming.status());
        assertEquals(Optional.of(incoming), book.activeOrder(new OrderId(2)));
        assertEquals(Optional.of(new Price(100)), book.bestBid());
        assertEquals(Optional.of(new Price(101)), book.bestAsk());
        assertEquals(2, book.activeOrderCount());
    }

    @Test
    void exactFillUsesMakerPriceAndCleansMakerState() {
        final OrderBook book = new OrderBook();
        final Order maker = sell(1, 100, 10, 1);
        final Order incoming = buy(2, 105, 10, 2);
        book.add(maker);

        final List<MatchFragment> fragments = book.matchLimit(incoming);

        assertEquals(
                List.of(new MatchFragment(
                        new OrderId(1),
                        new OrderId(2),
                        new Price(100),
                        new Quantity(10),
                        0,
                        0)),
                fragments);
        assertEquals(OrderStatus.FILLED, maker.status());
        assertEquals(OrderStatus.FILLED, incoming.status());
        assertEquals(0, book.activeOrderCount());
        assertEquals(Optional.empty(), book.bestAsk());
        assertEquals(Optional.empty(), book.activeOrder(new OrderId(1)));
        assertEquals(Optional.empty(), book.activeOrder(new OrderId(2)));
        assertThrows(
                UnsupportedOperationException.class,
                () -> fragments.add(null));
    }

    @Test
    void partialMakerFillKeepsMakerAtQueueHead() {
        final OrderBook book = new OrderBook();
        final Order maker = sell(1, 100, 100, 1);
        final Order incoming = buy(2, 100, 40, 2);
        book.add(maker);

        final List<MatchFragment> fragments = book.matchLimit(incoming);

        assertEquals(
                List.of(new MatchFragment(
                        new OrderId(1),
                        new OrderId(2),
                        new Price(100),
                        new Quantity(40),
                        60,
                        0)),
                fragments);
        assertEquals(OrderStatus.PARTIALLY_FILLED, maker.status());
        assertEquals(60, maker.remainingQuantityUnits());
        assertEquals(1, book.activeOrderCount());
        assertSame(
                maker,
                book.activeOrder(new OrderId(1)).orElseThrow());
        assertEquals(
                60,
                book.askLevelAt(new Price(100))
                        .orElseThrow()
                        .totalQuantityUnits());
        assertEquals(Optional.empty(), book.activeOrder(new OrderId(2)));
    }

    @Test
    void takerResidualRestsOnceAtIncomingPrice() {
        final OrderBook book = new OrderBook();
        final Order maker = sell(1, 100, 100, 1);
        final Order incoming = buy(2, 101, 250, 2);
        book.add(maker);

        final List<MatchFragment> fragments = book.matchLimit(incoming);

        assertEquals(
                List.of(new MatchFragment(
                        new OrderId(1),
                        new OrderId(2),
                        new Price(100),
                        new Quantity(100),
                        0,
                        150)),
                fragments);
        assertEquals(OrderStatus.FILLED, maker.status());
        assertEquals(OrderStatus.PARTIALLY_FILLED, incoming.status());
        assertEquals(Optional.of(incoming), book.activeOrder(new OrderId(2)));
        assertEquals(
                150,
                book.bidLevelAt(new Price(101))
                        .orElseThrow()
                        .totalQuantityUnits());
        assertSame(
                book.activeNode(new OrderId(2)).orElseThrow(),
                book.bidLevelAt(new Price(101)).orElseThrow().tail());
        assertEquals(Optional.empty(), book.bestAsk());
        assertEquals(Optional.of(new Price(101)), book.bestBid());
        assertEquals(1, book.activeOrderCount());
        assertLevelConsistency(
                book,
                book.bidLevelAt(new Price(101)).orElseThrow(),
                new OrderId(2));
        assertNonCrossed(book);
    }

    @Test
    void takerResidualAppendsAfterExistingSamePriceOrders() {
        final OrderBook book = new OrderBook();
        final Order existingBid = buy(1, 101, 20, 1);
        final Order maker = sell(2, 100, 100, 2);
        final Order incoming = buy(3, 101, 150, 3);
        book.add(existingBid);
        book.add(maker);

        book.matchLimit(incoming);

        final PriceLevel level = book.bidLevelAt(new Price(101)).orElseThrow();
        assertSame(existingBid, level.head().order());
        assertSame(incoming, level.tail().order());
        assertEquals(70, level.totalQuantityUnits());
        assertEquals(OrderStatus.PARTIALLY_FILLED, incoming.status());
        assertEquals(Optional.of(incoming), book.activeOrder(new OrderId(3)));
        assertLevelConsistency(book, level, new OrderId(1), new OrderId(3));
        assertSame(
                book.activeNode(new OrderId(3)).orElseThrow(),
                level.tail());
        assertNonCrossed(book);
    }

    @Test
    void buySweepConsumesBestPriceThenWorsePrices() {
        final OrderBook book = new OrderBook();
        final Order first = sell(1, 100, 100, 1);
        final Order second = sell(2, 101, 200, 2);
        final Order third = sell(3, 102, 300, 3);
        final Order incoming = buy(4, 102, 450, 4);
        book.add(first);
        book.add(second);
        book.add(third);

        final List<MatchFragment> fragments = book.matchLimit(incoming);

        assertEquals(
                List.of(
                        new MatchFragment(
                                new OrderId(1),
                                new OrderId(4),
                                new Price(100),
                                new Quantity(100),
                                0,
                                350),
                        new MatchFragment(
                                new OrderId(2),
                                new OrderId(4),
                                new Price(101),
                                new Quantity(200),
                                0,
                                150),
                        new MatchFragment(
                                new OrderId(3),
                                new OrderId(4),
                                new Price(102),
                                new Quantity(150),
                                150,
                                0)),
                fragments);
        assertEquals(OrderStatus.FILLED, incoming.status());
        assertEquals(OrderStatus.PARTIALLY_FILLED, third.status());
        assertEquals(Optional.empty(), book.askLevelAt(new Price(100)));
        assertEquals(Optional.of(new Price(102)), book.bestAsk());
        assertEquals(150, third.remainingQuantityUnits());
        assertEquals(1, book.activeOrderCount());
        assertLevelConsistency(
                book,
                book.askLevelAt(new Price(102)).orElseThrow(),
                new OrderId(3));
        assertNonCrossed(book);
    }

    @Test
    void samePriceFifoIsConsumedBeforeLaterOrders() {
        final OrderBook book = new OrderBook();
        final Order first = sell(1, 100, 100, 1);
        final Order second = sell(2, 100, 200, 2);
        final Order incoming = buy(3, 100, 150, 3);
        book.add(first);
        book.add(second);

        final List<MatchFragment> fragments = book.matchLimit(incoming);

        assertEquals(
                List.of(
                        new MatchFragment(
                                new OrderId(1),
                                new OrderId(3),
                                new Price(100),
                                new Quantity(100),
                                0,
                                50),
                        new MatchFragment(
                                new OrderId(2),
                                new OrderId(3),
                                new Price(100),
                                new Quantity(50),
                                150,
                                0)),
                fragments);
        assertEquals(OrderStatus.FILLED, first.status());
        assertEquals(OrderStatus.PARTIALLY_FILLED, second.status());
        assertSame(
                second,
                book.askLevelAt(new Price(100))
                        .orElseThrow()
                        .head()
                        .order());
        assertLevelConsistency(
                book,
                book.askLevelAt(new Price(100)).orElseThrow(),
                new OrderId(2));
        assertNonCrossed(book);
    }

    @Test
    void sellSweepIsSymmetricAndUsesMakerPrices() {
        final OrderBook book = new OrderBook();
        final Order first = buy(1, 102, 100, 1);
        final Order second = buy(2, 101, 200, 2);
        final Order third = buy(3, 100, 300, 3);
        final Order incoming = sell(4, 100, 450, 4);
        book.add(first);
        book.add(second);
        book.add(third);

        final List<MatchFragment> fragments = book.matchLimit(incoming);

        assertEquals(
                List.of(
                        new MatchFragment(
                                new OrderId(1),
                                new OrderId(4),
                                new Price(102),
                                new Quantity(100),
                                0,
                                350),
                        new MatchFragment(
                                new OrderId(2),
                                new OrderId(4),
                                new Price(101),
                                new Quantity(200),
                                0,
                                150),
                        new MatchFragment(
                                new OrderId(3),
                                new OrderId(4),
                                new Price(100),
                                new Quantity(150),
                                150,
                                0)),
                fragments);
        assertEquals(OrderStatus.FILLED, incoming.status());
        assertEquals(Optional.of(new Price(100)), book.bestBid());
        assertEquals(150, third.remainingQuantityUnits());
        assertLevelConsistency(
                book,
                book.bidLevelAt(new Price(100)).orElseThrow(),
                new OrderId(3));
        assertNonCrossed(book);
    }

    @Test
    void rejectsInvalidIncomingOrdersBeforeMutation() {
        final OrderBook book = new OrderBook();
        final Order active = sell(1, 100, 10, 1);
        final Order market = Order.market(
                new OrderId(2),
                Side.BUY,
                new Quantity(10),
                new Sequence(2));
        final Order canceled = buy(3, 100, 10, 3);
        final Order partiallyFilled = buy(4, 100, 10, 4);
        canceled.cancel();
        partiallyFilled.applyExecution(new Quantity(1));
        book.add(active);

        assertThrows(
                IllegalArgumentException.class,
                () -> book.matchLimit(market));
        assertThrows(
                IllegalStateException.class,
                () -> book.matchLimit(canceled));
        assertThrows(
                IllegalStateException.class,
                () -> book.matchLimit(partiallyFilled));
        assertThrows(
                IllegalArgumentException.class,
                () -> book.matchLimit(sell(1, 99, 1, 5)));
        assertEquals(1, book.activeOrderCount());
        assertEquals(Optional.of(active), book.activeOrder(new OrderId(1)));
        assertThrows(
                NullPointerException.class,
                () -> book.matchLimit(null));
    }

    @Test
    void identicalOrderedInputsProduceIdenticalFragmentsAndState() {
        final OrderBook firstBook = new OrderBook();
        final OrderBook secondBook = new OrderBook();
        addDeterministicInputs(firstBook);
        addDeterministicInputs(secondBook);

        final List<MatchFragment> firstFragments = firstBook.matchLimit(
                buy(5, 102, 450, 5));
        final List<MatchFragment> secondFragments = secondBook.matchLimit(
                buy(5, 102, 450, 5));

        assertEquals(firstFragments, secondFragments);
        assertEquals(firstBook.bestBid(), secondBook.bestBid());
        assertEquals(firstBook.bestAsk(), secondBook.bestAsk());
        assertEquals(
                firstBook.activeOrderCount(),
                secondBook.activeOrderCount());
        assertEquals(
                firstBook.activeOrder(new OrderId(3))
                        .orElseThrow()
                        .remainingQuantityUnits(),
                secondBook.activeOrder(new OrderId(3))
                        .orElseThrow()
                        .remainingQuantityUnits());
        assertEquals(
                firstBook.activeOrder(new OrderId(3)).orElseThrow().status(),
                secondBook.activeOrder(new OrderId(3)).orElseThrow().status());
        assertLevelConsistency(
                firstBook,
                firstBook.askLevelAt(new Price(102)).orElseThrow(),
                new OrderId(3));
        assertLevelConsistency(
                secondBook,
                secondBook.askLevelAt(new Price(102)).orElseThrow(),
                new OrderId(3));
        assertNonCrossed(firstBook);
        assertNonCrossed(secondBook);
    }

    private static void addDeterministicInputs(final OrderBook book) {
        book.add(sell(1, 100, 100, 1));
        book.add(sell(2, 101, 200, 2));
        book.add(sell(3, 102, 300, 3));
    }

    private static void assertLevelConsistency(
            final OrderBook book,
            final PriceLevel level,
            final OrderId... expectedOrderIds) {
        long sum = 0;
        int index = 0;
        OrderNode node = level.head();
        while (node != null) {
            assertTrue(index < expectedOrderIds.length);
            assertEquals(expectedOrderIds[index], node.order().orderId());
            assertSame(
                    node,
                    book.activeNode(node.order().orderId()).orElseThrow());
            sum = Math.addExact(sum, node.order().remainingQuantityUnits());
            node = node.next();
            index++;
        }
        assertEquals(expectedOrderIds.length, index);
        assertEquals(expectedOrderIds.length, level.orderCount());
        assertEquals(sum, level.totalQuantityUnits());
    }

    private static void assertNonCrossed(final OrderBook book) {
        if (book.bestBid().isPresent() && book.bestAsk().isPresent()) {
            assertTrue(
                    book.bestBid().orElseThrow().compareTo(
                            book.bestAsk().orElseThrow()) < 0);
        }
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
