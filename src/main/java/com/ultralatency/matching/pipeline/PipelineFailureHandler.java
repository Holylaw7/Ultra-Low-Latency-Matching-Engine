package com.ultralatency.matching.pipeline;

/**
 * Non-blocking observer for the first terminal pipeline failure.
 *
 * <p>The callback runs on the thread that observes the failure. A network adapter may use it to
 * schedule shutdown on its own event loop; it must not perform blocking work here.</p>
 */
@FunctionalInterface
public interface PipelineFailureHandler {

    /**
     * Observes the first terminal failure at most once.
     *
     * @param failure first terminal failure cause
     */
    void onFailure(Throwable failure);
}
