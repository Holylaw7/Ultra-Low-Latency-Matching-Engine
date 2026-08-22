package com.ultralatency.matching.integration.durable;

import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.pipeline.PipelinePublishOutcome;
import java.util.Objects;

/**
 * Adapter port for the existing bounded pipeline's non-blocking publication.
 */
@FunctionalInterface
public interface DurablePublishPort {

    /**
     * Attempts exactly one publication without retrying or waiting.
     *
     * @param command immutable engine command
     * @return existing pipeline admission outcome
     */
    PipelinePublishOutcome tryPublish(EngineCommand command);

    /**
     * Attempts publication of the command carried by an immutable durable envelope.
     *
     * @param command durable command envelope
     * @return existing pipeline admission outcome
     */
    default PipelinePublishOutcome tryPublish(final DurableCommand command) {
        return tryPublish(Objects.requireNonNull(command, "command").command());
    }
}
