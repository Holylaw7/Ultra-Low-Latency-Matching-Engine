package com.ultralatency.matching.pipeline;

import com.lmax.disruptor.ExceptionHandler;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Bridges Disruptor lifecycle and consumer failures into the project-owned fail-stop state.
 */
final class PipelineExceptionHandler implements ExceptionHandler<CommandEvent> {

    private final Consumer<Throwable> failureHandler;

    PipelineExceptionHandler(final Consumer<Throwable> failureHandler) {
        this.failureHandler = Objects.requireNonNull(failureHandler, "failureHandler");
    }

    @Override
    public void handleEventException(
            final Throwable exception, final long sequence, final CommandEvent event) {
        failureHandler.accept(exception);
    }

    @Override
    public void handleOnStartException(final Throwable exception) {
        failureHandler.accept(exception);
    }

    @Override
    public void handleOnShutdownException(final Throwable exception) {
        failureHandler.accept(exception);
    }
}
