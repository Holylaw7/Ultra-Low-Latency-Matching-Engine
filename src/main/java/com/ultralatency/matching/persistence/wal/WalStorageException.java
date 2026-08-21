package com.ultralatency.matching.persistence.wal;

import java.io.IOException;
import java.nio.file.Path;

/** Signals a WAL storage or strict-scan failure with its physical location. */
public class WalStorageException extends IOException {

    private static final long serialVersionUID = 1L;

    private final transient Path path;
    private final long offset;

    /**
     * Creates a storage failure.
     *
     * @param path physical path involved, possibly null for directory failures
     * @param offset byte offset, or -1 when not applicable
     * @param message diagnostic message
     */
    public WalStorageException(final Path path, final long offset, final String message) {
        super(message);
        this.path = path;
        this.offset = offset;
    }

    /**
     * Creates a storage failure with a cause.
     *
     * @param path physical path involved, possibly null for directory failures
     * @param offset byte offset, or -1 when not applicable
     * @param message diagnostic message
     * @param cause underlying cause
     */
    public WalStorageException(
            final Path path,
            final long offset,
            final String message,
            final Throwable cause) {
        super(message, cause);
        this.path = path;
        this.offset = offset;
    }

    /** @return physical path involved, or null when not applicable */
    public Path path() {
        return path;
    }

    /** @return byte offset, or -1 when not applicable */
    public long offset() {
        return offset;
    }
}
