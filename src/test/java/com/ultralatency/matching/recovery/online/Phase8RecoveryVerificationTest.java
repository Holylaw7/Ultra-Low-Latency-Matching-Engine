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
import com.ultralatency.matching.engine.EngineResult;
import com.ultralatency.matching.engine.SubmitLimitCommand;
import com.ultralatency.matching.integration.durable.DurableConfiguration;
import com.ultralatency.matching.integration.recovery.RecoverableDurableRuntime;
import com.ultralatency.matching.integration.recovery.RecoveryRuntimeConfiguration;
import com.ultralatency.matching.network.netty.durable.DurableNetworkConfiguration;
import com.ultralatency.matching.network.netty.recovery.RecoverableDurableMatchingEngineTcpServer;
import com.ultralatency.matching.network.netty.recovery.RecoverableNetworkConfiguration;
import com.ultralatency.matching.network.protocol.ClientRequestId;
import com.ultralatency.matching.integration.recovery.RecoveryRuntimeState;
import com.ultralatency.matching.persistence.snapshot.OfflineSnapshotGenerator;
import com.ultralatency.matching.persistence.snapshot.Snapshot;
import com.ultralatency.matching.persistence.snapshot.SnapshotFormatException;
import com.ultralatency.matching.persistence.snapshot.SnapshotStore;
import com.ultralatency.matching.persistence.wal.CommandWalWriter;
import com.ultralatency.matching.persistence.wal.WalCommandCodec;
import com.ultralatency.matching.persistence.wal.WalConfiguration;
import com.ultralatency.matching.persistence.wal.WalDurabilityMode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Dynamic Phase 8 crash, corruption and deterministic recovery evidence. */
class Phase8RecoveryVerificationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void temporarySnapshotIsIgnoredButPublishedCorruptionFailsClosed() throws IOException {
        final WalConfiguration configuration = configuration("temporary-snapshot-wal");
        write(configuration, List.of(command(1, 1, Side.BUY, 100, 1)));
        final SnapshotStore store = new SnapshotStore(
                temporaryDirectory.resolve("temporary-snapshot-store"));
        final Snapshot published = new OfflineSnapshotGenerator(configuration, store).generate();

        final Path orphan = store.directory().resolve(
                "snapshot-00000000000000000002.tmp");
        Files.writeString(orphan, "incomplete snapshot");
        assertEquals(published, store.readLatest().orElseThrow());

        final Path corruptPublished = store.directory().resolve(
                "snapshot-00000000000000000002.bin");
        Files.write(corruptPublished, new byte[WalCommandCodec.SEGMENT_HEADER_LENGTH]);
        assertThrows(SnapshotFormatException.class, store::readLatest);
    }

    @Test
    void repeatedPureWalAndSnapshotTailRecoveryPreserveSuffixIdentity() throws IOException {
        final WalConfiguration configuration = configuration("repeated-recovery-wal");
        final List<EngineCommand> prefix = List.of(
                command(1, 10, Side.SELL, 100, 2),
                command(2, 11, Side.BUY, 100, 1));
        final List<EngineCommand> tail = List.of(
                command(3, 12, Side.BUY, 101, 1),
                command(4, 13, Side.SELL, 101, 1));
        write(configuration, prefix);
        final SnapshotStore store = new SnapshotStore(
                temporaryDirectory.resolve("repeated-recovery-snapshots"));
        final Snapshot snapshot = new OfflineSnapshotGenerator(configuration, store).generate();
        append(configuration, tail);

        for (int cycle = 0; cycle < 3; cycle++) {
            final RecoveryResult pure = planner(configuration, store)
                    .recover(RecoveryMode.PURE_WAL);
            final RecoveryResult snapshotTail = planner(configuration, store)
                    .recover(RecoveryMode.SNAPSHOT_THEN_WAL);

            assertEquals(snapshot.checkpointSequence(), snapshotTail.snapshotSequence());
            assertEquals(pure.checkpoint(), snapshotTail.checkpoint());
            assertEquals(pure.checkpointDigestHex(), snapshotTail.checkpointDigestHex());
            assertEquals(pure.walDigestHex(), snapshotTail.walDigestHex());
            assertEquals(pure.nextCommandSequence(), snapshotTail.nextCommandSequence());

            final List<EngineResult> pureSuffix = pure.replayTranscript().results()
                    .subList(Math.toIntExact(snapshot.checkpointSequence()),
                            pure.replayTranscript().results().size());
            assertEquals(pureSuffix, snapshotTail.replayTranscript().results());
            assertEquals(tradeIds(pureSuffix), tradeIds(snapshotTail.replayTranscript().results()));
            assertEquals(
                    eventSequences(pureSuffix),
                    eventSequences(snapshotTail.replayTranscript().results()));

            final EngineCommand probe = command(5, 15, Side.SELL, 102, 1);
            assertEquals(
                    pure.engine().process(probe),
                    snapshotTail.engine().process(probe));
        }
    }

    @Test
    void hardWalCorruptionFailsBeforeRecoveredListenerBind() throws IOException {
        final Path walDirectory = temporaryDirectory.resolve("hard-corrupt-wal");
        final WalConfiguration wal = WalConfiguration.defaults(walDirectory);
        write(wal, List.of(command(1, 21, Side.BUY, 100, 1)));
        final Path segment = walDirectory.resolve("wal-00000000000000000001.log");
        final byte[] bytes = Files.readAllBytes(segment);
        bytes[0] ^= 1;
        Files.write(segment, bytes);

        final RecoverableNetworkConfiguration network = RecoverableNetworkConfiguration.from(
                DurableNetworkConfiguration.defaults(walDirectory),
                temporaryDirectory.resolve("hard-corrupt-snapshots"),
                RecoveryMode.PURE_WAL);
        final RecoverableDurableMatchingEngineTcpServer server =
                new RecoverableDurableMatchingEngineTcpServer(network);

        assertThrows(RuntimeException.class, server::start);
        assertEquals(RecoveryRuntimeState.FAILED, server.state());
        assertTrue(server.localAddress().isEmpty());
        assertTrue(server.failureCause().isPresent());
        server.shutdown();
    }

    @Test
    void recoveredRuntimeEmitsNoReplayResultsAndFirstLiveCommandUsesNextSequence()
            throws Exception {
        final WalConfiguration configuration = configuration("live-handoff-wal");
        write(configuration, List.of(
                command(1, 31, Side.SELL, 100, 2),
                command(2, 32, Side.BUY, 100, 1)));
        final SnapshotStore store = new SnapshotStore(
                temporaryDirectory.resolve("live-handoff-snapshots"));
        new OfflineSnapshotGenerator(configuration, store).generate();
        append(configuration, List.of(
                command(3, 33, Side.BUY, 101, 1),
                command(4, 34, Side.SELL, 101, 1)));

        final CountDownLatch liveResultSeen = new CountDownLatch(1);
        final AtomicInteger observedResultCount = new AtomicInteger();
        final AtomicReference<EngineResult> observedResult = new AtomicReference<>();
        final RecoverableDurableRuntime runtime = new RecoverableDurableRuntime(
                new RecoveryRuntimeConfiguration(
                        RecoveryMode.SNAPSHOT_THEN_WAL,
                        store.directory(),
                        DurableConfiguration.defaults(configuration.directory())),
                result -> {
                    observedResultCount.incrementAndGet();
                    observedResult.set(result);
                    liveResultSeen.countDown();
                });

        runtime.start();
        try {
            assertEquals(0, observedResultCount.get());
            runtime.coordinator().accept(
                    ClientRequestId.of(1),
                    sequence -> command(sequence.toSequence(), 35, Side.SELL, 102, 1));
            assertTrue(liveResultSeen.await(2, TimeUnit.SECONDS));
            assertEquals(1, observedResultCount.get());
            assertEquals(5, observedResult.get().commandSequence().value());
        } finally {
            runtime.shutdown();
        }
    }

    private RecoveryPlanner planner(
            final WalConfiguration configuration,
            final SnapshotStore store) {
        return new RecoveryPlanner(configuration, store);
    }

    private WalConfiguration configuration(final String name) {
        return new WalConfiguration(
                temporaryDirectory.resolve(name),
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
            final long price,
            final long quantity) {
        return command(Sequence.of(sequence), orderId, side, price, quantity);
    }

    private static SubmitLimitCommand command(
            final Sequence sequence,
            final long orderId,
            final Side side,
            final long price,
            final long quantity) {
        return new SubmitLimitCommand(
                sequence,
                OrderId.of(orderId),
                side,
                Price.of(price),
                Quantity.of(quantity));
    }

    private static List<Long> tradeIds(final List<EngineResult> results) {
        return results.stream()
                .flatMap(result -> result.matches().stream())
                .map(match -> match.trade().tradeId().value())
                .toList();
    }

    private static List<Long> eventSequences(final List<EngineResult> results) {
        return results.stream()
                .flatMap(result -> result.matches().stream())
                .map(match -> match.eventSequence().value())
                .toList();
    }
}
