package com.ultralatency.matching.persistence.snapshot;

/** Signals an invalid or unsupported Snapshot v1 byte sequence. */
public final class SnapshotFormatException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    /** Creates a Snapshot format error. */
    public SnapshotFormatException(final String message) {
        super(message);
    }

    /** Creates a Snapshot format error with its underlying cause. */
    public SnapshotFormatException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
