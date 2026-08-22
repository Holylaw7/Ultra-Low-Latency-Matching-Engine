package com.ultralatency.matching.recovery.online;

import com.ultralatency.matching.domain.Sequence;
import java.io.IOException;

/** Signals a strict offline recovery failure at an optional command sequence. */
public final class RecoveryException extends IOException {

    private static final long serialVersionUID = 1L;

    private final transient Sequence commandSequence;

    /** Creates a recovery failure without a command sequence. */
    public RecoveryException(final String message) {
        this(null, message, null);
    }

    /** Creates a recovery failure without a command sequence and with a cause. */
    public RecoveryException(final String message, final Throwable cause) {
        this(null, message, cause);
    }

    /** Creates a recovery failure with a cause and optional command sequence. */
    public RecoveryException(
            final Sequence commandSequence,
            final String message,
            final Throwable cause) {
        super(message, cause);
        this.commandSequence = commandSequence;
    }

    /** @return command sequence being applied, or null before command replay */
    public Sequence commandSequence() {
        return commandSequence;
    }
}
