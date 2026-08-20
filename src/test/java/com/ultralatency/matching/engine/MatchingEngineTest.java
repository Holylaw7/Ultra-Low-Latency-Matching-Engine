package com.ultralatency.matching.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.domain.Side;
import org.junit.jupiter.api.Test;

class MatchingEngineTest {

    @Test
    void rejectsNullAndNonContiguousSequencesBeforeApplication() {
        final MatchingEngine engine = new MatchingEngine();

        assertThrows(NullPointerException.class, () -> engine.process(null));
        assertThrows(IllegalArgumentException.class, () -> engine.process(submit(2, 1)));
        assertEquals(CommandOutcome.ACCEPTED, engine.process(submit(1, 1)).outcome());
        assertThrows(IllegalArgumentException.class, () -> engine.process(submit(1, 2)));
        assertThrows(IllegalArgumentException.class, () -> engine.process(submit(3, 2)));
        assertEquals(CommandOutcome.ACCEPTED, engine.process(submit(2, 2)).outcome());
    }

    @Test
    void supportsNoCrossSubmissionAndCancellationOutcomes() {
        final MatchingEngine engine = new MatchingEngine();

        final EngineResult submitted = engine.process(submit(1, 1));
        final EngineResult canceled = engine.process(cancel(2, 1));
        final EngineResult missing = engine.process(cancel(3, 1));

        assertEquals(CommandOutcome.ACCEPTED, submitted.outcome());
        assertTrue(submitted.matches().isEmpty());
        assertEquals(CommandOutcome.CANCELED, canceled.outcome());
        assertTrue(canceled.matches().isEmpty());
        assertEquals(CommandOutcome.NOT_FOUND, missing.outcome());
        assertTrue(missing.matches().isEmpty());
    }

    private static SubmitLimitCommand submit(final long sequence, final long orderId) {
        return new SubmitLimitCommand(
                new Sequence(sequence),
                new OrderId(orderId),
                Side.BUY,
                new Price(100),
                new Quantity(1));
    }

    private static CancelOrderCommand cancel(final long sequence, final long orderId) {
        return new CancelOrderCommand(new Sequence(sequence), new OrderId(orderId));
    }
}
