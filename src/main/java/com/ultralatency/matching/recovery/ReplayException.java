package com.ultralatency.matching.recovery;

import com.ultralatency.matching.domain.Sequence;
import java.io.IOException;

/** Signals a deterministic replay failure at a specific command sequence. */
public final class ReplayException extends IOException {

    private static final long serialVersionUID = 1L;

    private final transient Sequence commandSequence;

    /**
     * Creates a replay failure.
     *
     * @param commandSequence sequence being applied, or null before the first command
     * @param message diagnostic message
     */
    public ReplayException(final Sequence commandSequence, final String message) {
        super(message);
        this.commandSequence = commandSequence;
    }

    /**
     * Creates a replay failure with a cause.
     *
     * @param commandSequence sequence being applied, or null before the first command
     * @param message diagnostic message
     * @param cause underlying engine failure
     */
    public ReplayException(
            final Sequence commandSequence,
            final String message,
            final Throwable cause) {
        super(message, cause);
        this.commandSequence = commandSequence;
    }

    /** @return command sequence being applied, or null before the first command */
    public Sequence commandSequence() {
        return commandSequence;
    }
}
