package com.ultralatency.matching.integration.recovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.ultralatency.matching.integration.durable.DurableCommandSequence;
import com.ultralatency.matching.integration.durable.DurableConfiguration;
import com.ultralatency.matching.integration.durable.DurableLifecycleState;
import com.ultralatency.matching.persistence.snapshot.OfflineSnapshotGenerator;
import com.ultralatency.matching.persistence.snapshot.RecoveryLease;
import com.ultralatency.matching.persistence.snapshot.SnapshotStore;
import com.ultralatency.matching.persistence.wal.CommandWalWriter;
import com.ultralatency.matching.persistence.wal.WalConfiguration;
import com.ultralatency.matching.recovery.online.RecoveryMode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecoverableDurableRuntimeTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void recoversBeforeStartingAndSeedsEveryLiveOwner() throws Exception {
        final WalConfiguration wal = WalConfiguration.defaults(temporaryDirectory.resolve("wal"));
        write(wal, List.of(
                command(1, 1, Side.SELL, 100, 2),
                command(2, 2, Side.BUY, 100, 1)));
        final CountDownLatch resultSeen = new CountDownLatch(1);
        final AtomicReference<EngineResult> observed = new AtomicReference<>();
        final RecoverableDurableRuntime runtime = runtime(wal, result -> {
            observed.set(result);
            resultSeen.countDown();
        });

        runtime.start();
        try {
            assertEquals(RecoveryRuntimeState.RUNNING, runtime.state());
            assertEquals(3, runtime.recoveryResult().nextCommandSequence());
            assertEquals(
                    new DurableCommandSequence(3),
                    runtime.coordinator().nextCommandSequence());
            assertEquals(DurableLifecycleState.RUNNING, runtime.coordinator().state());
            assertEquals(3, runtime.recoveryResult().engine().checkpoint()
                    .lastAppliedCommandSequence() + 1);

            runtime.coordinator().accept(
                    com.ultralatency.matching.network.protocol.ClientRequestId.of(1),
                    sequence -> command(sequence.toSequence(), 3, Side.BUY, 100, 1));

            assertTrue(resultSeen.await(2, TimeUnit.SECONDS));
            assertEquals(Sequence.of(3), observed.get().commandSequence());
            assertEquals(2, observed.get().matches().get(0).trade().tradeId().value());
            assertEquals(2, observed.get().matches().get(0).eventSequence().value());
        } finally {
            assertEquals(RecoveryRuntimeState.STOPPED, runtime.shutdown());
        }

        assertFalse(runtime.failureCause().isPresent());
        try (RecoveryLease ignored = RecoveryLease.acquire(wal.directory())) {
            assertTrue(ignored.isHeld());
        }
    }

    @Test
    void startupFailureIsTerminalAndDoesNotExposeAListenerOrAdmission() throws IOException {
        final WalConfiguration wal = WalConfiguration.defaults(temporaryDirectory.resolve("wal-failure"));
        final RecoveryRuntimeConfiguration configuration = new RecoveryRuntimeConfiguration(
                RecoveryMode.SNAPSHOT_THEN_WAL,
                temporaryDirectory.resolve("missing-snapshots"),
                DurableConfiguration.defaults(wal.directory()));
        final RecoverableDurableRuntime runtime = new RecoverableDurableRuntime(
                configuration,
                result -> { });

        assertThrows(RuntimeException.class, runtime::start);
        assertEquals(RecoveryRuntimeState.FAILED, runtime.state());
        assertTrue(runtime.failureCause().isPresent());
        assertThrows(IllegalStateException.class, runtime::coordinator);
        runtime.shutdown();
    }

    @Test
    void snapshotTailRecoverySeedsTheLiveOwnerAfterTheTail() throws Exception {
        final WalConfiguration wal = WalConfiguration.defaults(temporaryDirectory.resolve("tail-wal"));
        write(wal, List.of(command(1, 1, Side.SELL, 100, 2)));
        final SnapshotStore store = new SnapshotStore(temporaryDirectory.resolve("tail-snapshots"));
        new OfflineSnapshotGenerator(wal, store).generate();
        append(wal, List.of(command(2, 2, Side.BUY, 100, 1)));
        final CountDownLatch resultSeen = new CountDownLatch(1);
        final AtomicReference<EngineResult> observed = new AtomicReference<>();
        final RecoverableDurableRuntime runtime = new RecoverableDurableRuntime(
                new RecoveryRuntimeConfiguration(
                        RecoveryMode.SNAPSHOT_THEN_WAL,
                        store.directory(),
                        DurableConfiguration.defaults(wal.directory())),
                result -> {
                    observed.set(result);
                    resultSeen.countDown();
                });

        runtime.start();
        try {
            assertEquals(3, runtime.coordinator().nextCommandSequence().value());
            assertEquals(1, runtime.recoveryResult().snapshotSequence());
            runtime.coordinator().accept(
                    com.ultralatency.matching.network.protocol.ClientRequestId.of(1),
                    sequence -> command(sequence.toSequence(), 3, Side.BUY, 100, 1));
            assertTrue(resultSeen.await(2, TimeUnit.SECONDS));
            assertEquals(Sequence.of(3), observed.get().commandSequence());
            assertEquals(2, observed.get().matches().get(0).trade().tradeId().value());
        } finally {
            runtime.shutdown();
        }
    }

    private RecoverableDurableRuntime runtime(
            final WalConfiguration wal,
            final com.ultralatency.matching.pipeline.EngineResultHandler handler) {
        return new RecoverableDurableRuntime(
                new RecoveryRuntimeConfiguration(
                        RecoveryMode.PURE_WAL,
                        temporaryDirectory.resolve("snapshots"),
                        DurableConfiguration.defaults(wal.directory())),
                handler);
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
        return command(new Sequence(sequence), orderId, side, price, quantity);
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
}
