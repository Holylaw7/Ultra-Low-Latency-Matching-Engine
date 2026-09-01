package com.ultralatency.matching.qualification.ga.capacity;

import java.util.List;
import java.util.Objects;

/** Immutable G5 capacity scale matrix and its bounded Quick readiness variant. */
public record GaCapacityMatrix(
        String version,
        String profile,
        long seed,
        int walSegmentSizeBytes,
        List<Integer> commandScales,
        int quickCommandCount) {

    /** Frozen G5 matrix identity from ADR-0019 D12. */
    public static final String APPROVED_VERSION = "ga-g5-capacity-v1";
    /** Workload profile used by every formal capacity point. */
    public static final String APPROVED_PROFILE = "LIFECYCLE_MIX";
    /** Frozen G5 workload seed. */
    public static final long APPROVED_SEED = 20_260_823L;
    /** Frozen WAL segment size. */
    public static final int APPROVED_WAL_SEGMENT_SIZE_BYTES = 65_536;
    /** Formal support-envelope command scales. */
    public static final List<Integer> APPROVED_COMMAND_SCALES = List.of(
            100_000, 250_000, 500_000, 1_000_000);
    /** Minimum active orders recovered at the largest formal scale. */
    public static final int MIN_RECOVERED_ACTIVE_ORDERS = 166_000;
    /** Short public-path workload used only by the Quick readiness lane. */
    public static final int QUICK_COMMAND_COUNT = 10_000;

    /** Creates and validates a capacity matrix. */
    public GaCapacityMatrix {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(profile, "profile");
        commandScales = List.copyOf(Objects.requireNonNull(commandScales, "commandScales"));
        if (version.isBlank() || profile.isBlank() || seed < 0 || walSegmentSizeBytes <= 0
                || commandScales.isEmpty() || commandScales.stream().anyMatch(scale ->
                scale == null || scale <= 0 || scale > 1_000_000)
                || commandScales.stream().distinct().count() != commandScales.size()
                || quickCommandCount <= 0 || quickCommandCount > 10_000) {
            throw new IllegalArgumentException("capacity matrix is outside its bounds");
        }
    }

    /** Returns the frozen future formal G5 matrix without executing it. */
    public static GaCapacityMatrix approved() {
        return new GaCapacityMatrix(
                APPROVED_VERSION,
                APPROVED_PROFILE,
                APPROVED_SEED,
                APPROVED_WAL_SEGMENT_SIZE_BYTES,
                APPROVED_COMMAND_SCALES,
                QUICK_COMMAND_COUNT);
    }

    /** Returns the bounded Quick readiness matrix; it is never formal evidence. */
    public static GaCapacityMatrix quick() {
        return new GaCapacityMatrix(
                "ga-g5-capacity-quick-v1",
                APPROVED_PROFILE,
                APPROVED_SEED,
                APPROVED_WAL_SEGMENT_SIZE_BYTES,
                List.of(QUICK_COMMAND_COUNT),
                QUICK_COMMAND_COUNT);
    }

    /** Returns a tiny deterministic matrix for unit tests only. */
    public static GaCapacityMatrix test() {
        return new GaCapacityMatrix(
                "ga-g5-capacity-test-v1",
                APPROVED_PROFILE,
                APPROVED_SEED,
                APPROVED_WAL_SEGMENT_SIZE_BYTES,
                List.of(8, 16, 32),
                8);
    }

    /** Returns whether this matrix is the frozen formal matrix. */
    public boolean isApproved() {
        return APPROVED_VERSION.equals(version)
                && APPROVED_PROFILE.equals(profile)
                && seed == APPROVED_SEED
                && walSegmentSizeBytes == APPROVED_WAL_SEGMENT_SIZE_BYTES
                && APPROVED_COMMAND_SCALES.equals(commandScales);
    }

    /** Returns the required recovered active-order minimum for one scale. */
    public int minimumRecoveredActiveOrders(final int commandCount) {
        if (!commandScales.contains(commandCount)) {
            throw new IllegalArgumentException("commandCount is not in the capacity matrix");
        }
        return commandCount == 1_000_000 ? MIN_RECOVERED_ACTIVE_ORDERS : 0;
    }
}
