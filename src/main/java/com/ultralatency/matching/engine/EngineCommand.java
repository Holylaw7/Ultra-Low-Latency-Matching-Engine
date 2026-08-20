package com.ultralatency.matching.engine;

import com.ultralatency.matching.domain.Sequence;

/**
 * Immutable command submitted to the matching engine.
 *
 * <p>The sequence is allocated by the upstream command source. The matching engine will later
 * validate that it is the exact next command sequence before mutating state.</p>
 */
public sealed interface EngineCommand permits SubmitLimitCommand, CancelOrderCommand {

    /**
     * Returns the upstream command sequence.
     *
     * @return command sequence
     */
    Sequence sequence();
}
