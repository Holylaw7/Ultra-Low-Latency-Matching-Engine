package com.ultralatency.matching.recovery.online;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.domain.Side;
import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.engine.MatchingEngine;
import com.ultralatency.matching.engine.SubmitLimitCommand;
import com.ultralatency.matching.persistence.snapshot.OfflineSnapshotGenerator;
import com.ultralatency.matching.persistence.snapshot.RecoveryLease;
import com.ultralatency.matching.persistence.snapshot.Snapshot;
import com.ultralatency.matching.persistence.snapshot.SnapshotStore;
import com.ultralatency.matching.persistence.wal.CommandWalWriter;
import com.ultralatency.matching.persistence.wal.WalCommandCodec;
import com.ultralatency.matching.persistence.wal.WalConfiguration;
import com.ultralatency.matching.persistence.wal.WalDurabilityMode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecoveryPlannerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void pureWalEmptyDirectoryProducesGenesisResult() throws IOException {
        final WalConfiguration configuration = configuration("empty-wal");
        final RecoveryResult result = planner(configuration, "empty-snapshots")
                .recover(RecoveryMode.PURE_WAL);

        assertEquals(RecoveryMode.PURE_WAL, result.mode());
        assertEquals(0, result.walEndSequence());
        assertEquals(1, result.nextCommandSequence());
        assertEquals(0, result.snapshotSequence());
        assertTrue(result.replayTranscript().results().isEmpty());
        assertEquals(0, result.checkpoint().lastAppliedCommandSequence());
    }

    @Test
    void externallyOwnedLeaseRemainsHeldAfterRecovery() throws IOException {
        final WalConfiguration configuration = configuration("externally-owned-wal");
        try (RecoveryLease lease = RecoveryLease.acquire(configuration.directory())) {
            final RecoveryResult result = planner(configuration, "externally-owned-snapshots")
                    .recover(RecoveryMode.PURE_WAL, lease);

            assertEquals(0, result.walEndSequence());
            assertTrue(lease.isHeld());
            assertThrows(
                    IOException.class,
                    () -> RecoveryLease.acquire(configuration.directory()));
        }
        try (RecoveryLease reacquired = RecoveryLease.acquire(configuration.directory())) {
            assertTrue(reacquired.isHeld());
        }
    }

    @Test
    void closedExternallyOwnedLeaseIsRejected() throws IOException {
        final WalConfiguration configuration = configuration("closed-lease-wal");
        final RecoveryLease lease = RecoveryLease.acquire(configuration.directory());
        lease.close();

        assertThrows(
                IOException.class,
                () -> planner(configuration, "closed-lease-snapshots")
                        .recover(RecoveryMode.PURE_WAL, lease));
    }

    @Test
    void pureWalAndSnapshotTailConvergeWithTailOnlyTranscript() throws IOException {
        final WalConfiguration configuration = configuration("convergent-wal");
        final List<EngineCommand> prefix = List.of(
                command(1, 1, Side.SELL, 100),
                command(2, 2, Side.BUY, 100));
        final List<EngineCommand> tail = List.of(
                command(3, 3, Side.BUY, 101),
                command(4, 4, Side.SELL, 101));
        write(configuration, prefix);
        final SnapshotStore store = new SnapshotStore(temporaryDirectory.resolve("convergent-snapshots"));
        final Snapshot snapshot = new OfflineSnapshotGenerator(configuration, store).generate();
        append(configuration, tail);

        final RecoveryResult pure = planner(configuration, store).recover(RecoveryMode.PURE_WAL);
        final RecoveryResult accelerated = planner(configuration, store)
                .recover(RecoveryMode.SNAPSHOT_THEN_WAL);

        assertEquals(2, snapshot.checkpointSequence());
        assertEquals(pure.checkpoint(), accelerated.checkpoint());
        assertEquals(pure.checkpointDigestHex(), accelerated.checkpointDigestHex());
        assertEquals(pure.walDigestHex(), accelerated.walDigestHex());
        assertEquals(pure.replayTranscript().results().subList(2, 4),
                accelerated.replayTranscript().results());
        assertEquals(2, accelerated.snapshotSequence());
        assertEquals(5, accelerated.nextCommandSequence());
    }

    @Test
    void snapshotAtWalEndDoesNotApplyCommandsTwice() throws IOException {
        final WalConfiguration configuration = configuration("at-end-wal");
        final List<EngineCommand> commands = List.of(
                command(1, 1, Side.SELL, 100),
                command(2, 2, Side.BUY, 100));
        write(configuration, commands);
        final SnapshotStore store = new SnapshotStore(temporaryDirectory.resolve("at-end-snapshots"));
        new OfflineSnapshotGenerator(configuration, store).generate();

        final RecoveryResult result = planner(configuration, store)
                .recover(RecoveryMode.SNAPSHOT_THEN_WAL);

        assertEquals(2, result.snapshotSequence());
        assertTrue(result.replayTranscript().results().isEmpty());
        assertEquals(3, result.nextCommandSequence());
    }

    @Test
    void missingSnapshotFailsWithoutPureWalFallback() throws IOException {
        final WalConfiguration configuration = configuration("missing-snapshot-wal");
        write(configuration, List.of(command(1, 1, Side.BUY, 100)));

        assertThrows(
                RecoveryException.class,
                () -> planner(configuration, "missing-snapshot-dir")
                        .recover(RecoveryMode.SNAPSHOT_THEN_WAL));
    }

    @Test
    void prefixDigestMismatchFailsClosed() throws IOException {
        final WalConfiguration configuration = configuration("mismatch-wal");
        final List<EngineCommand> commands = List.of(command(1, 1, Side.BUY, 100));
        write(configuration, commands);
        final SnapshotStore sourceStore = new SnapshotStore(temporaryDirectory.resolve("source"));
        final Snapshot source = new OfflineSnapshotGenerator(configuration, sourceStore).generate();
        final SnapshotStore badStore = new SnapshotStore(temporaryDirectory.resolve("bad"));
        badStore.publish(new Snapshot(source.checkpoint(), new byte[Snapshot.SHA256_LENGTH]));

        assertThrows(
                RecoveryException.class,
                () -> planner(configuration, badStore).recover(RecoveryMode.SNAPSHOT_THEN_WAL));
    }

    @Test
    void newerSnapshotFailsClosed() throws IOException {
        final WalConfiguration configuration = configuration("newer-snapshot-wal");
        write(configuration, List.of(command(1, 1, Side.BUY, 100)));
        final MatchingEngine engine = new MatchingEngine();
        engine.process(command(1, 1, Side.BUY, 100));
        engine.process(command(2, 2, Side.BUY, 101));
        final SnapshotStore store = new SnapshotStore(temporaryDirectory.resolve("newer-snapshot"));
        store.publish(new Snapshot(engine.checkpoint(), new byte[Snapshot.SHA256_LENGTH]));

        assertThrows(
                RecoveryException.class,
                () -> planner(configuration, store).recover(RecoveryMode.SNAPSHOT_THEN_WAL));
    }

    @Test
    void corruptLatestSnapshotFailsWithoutOlderFallback() throws IOException {
        final WalConfiguration configuration = configuration("corrupt-snapshot-wal");
        write(configuration, List.of(command(1, 1, Side.BUY, 100)));
        final SnapshotStore store = new SnapshotStore(temporaryDirectory.resolve("corrupt-snapshot"));
        new OfflineSnapshotGenerator(configuration, store).generate();
        final Path latest = store.directory().resolve("snapshot-00000000000000000001.bin");
        final byte[] bytes = Files.readAllBytes(latest);
        bytes[bytes.length - 1] ^= 1;
        Files.write(latest, bytes);

        assertThrows(
                RecoveryException.class,
                () -> planner(configuration, store).recover(RecoveryMode.SNAPSHOT_THEN_WAL));
    }

    @Test
    void poisonCommandFailsAtItsSequenceWithoutFallback() throws IOException {
        final WalConfiguration configuration = configuration("poison-wal");
        write(configuration, List.of(
                command(1, 1, Side.SELL, 100),
                command(2, 1, Side.BUY, 100)));

        final RecoveryException failure = assertThrows(
                RecoveryException.class,
                () -> planner(configuration, "poison-snapshots").recover(RecoveryMode.PURE_WAL));

        assertEquals(new Sequence(2), failure.commandSequence());
    }

    private RecoveryPlanner planner(
            final WalConfiguration configuration,
            final String snapshotDirectory) {
        return planner(configuration, new SnapshotStore(temporaryDirectory.resolve(snapshotDirectory)));
    }

    private static RecoveryPlanner planner(
            final WalConfiguration configuration,
            final SnapshotStore store) {
        return new RecoveryPlanner(configuration, store);
    }

    private WalConfiguration configuration(final String directoryName) {
        return new WalConfiguration(
                temporaryDirectory.resolve(directoryName),
                WalCommandCodec.MIN_SEGMENT_SIZE_BYTES,
                WalDurabilityMode.BUFFERED);
    }

    private static void write(
            final WalConfiguration configuration,
            final List<EngineCommand> commands) throws IOException {
        try (CommandWalWriter writer = CommandWalWriter.open(configuration)) {
            for (final EngineCommand command : commands) {
                writer.append(command);
            }
        }
    }

    private static void append(
            final WalConfiguration configuration,
            final List<EngineCommand> commands) throws IOException {
        try (CommandWalWriter writer = CommandWalWriter.reopen(configuration)) {
            for (final EngineCommand command : commands) {
                writer.append(command);
            }
        }
    }

    private static SubmitLimitCommand command(
            final long sequence,
            final long orderId,
            final Side side,
            final long price) {
        return new SubmitLimitCommand(
                new Sequence(sequence),
                new OrderId(orderId),
                side,
                new Price(price),
                new Quantity(1));
    }
}
