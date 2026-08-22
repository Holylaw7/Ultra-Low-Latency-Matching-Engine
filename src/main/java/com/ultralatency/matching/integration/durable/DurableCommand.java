package com.ultralatency.matching.integration.durable;

import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.network.protocol.ClientRequestId;
import java.util.Objects;

/**
 * Immutable command envelope carrying session correlation without changing the frozen engine
 * command or WAL formats.
 *
 * @param identity request and command identities
 * @param command existing versioned engine command
 */
public record DurableCommand(DurableCommandIdentity identity, EngineCommand command) {

    /**
     * Validates that the envelope identity and engine command agree on the command sequence.
     */
    public DurableCommand {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(command, "command");
        if (!identity.commandSequence().toSequence().equals(command.sequence())) {
            throw new IllegalArgumentException(
                    "Durable command identity sequence must match engine command sequence");
        }
    }

    /**
     * Creates an envelope from a session request ID and a sequenced engine command.
     *
     * @param requestId session-owned request ID
     * @param command sequenced engine command
     * @return immutable envelope
     */
    public static DurableCommand of(
            final ClientRequestId requestId,
            final EngineCommand command) {
        Objects.requireNonNull(command, "command");
        return new DurableCommand(
                new DurableCommandIdentity(
                        requestId, new DurableCommandSequence(command.sequence())),
                command);
    }
}
