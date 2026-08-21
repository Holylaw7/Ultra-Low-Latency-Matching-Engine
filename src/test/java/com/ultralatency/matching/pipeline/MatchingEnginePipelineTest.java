package com.ultralatency.matching.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.domain.Side;
import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.engine.EngineResult;
import com.ultralatency.matching.engine.SubmitLimitCommand;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class MatchingEnginePipelineTest {

    @Test
    void publishesAndDrainsAnEngineResult() throws InterruptedException {
        final CountDownLatch resultLatch = new CountDownLatch(1);
        final AtomicReference<EngineResult> observed = new AtomicReference<>();
        final MatchingEnginePipeline pipeline = newPipeline(result -> {
            observed.set(result);
            resultLatch.countDown();
        });

        pipeline.start();
        assertEquals(PipelinePublishOutcome.ACCEPTED, pipeline.tryPublish(command(1, 1)));

        assertTrue(resultLatch.await(2, TimeUnit.SECONDS));
        assertEquals(PipelineState.STOPPED, pipeline.shutdown(Duration.ofSeconds(2)));
        assertEquals(Sequence.of(1), observed.get().commandSequence());
    }

    @Test
    void preservesPublicationOrderForMultipleCommands() throws InterruptedException {
        final CountDownLatch resultLatch = new CountDownLatch(3);
        final List<Sequence> sequences = new ArrayList<>();
        final MatchingEnginePipeline pipeline = newPipeline(result -> {
            sequences.add(result.commandSequence());
            resultLatch.countDown();
        });

        pipeline.start();
        for (int sequence = 1; sequence <= 3; sequence++) {
            assertEquals(
                    PipelinePublishOutcome.ACCEPTED,
                    pipeline.tryPublish(command(sequence, sequence)));
        }

        assertTrue(resultLatch.await(2, TimeUnit.SECONDS));
        assertEquals(PipelineState.STOPPED, pipeline.shutdown(Duration.ofSeconds(2)));
        assertEquals(List.of(Sequence.of(1), Sequence.of(2), Sequence.of(3)), sequences);
    }

    @Test
    void fullRingReturnsFullWithoutConsumingTheSubmittedCommand() throws InterruptedException {
        final CountDownLatch handlerEntered = new CountDownLatch(1);
        final CountDownLatch releaseHandler = new CountDownLatch(1);
        final MatchingEnginePipeline pipeline = newPipeline(2, result -> {
            handlerEntered.countDown();
            await(releaseHandler);
        });

        pipeline.start();
        assertEquals(PipelinePublishOutcome.ACCEPTED, pipeline.tryPublish(command(1, 1)));
        assertTrue(handlerEntered.await(2, TimeUnit.SECONDS));
        PipelinePublishOutcome outcome = PipelinePublishOutcome.ACCEPTED;
        for (int sequence = 2; sequence <= 16 && outcome == PipelinePublishOutcome.ACCEPTED; sequence++) {
            outcome = pipeline.tryPublish(command(sequence, sequence));
        }
        assertEquals(PipelinePublishOutcome.FULL, outcome);

        releaseHandler.countDown();
        assertEquals(PipelineState.STOPPED, pipeline.shutdown(Duration.ofSeconds(2)));
    }

    @Test
    void rejectsForeignProducerBeforePublication() throws InterruptedException {
        final MatchingEnginePipeline pipeline = newPipeline(result -> { });
        pipeline.start();
        assertEquals(PipelinePublishOutcome.ACCEPTED, pipeline.tryPublish(command(1, 1)));

        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final Thread foreignProducer = Thread.ofPlatform().start(() -> {
            try {
                pipeline.tryPublish(command(2, 2));
            } catch (final Throwable exception) {
                failure.set(exception);
            }
        });
        foreignProducer.join(2_000);

        assertInstanceOf(IllegalStateException.class, failure.get());
        assertEquals(PipelineState.STOPPED, pipeline.shutdown(Duration.ofSeconds(2)));
    }

    @Test
    void invalidSequenceFailsThePipelineAndRejectsLaterPublication() throws InterruptedException {
        final MatchingEnginePipeline pipeline = newPipeline(result -> { });
        pipeline.start();
        assertEquals(PipelinePublishOutcome.ACCEPTED, pipeline.tryPublish(command(2, 1)));

        awaitFailure(pipeline);

        assertEquals(PipelineState.FAILED, pipeline.state());
        assertFalse(pipeline.failureCause().isEmpty());
        assertThrows(
                IllegalStateException.class,
                () -> pipeline.tryPublish(command(1, 2)));
        assertInstanceOf(IllegalArgumentException.class, pipeline.failureCause().orElseThrow());
    }

    @Test
    void handlerFailureIsTerminal() throws InterruptedException {
        final MatchingEnginePipeline pipeline = newPipeline(result -> {
            throw new IllegalStateException("handler failure");
        });
        pipeline.start();
        assertEquals(PipelinePublishOutcome.ACCEPTED, pipeline.tryPublish(command(1, 1)));

        awaitFailure(pipeline);

        assertEquals(PipelineState.FAILED, pipeline.state());
        assertEquals("handler failure", pipeline.failureCause().orElseThrow().getMessage());
    }

    @Test
    void timeoutTransitionsToFailedAndCanBeReleased() throws InterruptedException {
        final CountDownLatch handlerEntered = new CountDownLatch(1);
        final CountDownLatch releaseHandler = new CountDownLatch(1);
        final MatchingEnginePipeline pipeline = newPipeline(result -> {
            handlerEntered.countDown();
            await(releaseHandler);
        });

        pipeline.start();
        assertEquals(PipelinePublishOutcome.ACCEPTED, pipeline.tryPublish(command(1, 1)));
        assertTrue(handlerEntered.await(2, TimeUnit.SECONDS));

        assertEquals(PipelineState.FAILED, pipeline.shutdown(Duration.ofMillis(50)));
        releaseHandler.countDown();
    }

    @Test
    void clearsCommandEventReferences() {
        final CommandEvent event = new CommandEvent();
        event.setCommand(command(1, 1));

        event.clear();

        assertNull(event.command());
    }

    private static MatchingEnginePipeline newPipeline(final EngineResultHandler handler) {
        return newPipeline(8, handler);
    }

    private static MatchingEnginePipeline newPipeline(
            final int capacity, final EngineResultHandler handler) {
        return new MatchingEnginePipeline(
                new PipelineConfiguration(capacity, PipelineWaitMode.BLOCKING), handler);
    }

    private static EngineCommand command(final long sequence, final long orderId) {
        return new SubmitLimitCommand(
                Sequence.of(sequence),
                OrderId.of(orderId),
                Side.BUY,
                Price.of(100),
                Quantity.of(1));
    }

    private static void awaitFailure(final MatchingEnginePipeline pipeline)
            throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (pipeline.state() != PipelineState.FAILED && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertEquals(PipelineState.FAILED, pipeline.state());
    }

    private static void await(final CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for test release");
            }
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for test release", exception);
        }
    }
}
