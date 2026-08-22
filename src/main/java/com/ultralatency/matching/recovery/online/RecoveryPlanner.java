package com.ultralatency.matching.recovery.online;

import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.engine.EngineResult;
import com.ultralatency.matching.engine.MatchingEngine;
import com.ultralatency.matching.persistence.snapshot.RecoveryLease;
import com.ultralatency.matching.persistence.snapshot.Snapshot;
import com.ultralatency.matching.persistence.snapshot.SnapshotStore;
import com.ultralatency.matching.persistence.wal.CommandWalReader;
import com.ultralatency.matching.persistence.wal.WalCommandCodec;
import com.ultralatency.matching.persistence.wal.WalConfiguration;
import com.ultralatency.matching.recovery.ReplayTranscript;
import com.ultralatency.matching.recovery.ReplayTranscriptDigest;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Strict offline planner and executor for the two explicit Phase 8 recovery
 * modes. It never starts a pipeline, network listener or live runtime.
 */
public final class RecoveryPlanner {

    private final WalConfiguration walConfiguration;
    private final SnapshotStore snapshotStore;
    private final WalCommandCodec codec;

    /** Creates a planner with the frozen WAL codec. */
    public RecoveryPlanner(
            final WalConfiguration walConfiguration,
            final SnapshotStore snapshotStore) {
        this(walConfiguration, snapshotStore, new WalCommandCodec());
    }

    /** Creates a planner with an explicit compatible WAL codec. */
    public RecoveryPlanner(
            final WalConfiguration walConfiguration,
            final SnapshotStore snapshotStore,
            final WalCommandCodec codec) {
        this.walConfiguration = Objects.requireNonNull(walConfiguration, "walConfiguration");
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    /**
     * Creates a planner using a Snapshot directory below the WAL directory.
     *
     * @param walConfiguration authoritative WAL configuration
     * @param snapshotDirectory published Snapshot directory
     * @return configured planner
     */
    public static RecoveryPlanner create(
            final WalConfiguration walConfiguration,
            final Path snapshotDirectory) {
        return new RecoveryPlanner(walConfiguration, new SnapshotStore(snapshotDirectory));
    }

    /**
     * Executes one explicitly selected offline recovery mode.
     *
     * @param mode explicit recovery policy
     * @return recovered engine and internal replay evidence
     * @throws IOException when strict WAL/Snapshot validation or replay fails
     */
    public RecoveryResult recover(final RecoveryMode mode) throws IOException {
        Objects.requireNonNull(mode, "mode");
        try (RecoveryLease ignored = RecoveryLease.acquire(walConfiguration.directory())) {
            final List<EngineCommand> commands = readStrictWal();
            final long walEnd = commands.size();
            final String walDigest = digestCommands(commands, 0, commands.size());
            if (mode == RecoveryMode.PURE_WAL) {
                return recoverPureWal(commands, walEnd, walDigest);
            }
            return recoverFromSnapshot(commands, walEnd, walDigest);
        }
    }

    private List<EngineCommand> readStrictWal() throws IOException {
        final List<EngineCommand> commands = CommandWalReader.read(walConfiguration);
        for (int index = 0; index < commands.size(); index++) {
            final long expected = index + 1L;
            if (commands.get(index).sequence().value() != expected) {
                throw new RecoveryException(
                        commands.get(index).sequence(),
                        "Strict WAL sequence does not start at or remain contiguous from one",
                        null);
            }
        }
        return commands;
    }

    private RecoveryResult recoverPureWal(
            final List<EngineCommand> commands,
            final long walEnd,
            final String walDigest) throws IOException {
        final MatchingEngine engine = new MatchingEngine();
        final List<EngineResult> results = apply(engine, commands, 0);
        return result(RecoveryMode.PURE_WAL, engine, walEnd, 0, results, walDigest);
    }

    private RecoveryResult recoverFromSnapshot(
            final List<EngineCommand> commands,
            final long walEnd,
            final String walDigest) throws IOException {
        final Snapshot snapshot = selectedSnapshot();
        final long snapshotSequence = snapshot.checkpointSequence();
        if (snapshotSequence > walEnd) {
            throw new RecoveryException(
                    "Selected Snapshot is newer than the strict WAL end");
        }
        final String expectedPrefix = digestCommands(
                commands,
                0,
                Math.toIntExact(snapshotSequence));
        if (!MessageDigest.isEqual(
                HexFormat.of().parseHex(expectedPrefix), snapshot.walPrefixDigest())) {
            throw new RecoveryException(
                    "Selected Snapshot WAL-prefix digest does not match the retained WAL");
        }
        final MatchingEngine engine;
        try {
            engine = MatchingEngine.fromCheckpoint(snapshot.checkpoint());
        } catch (final RuntimeException exception) {
            throw new RecoveryException(
                    "Selected Snapshot checkpoint cannot be restored", exception);
        }
        final List<EngineCommand> tail = commands.subList(Math.toIntExact(snapshotSequence), commands.size());
        final List<EngineResult> results = apply(engine, tail, 0);
        return result(
                RecoveryMode.SNAPSHOT_THEN_WAL,
                engine,
                walEnd,
                snapshotSequence,
                results,
                walDigest);
    }

    private Snapshot selectedSnapshot() throws IOException {
        try {
            final Optional<Snapshot> snapshot = snapshotStore.readLatest();
            if (snapshot.isEmpty()) {
                throw new RecoveryException(
                        "SNAPSHOT_THEN_WAL requires a published Snapshot");
            }
            return snapshot.orElseThrow();
        } catch (final RecoveryException exception) {
            throw exception;
        } catch (final RuntimeException exception) {
            throw new RecoveryException("Selected Snapshot failed strict validation", exception);
        }
    }

    private RecoveryResult result(
            final RecoveryMode mode,
            final MatchingEngine engine,
            final long walEnd,
            final long snapshotSequence,
            final List<EngineResult> results,
            final String walDigest) throws RecoveryException {
        final ReplayTranscript transcript = new ReplayTranscript(
                List.copyOf(results),
                ReplayTranscriptDigest.sha256Hex(results));
        final long nextSequence;
        try {
            nextSequence = Math.addExact(walEnd, 1);
        } catch (final ArithmeticException exception) {
            throw new RecoveryException("WAL end sequence cannot advance", exception);
        }
        try {
            return new RecoveryResult(
                    mode,
                    engine,
                    engine.checkpoint(),
                    walEnd,
                    nextSequence,
                    snapshotSequence,
                    transcript,
                    walDigest);
        } catch (final RuntimeException exception) {
            throw new RecoveryException("Recovered state failed convergence validation", exception);
        }
    }

    private List<EngineResult> apply(
            final MatchingEngine engine,
            final List<EngineCommand> commands,
            final int startIndex) throws RecoveryException {
        final List<EngineResult> results = new ArrayList<>(commands.size() - startIndex);
        for (int index = startIndex; index < commands.size(); index++) {
            final EngineCommand command = commands.get(index);
            try {
                results.add(engine.process(command));
            } catch (final RuntimeException exception) {
                throw new RecoveryException(
                        command.sequence(),
                        "Recovery replay rejected command at sequence "
                                + command.sequence().value(),
                        exception);
            }
        }
        return List.copyOf(results);
    }

    private String digestCommands(
            final List<EngineCommand> commands,
            final int fromInclusive,
            final int toExclusive) throws RecoveryException {
        final MessageDigest digest = sha256();
        for (int index = fromInclusive; index < toExclusive; index++) {
            try {
                digest.update(codec.encodeRecord(commands.get(index)));
            } catch (final RuntimeException exception) {
                throw new RecoveryException(
                        commands.get(index).sequence(),
                        "Unable to encode strict WAL command for digest",
                        exception);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK must provide SHA-256", exception);
        }
    }
}
