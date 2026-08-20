package com.ultralatency.matching.orderbook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.ultralatency.matching.domain.Order;
import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.OrderStatus;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.domain.Side;

class PriceLevelTest {

    @Test
    void addsOrdersInFifoOrderAndTracksAggregateQuantity() {
        final PriceLevel level = new PriceLevel(new Price(100));
        final OrderNode first = level.add(order(1, 10, 1));
        final OrderNode second = level.add(order(2, 20, 2));
        final OrderNode third = level.add(order(3, 30, 3));

        assertEquals(new Price(100), level.price());
        assertSame(first, level.head());
        assertSame(third, level.tail());
        assertEquals(3, level.orderCount());
        assertEquals(60, level.totalQuantityUnits());
        assertEquals(60, sumRemaining(level));
        assertSame(second, first.next());
        assertSame(first, second.previous());
        assertSame(third, second.next());
        assertSame(second, third.previous());
    }

    @Test
    void cancelsHeadMiddleAndTailAndCleansTheEmptyLevel() {
        final PriceLevel level = new PriceLevel(new Price(100));
        final OrderNode first = level.add(order(1, 10, 1));
        final OrderNode second = level.add(order(2, 20, 2));
        final OrderNode third = level.add(order(3, 30, 3));

        assertTrue(level.cancel(second));
        assertEquals(OrderStatus.CANCELED, second.order().status());
        assertSame(first, level.head());
        assertSame(third, level.tail());
        assertEquals(40, level.totalQuantityUnits());
        assertEquals(40, sumRemaining(level));

        assertTrue(level.cancel(first));
        assertTrue(level.cancel(third));
        assertTrue(level.isEmpty());
        assertEquals(0, level.orderCount());
        assertEquals(0, level.totalQuantityUnits());
        assertNull(level.head());
        assertNull(level.tail());
        assertFalse(level.cancel(third));
    }

    @Test
    void partialAndFullExecutionUpdateQuantityAndRemoveFilledNodes() {
        final PriceLevel level = new PriceLevel(new Price(100));
        final OrderNode first = level.add(order(1, 10, 1));
        final OrderNode second = level.add(order(2, 5, 2));

        level.applyExecution(first, new Quantity(4));
        assertEquals(OrderStatus.PARTIALLY_FILLED, first.order().status());
        assertEquals(6, first.order().remainingQuantityUnits());
        assertEquals(11, level.totalQuantityUnits());
        assertEquals(11, sumRemaining(level));

        level.applyExecution(first, new Quantity(6));
        assertEquals(OrderStatus.FILLED, first.order().status());
        assertFalse(first.isLinked());
        assertSame(second, level.head());
        assertEquals(5, level.totalQuantityUnits());
        assertEquals(5, sumRemaining(level));

        level.applyExecution(second, new Quantity(5));
        assertEquals(OrderStatus.FILLED, second.order().status());
        assertTrue(level.isEmpty());
        assertEquals(0, level.totalQuantityUnits());
        assertEquals(0, sumRemaining(level));
    }

    @Test
    void rejectsExecutionBeyondRemainingQuantityWithoutChangingTheLevel() {
        final PriceLevel level = new PriceLevel(new Price(100));
        final OrderNode node = level.add(order(1, 10, 1));

        assertThrows(
                IllegalArgumentException.class,
                () -> level.applyExecution(node, new Quantity(11)));
        assertEquals(OrderStatus.NEW, node.order().status());
        assertEquals(10, node.order().remainingQuantityUnits());
        assertEquals(10, level.totalQuantityUnits());
        assertEquals(10, sumRemaining(level));
    }

    @Test
    void rejectsOrdersThatCannotRestAtThisPrice() {
        final PriceLevel level = new PriceLevel(new Price(100));
        final Order market = Order.market(
                new OrderId(1),
                Side.BUY,
                new Quantity(1),
                new Sequence(1));
        final Order wrongPrice = Order.limit(
                new OrderId(2),
                Side.BUY,
                new Price(101),
                new Quantity(1),
                new Sequence(2));
        final Order canceled = order(3, 1, 3);
        canceled.cancel();

        assertThrows(IllegalArgumentException.class, () -> level.add(market));
        assertThrows(IllegalArgumentException.class, () -> level.add(wrongPrice));
        assertThrows(IllegalStateException.class, () -> level.add(canceled));
        assertThrows(NullPointerException.class, () -> level.add(null));
    }

    @Test
    void rejectsInvalidNodeOperationsAndPreservesOwnership() {
        final PriceLevel level = new PriceLevel(new Price(100));
        final PriceLevel otherLevel = new PriceLevel(new Price(100));
        final OrderNode node = level.add(order(1, 10, 1));

        assertFalse(otherLevel.cancel(node));
        assertThrows(
                IllegalArgumentException.class,
                () -> otherLevel.applyExecution(node, new Quantity(1)));
        assertTrue(node.belongsTo(level));
        assertEquals(1, level.orderCount());
        assertEquals(10, level.totalQuantityUnits());
    }

    private static long sumRemaining(final PriceLevel level) {
        long total = 0;
        OrderNode current = level.head();
        while (current != null) {
            total = Math.addExact(total, current.order().remainingQuantityUnits());
            current = current.next();
        }
        return total;
    }

    private static Order order(final long id, final long quantity, final long sequence) {
        return Order.limit(
                new OrderId(id),
                Side.BUY,
                new Price(100),
                new Quantity(quantity),
                new Sequence(sequence));
    }
}
