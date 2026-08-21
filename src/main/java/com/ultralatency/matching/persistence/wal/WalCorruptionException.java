package com.ultralatency.matching.persistence.wal;

import java.nio.file.Path;

/** Signals strict WAL corruption or an incomplete physical tail. */
public final class WalCorruptionException extends WalStorageException {

    private static final long serialVersionUID = 1L;

    private final boolean incompleteTail;

    /**
     * Creates a corruption diagnostic.
     *
     * @param path segment path
     * @param offset byte offset
     * @param message diagnostic message
     * @param incompleteTail whether only an incomplete final physical record was found
     */
    public WalCorruptionException(
            final Path path,
            final long offset,
            final String message,
            final boolean incompleteTail) {
        super(path, offset, message);
        this.incompleteTail = incompleteTail;
    }

    /**
     * Creates a corruption diagnostic with a cause.
     *
     * @param path segment path
     * @param offset byte offset
     * @param message diagnostic message
     * @param cause underlying cause
     * @param incompleteTail whether only an incomplete final physical record was found
     */
    public WalCorruptionException(
            final Path path,
            final long offset,
            final String message,
            final Throwable cause,
            final boolean incompleteTail) {
        super(path, offset, message, cause);
        this.incompleteTail = incompleteTail;
    }

    /** @return whether this is an incomplete final physical record */
    public boolean incompleteTail() {
        return incompleteTail;
    }
}
