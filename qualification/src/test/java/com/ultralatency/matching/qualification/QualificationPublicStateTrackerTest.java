package com.ultralatency.matching.qualification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.engine.CancelOrderCommand;
import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.engine.SubmitLimitCommand;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies bounded active-order evidence derived only from public exchanges. */
class QualificationPublicStateTrackerTest {

    @Test
    void memoryCycleProvesMaximumAndFinalActiveOrderCountsFromPublicResponses() {
        final QualificationConfiguration configuration = new QualificationConfiguration(
                QualificationProfile.MEMORY_STEADY_STATE_V1, 20260823L, 4,
                Duration.ofSeconds(1), Path.of("results"));
        final EngineCommand first = QualificationWorkloadV1.commandAt(configuration, 0);
        final EngineCommand second = QualificationWorkloadV1.commandAt(configuration, 1);
        final EngineCommand third = QualificationWorkloadV1.commandAt(configuration, 2);
        final EngineCommand fourth = QualificationWorkloadV1.commandAt(configuration, 3);
        final long firstOrderId = ((SubmitLimitCommand) first).orderId().value();
        final long secondOrderId = ((SubmitLimitCommand) second).orderId().value();
        final long thirdOrderId = ((SubmitLimitCommand) third).orderId().value();

        final QualificationPublicStateTracker tracker = new QualificationPublicStateTracker();
        tracker.accept(first, exchange(1, 1, List.of()));
        tracker.accept(second, exchange(2, 1, List.of(
                new QualificationMatch(1, 1, 100, 1, firstOrderId, secondOrderId))));
        tracker.accept(third, exchange(3, 1, List.of()));
        tracker.accept(fourth, exchange(4, 2, List.of()));

        final QualificationPublicStateTracker.Summary summary = tracker.finish();

        assertEquals(1, summary.maximumActiveOrderCount());
        assertEquals(0, summary.finalActiveOrderCount());
        assertTrue(summary.boundPassed());
        assertTrue(fourth instanceof CancelOrderCommand);
        assertEquals(thirdOrderId, ((CancelOrderCommand) fourth).orderId().value());
    }

    private static QualificationExchange exchange(
            final long requestId,
            final int outcomeCode,
            final List<QualificationMatch> matches) {
        return new QualificationExchange(
                requestId, requestId, outcomeCode, matches, matches.size() + 1,
                "0000000000000000000000000000000000000000000000000000000000000000");
    }
}
