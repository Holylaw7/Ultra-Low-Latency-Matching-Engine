package com.ultralatency.matching.integration.durable;

import com.ultralatency.matching.engine.EngineCommand;
import java.io.IOException;
import java.util.Objects;

/**
 * Adapter port for one synchronous command WAL append.
 *
 * <p>An implementation must return only after its configured durability action has completed.
 * The existing {@code CommandWalWriter} is the production adapter; this interface does not add a
 * queue, retry or alternate WAL format.</p>
 */
@FunctionalInterface
public interface DurableAppendPort {

    /**
     * Appends one command and reports failure by throwing.
     *
     * @param command immutable engine command
     * @throws IOException when write, force or rotation fails
     */
    void append(EngineCommand command) throws IOException;

    /**
     * Appends the command carried by an immutable durable envelope.
     *
     * @param command durable command envelope
     * @throws IOException when write, force or rotation fails
     */
    default void append(final DurableCommand command) throws IOException {
        append(Objects.requireNonNull(command, "command").command());
    }
}
