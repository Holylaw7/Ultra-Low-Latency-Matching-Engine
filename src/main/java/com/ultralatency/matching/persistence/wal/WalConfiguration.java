package com.ultralatency.matching.persistence.wal;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Immutable configuration for a command WAL.
 *
 * @param directory WAL directory
 * @param segmentSizeBytes maximum physical segment size
 * @param durabilityMode append durability action
 */
public record WalConfiguration(
        Path directory,
        int segmentSizeBytes,
        WalDurabilityMode durabilityMode) {

    /** Default segment size for the correctness baseline. */
    public static final int DEFAULT_SEGMENT_SIZE_BYTES = 64 * 1024;

    /**
     * Validates configuration without touching the filesystem.
     */
    public WalConfiguration {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(durabilityMode, "durabilityMode");
        if (segmentSizeBytes < WalCommandCodec.MIN_SEGMENT_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "Segment size must be at least " + WalCommandCodec.MIN_SEGMENT_SIZE_BYTES);
        }
    }

    /**
     * Returns the correctness-first default configuration for a directory.
     *
     * @param directory WAL directory
     * @return synchronous default configuration
     */
    public static WalConfiguration defaults(final Path directory) {
        return new WalConfiguration(
                directory,
                DEFAULT_SEGMENT_SIZE_BYTES,
                WalDurabilityMode.SYNC_EACH_APPEND);
    }
}
