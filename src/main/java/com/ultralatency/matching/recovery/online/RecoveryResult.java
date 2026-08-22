package com.ultralatency.matching.recovery.online;

import com.ultralatency.matching.engine.MatchingEngine;
import com.ultralatency.matching.engine.MatchingEngineCheckpoint;
import com.ultralatency.matching.recovery.ReplayTranscript;
import java.util.Objects;

/**
 * Immutable metadata boundary for one completed offline recovery run.
 *
 * <p>The engine reference is intentionally the recovered state owner for the
 * later live-handoff task. This result itself performs no listener, pipeline or
 * client work.</p>
 */
public final class RecoveryResult {

    private final RecoveryMode mode;
    private final MatchingEngine engine;
    private final MatchingEngineCheckpoint checkpoint;
    private final long walEndSequence;
    private final long nextCommandSequence;
    private final long snapshotSequence;
    private final ReplayTranscript replayTranscript;
    private final String walDigestHex;

    /** Creates one validated completed recovery result. */
    public RecoveryResult(
            final RecoveryMode mode,
            final MatchingEngine engine,
            final MatchingEngineCheckpoint checkpoint,
            final long walEndSequence,
            final long nextCommandSequence,
            final long snapshotSequence,
            final ReplayTranscript replayTranscript,
            final String walDigestHex) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.checkpoint = Objects.requireNonNull(checkpoint, "checkpoint");
        this.replayTranscript = Objects.requireNonNull(replayTranscript, "replayTranscript");
        this.walDigestHex = requireDigest(walDigestHex, "walDigestHex");
        if (walEndSequence < 0) {
            throw new IllegalArgumentException("WAL end sequence must not be negative");
        }
        final long expectedNextSequence;
        try {
            expectedNextSequence = Math.addExact(walEndSequence, 1);
        } catch (final ArithmeticException exception) {
            throw new IllegalArgumentException("WAL end sequence cannot advance", exception);
        }
        if (nextCommandSequence != expectedNextSequence) {
            throw new IllegalArgumentException("Next command sequence does not converge");
        }
        if (checkpoint.lastAppliedCommandSequence() != walEndSequence) {
            throw new IllegalArgumentException("Checkpoint sequence does not match WAL end");
        }
        if (!engine.checkpoint().equals(checkpoint)) {
            throw new IllegalArgumentException("Engine does not match recovered checkpoint");
        }
        if (snapshotSequence < 0 || snapshotSequence > walEndSequence) {
            throw new IllegalArgumentException("Snapshot sequence is outside WAL range");
        }
        if (mode == RecoveryMode.PURE_WAL && snapshotSequence != 0) {
            throw new IllegalArgumentException("PURE_WAL cannot carry a Snapshot sequence");
        }
        if (mode == RecoveryMode.SNAPSHOT_THEN_WAL && snapshotSequence < 1) {
            throw new IllegalArgumentException("Snapshot recovery requires a Snapshot sequence");
        }
        this.walEndSequence = walEndSequence;
        this.nextCommandSequence = nextCommandSequence;
        this.snapshotSequence = snapshotSequence;
    }

    /** @return explicit recovery mode */
    public RecoveryMode mode() {
        return mode;
    }

    /** @return recovered engine for the later live-handoff task */
    public MatchingEngine engine() {
        return engine;
    }

    /** @return final canonical engine checkpoint */
    public MatchingEngineCheckpoint checkpoint() {
        return checkpoint;
    }

    /** @return last strict WAL command sequence, or zero for an empty WAL */
    public long walEndSequence() {
        return walEndSequence;
    }

    /** @return next command sequence accepted by a later live owner */
    public long nextCommandSequence() {
        return nextCommandSequence;
    }

    /** @return selected Snapshot sequence, or zero for PURE_WAL */
    public long snapshotSequence() {
        return snapshotSequence;
    }

    /** @return ordered replay results; Snapshot mode contains only the WAL tail */
    public ReplayTranscript replayTranscript() {
        return replayTranscript;
    }

    /** @return lowercase SHA-256 digest of the complete strict WAL prefix */
    public String walDigestHex() {
        return walDigestHex;
    }

    /** @return lowercase SHA-256 digest of the final canonical checkpoint */
    public String checkpointDigestHex() {
        return java.util.HexFormat.of().formatHex(checkpoint.canonicalCheckpointDigest());
    }

    private static String requireDigest(final String digest, final String name) {
        Objects.requireNonNull(digest, name);
        if (digest.length() != 64 || !digest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must be a lowercase SHA-256 digest");
        }
        return digest;
    }
}
