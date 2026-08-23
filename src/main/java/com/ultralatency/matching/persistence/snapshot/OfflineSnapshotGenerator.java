package com.ultralatency.matching.persistence.snapshot;

import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.engine.MatchingEngine;
import com.ultralatency.matching.persistence.wal.CommandWalReader;
import com.ultralatency.matching.persistence.wal.WalCommandCodec;
import com.ultralatency.matching.persistence.wal.WalConfiguration;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;

/**
 * Offline, quiescent Snapshot generator for a strictly closed WAL.
 *
 * <p>The shared recovery lease is held from inventory capture through immutable
 * Snapshot publication. This class deliberately does not implement live
 * recovery, WAL retention or Snapshot-tail orchestration.</p>
 */
public final class OfflineSnapshotGenerator {

    private final WalConfiguration walConfiguration;
    private final SnapshotStore store;
    private final WalCommandCodec codec;

    /** Creates a generator using the standard WAL codec and Snapshot store. */
    public OfflineSnapshotGenerator(
            final WalConfiguration walConfiguration,
            final SnapshotStore store) {
        this(walConfiguration, store, new WalCommandCodec());
    }

    /** Creates a generator with explicit frozen WAL codec and Snapshot store. */
    public OfflineSnapshotGenerator(
            final WalConfiguration walConfiguration,
            final SnapshotStore store,
            final WalCommandCodec codec) {
        this.walConfiguration = Objects.requireNonNull(walConfiguration, "walConfiguration");
        this.store = Objects.requireNonNull(store, "store");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    /**
     * Strictly replays a closed WAL and publishes one immutable Snapshot.
     *
     * @return published Snapshot value
     * @throws IOException when the WAL is empty/open/corrupt or publication fails
     */
    public Snapshot generate() throws IOException {
        final Path walDirectory = walConfiguration.directory();
        try (RecoveryLease ignored = RecoveryLease.acquire(walDirectory)) {
            final WalInventory scanInventory = captureInventory(walDirectory);
            final List<EngineCommand> commands = CommandWalReader.read(walConfiguration);
            if (commands.isEmpty()) {
                throw new IOException("Cannot generate a Snapshot from an empty WAL");
            }
            requireStable(walDirectory, scanInventory);
            final MatchingEngine engine = new MatchingEngine();
            final MessageDigest prefixDigest = sha256();
            for (final EngineCommand command : commands) {
                try {
                    engine.process(command);
                } catch (final RuntimeException exception) {
                    throw new IOException(
                            "WAL replay failed at command sequence "
                                    + command.sequence().value(),
                            exception);
                }
                prefixDigest.update(codec.encodeRecord(command));
            }
            final Snapshot snapshot = new Snapshot(engine.checkpoint(), prefixDigest.digest());
            store.publish(snapshot, walDirectory, scanInventory);
            return snapshot;
        }
    }

    private static WalInventory captureInventory(final Path walDirectory) throws IOException {
        try {
            return WalInventory.capture(walDirectory);
        } catch (final RuntimeException exception) {
            throw new IOException("Unable to capture WAL inventory", exception);
        }
    }

    private static void requireStable(
            final Path walDirectory,
            final WalInventory expected) throws IOException {
        final WalInventory actual = captureInventory(walDirectory);
        if (!expected.equals(actual)) {
            throw new IOException("WAL inventory changed during strict scan");
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK must provide SHA-256", exception);
        }
    }
}
