package com.ultralatency.matching.integration.durable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.domain.Side;
import com.ultralatency.matching.engine.CancelOrderCommand;
import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.engine.EngineResult;
import com.ultralatency.matching.engine.MatchingEngine;
import com.ultralatency.matching.engine.SubmitLimitCommand;
import com.ultralatency.matching.network.protocol.ClientRequestId;
import com.ultralatency.matching.persistence.wal.CommandWalReader;
import com.ultralatency.matching.persistence.wal.CommandWalWriter;
import com.ultralatency.matching.persistence.wal.WalConfiguration;
import com.ultralatency.matching.pipeline.MatchingEnginePipeline;
import com.ultralatency.matching.pipeline.PipelineConfiguration;
import com.ultralatency.matching.pipeline.PipelinePublishOutcome;
import com.ultralatency.matching.recovery.CommandWalReplayer;
import com.ultralatency.matching.recovery.ReplayProbeResult;
import com.ultralatency.matching.recovery.ReplayTranscript;
import com.ultralatency.matching.recovery.ReplayTranscriptDigest;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Phase7DurabilityVerificationTest {

    @TempDir
    Path tempDir;

    @Test
    void appendFailureNeverPublishesAndRejectsLaterAdmission() {
        final IOException appendFailure = new IOException("force failure");
        final AtomicInteger publishes = new AtomicInteger();
        final DurableCommandCoordinator coordinator = new DurableCommandCoordinator(
                command -> {
                    throw appendFailure;
                },
                command -> {
                    publishes.incrementAndGet();
                    return PipelinePublishOutcome.ACCEPTED;
                });
        coordinator.start();

        final DurableTerminalException first = assertThrows(
                DurableTerminalException.class,
                () -> coordinator.accept(
                        ClientRequestId.of(1),
                        sequence -> submit(sequence.toSequence(), 501)));
        final DurableTerminalException second = assertThrows(
                DurableTerminalException.class,
                () -> coordinator.accept(
                        ClientRequestId.of(2),
                        sequence -> submit(sequence.toSequence(), 502)));

        assertEquals(DurableFailureStage.APPEND, first.failure().stage());
        assertSame(appendFailure, first.failure().cause());
        assertSame(first.failure(), second.failure());
        assertEquals(0, publishes.get());
        assertEquals(new DurableCommandSequence(1), coordinator.nextCommandSequence());
        assertEquals(DurableLifecycleState.FAILED, coordinator.state());
    }

    @Test
    void durableThenFullIsTerminalAndDoesNotBecomeRetryable() {
        final AtomicInteger publishes = new AtomicInteger();
        final DurableCommandCoordinator coordinator = new DurableCommandCoordinator(
                command -> { },
                command -> {
                    publishes.incrementAndGet();
                    return PipelinePublishOutcome.FULL;
                });
        coordinator.start();

        final DurableTerminalException failure = assertThrows(
                DurableTerminalException.class,
                () -> coordinator.accept(
                        ClientRequestId.of(10),
                        sequence -> submit(sequence.toSequence(), 510)));

        assertEquals(DurableFailureStage.DURABLE_THEN_FULL, failure.failure().stage());
        assertEquals(1, publishes.get());
        assertEquals(new DurableCommandSequence(2), coordinator.nextCommandSequence());
        assertThrows(
                DurableTerminalException.class,
                () -> coordinator.accept(
                        ClientRequestId.of(11),
                        sequence -> submit(sequence.toSequence(), 511)));
    }

    @Test
    void publishFailureRetainsFirstCauseAndRejectsLaterAdmission() {
        final RuntimeException publishFailure = new RuntimeException("pipeline failure");
        final AtomicInteger appends = new AtomicInteger();
        final DurableCommandCoordinator coordinator = new DurableCommandCoordinator(
                command -> appends.incrementAndGet(),
                command -> {
                    throw publishFailure;
                });
        coordinator.start();

        final DurableTerminalException first = assertThrows(
                DurableTerminalException.class,
                () -> coordinator.accept(
                        ClientRequestId.of(20),
                        sequence -> submit(sequence.toSequence(), 520)));
        final DurableTerminalException second = assertThrows(
                DurableTerminalException.class,
                () -> coordinator.accept(
                        ClientRequestId.of(21),
                        sequence -> submit(sequence.toSequence(), 521)));

        assertEquals(DurableFailureStage.PIPELINE, first.failure().stage());
        assertSame(publishFailure, first.failure().cause());
        assertSame(first.failure(), second.failure());
        assertEquals(1, appends.get());
    }

    @Test
    void closedLiveWalReplaysToTheSameTranscriptDigestAndProbe() throws Exception {
        final Path walDirectory = tempDir.resolve("live-wal");
        final WalConfiguration configuration = WalConfiguration.defaults(walDirectory);
        final List<EngineResult> liveResults = new ArrayList<>();
        final CountDownLatch resultLatch = new CountDownLatch(3);
        final MatchingEnginePipeline pipeline = new MatchingEnginePipeline(
                PipelineConfiguration.defaults(),
                result -> {
                    synchronized (liveResults) {
                        liveResults.add(result);
                    }
                    resultLatch.countDown();
                });
        final DurableCommandCoordinator coordinator;

        try (CommandWalWriter writer = CommandWalWriter.open(configuration)) {
            coordinator = new DurableCommandCoordinator(
                    writer::append,
                    pipeline::tryPublish);
            pipeline.start();
            coordinator.start();
            coordinator.accept(
                    ClientRequestId.of(101),
                    sequence -> submit(sequence.toSequence(), 701));
            coordinator.accept(
                    ClientRequestId.of(102),
                    sequence -> submit(sequence.toSequence(), 702));
            coordinator.accept(
                    ClientRequestId.of(103),
                    sequence -> new CancelOrderCommand(sequence.toSequence(), OrderId.of(701)));

            assertTrue(resultLatch.await(5, TimeUnit.SECONDS));
            assertEquals(DurableLifecycleState.STOPPED, coordinator.shutdown());
            assertEquals(
                    com.ultralatency.matching.pipeline.PipelineState.STOPPED,
                    pipeline.shutdown(Duration.ofSeconds(2)));
        }

        final List<EngineCommand> commands = CommandWalReader.read(configuration);
        final List<EngineResult> liveTranscript;
        synchronized (liveResults) {
            liveTranscript = List.copyOf(liveResults);
        }
        final ReplayTranscript replay = new CommandWalReplayer(configuration).replay();
        assertEquals(commands.size(), liveTranscript.size());
        assertEquals(liveTranscript, replay.results());
        assertEquals(ReplayTranscriptDigest.sha256Hex(liveTranscript), replay.sha256DigestHex());

        final EngineCommand probe = submit(Sequence.of(4), 704);
        final ReplayProbeResult replayWithProbe = new CommandWalReplayer(configuration)
                .replayWithProbe(List.of(probe));
        final MatchingEngine direct = new MatchingEngine();
        for (EngineCommand command : commands) {
            direct.process(command);
        }
        assertEquals(List.of(direct.process(probe)), replayWithProbe.probeResults());
    }

    private static EngineCommand submit(final Sequence sequence, final long orderId) {
        return new SubmitLimitCommand(
                sequence,
                OrderId.of(orderId),
                Side.BUY,
                Price.of(100),
                Quantity.of(1));
    }
}
