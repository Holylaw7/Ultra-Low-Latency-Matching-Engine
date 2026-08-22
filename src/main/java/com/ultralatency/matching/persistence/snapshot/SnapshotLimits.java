package com.ultralatency.matching.persistence.snapshot;

/** Allocation and file-size limits applied before decoding Snapshot v1. */
public record SnapshotLimits(int maxActiveOrders, int maxSnapshotBytes) {

    /** Snapshot v1 header plus one maximum-sized order record. */
    public static final int MIN_SNAPSHOT_BYTES = 132 + 48;

    /** Conservative correctness-baseline limits. */
    public static final SnapshotLimits DEFAULTS = new SnapshotLimits(100_000, 8 * 1024 * 1024);

    /** Validates limits without touching the filesystem. */
    public SnapshotLimits {
        if (maxActiveOrders < 0) {
            throw new IllegalArgumentException("Maximum active orders must not be negative");
        }
        if (maxSnapshotBytes < MIN_SNAPSHOT_BYTES) {
            throw new IllegalArgumentException(
                    "Maximum Snapshot size must be at least " + MIN_SNAPSHOT_BYTES);
        }
        final long required = 132L + 48L * maxActiveOrders;
        if (required > maxSnapshotBytes) {
            throw new IllegalArgumentException(
                    "Maximum Snapshot size cannot encode the configured order limit");
        }
    }

    /** Returns the default bounded decoder limits. */
    public static SnapshotLimits defaults() {
        return DEFAULTS;
    }
}
