package com.ultralatency.matching.persistence.wal;

import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.engine.EngineCommand;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Synchronous, caller-owned, single-writer segmented command WAL. */
public final class CommandWalWriter implements AutoCloseable {

    private final WalConfiguration configuration;
    private final WalCommandCodec codec;
    private FileChannel channel;
    private FileLock activeLock;
    private Path activePath;
    private long activeSegmentId;
    private long activeFirstSequence;
    private long writeOffset;
    private long nextSequence;
    private boolean closed;
    private boolean terminal;

    /**
     * Opens a writer and explicitly repairs only an incomplete final physical tail.
     *
     * @param configuration WAL configuration
     * @throws IOException when the existing WAL is corrupt or cannot be opened
     */
    public CommandWalWriter(final WalConfiguration configuration) throws IOException {
        this(configuration, new WalCommandCodec());
    }

    /**
     * Opens a writer with an explicit codec and explicit final-tail recovery.
     *
     * @param configuration WAL configuration
     * @param codec version-1 command codec
     * @throws IOException when the existing WAL is corrupt or cannot be opened
     */
    public CommandWalWriter(
            final WalConfiguration configuration,
            final WalCommandCodec codec) throws IOException {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.codec = Objects.requireNonNull(codec, "codec");
        initialize();
    }

    /**
     * Opens a writer and explicitly repairs only an incomplete final physical tail.
     *
     * @param configuration WAL configuration
     * @return opened writer
     * @throws IOException when the existing WAL is corrupt or cannot be opened
     */
    public static CommandWalWriter open(final WalConfiguration configuration) throws IOException {
        return new CommandWalWriter(configuration);
    }

    /**
     * Alias that makes the explicit reopen/recovery action visible at call sites.
     *
     * @param configuration WAL configuration
     * @return reopened writer
     * @throws IOException when the existing WAL is corrupt or cannot be opened
     */
    public static CommandWalWriter reopen(final WalConfiguration configuration) throws IOException {
        return new CommandWalWriter(configuration);
    }

    /**
     * Appends one command after complete encoding, writing and configured durability action.
     *
     * @param command immutable engine command
     * @throws IOException when the writer becomes terminal due to storage failure
     * @throws WalSequenceException when the command is not the exact next sequence
     */
    public void append(final EngineCommand command) throws IOException {
        ensureOpen();
        Objects.requireNonNull(command, "command");
        final long commandSequence = command.sequence().value();
        if (commandSequence != nextSequence) {
            throw new WalSequenceException(
                    "Expected command sequence " + nextSequence + ", actual " + commandSequence);
        }
        final byte[] record = codec.encodeRecord(command);
        try {
            if (channel == null || writeOffset + record.length > configuration.segmentSizeBytes()) {
                rotateTo(command.sequence());
            }
            writeFully(record);
            if (configuration.durabilityMode() == WalDurabilityMode.SYNC_EACH_APPEND) {
                channel.force(true);
            }
            writeOffset += record.length;
            nextSequence = command.sequence().next().value();
        } catch (final IOException exception) {
            terminal = true;
            throw exception;
        }
    }

    /** @return next logical command sequence accepted by this writer */
    public long nextCommandSequence() {
        return nextSequence;
    }

    /** @return whether a storage failure made this writer terminal */
    public boolean isTerminal() {
        return terminal;
    }

    /** @return current active segment path, or null before the first append */
    public Path activeSegmentPath() {
        return activePath;
    }

    /**
     * Closes the writer. Closing is idempotent and does not turn a successful writer into a
     * failed one merely because it was already closed.
     *
     * @throws IOException when channel or lock release fails
     */
    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        IOException failure = null;
        if (activeLock != null) {
            try {
                activeLock.release();
            } catch (final IOException exception) {
                failure = exception;
            } finally {
                activeLock = null;
            }
        }
        if (channel != null) {
            try {
                channel.close();
            } catch (final IOException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            } finally {
                channel = null;
            }
        }
        if (failure != null) {
            terminal = true;
            throw failure;
        }
    }

    private void initialize() throws IOException {
        Files.createDirectories(configuration.directory());
        WalScanResult scan = WalStorageScanner.scan(
                configuration.directory(),
                configuration,
                codec);
        while (scan.hasTail() || scan.emptyTrailingSegment()) {
            if (scan.emptyTrailingSegment()) {
                Files.deleteIfExists(scan.tailPath());
            } else {
                truncateTail(scan);
            }
            scan = WalStorageScanner.scan(configuration.directory(), configuration, codec);
        }
        final List<EngineCommand> commands = scan.commands();
        nextSequence = commands.isEmpty()
                ? 1
                : commands.get(commands.size() - 1).sequence().next().value();
        if (!scan.segments().isEmpty()) {
            final WalSegmentInfo last = scan.segments().get(scan.segments().size() - 1);
            activeSegmentId = last.segmentId();
            activeFirstSequence = last.firstCommandSequence();
            activePath = last.path();
            writeOffset = last.validEndOffset();
            openActiveSegment();
        }
    }

    private void truncateTail(final WalScanResult scan) throws IOException {
        try (FileChannel tail = FileChannel.open(scan.tailPath(), StandardOpenOption.WRITE)) {
            tail.truncate(scan.tailOffset());
            if (configuration.durabilityMode() == WalDurabilityMode.SYNC_EACH_APPEND) {
                tail.force(true);
            }
        }
    }

    private void rotateTo(final Sequence firstSequence) throws IOException {
        if (channel != null) {
            releaseActive();
        }
        final long segmentId = activeSegmentId == 0 ? 1 : activeSegmentId + 1;
        final Path path = configuration.directory().resolve(segmentName(firstSequence.value()));
        final FileChannel newChannel;
        try {
            newChannel = FileChannel.open(
                    path,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE);
        } catch (final IOException exception) {
            throw new WalStorageException(path, -1, "Unable to create WAL segment", exception);
        }
        FileLock newLock = null;
        try {
            newLock = newChannel.tryLock();
            if (newLock == null) {
                throw new IOException("WAL segment is already locked: " + path);
            }
            final byte[] header = codec.encodeSegmentHeader(segmentId, firstSequence);
            writeFully(newChannel, header, 0, path);
            if (configuration.durabilityMode() == WalDurabilityMode.SYNC_EACH_APPEND) {
                newChannel.force(true);
            }
            channel = newChannel;
            activeLock = newLock;
            activePath = path;
            activeSegmentId = segmentId;
            activeFirstSequence = firstSequence.value();
            writeOffset = WalCommandCodec.SEGMENT_HEADER_LENGTH;
        } catch (final IOException | RuntimeException exception) {
            if (newLock != null) {
                newLock.release();
            }
            newChannel.close();
            throw exception;
        }
    }

    private void openActiveSegment() throws IOException {
        final FileChannel existing = FileChannel.open(
                activePath,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE);
        try {
            activeLock = existing.tryLock();
            if (activeLock == null) {
                throw new IOException("WAL segment is already locked: " + activePath);
            }
            channel = existing;
        } catch (final OverlappingFileLockException | IOException exception) {
            existing.close();
            throw new WalStorageException(activePath, -1, "Unable to lock active WAL segment", exception);
        }
    }

    private void writeFully(final byte[] bytes) throws IOException {
        writeFully(channel, bytes, writeOffset, activePath);
    }

    private static void writeFully(
            final FileChannel target,
            final byte[] bytes,
            final long offset,
            final Path path) throws IOException {
        final ByteBuffer buffer = ByteBuffer.wrap(bytes);
        long position = offset;
        while (buffer.hasRemaining()) {
            final int written = target.write(buffer, position);
            if (written <= 0) {
                throw new WalStorageException(path, position, "WAL write made no progress");
            }
            position += written;
        }
    }

    private void releaseActive() throws IOException {
        IOException failure = null;
        if (activeLock != null) {
            try {
                activeLock.release();
            } catch (final IOException exception) {
                failure = exception;
            } finally {
                activeLock = null;
            }
        }
        if (channel != null) {
            try {
                channel.close();
            } catch (final IOException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            } finally {
                channel = null;
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("WAL writer is closed");
        }
        if (terminal) {
            throw new IllegalStateException("WAL writer is terminal");
        }
    }

    private static String segmentName(final long firstSequence) {
        return String.format(Locale.ROOT, "wal-%020d.log", firstSequence);
    }
}
