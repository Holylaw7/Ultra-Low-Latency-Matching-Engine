package com.ultralatency.matching.persistence.wal;

import java.nio.file.Path;

/** Package-private validated physical segment metadata. */
record WalSegmentInfo(
        Path path,
        long segmentId,
        long firstCommandSequence,
        long lastCommandSequence,
        long validEndOffset,
        boolean empty) {
}
