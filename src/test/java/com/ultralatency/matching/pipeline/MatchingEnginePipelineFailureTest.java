package com.ultralatency.matching.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.engine.EngineResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class MatchingEnginePipelineFailureTest {

    @Test
    void fullAdmissionCanRetryTheSameCommandExactlyOnce() throws InterruptedException {
        final CountDownLatch firstHandlerEntered = new CountDownLatch(1);
        final CountDownLatch releaseFirstHandler = new CountDownLatch(1);
        final AtomicBoolean first = new AtomicBoolean(true);
        final AtomicInteger observedCount = new AtomicInteger();
        final List<EngineResult> results = new ArrayList<>();
        final MatchingEnginePipeline pipeline = new MatchingEnginePipeline(
                new PipelineConfiguration(2, PipelineWaitMode.BLOCKING), result -> {
                    if (first.getAndSet(false)) {
                        firstHandlerEntered.countDown();
                        await(releaseFirstHandler);
                    }
                    results.add(result);
                    observedCount.incrementAndGet();
                });

        pipeline.start();
        assertEquals(PipelinePublishOutcome.ACCEPTED, pipeline.tryPublish(PipelineCommandFixture.command(1, 1)));
        assertTrue(firstHandlerEntered.await(2, TimeUnit.SECONDS));

        int acceptedCount = 1;
        long retrySequence = 2;
        PipelinePublishOutcome outcome = PipelinePublishOutcome.ACCEPTED;
        EngineCommand retryCommand = PipelineCommandFixture.command(retrySequence, retrySequence);
        while (outcome == PipelinePublishOutcome.ACCEPTED && retrySequence <= 64) {
            outcome = pipeline.tryPublish(retryCommand);
            if (outcome == PipelinePublishOutcome.ACCEPTED) {
                acceptedCount++;
                retrySequence++;
                retryCommand = PipelineCommandFixture.command(retrySequence, retrySequence);
            }
        }
        assertEquals(PipelinePublishOutcome.FULL, outcome);

        releaseFirstHandler.countDown();
        awaitCount(observedCount, acceptedCount);
        assertEquals(PipelinePublishOutcome.ACCEPTED, pipeline.tryPublish(retryCommand));
        awaitCount(observedCount, acceptedCount + 1);
        assertEquals(PipelineState.STOPPED, pipeline.shutdown(Duration.ofSeconds(5)));

        assertEquals(acceptedCount + 1, results.size());
        for (int index = 0; index < results.size(); index++) {
            assertEquals(index + 1L, results.get(index).commandSequence().value());
        }
    }

    @Test
    void lifecycleIsExplicitAndSingleUse() {
        final MatchingEnginePipeline pipeline = newPipeline();

        assertThrows(
                IllegalStateException.class,
                () -> pipeline.tryPublish(PipelineCommandFixture.command(1, 1)));
        pipeline.start();
        assertThrows(IllegalStateException.class, pipeline::start);
        assertEquals(PipelinePublishOutcome.ACCEPTED, pipeline.tryPublish(PipelineCommandFixture.command(1, 1)));
        assertEquals(PipelineState.STOPPED, pipeline.shutdown(Duration.ofSeconds(2)));
        assertThrows(
                IllegalStateException.class,
                () -> pipeline.tryPublish(PipelineCommandFixture.command(2, 2)));
    }

    @Test
    void invalidSequenceIsTerminalAndPreservesFirstCause() throws InterruptedException {
        final MatchingEnginePipeline pipeline = newPipeline();
        pipeline.start();
        assertEquals(PipelinePublishOutcome.ACCEPTED, pipeline.tryPublish(PipelineCommandFixture.command(2, 1)));

        awaitState(pipeline, PipelineState.FAILED);

        assertFalse(pipeline.failureCause().isEmpty());
        assertInstanceOf(IllegalArgumentException.class, pipeline.failureCause().orElseThrow());
        assertThrows(
                IllegalStateException.class,
                () -> pipeline.tryPublish(PipelineCommandFixture.command(1, 2)));
    }

    @Test
    void resultHandlerFailureIsTerminalAndRejectsLaterCommands() throws InterruptedException {
        final AtomicInteger failureCount = new AtomicInteger();
        final AtomicReference<Throwable> observedFailure = new AtomicReference<>();
        final CountDownLatch callbackCompleted = new CountDownLatch(1);
        final MatchingEnginePipeline pipeline = new MatchingEnginePipeline(
                new PipelineConfiguration(8, PipelineWaitMode.BLOCKING), result -> {
                    throw new IllegalStateException("handler failure");
                }, failure -> {
                    failureCount.incrementAndGet();
                    observedFailure.compareAndSet(null, failure);
                    callbackCompleted.countDown();
                });
        pipeline.start();
        assertEquals(PipelinePublishOutcome.ACCEPTED, pipeline.tryPublish(PipelineCommandFixture.command(1, 1)));

        awaitState(pipeline, PipelineState.FAILED);
        assertTrue(callbackCompleted.await(5, TimeUnit.SECONDS));
        awaitCount(failureCount, 1);

        assertEquals("handler failure", pipeline.failureCause().orElseThrow().getMessage());
        assertEquals(1, failureCount.get());
        assertEquals(pipeline.failureCause().orElseThrow(), observedFailure.get());
        assertThrows(
                IllegalStateException.class,
                () -> pipeline.tryPublish(PipelineCommandFixture.command(2, 2)));
    }

    @Test
    void observerFailureCannotReplaceFirstPipelineCause() throws InterruptedException {
        final AtomicInteger failureCount = new AtomicInteger();
        final MatchingEnginePipeline pipeline = new MatchingEnginePipeline(
                new PipelineConfiguration(8, PipelineWaitMode.BLOCKING), result -> {
                    throw new IllegalStateException("first cause");
                }, failure -> {
                    failureCount.incrementAndGet();
                    throw new AssertionError("observer failure");
                });
        pipeline.start();
        assertEquals(PipelinePublishOutcome.ACCEPTED, pipeline.tryPublish(PipelineCommandFixture.command(1, 1)));

        awaitState(pipeline, PipelineState.FAILED);
        awaitCount(failureCount, 1);

        assertEquals(1, failureCount.get());
        assertEquals("first cause", pipeline.failureCause().orElseThrow().getMessage());
    }

    @Test
    void foreignProducerIsRejectedBeforeItCanPublish() throws InterruptedException {
        final MatchingEnginePipeline pipeline = newPipeline();
        pipeline.start();
        assertEquals(PipelinePublishOutcome.ACCEPTED, pipeline.tryPublish(PipelineCommandFixture.command(1, 1)));

        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final Thread foreignProducer = Thread.ofPlatform().start(() -> {
            try {
                pipeline.tryPublish(PipelineCommandFixture.command(2, 2));
            } catch (final Throwable exception) {
                failure.set(exception);
            }
        });
        foreignProducer.join(2_000);

        assertFalse(foreignProducer.isAlive());
        assertInstanceOf(IllegalStateException.class, failure.get());
        assertEquals(PipelineState.STOPPED, pipeline.shutdown(Duration.ofSeconds(2)));
    }

    private static MatchingEnginePipeline newPipeline() {
        return new MatchingEnginePipeline(
                new PipelineConfiguration(8, PipelineWaitMode.BLOCKING), result -> { });
    }

    private static void awaitCount(final AtomicInteger count, final int expected)
            throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (count.get() < expected && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertTrue(count.get() >= expected);
    }

    private static void awaitState(
            final MatchingEnginePipeline pipeline, final PipelineState expected)
            throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (pipeline.state() != expected && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertEquals(expected, pipeline.state());
    }

    private static void await(final CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for test release");
            }
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for test release", exception);
        }
    }
}
