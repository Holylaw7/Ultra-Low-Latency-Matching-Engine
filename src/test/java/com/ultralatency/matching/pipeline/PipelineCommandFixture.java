package com.ultralatency.matching.pipeline;

import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.domain.Side;
import com.ultralatency.matching.engine.CancelOrderCommand;
import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.engine.SubmitLimitCommand;
import java.util.ArrayList;
import java.util.List;

/**
 * Fixed deterministic command fixtures for pipeline verification.
 */
final class PipelineCommandFixture {

    private PipelineCommandFixture() {
    }

    static List<EngineCommand> commandStream(final int cycles) {
        final List<EngineCommand> commands = new ArrayList<>(cycles * 8);
        long sequence = 1;
        for (int cycle = 0; cycle < cycles; cycle++) {
            final long cycleId = cycle + 1L;
            final long askAt100 = 10_000L + cycleId;
            final long bidAt100 = 20_000L + cycleId;
            final long bidAt99 = 30_000L + cycleId;
            final long askAt99 = 40_000L + cycleId;
            final long askAt101 = 50_000L + cycleId;
            final long bidAt98 = 60_000L + cycleId;
            commands.add(submit(sequence++, askAt100, Side.SELL, 100));
            commands.add(submit(sequence++, bidAt100, Side.BUY, 100));
            commands.add(submit(sequence++, bidAt99, Side.BUY, 99));
            commands.add(submit(sequence++, askAt99, Side.SELL, 99));
            commands.add(submit(sequence++, askAt101, Side.SELL, 101));
            commands.add(new CancelOrderCommand(Sequence.of(sequence++), OrderId.of(askAt101)));
            commands.add(submit(sequence++, bidAt98, Side.BUY, 98));
            commands.add(new CancelOrderCommand(Sequence.of(sequence++), OrderId.of(bidAt98)));
        }
        return List.copyOf(commands);
    }

    static EngineCommand command(final long sequence, final long orderId) {
        return submit(sequence, orderId, Side.BUY, 100);
    }

    private static EngineCommand submit(
            final long sequence, final long orderId, final Side side, final long price) {
        return new SubmitLimitCommand(
                Sequence.of(sequence),
                OrderId.of(orderId),
                side,
                Price.of(price),
                Quantity.of(1));
    }
}
