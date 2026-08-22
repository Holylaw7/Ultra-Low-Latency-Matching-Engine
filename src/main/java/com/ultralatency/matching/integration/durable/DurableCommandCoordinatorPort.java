package com.ultralatency.matching.integration.durable;

import java.util.Optional;

/**
 * Admission and lifecycle observation port implemented by the Phase 7 coordinator.
 *
 * <p>The command factory receives the sequence allocated by the coordinator. A successful return
 * proves append/force followed by pipeline {@code ACCEPTED}; it does not prove response completion.
 * Terminal failures are fail-stop and must be retained by the implementation.</p>
 */
public interface DurableCommandCoordinatorPort {

    /**
     * Constructs, durably appends and publishes one request in the owning producer context.
     *
     * @param requestId session-owned request identity
     * @param commandFactory coordinator-sequenced command construction callback
     * @return live-accepted outcome
     */
    LiveAcceptedOutcome accept(
            com.ultralatency.matching.network.protocol.ClientRequestId requestId,
            DurableCommandFactory commandFactory);

    /**
     * Returns the current lifecycle state.
     *
     * @return lifecycle state
     */
    DurableLifecycleState state();

    /**
     * Returns the first terminal failure, if the coordinator has failed.
     *
     * @return retained terminal failure
     */
    Optional<DurableTerminalFailure> terminalFailure();
}
