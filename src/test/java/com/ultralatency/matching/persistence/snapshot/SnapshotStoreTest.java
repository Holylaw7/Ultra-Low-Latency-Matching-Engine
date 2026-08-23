package com.ultralatency.matching.persistence.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.engine.MatchingEngineCheckpoint;
import com.ultralatency.matching.orderbook.OrderBookCheckpoint;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnapshotStoreTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void publishesReadBackValidatedImmutableFinalAndIgnoresOrphanTemp() throws IOException {
        final SnapshotStore store = new SnapshotStore(temporaryDirectory.resolve("snapshots"));
        final Snapshot snapshot = snapshot(3);

        final Path published = store.publish(snapshot);

        assertTrue(Files.exists(published));
        assertEquals(snapshot, store.read(published));
        assertEquals(Optional.of(snapshot), store.readLatest());
        assertTrue(SnapshotStore.isTemporary(
                temporaryDirectory.resolve("snapshots/snapshot-00000000000000000003.tmp")));
        Files.writeString(
                temporaryDirectory.resolve("snapshots/snapshot-00000000000000000004.tmp"),
                "orphan");
        assertEquals(Optional.of(snapshot), store.readLatest());
        assertThrows(IOException.class, () -> store.publish(snapshot));
    }

    @Test
    void corruptLatestFinalSnapshotFailsClosedInsteadOfFallingBack() throws IOException {
        final SnapshotStore store = new SnapshotStore(temporaryDirectory.resolve("snapshots"));
        store.publish(snapshot(3));
        final Path corrupt = store.directory().resolve("snapshot-00000000000000000004.bin");
        Files.write(corrupt, new byte[132]);

        assertThrows(SnapshotFormatException.class, store::readLatest);
    }

    @Test
    void inventoryMismatchRejectsPublication() throws IOException {
        final Path walDirectory = temporaryDirectory.resolve("wal");
        Files.createDirectories(walDirectory);
        Files.write(walDirectory.resolve("wal-00000000000000000001.log"), new byte[] {1});
        final WalInventory expected = WalInventory.capture(walDirectory);
        Files.write(walDirectory.resolve("wal-00000000000000000001.log"), new byte[] {1, 2});
        final SnapshotStore store = new SnapshotStore(temporaryDirectory.resolve("snapshots"));

        assertThrows(
                IOException.class,
                () -> store.publish(snapshot(3), walDirectory, expected));
        assertFalse(Files.exists(store.directory().resolve("snapshot-00000000000000000003.bin")));
    }

    @Test
    void recoveryLeaseIsExclusiveAndReusableAfterClose() throws IOException {
        final Path walDirectory = temporaryDirectory.resolve("wal");
        try (RecoveryLease first = RecoveryLease.acquire(walDirectory)) {
            assertTrue(first.isHeld());
            assertThrows(IOException.class, () -> RecoveryLease.acquire(walDirectory));
        }
        try (RecoveryLease second = RecoveryLease.acquire(walDirectory)) {
            assertTrue(second.isHeld());
        }
    }

    private static Snapshot snapshot(final long sequence) {
        final MatchingEngineCheckpoint checkpoint = new MatchingEngineCheckpoint(
                sequence,
                2,
                2,
                new OrderBookCheckpoint(List.of(), List.of()));
        final byte[] digest = new byte[Snapshot.SHA256_LENGTH];
        Arrays.fill(digest, (byte) sequence);
        return new Snapshot(checkpoint, digest);
    }
}
