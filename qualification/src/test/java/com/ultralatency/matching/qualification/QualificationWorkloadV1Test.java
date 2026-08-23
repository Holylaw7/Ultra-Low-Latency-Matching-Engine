package com.ultralatency.matching.qualification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.engine.CancelOrderCommand;
import com.ultralatency.matching.engine.MatchingEngine;
import com.ultralatency.matching.engine.SubmitLimitCommand;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Golden-vector and determinism tests for QualificationWorkloadV1. */
class QualificationWorkloadV1Test {

    @Test
    void sameVersionSeedAndProfileProduceTheSameWorkload() {
        final QualificationConfiguration configuration =
                new QualificationConfiguration(
                        QualificationProfile.CROSSING_MULTI_MATCH,
                        20260823L,
                        12,
                        java.time.Duration.ofSeconds(1),
                        java.nio.file.Path.of("results"));

        final QualificationWorkload first = QualificationWorkloadV1.generate(configuration);
        final QualificationWorkload second = QualificationWorkloadV1.generate(configuration);

        assertEquals(first, second);
        assertEquals("qualification-workload-v1", first.version());
        assertEquals(12, first.commandCount());
        assertEquals(64, first.digestHex().length());
    }

    @Test
    void profilesHaveStableVectorsAndExpectedCommandKinds() {
        final QualificationWorkload lifecycle = QualificationWorkloadV1.generate(
                new QualificationConfiguration(
                        QualificationProfile.LIFECYCLE_MIX, 1, 6,
                        java.time.Duration.ofSeconds(1), java.nio.file.Path.of("results")));
        final QualificationWorkload crossing = QualificationWorkloadV1.generate(
                new QualificationConfiguration(
                        QualificationProfile.CROSSING_MULTI_MATCH, 1, 6,
                        java.time.Duration.ofSeconds(1), java.nio.file.Path.of("results")));
        final QualificationWorkload resting = QualificationWorkloadV1.generate(
                new QualificationConfiguration(
                        QualificationProfile.RESTING_DEPTH, 1, 8,
                        java.time.Duration.ofSeconds(1), java.nio.file.Path.of("results")));

        assertEquals(3, lifecycle.commands().stream()
                .filter(CancelOrderCommand.class::isInstance).count());
        assertEquals(6, crossing.commands().stream()
                .filter(SubmitLimitCommand.class::isInstance).count());
        assertEquals(4, resting.commands().stream()
                .filter(CancelOrderCommand.class::isInstance).count());
        assertEquals(
                "005881a2e501987c8a54aa0292ca6619849bab668e7b561597f4d6af05e6bed3",
                lifecycle.digestHex());
        assertEquals(
                "2eea60b04b7585cf29b0108a44670c6f9e26708a32e5fdb58bdaffd82a85096b",
                crossing.digestHex());
        assertEquals(
                "17d708fde64ba538a76c599738af94539a839aff30fc215b274a78063a813627",
                resting.digestHex());
        assertNotEquals(lifecycle.digestHex(), crossing.digestHex());
        assertNotEquals(crossing.digestHex(), resting.digestHex());
    }

    @Test
    void workloadRejectsNonContiguousCommands() {
        assertThrows(IllegalArgumentException.class, () -> new QualificationWorkload(
                QualificationWorkloadV1.VERSION,
                QualificationProfile.LIFECYCLE_MIX,
                1,
                java.util.List.of(
                        new SubmitLimitCommand(
                                com.ultralatency.matching.domain.Sequence.of(2),
                                com.ultralatency.matching.domain.OrderId.of(1),
                                com.ultralatency.matching.domain.Side.SELL,
                                com.ultralatency.matching.domain.Price.of(100),
                                com.ultralatency.matching.domain.Quantity.of(1))),
                "0000000000000000000000000000000000000000000000000000000000000000"));
    }

    @Test
    void workloadRejectsDigestThatDoesNotMatchCommands() {
        final QualificationConfiguration configuration = new QualificationConfiguration(
                QualificationProfile.LIFECYCLE_MIX, 1, 1,
                java.time.Duration.ofSeconds(1), java.nio.file.Path.of("results"));
        final QualificationWorkload generated = QualificationWorkloadV1.generate(configuration);

        assertThrows(IllegalArgumentException.class, () -> new QualificationWorkload(
                generated.version(), generated.profile(), generated.seed(),
                generated.commands(),
                "0000000000000000000000000000000000000000000000000000000000000000"));
    }

    @Test
    void workloadRejectsUnsupportedVersion() {
        final QualificationWorkload generated = QualificationWorkloadV1.generate(
                new QualificationConfiguration(
                        QualificationProfile.LIFECYCLE_MIX, 1, 1,
                        java.time.Duration.ofSeconds(1), java.nio.file.Path.of("results")));

        assertThrows(IllegalArgumentException.class, () -> new QualificationWorkload(
                "qualification-workload-v0", generated.profile(), generated.seed(),
                generated.commands(), generated.digestHex()));
    }

    @Test
    void generatedProfilesAreApplicableToTheFrozenEngine() {
        for (final QualificationProfile profile : QualificationProfile.values()) {
            final QualificationConfiguration configuration = new QualificationConfiguration(
                    profile, 20260823L, 24,
                    java.time.Duration.ofSeconds(1), java.nio.file.Path.of("results"));
            final MatchingEngine engine = new MatchingEngine();
            for (final com.ultralatency.matching.engine.EngineCommand command
                    : QualificationWorkloadV1.generate(configuration).commands()) {
                engine.process(command);
            }
            assertEquals(24, engine.checkpoint().lastAppliedCommandSequence());
        }
    }

    @Test
    void memorySteadyStateProfileKeepsLiveOrderStateBounded() {
        final QualificationConfiguration configuration = new QualificationConfiguration(
                QualificationProfile.MEMORY_STEADY_STATE_V1,
                20260823L,
                64,
                java.time.Duration.ofSeconds(1),
                java.nio.file.Path.of("results"));
        final QualificationWorkload workload = QualificationWorkloadV1.generate(configuration);
        final MatchingEngine engine = new MatchingEngine();
        int maximumActiveOrders = 0;
        for (final com.ultralatency.matching.engine.EngineCommand command : workload.commands()) {
            engine.process(command);
            maximumActiveOrders = Math.max(
                    maximumActiveOrders, engine.checkpoint().activeOrderCount());
        }

        assertEquals(QualificationWorkloadV1.MEMORY_STEADY_STATE_VERSION, workload.version());
        assertEquals(0, engine.checkpoint().activeOrderCount());
        assertEquals(QualificationWorkloadV1.MEMORY_STEADY_STATE_MAX_ACTIVE_ORDERS,
                maximumActiveOrders);
        assertEquals(workload.digestHex(),
                QualificationWorkloadV1.generate(configuration).digestHex());
    }

    @Test
    void memorySteadyStateCanExtendTheDeterministicPrefixForContinuousObservation() {
        final QualificationConfiguration configuration = new QualificationConfiguration(
                QualificationProfile.MEMORY_STEADY_STATE_V1,
                20260823L,
                4,
                java.time.Duration.ofSeconds(1),
                java.nio.file.Path.of("results"));
        final List<com.ultralatency.matching.engine.EngineCommand> commands = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            commands.add(QualificationWorkloadV1.commandAtForRun(configuration, index));
        }

        assertTrue(QualificationWorkloadV1.matches(commands, configuration));
        assertEquals(8, QualificationWorkloadV1.generate(configuration, 8).commandCount());
        assertEquals(commands,
                QualificationWorkloadV1.generate(configuration, 8).commands());
    }
}
