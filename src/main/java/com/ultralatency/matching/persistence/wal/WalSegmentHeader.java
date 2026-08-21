package com.ultralatency.matching.persistence.wal;

import com.ultralatency.matching.domain.Sequence;
import java.util.Objects;

/**
 * Decoded immutable WAL segment header.
 *
 * @param version format version
 * @param segmentId positive physical segment identifier
 * @param firstCommandSequence first logical command sequence in the segment
 */
public record WalSegmentHeader(
        int version,
        long segmentId,
        Sequence firstCommandSequence) {

    /**
     * Validates header values.
     */
    public WalSegmentHeader {
        if (version <= 0) {
            throw new IllegalArgumentException("WAL format version must be positive");
        }
        if (segmentId <= 0) {
            throw new IllegalArgumentException("WAL segment id must be positive");
        }
        Objects.requireNonNull(firstCommandSequence, "firstCommandSequence");
    }
}
