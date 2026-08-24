package com.ultralatency.matching.qualification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.engine.EngineCommand;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests bounded streaming aggregation without materializing a full evidence history. */
class QualificationStreamingAccumulatorTest {

    @Test
    void preservesCanonicalDigestsAndOnlyRetainsTheBoundedProbeSuffix() {
        final QualificationConfiguration configuration = new QualificationConfiguration(
                QualificationProfile.MEMORY_STEADY_STATE_V1,
                20260823L,
                8,
                Duration.ofSeconds(1),
                Path.of("results"));
        final List<EngineCommand> commands = QualificationWorkloadV1.generate(configuration)
                .commands();
        final List<QualificationExchange> exchanges = new ArrayList<>();
        for (int index = 0; index < commands.size(); index++) {
            exchanges.add(exchange(index + 1L));
        }

        final QualificationStreamingAccumulator accumulator =
                new QualificationStreamingAccumulator(2);
        for (int index = 0; index < commands.size(); index++) {
            accumulator.accept(commands.get(index), exchanges.get(index));
            assertTrue(accumulator.retainedProbeCount() <= 2);
        }

        final QualificationStreamingSummary summary = accumulator.finish();

        assertEquals(QualificationCanonicalizer.digest(commands), summary.commandDigestHex());
        assertEquals(8L, summary.responseCount());
        assertEquals(0L, summary.tradeCount());
        assertEquals(2, summary.retainedProbeCount());
        final int probeStart = commands.size() - 2;
        assertEquals(
                QualificationCanonicalizer.digestPublicProbe(
                        commands, exchanges.subList(probeStart, exchanges.size()), probeStart),
                summary.publicProbeDigestHex());
        assertThrows(IllegalStateException.class, () -> accumulator.finish());
        assertThrows(IllegalStateException.class,
                () -> accumulator.accept(commands.get(0), exchanges.get(0)));
    }

    private static QualificationExchange exchange(final long sequence) {
        return new QualificationExchange(
                sequence,
                sequence,
                1,
                List.of(),
                1,
                QualificationCanonicalizer.EMPTY_DIGEST);
    }
}
