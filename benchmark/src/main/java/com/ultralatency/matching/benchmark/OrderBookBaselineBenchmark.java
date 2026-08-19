package com.ultralatency.matching.benchmark;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
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
 * Baseline JMH measurements for the approved Phase 2 OrderBook operations.
 *
 * <p>This benchmark measures the existing TreeMap, intrusive FIFO and active
 * OrderId index implementation. It is evidence for the baseline only and does
 * not authorize or contain performance alternatives.</p>
 */
@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2)
@Threads(1)
public class OrderBookBaselineBenchmark {

    private static final long BASE_PRICE = 100;
    private static final int SWEEP_LEVEL_COUNT = 64;

    /**
     * Measures insertion into an empty price index.
     *
     * @param state fresh insertion state
     * @param blackhole benchmark sink
     */
    @Benchmark
    public void priceLevelInsertion(
            final InsertionState state,
            final Blackhole blackhole) {
        state.book.add(state.order);
        blackhole.consume(state.book.bestBid());
    }

    /**
     * Measures best bid lookup on a populated book.
     *
     * @param state populated lookup state
     * @param blackhole benchmark sink
     */
    @Benchmark
    public void bestBidLookup(
            final LookupState state,
            final Blackhole blackhole) {
        blackhole.consume(state.book.bestBid());
    }

    /**
     * Measures best ask lookup on a populated book.
     *
     * @param state populated lookup state
     * @param blackhole benchmark sink
     */
    @Benchmark
    public void bestAskLookup(
            final LookupState state,
            final Blackhole blackhole) {
        blackhole.consume(state.book.bestAsk());
    }

    /**
     * Measures cancellation while retaining the price level.
     *
     * @param state cancellation state with two same-price orders
     * @param blackhole benchmark sink
     */
    @Benchmark
    public void cancelByOrderId(
            final CancelState state,
            final Blackhole blackhole) {
        blackhole.consume(state.book.cancel(state.orderId));
    }

    /**
     * Measures cancellation that also removes the last price level.
     *
     * @param state single-order cleanup state
     * @param blackhole benchmark sink
     */
    @Benchmark
    public void cancelAndCleanEmptyLevel(
            final EmptyLevelCleanupState state,
            final Blackhole blackhole) {
        blackhole.consume(state.book.cancel(state.orderId));
        blackhole.consume(state.book.bidPriceLevelCount());
    }

    /**
     * Measures one maker/taker match at one price level.
     *
     * @param state one-level matching state
     * @param blackhole benchmark sink
     */
    @Benchmark
    public void oneLevelMatch(
            final OneLevelMatchState state,
            final Blackhole blackhole) {
        blackhole.consume(state.book.matchLimit(state.incoming));
        blackhole.consume(state.incoming.remainingQuantityUnits());
    }

    /**
     * Measures a deterministic sweep across multiple ask price levels.
     *
     * @param state multi-level matching state
     * @param blackhole benchmark sink
     */
    @Benchmark
    public void multiLevelMatch(
            final MultiLevelMatchState state,
            final Blackhole blackhole) {
        blackhole.consume(state.book.matchLimit(state.incoming));
        blackhole.consume(state.incoming.status());
    }

    /**
     * State for a single insertion operation.
     */
    @State(Scope.Thread)
    public static class InsertionState {

        private OrderBook book;
        private Order order;

        /**
         * Creates fresh state for each measured invocation.
         */
        @Setup(Level.Invocation)
        public void setUp() {
            book = new OrderBook();
            order = limit(1, Side.BUY, BASE_PRICE, 1, 1);
        }
    }

    /**
     * State for best-price read operations.
     */
    @State(Scope.Thread)
    public static class LookupState {

        private OrderBook book;

        /**
         * Populates both sides before each benchmark iteration.
         */
        @Setup(Level.Iteration)
        public void setUp() {
            book = new OrderBook();
            for (int index = 0; index < SWEEP_LEVEL_COUNT; index++) {
                final long price = BASE_PRICE + index;
                book.add(limit(
                        index + 1L,
                        Side.BUY,
                        price,
                        1,
                        index + 1L));
                book.add(limit(
                        SWEEP_LEVEL_COUNT + index + 1L,
                        Side.SELL,
                        price + SWEEP_LEVEL_COUNT,
                        1,
                        SWEEP_LEVEL_COUNT + index + 1L));
            }
        }
    }

    /**
     * State for cancellation without empty-level removal.
     */
    @State(Scope.Thread)
    public static class CancelState {

        private final OrderId orderId = new OrderId(1);
        private OrderBook book;

        /**
         * Creates two same-price orders before each measured invocation.
         */
        @Setup(Level.Invocation)
        public void setUp() {
            book = new OrderBook();
            book.add(limit(1, Side.BUY, BASE_PRICE, 1, 1));
            book.add(limit(2, Side.BUY, BASE_PRICE, 1, 2));
        }
    }

    /**
     * State for cancellation that removes the only price level.
     */
    @State(Scope.Thread)
    public static class EmptyLevelCleanupState {

        private final OrderId orderId = new OrderId(1);
        private OrderBook book;

        /**
         * Creates one order before each measured invocation.
         */
        @Setup(Level.Invocation)
        public void setUp() {
            book = new OrderBook();
            book.add(limit(1, Side.BUY, BASE_PRICE, 1, 1));
        }
    }

    /**
     * State for one-level exact matching.
     */
    @State(Scope.Thread)
    public static class OneLevelMatchState {

        private OrderBook book;
        private Order incoming;

        /**
         * Creates one maker and one crossing taker before each invocation.
         */
        @Setup(Level.Invocation)
        public void setUp() {
            book = new OrderBook();
            book.add(limit(1, Side.SELL, BASE_PRICE, 1, 1));
            incoming = limit(2, Side.BUY, BASE_PRICE + 1, 1, 2);
        }
    }

    /**
     * State for a full multi-level ask sweep.
     */
    @State(Scope.Thread)
    public static class MultiLevelMatchState {

        private OrderBook book;
        private Order incoming;

        /**
         * Creates contiguous ask levels and a taker that consumes all of them.
         */
        @Setup(Level.Invocation)
        public void setUp() {
            book = new OrderBook();
            for (int index = 0; index < SWEEP_LEVEL_COUNT; index++) {
                final long id = index + 1L;
                book.add(limit(
                        id,
                        Side.SELL,
                        BASE_PRICE + index,
                        1,
                        id));
            }
            incoming = limit(
                    SWEEP_LEVEL_COUNT + 1L,
                    Side.BUY,
                    BASE_PRICE + SWEEP_LEVEL_COUNT - 1,
                    SWEEP_LEVEL_COUNT,
                    SWEEP_LEVEL_COUNT + 1L);
        }
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
}
