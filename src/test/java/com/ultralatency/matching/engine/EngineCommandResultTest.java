package com.ultralatency.matching.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.domain.EventSequence;
import com.ultralatency.matching.domain.Execution;
import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.domain.Side;
import com.ultralatency.matching.domain.Trade;
import com.ultralatency.matching.domain.TradeId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EngineCommandResultTest {

    @Test
    void commandsAreImmutableValueObjects() {
        final SubmitLimitCommand submit = new SubmitLimitCommand(
                new Sequence(1),
                new OrderId(100),
                Side.BUY,
                new Price(10025),
                new Quantity(50));
        final SubmitLimitCommand sameSubmit = new SubmitLimitCommand(
                new Sequence(1),
                new OrderId(100),
                Side.BUY,
                new Price(10025),
                new Quantity(50));
        final CancelOrderCommand cancel = new CancelOrderCommand(new Sequence(2), new OrderId(100));

        assertEquals(sameSubmit, submit);
        assertEquals(new Sequence(1), submit.sequence());
        assertEquals(new OrderId(100), submit.orderId());
        assertEquals(Side.BUY, submit.side());
        assertEquals(new Price(10025), submit.price());
        assertEquals(new Quantity(50), submit.quantity());
        assertEquals(new Sequence(2), cancel.sequence());
        assertEquals(new OrderId(100), cancel.orderId());
    }

    @Test
    void commandsRejectMissingValues() {
        assertThrows(
                NullPointerException.class,
                () -> new SubmitLimitCommand(
                        null, new OrderId(1), Side.BUY, new Price(100), new Quantity(1)));
        assertThrows(
                NullPointerException.class,
                () -> new SubmitLimitCommand(
                        new Sequence(1), null, Side.BUY, new Price(100), new Quantity(1)));
        assertThrows(
                NullPointerException.class,
                () -> new SubmitLimitCommand(
                        new Sequence(1), new OrderId(1), null, new Price(100), new Quantity(1)));
        assertThrows(
                NullPointerException.class,
                () -> new SubmitLimitCommand(
                        new Sequence(1), new OrderId(1), Side.BUY, null, new Quantity(1)));
        assertThrows(
                NullPointerException.class,
                () -> new SubmitLimitCommand(
                        new Sequence(1), new OrderId(1), Side.BUY, new Price(100), null));
        assertThrows(NullPointerException.class, () -> new CancelOrderCommand(null, new OrderId(1)));
        assertThrows(
                NullPointerException.class,
                () -> new CancelOrderCommand(new Sequence(1), null));
    }

    @Test
    void resultSnapshotsAnImmutableOrderedMatchList() {
        final MatchResult match = matchResult();
        final List<MatchResult> mutableMatches = new ArrayList<>();
        final EngineResult result = new EngineResult(
                new Sequence(10), CommandOutcome.ACCEPTED, mutableMatches);

        mutableMatches.add(match);

        assertTrue(result.matches().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> result.matches().add(match));
        assertEquals(
                new EngineResult(new Sequence(10), CommandOutcome.ACCEPTED, List.of()),
                result);
    }

    @Test
    void matchResultRejectsInconsistentAggregateValues() {
        final MatchResult match = matchResult();
        final Trade trade = match.trade();

        assertThrows(
                IllegalArgumentException.class,
                () -> new MatchResult(
                        new EventSequence(2),
                        trade,
                        match.makerExecution(),
                        match.takerExecution()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new MatchResult(
                        trade.eventSequence(),
                        trade,
                        new Execution(
                                new TradeId(9),
                                trade.makerOrderId(),
                                trade.price(),
                                trade.quantity(),
                                0),
                        match.takerExecution()));
    }

    private static MatchResult matchResult() {
        final TradeId tradeId = new TradeId(7);
        final EventSequence eventSequence = new EventSequence(3);
        final Price price = new Price(10025);
        final Quantity quantity = new Quantity(50);
        final OrderId makerOrderId = new OrderId(1);
        final OrderId takerOrderId = new OrderId(2);
        final Trade trade = new Trade(
                tradeId, eventSequence, price, quantity, makerOrderId, takerOrderId);
        final Execution makerExecution = new Execution(
                tradeId, makerOrderId, price, quantity, 100);
        final Execution takerExecution = new Execution(
                tradeId, takerOrderId, price, quantity, 0);
        return new MatchResult(eventSequence, trade, makerExecution, takerExecution);
    }
}
