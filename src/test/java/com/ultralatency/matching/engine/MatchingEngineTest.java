package com.ultralatency.matching.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.domain.EventSequence;
import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.domain.Side;
import com.ultralatency.matching.domain.TradeId;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchingEngineTest {

    @Test
    void rejectsNullAndNonContiguousSequencesBeforeApplication() {
        final MatchingEngine engine = new MatchingEngine();

        assertThrows(NullPointerException.class, () -> engine.process(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> engine.process(submit(2, 1, Side.BUY, 100, 1)));
        assertEquals(
                CommandOutcome.ACCEPTED,
                engine.process(submit(1, 1, Side.BUY, 100, 1)).outcome());
        assertThrows(
                IllegalArgumentException.class,
                () -> engine.process(submit(1, 2, Side.BUY, 100, 1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> engine.process(submit(3, 2, Side.BUY, 100, 1)));
        assertEquals(
                CommandOutcome.ACCEPTED,
                engine.process(submit(2, 2, Side.BUY, 100, 1)).outcome());
    }

    @Test
    void supportsNoCrossSubmissionAndCancellationOutcomes() {
        final MatchingEngine engine = new MatchingEngine();

        final EngineResult submitted = engine.process(submit(1, 1, Side.BUY, 100, 1));
        final EngineResult canceled = engine.process(cancel(2, 1));
        final EngineResult missing = engine.process(cancel(3, 1));

        assertEquals(CommandOutcome.ACCEPTED, submitted.outcome());
        assertTrue(submitted.matches().isEmpty());
        assertEquals(CommandOutcome.CANCELED, canceled.outcome());
        assertTrue(canceled.matches().isEmpty());
        assertEquals(CommandOutcome.NOT_FOUND, missing.outcome());
        assertTrue(missing.matches().isEmpty());
    }

    @Test
    void createsOneMakerPriceTradeAndTwoExecutionsForOneFragment() {
        final MatchingEngine engine = new MatchingEngine();

        engine.process(submit(1, 10, Side.SELL, 99, 5));
        final EngineResult result = engine.process(submit(2, 11, Side.BUY, 100, 5));

        assertEquals(CommandOutcome.ACCEPTED, result.outcome());
        assertEquals(1, result.matches().size());
        final MatchResult match = result.matches().getFirst();
        assertEquals(new TradeId(1), match.trade().tradeId());
        assertEquals(new EventSequence(1), match.eventSequence());
        assertEquals(new Price(99), match.trade().price());
        assertEquals(new Quantity(5), match.trade().quantity());
        assertEquals(new OrderId(10), match.trade().makerOrderId());
        assertEquals(new OrderId(11), match.trade().takerOrderId());
        assertEquals(new OrderId(10), match.makerExecution().orderId());
        assertEquals(new OrderId(11), match.takerExecution().orderId());
        assertEquals(0, match.makerExecution().remainingQuantityUnits());
        assertEquals(0, match.takerExecution().remainingQuantityUnits());
    }

    @Test
    void preservesFragmentOrderAndIncomingResidualForMultiMatch() {
        final MatchingEngine engine = new MatchingEngine();

        engine.process(submit(1, 1, Side.SELL, 99, 2));
        engine.process(submit(2, 2, Side.SELL, 100, 3));
        final EngineResult result = engine.process(submit(3, 3, Side.BUY, 100, 6));

        assertEquals(2, result.matches().size());
        assertEquals(new TradeId(1), result.matches().get(0).trade().tradeId());
        assertEquals(new TradeId(2), result.matches().get(1).trade().tradeId());
        assertEquals(new EventSequence(1), result.matches().get(0).eventSequence());
        assertEquals(new EventSequence(2), result.matches().get(1).eventSequence());
        assertEquals(new Price(99), result.matches().get(0).trade().price());
        assertEquals(new Price(100), result.matches().get(1).trade().price());
        assertEquals(4, result.matches().get(0).takerExecution().remainingQuantityUnits());
        assertEquals(1, result.matches().get(1).takerExecution().remainingQuantityUnits());
        assertEquals(CommandOutcome.CANCELED, engine.process(cancel(4, 3)).outcome());
    }

    @Test
    void rejectedDuplicateLeavesInputAndOutputCountersAvailable() {
        final MatchingEngine engine = new MatchingEngine();

        engine.process(submit(1, 10, Side.SELL, 99, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> engine.process(submit(2, 10, Side.BUY, 100, 1)));

        final EngineResult result = engine.process(submit(2, 11, Side.BUY, 100, 1));
        assertEquals(new TradeId(1), result.matches().getFirst().trade().tradeId());
        assertEquals(new EventSequence(1), result.matches().getFirst().eventSequence());
    }

    @Test
    void rejectedGapLeavesRestingBookAndOutputCountersUnchanged() {
        final MatchingEngine engine = new MatchingEngine();

        engine.process(submit(1, 10, Side.SELL, 99, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> engine.process(submit(3, 11, Side.BUY, 100, 1)));

        final EngineResult result = engine.process(submit(2, 11, Side.BUY, 100, 1));
        assertEquals(1, result.matches().size());
        assertEquals(new TradeId(1), result.matches().getFirst().trade().tradeId());
        assertEquals(new EventSequence(1), result.matches().getFirst().eventSequence());
    }

    @Test
    void equalCommandStreamsProduceEqualResults() {
        final List<EngineResult> firstResults = applyStream(new MatchingEngine());
        final List<EngineResult> secondResults = applyStream(new MatchingEngine());

        assertEquals(firstResults, secondResults);
    }

    private static List<EngineResult> applyStream(final MatchingEngine engine) {
        return List.of(
                engine.process(submit(1, 1, Side.SELL, 99, 2)),
                engine.process(submit(2, 2, Side.BUY, 100, 1)),
                engine.process(cancel(3, 1)));
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

    private static CancelOrderCommand cancel(final long sequence, final long orderId) {
        return new CancelOrderCommand(new Sequence(sequence), new OrderId(orderId));
    }
}
