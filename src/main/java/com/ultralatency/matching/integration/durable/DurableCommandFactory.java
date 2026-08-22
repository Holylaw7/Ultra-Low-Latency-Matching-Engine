package com.ultralatency.matching.integration.durable;

import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.engine.EngineCommand;

/**
 * Coordinator-owned command construction boundary.
 *
 * <p>The coordinator supplies the candidate command sequence. A gateway can therefore translate
 * a validated Protocol v1 request without owning or deriving the durable sequence.</p>
 */
@FunctionalInterface
public interface DurableCommandFactory {

    /**
     * Builds an immutable engine command for the supplied coordinator sequence.
     *
     * @param sequence candidate logical command sequence
     * @return sequenced engine command
     */
    EngineCommand create(DurableCommandSequence sequence);

    /**
     * Builds a command from the frozen domain sequence at an adapter boundary.
     *
     * @param sequence validated domain command sequence
     * @return sequenced engine command
     */
    default EngineCommand create(final Sequence sequence) {
        return create(new DurableCommandSequence(sequence));
    }
}
