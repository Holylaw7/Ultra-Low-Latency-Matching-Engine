package com.ultralatency.matching.pipeline;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.EventTranslatorOneArg;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.TimeoutException;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.engine.MatchingEngine;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Bounded synchronous-owner pipeline facade around one matching engine.
 *
 * <p>Publication is non-blocking and single-producer. The consumer thread owns the matching
 * engine while the pipeline is running. This class contains no network, persistence or recovery
 * behavior.</p>
 */
public final class MatchingEnginePipeline {

    private static final EventTranslatorOneArg<CommandEvent, EngineCommand> COMMAND_TRANSLATOR =
            (event, sequence, command) -> event.setCommand(command);

    private final Object lifecycleMonitor = new Object();
    private final PipelineConfiguration configuration;
    private final MatchingEngine matchingEngine;
    private final EngineResultHandler resultHandler;
    private volatile PipelineState state = PipelineState.NEW;
    private volatile Throwable failureCause;
    private Thread producerThread;
    private Disruptor<CommandEvent> disruptor;
    private RingBuffer<CommandEvent> ringBuffer;

    /**
     * Creates a pipeline facade without allocating a consumer thread or starting a ring.
     *
     * @param configuration validated pipeline configuration
     * @param resultHandler synchronous in-memory result handler
     */
    public MatchingEnginePipeline(
            final PipelineConfiguration configuration,
            final EngineResultHandler resultHandler) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.resultHandler = Objects.requireNonNull(resultHandler, "resultHandler");
        matchingEngine = new MatchingEngine();
    }

    /**
     * Starts the single pipeline consumer exactly once.
     *
     * @throws IllegalStateException when the lifecycle does not permit starting
     */
    public void start() {
        synchronized (lifecycleMonitor) {
            requireState(PipelineState.NEW, "start");
            try {
                disruptor = createDisruptor();
                disruptor.handleEventsWith(new MatchingEventHandler(
                        matchingEngine, resultHandler, this::failTerminal));
                disruptor.setDefaultExceptionHandler(new PipelineExceptionHandler(this::failTerminal));
                ringBuffer = disruptor.getRingBuffer();
                state = PipelineState.RUNNING;
                disruptor.start();
            } catch (final Throwable failure) {
                failTerminal(failure);
                throw rethrowUnchecked(failure);
            }
        }
    }

    /**
     * Attempts to publish one command without blocking or retrying internally.
     *
     * @param command immutable engine command
     * @return admission outcome
     */
    public PipelinePublishOutcome tryPublish(final EngineCommand command) {
        Objects.requireNonNull(command, "command");
        synchronized (lifecycleMonitor) {
            requireRunning();
            claimProducerThread();
            try {
                return ringBuffer.tryPublishEvent(COMMAND_TRANSLATOR, command)
                        ? PipelinePublishOutcome.ACCEPTED
                        : PipelinePublishOutcome.FULL;
            } catch (final RuntimeException failure) {
                failTerminal(failure);
                throw failure;
            }
        }
    }

    /**
     * Stops publication, drains accepted commands within the supplied bound and returns the
     * resulting lifecycle state.
     *
     * @param timeout maximum drain duration
     * @return {@link PipelineState#STOPPED} on a clean drain or {@link PipelineState#FAILED}
     *         when the drain times out or another terminal failure occurred
     */
    public PipelineState shutdown(final Duration timeout) {
        final long timeoutNanos = timeoutNanos(timeout);
        final Disruptor<CommandEvent> currentDisruptor;
        synchronized (lifecycleMonitor) {
            if (state == PipelineState.STOPPED || state == PipelineState.FAILED) {
                return state;
            }
            requireState(PipelineState.RUNNING, "shutdown");
            state = PipelineState.DRAINING;
            currentDisruptor = disruptor;
        }
        try {
            currentDisruptor.shutdown(timeoutNanos, TimeUnit.NANOSECONDS);
            synchronized (lifecycleMonitor) {
                if (state == PipelineState.DRAINING) {
                    state = PipelineState.STOPPED;
                }
                return state;
            }
        } catch (final TimeoutException exception) {
            failTerminal(new IllegalStateException("Pipeline drain timed out", exception));
            return state;
        }
    }

    /**
     * Returns the current lifecycle state.
     *
     * @return current state
     */
    public PipelineState state() {
        return state;
    }

    /**
     * Returns the first terminal failure, when one exists.
     *
     * @return optional failure cause
     */
    public Optional<Throwable> failureCause() {
        return Optional.ofNullable(failureCause);
    }

    private Disruptor<CommandEvent> createDisruptor() {
        final Disruptor<CommandEvent> newDisruptor = new Disruptor<>(
                CommandEvent::new,
                configuration.capacity(),
                pipelineThreadFactory(),
                ProducerType.SINGLE,
                waitStrategy(configuration.waitMode()));
        newDisruptor.setDefaultExceptionHandler(new PipelineExceptionHandler(this::failTerminal));
        return newDisruptor;
    }

    private static ThreadFactory pipelineThreadFactory() {
        return Thread.ofPlatform().name("matching-engine-pipeline").factory();
    }

    private static WaitStrategy waitStrategy(final PipelineWaitMode waitMode) {
        return switch (waitMode) {
            case BLOCKING -> new BlockingWaitStrategy();
            case YIELDING -> new YieldingWaitStrategy();
            case BUSY_SPIN -> new BusySpinWaitStrategy();
        };
    }

    private void claimProducerThread() {
        final Thread currentThread = Thread.currentThread();
        if (producerThread == null) {
            producerThread = currentThread;
        } else if (producerThread != currentThread) {
            throw new IllegalStateException("Pipeline supports one producer thread");
        }
    }

    private void requireRunning() {
        if (state != PipelineState.RUNNING) {
            if (state == PipelineState.FAILED && failureCause != null) {
                throw new IllegalStateException("Pipeline is failed", failureCause);
            }
            throw new IllegalStateException("Pipeline is not running: " + state);
        }
    }

    private void requireState(final PipelineState expected, final String operation) {
        if (state != expected) {
            throw new IllegalStateException(
                    "Cannot " + operation + " while pipeline is " + state);
        }
    }

    private void failTerminal(final Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        synchronized (lifecycleMonitor) {
            if (state == PipelineState.FAILED || state == PipelineState.STOPPED) {
                return;
            }
            failureCause = failure;
            state = PipelineState.FAILED;
            if (disruptor != null) {
                disruptor.halt();
            }
        }
    }

    private static long timeoutNanos(final Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("Shutdown timeout must not be negative");
        }
        try {
            return timeout.toNanos();
        } catch (final ArithmeticException exception) {
            throw new IllegalArgumentException("Shutdown timeout is too large", exception);
        }
    }

    private static RuntimeException rethrowUnchecked(final Throwable failure) {
        if (failure instanceof RuntimeException exception) {
            return exception;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        return new IllegalStateException("Pipeline failed to start", failure);
    }
}
