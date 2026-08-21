package com.ultralatency.matching.pipeline;

import com.lmax.disruptor.EventHandler;
import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.engine.EngineResult;
import com.ultralatency.matching.engine.MatchingEngine;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Disruptor consumer that applies commands and synchronously hands off results.
 */
final class MatchingEventHandler implements EventHandler<CommandEvent> {

    private final MatchingEngine matchingEngine;
    private final EngineResultHandler resultHandler;
    private final Consumer<Throwable> failureHandler;

    MatchingEventHandler(
            final MatchingEngine matchingEngine,
            final EngineResultHandler resultHandler,
            final Consumer<Throwable> failureHandler) {
        this.matchingEngine = Objects.requireNonNull(matchingEngine, "matchingEngine");
        this.resultHandler = Objects.requireNonNull(resultHandler, "resultHandler");
        this.failureHandler = Objects.requireNonNull(failureHandler, "failureHandler");
    }

    @Override
    public void onEvent(final CommandEvent event, final long sequence, final boolean endOfBatch)
            throws Exception {
        try {
            final EngineCommand command = Objects.requireNonNull(event.command(), "command event");
            final EngineResult result = matchingEngine.process(command);
            resultHandler.onResult(result);
        } catch (final Throwable failure) {
            failureHandler.accept(failure);
            rethrow(failure);
        } finally {
            event.clear();
        }
    }

    private static void rethrow(final Throwable failure) throws Exception {
        if (failure instanceof Exception exception) {
            throw exception;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new RuntimeException(failure);
    }
}
