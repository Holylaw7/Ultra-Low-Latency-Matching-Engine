package com.ultralatency.matching.engine;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.domain.Side;
import org.junit.jupiter.api.Test;

class MatchingEngineTest {

    @Test
    void rejectsNullAndNonGenesisSequencesBeforeApplication() {
        final MatchingEngine engine = new MatchingEngine();

        assertThrows(NullPointerException.class, () -> engine.process(null));
        assertThrows(IllegalArgumentException.class, () -> engine.process(submit(2, 1)));
        assertThrows(UnsupportedOperationException.class, () -> engine.process(submit(1, 1)));
        assertThrows(UnsupportedOperationException.class, () -> engine.process(submit(1, 1)));
    }

    private static SubmitLimitCommand submit(final long sequence, final long orderId) {
        return new SubmitLimitCommand(
                new Sequence(sequence),
                new OrderId(orderId),
                Side.BUY,
                new Price(100),
                new Quantity(1));
    }
}
