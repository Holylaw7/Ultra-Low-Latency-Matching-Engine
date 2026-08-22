package com.ultralatency.matching.integration.durable;

/**
 * Observer for the first terminal failure of the durable composition.
 *
 * <p>The observer reports an immutable value and does not authorize retry, recovery, a second
 * producer or a lifecycle transition.  The coordinator remains the owner of those semantics.</p>
 */
@FunctionalInterface
public interface DurableFailurePort {

    /**
     * Observes the retained first terminal failure.
     *
     * @param failure immutable terminal failure
     */
    void onFailure(DurableTerminalFailure failure);
}
