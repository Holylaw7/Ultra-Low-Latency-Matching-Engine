package com.ultralatency.matching.qualification.ga.durability;

import java.util.List;
import java.util.Objects;

/** Immutable, versioned G3 durability and crash-recovery matrix. */
public record GaDurabilityMatrix(
        String version,
        List<Integer> walSegmentSizes,
        int gracefulCycles,
        int forcedCycles,
        int commandsPerCycle,
        long seed,
        List<GaDurabilityFixture> corruptionFixtures) {

    /** Frozen matrix identity. */
    public static final String APPROVED_VERSION = "ga-g3-g7-v1";
    /** Frozen segment sizes from ADR-0019 D10. */
    public static final List<Integer> APPROVED_SEGMENT_SIZES = List.of(
            com.ultralatency.matching.persistence.wal.WalCommandCodec.MIN_SEGMENT_SIZE_BYTES,
            65_536,
            1_048_576);
    /** Frozen graceful termination count. */
    public static final int APPROVED_GRACEFUL_CYCLES = 50;
    /** Frozen completed-response forced termination count. */
    public static final int APPROVED_FORCED_CYCLES = 50;
    /** Frozen workload count per lifecycle cycle. */
    public static final int APPROVED_COMMANDS_PER_CYCLE = 10_000;
    /** Frozen lifecycle workload seed. */
    public static final long APPROVED_SEED = 20_260_823L;

    /** Creates and validates one matrix. */
    public GaDurabilityMatrix {
        Objects.requireNonNull(version, "version");
        if (version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
        walSegmentSizes = List.copyOf(Objects.requireNonNull(walSegmentSizes,
                "walSegmentSizes"));
        if (walSegmentSizes.isEmpty()
                || walSegmentSizes.stream().anyMatch(size -> size == null
                        || size < com.ultralatency.matching.persistence.wal
                                .WalCommandCodec.MIN_SEGMENT_SIZE_BYTES
                        || size > com.ultralatency.matching.app.RuntimeConfiguration
                                .MAX_WAL_SEGMENT_SIZE_BYTES)
                || walSegmentSizes.stream().distinct().count() != walSegmentSizes.size()) {
            throw new IllegalArgumentException(
                    "WAL segment sizes must be unique and within application bounds");
        }
        if (gracefulCycles < 0 || forcedCycles < 0
                || gracefulCycles + forcedCycles <= 0
                || gracefulCycles + forcedCycles > 100) {
            throw new IllegalArgumentException("lifecycle cycles must be between 1 and 100");
        }
        if (commandsPerCycle <= 0 || commandsPerCycle > 10_000) {
            throw new IllegalArgumentException("commandsPerCycle is outside the matrix bound");
        }
        if (seed < 0) {
            throw new IllegalArgumentException("seed must not be negative");
        }
        corruptionFixtures = List.copyOf(Objects.requireNonNull(corruptionFixtures,
                "corruptionFixtures"));
        if (corruptionFixtures.isEmpty()
                || corruptionFixtures.stream().anyMatch(Objects::isNull)
                || corruptionFixtures.stream().distinct().count() != corruptionFixtures.size()) {
            throw new IllegalArgumentException("corruption fixtures must be unique and non-empty");
        }
    }

    /** Returns the Human-approved G3/G7 matrix. */
    public static GaDurabilityMatrix approved() {
        return new GaDurabilityMatrix(
                APPROVED_VERSION,
                APPROVED_SEGMENT_SIZES,
                APPROVED_GRACEFUL_CYCLES,
                APPROVED_FORCED_CYCLES,
                APPROVED_COMMANDS_PER_CYCLE,
                APPROVED_SEED,
                List.of(GaDurabilityFixture.values()));
    }

    /** Returns a small deterministic matrix for focused tests only. */
    public static GaDurabilityMatrix test() {
        return new GaDurabilityMatrix(
                "ga-g3-g7-test-v1",
                List.of(8_192),
                1,
                1,
                24,
                APPROVED_SEED,
                List.of(
                        GaDurabilityFixture.SEGMENT_MAGIC,
                        GaDurabilityFixture.RECORD_BODY_CHECKSUM,
                        GaDurabilityFixture.FINAL_TORN_TAIL,
                        GaDurabilityFixture.ROTATION_PATH_COLLISION));
    }

    /** Returns the total lifecycle cycle count for every segment size. */
    public int lifecycleCycles() {
        return gracefulCycles + forcedCycles;
    }

    /** Returns the total number of lifecycle physical executions in the matrix. */
    public int lifecycleExecutionCount() {
        return lifecycleCycles();
    }

    /** Returns the deterministic segment size assigned to a one-based lifecycle cycle. */
    public int segmentSizeForLifecycleCycle(final int cycle) {
        if (cycle < 1 || cycle > lifecycleCycles()) {
            throw new IllegalArgumentException("cycle is outside the lifecycle matrix");
        }
        return walSegmentSizes.get((cycle - 1) % walSegmentSizes.size());
    }

    /** Returns the total number of required corruption fixture executions. */
    public int corruptionExecutionCount() {
        return walSegmentSizes.size() * corruptionFixtures.size();
    }

    /** Returns whether this is the exact approved matrix rather than a focused test matrix. */
    public boolean isApproved() {
        return APPROVED_VERSION.equals(version)
                && APPROVED_SEGMENT_SIZES.equals(walSegmentSizes)
                && gracefulCycles == APPROVED_GRACEFUL_CYCLES
                && forcedCycles == APPROVED_FORCED_CYCLES
                && commandsPerCycle == APPROVED_COMMANDS_PER_CYCLE
                && seed == APPROVED_SEED
                && corruptionFixtures.equals(List.of(GaDurabilityFixture.values()));
    }
}
