package com.ultralatency.matching.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class OrderTest {

    @Test
    void createsLimitAndMarketOrdersWithStableIdentity() {
        final OrderId orderId = new OrderId(1);
        final Price price = new Price(10025);
        final Quantity quantity = new Quantity(100);
        final Sequence sequence = new Sequence(7);

        final Order limitOrder = Order.limit(orderId, Side.BUY, price, quantity, sequence);
        final Order marketOrder = Order.market(orderId, Side.SELL, quantity, sequence);

        assertEquals(OrderType.LIMIT, limitOrder.type());
        assertEquals(Optional.of(price), limitOrder.limitPrice());
        assertEquals(OrderType.MARKET, marketOrder.type());
        assertEquals(Optional.empty(), marketOrder.limitPrice());
        assertEquals(limitOrder, marketOrder);
        assertEquals(limitOrder.hashCode(), marketOrder.hashCode());
    }

    @Test
    void appliesPartialAndFullExecutionsInOrder() {
        final Order order = Order.limit(
                new OrderId(1),
                Side.BUY,
                new Price(100),
                new Quantity(100),
                new Sequence(1));

        order.applyExecution(new Quantity(40));
        assertEquals(OrderStatus.PARTIALLY_FILLED, order.status());
        assertEquals(60, order.remainingQuantityUnits());
        assertTrue(order.isActive());

        order.applyExecution(new Quantity(60));
        assertEquals(OrderStatus.FILLED, order.status());
        assertEquals(0, order.remainingQuantityUnits());
        assertFalse(order.isActive());
    }

    @Test
    void rejectsInvalidExecutionAndTerminalStateMutation() {
        final Order order = Order.market(
                new OrderId(1),
                Side.SELL,
                new Quantity(10),
                new Sequence(1));

        assertThrows(
                IllegalArgumentException.class,
                () -> order.applyExecution(new Quantity(11)));
        assertTrue(order.cancel());
        assertFalse(order.cancel());
        assertThrows(
                IllegalStateException.class,
                () -> order.applyExecution(new Quantity(1)));
    }

    @Test
    void filledOrderCannotBeCanceled() {
        final Order order = Order.market(
                new OrderId(1),
                Side.BUY,
                new Quantity(1),
                new Sequence(1));

        order.applyExecution(new Quantity(1));

        assertThrows(IllegalStateException.class, order::cancel);
    }

    @Test
    void rejectsNullOrderId() {
        assertThrows(
                NullPointerException.class,
                () -> Order.limit(
                        null,
                        Side.BUY,
                        new Price(100),
                        new Quantity(1),
                        new Sequence(1)));
    }
}
