package com.ultralatency.matching.persistence.snapshot;

import com.ultralatency.matching.engine.MatchingEngineCheckpoint;
import java.util.Arrays;
import java.util.Objects;

/** Immutable derived Snapshot v1 value bound to one WAL prefix. */
public final class Snapshot {

    /** Raw SHA-256 digest length. */
    public static final int SHA256_LENGTH = 32;

    private final MatchingEngineCheckpoint checkpoint;
    private final byte[] walPrefixDigest;

    /**
     * Creates a Snapshot bound to the checkpoint's command prefix.
     *
     * @param checkpoint canonical engine checkpoint
     * @param walPrefixDigest raw SHA-256 digest of WAL record envelope bytes 1..N
     */
    public Snapshot(
            final MatchingEngineCheckpoint checkpoint,
            final byte[] walPrefixDigest) {
        this.checkpoint = Objects.requireNonNull(checkpoint, "checkpoint");
        if (checkpoint.lastAppliedCommandSequence() < 1) {
            throw new SnapshotFormatException("Snapshot checkpoint sequence must be positive");
        }
        Objects.requireNonNull(walPrefixDigest, "walPrefixDigest");
        if (walPrefixDigest.length != SHA256_LENGTH) {
            throw new SnapshotFormatException("WAL prefix digest must be 32 bytes");
        }
        this.walPrefixDigest = walPrefixDigest.clone();
    }

    /** @return canonical engine checkpoint */
    public MatchingEngineCheckpoint checkpoint() {
        return checkpoint;
    }

    /** @return checkpoint Command Sequence */
    public long checkpointSequence() {
        return checkpoint.lastAppliedCommandSequence();
    }

    /** @return a defensive copy of the raw WAL-prefix digest */
    public byte[] walPrefixDigest() {
        return walPrefixDigest.clone();
    }

    /** @return a freshly computed canonical checkpoint digest */
    public byte[] canonicalCheckpointDigest() {
        return checkpoint.canonicalCheckpointDigest();
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Snapshot that)) {
            return false;
        }
        return checkpoint.equals(that.checkpoint)
                && Arrays.equals(walPrefixDigest, that.walPrefixDigest);
    }

    @Override
    public int hashCode() {
        return 31 * checkpoint.hashCode() + Arrays.hashCode(walPrefixDigest);
    }
}
