package com.ultralatency.matching.persistence.snapshot;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.domain.Side;
import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.engine.SubmitLimitCommand;
import com.ultralatency.matching.persistence.wal.CommandWalWriter;
import com.ultralatency.matching.persistence.wal.WalCommandCodec;
import com.ultralatency.matching.persistence.wal.WalConfiguration;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OfflineSnapshotGeneratorTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void generatesFromClosedWalWithExactPrefixDigestAndCheckpoint() throws IOException {
        final Path walDirectory = temporaryDirectory.resolve("wal");
        final WalConfiguration configuration = WalConfiguration.defaults(walDirectory);
        final List<EngineCommand> commands = List.of(
                command(1, 1, Side.BUY, 100, 5),
                command(2, 2, Side.BUY, 101, 7),
                command(3, 3, Side.SELL, 100, 2));
        try (CommandWalWriter writer = CommandWalWriter.open(configuration)) {
            for (final EngineCommand command : commands) {
                writer.append(command);
            }
        }

        final SnapshotStore store = new SnapshotStore(temporaryDirectory.resolve("snapshots"));
        final Snapshot snapshot = new OfflineSnapshotGenerator(configuration, store).generate();

        assertEquals(3, snapshot.checkpointSequence());
        assertEquals(2, snapshot.checkpoint().activeOrderCount());
        assertArrayEquals(expectedDigest(commands), snapshot.walPrefixDigest());
        assertEquals(snapshot, store.readLatest().orElseThrow());
    }

    @Test
    void rejectsEmptyWalAndLeaseContention() throws IOException {
        final Path walDirectory = temporaryDirectory.resolve("wal");
        final WalConfiguration configuration = WalConfiguration.defaults(walDirectory);
        final SnapshotStore store = new SnapshotStore(temporaryDirectory.resolve("snapshots"));
        final OfflineSnapshotGenerator generator = new OfflineSnapshotGenerator(configuration, store);

        assertThrows(IOException.class, generator::generate);
        try (RecoveryLease ignored = RecoveryLease.acquire(walDirectory)) {
            assertThrows(IOException.class, generator::generate);
        }
    }

    @Test
    void rejectsIncompleteClosedWalTail() throws IOException {
        final Path walDirectory = temporaryDirectory.resolve("wal");
        final WalConfiguration configuration = WalConfiguration.defaults(walDirectory);
        try (CommandWalWriter writer = CommandWalWriter.open(configuration)) {
            writer.append(command(1, 1, Side.BUY, 100, 1));
        }
        final Path segment = walDirectory.resolve("wal-00000000000000000001.log");
        java.nio.file.Files.write(
                segment,
                new byte[] {0, 0, 0, 52},
                java.nio.file.StandardOpenOption.APPEND);

        assertThrows(
                IOException.class,
                () -> new OfflineSnapshotGenerator(
                        configuration,
                        new SnapshotStore(temporaryDirectory.resolve("snapshots"))).generate());
    }

    private static SubmitLimitCommand command(
            final long sequence,
            final long orderId,
            final Side side,
            final long price,
            final long quantity) {
        return new SubmitLimitCommand(
                new Sequence(sequence),
                new OrderId(orderId),
                side,
                new Price(price),
                new Quantity(quantity));
    }

    private static byte[] expectedDigest(final List<EngineCommand> commands) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final WalCommandCodec codec = new WalCommandCodec();
            for (final EngineCommand command : commands) {
                digest.update(codec.encodeRecord(command));
            }
            return digest.digest();
        } catch (final NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
