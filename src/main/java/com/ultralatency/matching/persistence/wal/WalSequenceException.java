package com.ultralatency.matching.persistence.wal;

/** Signals a command sequence that is not the exact next logical WAL sequence. */
public final class WalSequenceException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a sequence validation failure.
     *
     * @param message diagnostic message
     */
    public WalSequenceException(final String message) {
        super(message);
    }
}
