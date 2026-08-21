package com.ultralatency.matching.pipeline;

import com.ultralatency.matching.engine.EngineCommand;

/**
 * Mutable infrastructure slot reused by the Disruptor ring.
 */
final class CommandEvent {

    private EngineCommand command;

    /**
     * Returns the command currently held by this slot.
     *
     * @return command, or {@code null} when the slot is clear
     */
    EngineCommand command() {
        return command;
    }

    /**
     * Assigns the immutable command reference for one publication.
     *
     * @param nextCommand command to hold
     */
    void setCommand(final EngineCommand nextCommand) {
        command = nextCommand;
    }

    /**
     * Releases the command reference after consumption.
     */
    void clear() {
        command = null;
    }
}
