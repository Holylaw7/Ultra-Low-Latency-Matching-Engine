package com.ultralatency.matching.persistence.wal;

import com.ultralatency.matching.engine.EngineCommand;
import java.nio.file.Path;
import java.util.List;

/** Package-private result of a strict physical WAL scan. */
record WalScanResult(
        List<EngineCommand> commands,
        List<WalSegmentInfo> segments,
        Path tailPath,
        long tailOffset,
        boolean emptyTrailingSegment) {

    boolean hasTail() {
        return tailPath != null;
    }
}
