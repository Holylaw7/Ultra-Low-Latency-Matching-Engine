package com.ultralatency.matching.persistence.snapshot;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict immutable Snapshot v1 store with same-directory atomic publication. */
public final class SnapshotStore {

    private static final Pattern FINAL_NAME = Pattern.compile("snapshot-(\\d{20})\\.bin");
    private static final Pattern TEMP_NAME = Pattern.compile("snapshot-(\\d{20})\\.tmp");

    private final Path directory;
    private final SnapshotCodec codec;

    /** Creates a store using bounded default codec limits. */
    public SnapshotStore(final Path directory) {
        this(directory, new SnapshotCodec());
    }

    /** Creates a store with an explicit strict codec. */
    public SnapshotStore(final Path directory, final SnapshotCodec codec) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    /** @return Snapshot directory */
    public Path directory() {
        return directory;
    }

    /**
     * Publishes one Snapshot atomically without replacing an existing final file.
     *
     * @param snapshot immutable Snapshot value
     * @return final immutable path
     * @throws IOException when storage, read-back or atomic publication fails
     */
    public Path publish(final Snapshot snapshot) throws IOException {
        return publish(snapshot, null, null);
    }

    /**
     * Publishes a Snapshot while checking a WAL inventory before and after the move.
     *
     * @param snapshot immutable Snapshot value
     * @param walDirectory authoritative WAL directory, or null for no inventory check
     * @param expectedInventory scan-boundary inventory, required when walDirectory is set
     * @return final immutable path
     * @throws IOException when storage, inventory, read-back or atomic publication fails
     */
    public Path publish(
            final Snapshot snapshot,
            final Path walDirectory,
            final WalInventory expectedInventory) throws IOException {
        Objects.requireNonNull(snapshot, "snapshot");
        if ((walDirectory == null) != (expectedInventory == null)) {
            throw new IllegalArgumentException(
                    "WAL directory and expected inventory must be supplied together");
        }
        Files.createDirectories(directory);
        final String stem = String.format(Locale.ROOT, "snapshot-%020d", snapshot.checkpointSequence());
        final Path temporary = directory.resolve(stem + ".tmp");
        final Path finalPath = directory.resolve(stem + ".bin");
        if (Files.exists(finalPath)) {
            throw new IOException("Snapshot final file already exists: " + finalPath);
        }
        final byte[] bytes = codec.encode(snapshot);
        try {
            if (walDirectory != null) {
                requireInventory(walDirectory, expectedInventory);
            }
            writeAndForce(temporary, bytes);
            final Snapshot readBack = codec.decode(Files.readAllBytes(temporary));
            if (!snapshot.equals(readBack)) {
                throw new IOException("Snapshot strict read-back value differs from source");
            }
            if (walDirectory != null) {
                requireInventory(walDirectory, expectedInventory);
            }
            if (Files.exists(finalPath)) {
                throw new IOException("Snapshot final file appeared during publication");
            }
            try {
                Files.move(temporary, finalPath, StandardCopyOption.ATOMIC_MOVE);
            } catch (final AtomicMoveNotSupportedException exception) {
                throw new IOException("Snapshot publication requires ATOMIC_MOVE", exception);
            }
            if (walDirectory != null && !WalInventory.capture(walDirectory).equals(expectedInventory)) {
                Files.deleteIfExists(finalPath);
                throw new IOException("WAL inventory changed during Snapshot publication");
            }
            return finalPath;
        } catch (final RuntimeException exception) {
            throw new IOException("Snapshot publication validation failed", exception);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Reads and strictly validates one final Snapshot file. */
    public Snapshot read(final Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        final Matcher matcher = FINAL_NAME.matcher(path.getFileName().toString());
        if (!matcher.matches()) {
            throw new SnapshotFormatException("Snapshot path is not a final v1 file: " + path);
        }
        final long size = Files.size(path);
        if (size > codec.limits().maxSnapshotBytes()) {
            throw new SnapshotFormatException("Snapshot exceeds configured size limit");
        }
        final Snapshot snapshot = codec.decode(Files.readAllBytes(path));
        final long fileSequence = Long.parseLong(matcher.group(1));
        if (snapshot.checkpointSequence() != fileSequence) {
            throw new SnapshotFormatException("Snapshot filename sequence does not match header");
        }
        return snapshot;
    }

    /** Reads the highest-sequence final Snapshot; orphan temps are ignored. */
    public Optional<Snapshot> readLatest() throws IOException {
        if (!Files.exists(directory)) {
            return Optional.empty();
        }
        Path latest = null;
        long latestSequence = -1;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (final Path path : stream) {
                final Matcher matcher = FINAL_NAME.matcher(path.getFileName().toString());
                if (!matcher.matches()) {
                    continue;
                }
                final long sequence = Long.parseLong(matcher.group(1));
                if (sequence > latestSequence) {
                    latestSequence = sequence;
                    latest = path;
                }
            }
        }
        return latest == null ? Optional.empty() : Optional.of(read(latest));
    }

    /** @return whether a path is an orphan Snapshot temp file */
    public static boolean isTemporary(final Path path) {
        return path != null && TEMP_NAME.matcher(path.getFileName().toString()).matches();
    }

    private static void writeAndForce(final Path path, final byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(
                path,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
            final ByteBuffer buffer = ByteBuffer.wrap(bytes);
            long offset = 0;
            while (buffer.hasRemaining()) {
                final int written = channel.write(buffer, offset);
                if (written <= 0) {
                    throw new IOException("Snapshot write made no progress");
                }
                offset += written;
            }
            channel.force(true);
        }
    }

    private static void requireInventory(
            final Path walDirectory,
            final WalInventory expectedInventory) throws IOException {
        final WalInventory actual;
        try {
            actual = WalInventory.capture(walDirectory);
        } catch (final RuntimeException exception) {
            throw new IOException("Unable to inspect WAL inventory", exception);
        }
        if (!actual.equals(expectedInventory)) {
            throw new IOException("WAL inventory changed during Snapshot generation");
        }
    }
}
