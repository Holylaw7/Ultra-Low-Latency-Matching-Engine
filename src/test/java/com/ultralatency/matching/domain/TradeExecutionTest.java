package com.ultralatency.matching.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TradeExecutionTest {

    @Test
    void tradeAndExecutionAreValueObjects() {
        final Trade firstTrade = new Trade(
                new TradeId(1),
                new EventSequence(10),
                new Price(10025),
                new Quantity(50),
                new OrderId(1),
                new OrderId(2));
        final Trade secondTrade = new Trade(
                new TradeId(1),
                new EventSequence(10),
                new Price(10025),
                new Quantity(50),
                new OrderId(1),
                new OrderId(2));

        final Execution execution = new Execution(
                new TradeId(1),
                new OrderId(2),
                new Price(10025),
                new Quantity(50),
                0);

        assertEquals(firstTrade, secondTrade);
        assertEquals(firstTrade.hashCode(), secondTrade.hashCode());
        assertEquals(new EventSequence(10), firstTrade.eventSequence());
        assertEquals(new OrderId(2), execution.orderId());
        assertEquals(0, execution.remainingQuantityUnits());
    }

    @Test
    void rejectsInvalidTradeAndExecutionValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Trade(
                        new TradeId(1),
                        new EventSequence(10),
                        new Price(100),
                        new Quantity(1),
                        new OrderId(1),
                        new OrderId(1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Execution(
                        new TradeId(1),
                        new OrderId(2),
                        new Price(100),
                        new Quantity(1),
                        -1));
    }

    @Test
    void equalInputsProduceEqualResults() {
        final Trade tradeA = new Trade(
                new TradeId(9),
                new EventSequence(20),
                new Price(101),
                new Quantity(3),
                new OrderId(10),
                new OrderId(11));
        final Trade tradeB = new Trade(
                new TradeId(9),
                new EventSequence(20),
                new Price(101),
                new Quantity(3),
                new OrderId(10),
                new OrderId(11));
        final Execution executionA = new Execution(
                new TradeId(9),
                new OrderId(10),
                new Price(101),
                new Quantity(3),
                0);
        final Execution executionB = new Execution(
                new TradeId(9),
                new OrderId(10),
                new Price(101),
                new Quantity(3),
                0);

        assertEquals(tradeA, tradeB);
        assertEquals(executionA, executionB);
    }
}
