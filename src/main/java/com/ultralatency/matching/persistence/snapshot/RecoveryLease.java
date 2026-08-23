package com.ultralatency.matching.persistence.snapshot;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/** Exclusive lease shared by offline Snapshot generation and recovery runtime. */
public final class RecoveryLease implements AutoCloseable {

    /** Fixed lease file name from ADR-0016. */
    public static final String FILE_NAME = "recovery.lock";

    private final FileChannel channel;
    private FileLock lock;
    private boolean closed;

    private RecoveryLease(final FileChannel channel, final FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    /**
     * Acquires the shared exclusive recovery lease without waiting.
     *
     * @param walDirectory authoritative WAL directory
     * @return held lease
     * @throws IOException when another participant owns the lease
     */
    public static RecoveryLease acquire(final Path walDirectory) throws IOException {
        Objects.requireNonNull(walDirectory, "walDirectory");
        Files.createDirectories(walDirectory);
        final Path lockPath = walDirectory.resolve(FILE_NAME);
        final FileChannel channel = FileChannel.open(
                lockPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE);
        try {
            final FileLock lock = channel.tryLock();
            if (lock == null) {
                throw new IOException("Recovery lease is already held: " + lockPath);
            }
            return new RecoveryLease(channel, lock);
        } catch (final OverlappingFileLockException exception) {
            try {
                channel.close();
            } catch (final IOException closeFailure) {
                exception.addSuppressed(closeFailure);
            }
            throw new IOException("Recovery lease is already held: " + lockPath, exception);
        } catch (final IOException exception) {
            try {
                channel.close();
            } catch (final IOException closeFailure) {
                exception.addSuppressed(closeFailure);
            }
            throw exception;
        }
    }

    /** @return whether this lease is still open and held */
    public boolean isHeld() {
        return !closed && lock != null && lock.isValid();
    }

    /** Releases the lock and closes the lease channel. */
    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        IOException failure = null;
        if (lock != null) {
            try {
                lock.release();
            } catch (final IOException exception) {
                failure = exception;
            } finally {
                lock = null;
            }
        }
        try {
            channel.close();
        } catch (final IOException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
