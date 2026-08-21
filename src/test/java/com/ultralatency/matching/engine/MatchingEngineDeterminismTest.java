package com.ultralatency.matching.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.ultralatency.matching.domain.EventSequence;
import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.domain.Side;
import com.ultralatency.matching.domain.TradeId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchingEngineDeterminismTest {

    private static final int CYCLE_COUNT = 32;
    private static final int COMMANDS_PER_CYCLE = 8;

    @Test
    void producesEqualOrderedResultsForExtendedFixedCommandStream() {
        final List<EngineCommand> commands = extendedCommandStream();

        assertEquals(CYCLE_COUNT * COMMANDS_PER_CYCLE, commands.size());

        final List<EngineResult> firstResults = apply(new MatchingEngine(), commands);
        final List<EngineResult> secondResults = apply(new MatchingEngine(), commands);

        assertEquals(firstResults, secondResults);

        final List<MatchResult> matches = allMatches(firstResults);
        assertEquals(CYCLE_COUNT * 3, matches.size());
        assertEquals(new TradeId(1), matches.getFirst().trade().tradeId());
        assertEquals(new EventSequence(1), matches.getFirst().eventSequence());
        assertEquals(new TradeId(CYCLE_COUNT * 3L), matches.getLast().trade().tradeId());
        assertEquals(new EventSequence(CYCLE_COUNT * 3L), matches.getLast().eventSequence());

        final EngineResult firstCycleMultiMatch = firstResults.get(5);
        assertEquals(2, firstCycleMultiMatch.matches().size());
        assertEquals(new Price(99), firstCycleMultiMatch.matches().get(0).trade().price());
        assertEquals(new Price(101), firstCycleMultiMatch.matches().get(1).trade().price());

        final EngineResult reordered = new EngineResult(
                firstCycleMultiMatch.commandSequence(),
                firstCycleMultiMatch.outcome(),
                List.of(
                        firstCycleMultiMatch.matches().get(1),
                        firstCycleMultiMatch.matches().get(0)));

        assertNotEquals(firstCycleMultiMatch, reordered);
    }

    @Test
    void producesEqualPublicApiProbeResultsAfterExtendedCommandStream() {
        final MatchingEngine first = new MatchingEngine();
        final MatchingEngine second = new MatchingEngine();
        final List<EngineCommand> commands = extendedCommandStream();

        apply(first, commands);
        apply(second, commands);

        final long finalCycleOrderId = orderIdForCycle(CYCLE_COUNT - 1, 6);
        final List<EngineCommand> probes = List.of(
                cancel(257, finalCycleOrderId),
                cancel(258, finalCycleOrderId),
                submit(259, 900_001, Side.SELL, 500, 1),
                submit(260, 900_002, Side.BUY, 501, 1));

        final List<EngineResult> firstProbeResults = apply(first, probes);
        final List<EngineResult> secondProbeResults = apply(second, probes);

        assertEquals(firstProbeResults, secondProbeResults);
        assertEquals(CommandOutcome.CANCELED, firstProbeResults.get(0).outcome());
        assertEquals(CommandOutcome.NOT_FOUND, firstProbeResults.get(1).outcome());
        assertEquals(1, firstProbeResults.get(3).matches().size());
    }

    private static List<EngineCommand> extendedCommandStream() {
        final List<EngineCommand> commands = new ArrayList<>(CYCLE_COUNT * COMMANDS_PER_CYCLE);
        long sequence = 1;
        for (int cycle = 0; cycle < CYCLE_COUNT; cycle++) {
            final long orderIdBase = cycle * 10L;
            final long basePrice = 100 + (cycle * 10L);
            final long firstAsk = orderIdForCycle(cycle, 1);
            final long secondAsk = orderIdForCycle(cycle, 2);
            final long restingBuy = orderIdForCycle(cycle, 6);

            commands.add(submit(sequence++, firstAsk, Side.SELL, basePrice, 2));
            commands.add(submit(sequence++, secondAsk, Side.SELL, basePrice + 1, 3));
            commands.add(submit(sequence++, orderIdBase + 3, Side.BUY, basePrice + 1, 1));
            commands.add(cancel(sequence++, firstAsk));
            commands.add(submit(sequence++, orderIdBase + 4, Side.SELL, basePrice - 1, 1));
            commands.add(submit(sequence++, orderIdBase + 5, Side.BUY, basePrice + 2, 4));
            commands.add(submit(sequence++, restingBuy, Side.BUY, basePrice - 2, 2));
            commands.add(cancel(sequence++, cancellationTarget(cycle, restingBuy)));
        }
        return List.copyOf(commands);
    }

    private static long cancellationTarget(final int cycle, final long restingBuy) {
        if (cycle == CYCLE_COUNT - 1) {
            return 1_000_000L + cycle;
        }
        return restingBuy;
    }

    private static long orderIdForCycle(final int cycle, final int offset) {
        return (cycle * 10L) + offset;
    }

    private static List<EngineResult> apply(
            final MatchingEngine engine,
            final List<? extends EngineCommand> commands) {
        final List<EngineResult> results = new ArrayList<>(commands.size());
        for (final EngineCommand command : commands) {
            results.add(engine.process(command));
        }
        return List.copyOf(results);
    }

    private static List<MatchResult> allMatches(final List<EngineResult> results) {
        final List<MatchResult> matches = new ArrayList<>();
        for (final EngineResult result : results) {
            matches.addAll(result.matches());
        }
        return List.copyOf(matches);
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
