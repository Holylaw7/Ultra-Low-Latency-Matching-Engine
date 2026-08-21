package com.ultralatency.matching.benchmark;

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
import com.ultralatency.matching.pipeline.MatchingEnginePipeline;
import com.ultralatency.matching.pipeline.PipelineConfiguration;
import com.ultralatency.matching.pipeline.PipelinePublishOutcome;
import com.ultralatency.matching.pipeline.PipelineState;
import com.ultralatency.matching.pipeline.PipelineWaitMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Component-level evidence for direct engine work and the approved pipeline boundary.
 *
 * <p>Producer admission and batch completion are deliberately separate measurements. This class
 * does not claim network, durable or end-to-end trading performance.</p>
 */
@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@Threads(1)
public class PipelineBenchmark {

    private static final int BATCH_SIZE = 256;

    /**
     * Measures one direct synchronous engine command application.
     *
     * @param state fresh direct-engine state
     * @return immutable engine result
     */
    @Benchmark
    public EngineResult directSynchronous(final DirectState state) {
        return state.engine.process(state.command);
    }

    /**
     * Measures producer-side admission only; setup and drain are outside this timer.
     *
     * @param state one-command pipeline state
     * @return accepted admission outcome
     */
    @Benchmark
    public PipelinePublishOutcome producerAdmission(final ProducerAdmissionState state) {
        final PipelinePublishOutcome outcome = state.pipeline.tryPublish(state.command);
        if (outcome == PipelinePublishOutcome.ACCEPTED) {
            state.acceptedCount.incrementAndGet();
            state.nextCommand();
        }
        return outcome;
    }

    /**
     * Measures publication plus verified completion of a fixed command batch.
     *
     * @param state fixed mixed-workload pipeline state
     * @return completed result count
     * @throws InterruptedException when the bounded completion wait is interrupted
     */
    @Benchmark
    public int batchCompletion(final BatchCompletionState state) throws InterruptedException {
        for (final EngineCommand command : state.commands) {
            final PipelinePublishOutcome outcome = state.pipeline.tryPublish(command);
            if (outcome != PipelinePublishOutcome.ACCEPTED) {
                throw new IllegalStateException("Batch admission unexpectedly returned " + outcome);
            }
        }
        state.publishedCount += state.commands.size();
        if (!state.completed.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for benchmark batch completion");
        }
        return state.completedCount.get();
    }

    /**
     * State for a direct synchronous baseline.
     */
    @State(Scope.Thread)
    public static class DirectState {

        private MatchingEngine engine;
        private EngineCommand command;

        /**
         * Creates a fresh engine and fixed no-match command outside measurement.
         */
        @Setup(Level.Invocation)
        public void setUp() {
            engine = new MatchingEngine();
            command = submit(1, 1, Side.BUY, 100);
        }
    }

    /**
     * State for isolated producer-side admission measurements.
     */
    @State(Scope.Thread)
    public static class ProducerAdmissionState {

        @Param({"1024", "65536"})
        private int capacity;

        @Param({"BLOCKING", "YIELDING", "BUSY_SPIN"})
        private String waitMode;

        private MatchingEnginePipeline pipeline;
        private AtomicInteger completedCount;
        private AtomicInteger acceptedCount;
        private EngineCommand command;
        private Sequence nextSequence;

        /**
         * Starts one pipeline per iteration so the measured operation remains admission only.
         */
        @Setup(Level.Iteration)
        public void setUp() {
            completedCount = new AtomicInteger();
            acceptedCount = new AtomicInteger();
            pipeline = new MatchingEnginePipeline(
                    new PipelineConfiguration(capacity, PipelineWaitMode.valueOf(waitMode)),
                    result -> completedCount.incrementAndGet());
            nextSequence = Sequence.of(1);
            command = submit(nextSequence.value(), nextSequence.value(), Side.BUY, 100);
            pipeline.start();
        }

        /**
         * Drains and validates accepted commands outside measurement.
         */
        @TearDown(Level.Iteration)
        public void tearDown() {
            if (pipeline.shutdown(Duration.ofSeconds(5)) != PipelineState.STOPPED) {
                throw new IllegalStateException("Admission benchmark pipeline did not stop cleanly");
            }
            if (completedCount.get() != acceptedCount.get()) {
                throw new IllegalStateException(
                        "Admission benchmark completed " + completedCount.get()
                                + " of " + acceptedCount.get());
            }
        }

        private void nextCommand() {
            nextSequence = nextSequence.next();
            command = submit(nextSequence.value(), nextSequence.value(), Side.BUY, 100);
        }
    }

    /**
     * State for fixed mixed command batch completion measurements.
     */
    @State(Scope.Thread)
    public static class BatchCompletionState {

        @Param({"1024", "65536"})
        private int capacity;

        @Param({"BLOCKING", "YIELDING", "BUSY_SPIN"})
        private String waitMode;

        private List<EngineCommand> commands;
        private MatchingEnginePipeline pipeline;
        private CountDownLatch completed;
        private AtomicInteger completedCount;
        private int publishedCount;
        private Sequence nextSequence;

        /**
         * Starts one pipeline for the iteration; batch preparation is outside measurement.
         */
        @Setup(Level.Iteration)
        public void setUp() {
            completedCount = new AtomicInteger();
            publishedCount = 0;
            nextSequence = Sequence.of(1);
            pipeline = new MatchingEnginePipeline(
                    new PipelineConfiguration(capacity, PipelineWaitMode.valueOf(waitMode)),
                    result -> {
                        completedCount.incrementAndGet();
                        completed.countDown();
                    });
            pipeline.start();
        }

        @Setup(Level.Invocation)
        public void prepareBatch() {
            commands = mixedCommands(nextSequence);
            nextSequence = Sequence.of(nextSequence.value() + BATCH_SIZE);
            completed = new CountDownLatch(commands.size());
        }

        /**
         * Drains and validates all results outside measurement.
         */
        @TearDown(Level.Iteration)
        public void tearDown() {
            if (pipeline.shutdown(Duration.ofSeconds(5)) != PipelineState.STOPPED) {
                throw new IllegalStateException("Batch benchmark pipeline did not stop cleanly");
            }
            if (completedCount.get() != publishedCount) {
                throw new IllegalStateException(
                        "Batch benchmark completed " + completedCount.get()
                                + " of " + publishedCount);
            }
        }
    }

    private static List<EngineCommand> mixedCommands(final Sequence firstSequence) {
        final List<EngineCommand> commands = new ArrayList<>(BATCH_SIZE);
        long sequence = firstSequence.value();
        final long firstCycle = ((sequence - 1) / 8) + 1;
        for (int cycle = 0; cycle < BATCH_SIZE / 8; cycle++) {
            final long cycleId = firstCycle + cycle;
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
