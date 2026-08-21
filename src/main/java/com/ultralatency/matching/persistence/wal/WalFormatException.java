package com.ultralatency.matching.persistence.wal;

/**
 * Signals an invalid or unsupported version-1 WAL byte sequence.
 */
public final class WalFormatException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a format error with a diagnostic message.
     *
     * @param message diagnostic message
     */
    public WalFormatException(final String message) {
        super(message);
    }

    /**
     * Creates a format error with a diagnostic message and cause.
     *
     * @param message diagnostic message
     * @param cause underlying cause
     */
    public WalFormatException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
