package com.ultralatency.matching.benchmark;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import com.ultralatency.matching.domain.Order;
import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.domain.Side;
import com.ultralatency.matching.orderbook.OrderBook;

/**
 * Measurement-isolation experiments for the approved OrderBook baseline.
 *
 * <p>The matching workloads prepare independent cases once per JMH iteration.
 * Each measured invocation consumes every prepared case exactly once, so setup
 * and reset work remain outside the matching operation.</p>
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2)
@Threads(1)
public class OrderBookMeasurementIsolationBenchmark {

    private static final long BASE_PRICE = 100;
    private static final int BATCH_SIZE = 1024;
    private static final int SWEEP_LEVEL_COUNT = 64;
    private static final long MULTI_LEVEL_CASE_WIDTH = SWEEP_LEVEL_COUNT + 1L;

    /**
     * Measures steady-state single-level matching.
     *
     * @param state prepared independent one-level cases
     * @param blackhole benchmark sink
     */
    @Benchmark
    @OperationsPerInvocation(BATCH_SIZE)
    public void steadyStateSingleLevelMatch(
            final SingleLevelState state,
            final Blackhole blackhole) {
        for (final MatchCase matchCase : state.cases) {
            blackhole.consume(
                    matchCase.book.matchLimit(matchCase.incoming));
            blackhole.consume(matchCase.incoming.status());
        }
    }

    /**
     * Measures steady-state multi-level matching.
     *
     * @param state prepared independent multi-level sweep cases
     * @param blackhole benchmark sink
     */
    @Benchmark
    @OperationsPerInvocation(BATCH_SIZE)
    public void steadyStateMultiLevelMatch(
            final MultiLevelState state,
            final Blackhole blackhole) {
        for (final MatchCase matchCase : state.cases) {
            blackhole.consume(
                    matchCase.book.matchLimit(matchCase.incoming));
            blackhole.consume(matchCase.incoming.status());
        }
    }

    /**
     * Measures single-level state construction separately from matching.
     *
     * @param blackhole benchmark sink
     */
    @Benchmark
    @OperationsPerInvocation(BATCH_SIZE)
    public void lifecycleSingleLevelPreparation(
            final Blackhole blackhole) {
        for (int index = 0; index < BATCH_SIZE; index++) {
            final long baseId = index * 2L + 1L;
            final MatchCase matchCase = singleLevelCase(baseId);
            blackhole.consume(matchCase.book.activeOrderCount());
            blackhole.consume(matchCase.incoming.remainingQuantityUnits());
        }
    }

    /**
     * Measures multi-level state construction separately from matching.
     *
     * @param blackhole benchmark sink
     */
    @Benchmark
    @OperationsPerInvocation(BATCH_SIZE)
    public void lifecycleMultiLevelPreparation(
            final Blackhole blackhole) {
        for (int index = 0; index < BATCH_SIZE; index++) {
            final long baseId = index * MULTI_LEVEL_CASE_WIDTH + 1L;
            final MatchCase matchCase = multiLevelCase(baseId);
            blackhole.consume(matchCase.book.activeOrderCount());
            blackhole.consume(matchCase.incoming.remainingQuantityUnits());
        }
    }

    /**
     * State whose cases are prepared outside the measured invocation.
     */
    @State(Scope.Thread)
    public static class SingleLevelState {

        private MatchCase[] cases;

        /**
         * Prepares independent one-level cases once per JMH iteration.
         */
        @Setup(Level.Iteration)
        public void setUp() {
            cases = new MatchCase[BATCH_SIZE];
            for (int index = 0; index < BATCH_SIZE; index++) {
                cases[index] = singleLevelCase(index * 2L + 1L);
            }
        }
    }

    /**
     * State whose cases are prepared outside the measured invocation.
     */
    @State(Scope.Thread)
    public static class MultiLevelState {

        private MatchCase[] cases;

        /**
         * Prepares independent multi-level cases once per JMH iteration.
         */
        @Setup(Level.Iteration)
        public void setUp() {
            cases = new MatchCase[BATCH_SIZE];
            for (int index = 0; index < BATCH_SIZE; index++) {
                cases[index] = multiLevelCase(
                        index * MULTI_LEVEL_CASE_WIDTH + 1L);
            }
        }
    }

    private static MatchCase singleLevelCase(final long baseId) {
        final OrderBook book = new OrderBook();
        book.add(limit(
                baseId,
                Side.SELL,
                BASE_PRICE,
                1,
                baseId));
        return new MatchCase(
                book,
                limit(
                        baseId + 1L,
                        Side.BUY,
                        BASE_PRICE + 1L,
                        1,
                        baseId + 1L));
    }

    private static MatchCase multiLevelCase(final long baseId) {
        final OrderBook book = new OrderBook();
        for (int index = 0; index < SWEEP_LEVEL_COUNT; index++) {
            final long orderId = baseId + index;
            book.add(limit(
                    orderId,
                    Side.SELL,
                    BASE_PRICE + index,
                    1,
                    orderId));
        }
        return new MatchCase(
                book,
                limit(
                        baseId + SWEEP_LEVEL_COUNT,
                        Side.BUY,
                        BASE_PRICE + SWEEP_LEVEL_COUNT - 1L,
                        SWEEP_LEVEL_COUNT,
                        baseId + SWEEP_LEVEL_COUNT));
    }

    private static Order limit(
            final long orderId,
            final Side side,
            final long price,
            final long quantity,
            final long sequence) {
        return Order.limit(
                new OrderId(orderId),
                side,
                new Price(price),
                new Quantity(quantity),
                new Sequence(sequence));
    }

    private static final class MatchCase {

        private final OrderBook book;
        private final Order incoming;

        private MatchCase(
                final OrderBook book,
                final Order incoming) {
            this.book = book;
            this.incoming = incoming;
        }
    }
}
