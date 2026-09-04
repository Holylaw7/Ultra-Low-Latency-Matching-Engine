package com.ultralatency.matching.qualification.ga.performance;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** Immutable G4 performance-SLO matrix and its non-formal quick variant. */
public record GaPerformanceMatrix(
        String version,
        String profile,
        long seed,
        int runCount,
        Duration runDuration,
        int lifecycleSamples,
        int quickCommandCount) {

    /** Frozen RC2 G4 matrix identity. */
    public static final String APPROVED_VERSION = "ga-g4-performance-v2";
    /** Profile used by every formal G4 run. */
    public static final String APPROVED_PROFILE = "MEMORY_STEADY_STATE_V1";
    /** Frozen G4 workload seed. */
    public static final long APPROVED_SEED = 20_260_823L;
    /** Number of independent formal SLO runs. */
    public static final int APPROVED_RUN_COUNT = 3;
    /** Duration of each formal SLO run. */
    public static final Duration APPROVED_RUN_DURATION = Duration.ofMinutes(10);
    /** Warmup duration excluded from every formal performance sample population. */
    public static final Duration APPROVED_WARMUP_DURATION = Duration.ofSeconds(60);
    /** Public protocol used by the frozen RC2 performance contract. */
    public static final String APPROVED_PROTOCOL = "v2";
    /** Bounded public protocol window used by the frozen RC2 performance contract. */
    public static final int APPROVED_PROTOCOL_V2_WINDOW = 8;
    /** Durability mode used by the frozen RC2 performance contract. */
    public static final String APPROVED_WAL_MODE = "SYNC_EACH_APPEND";
    /** Formal load model: refill one request whenever a validated response releases capacity. */
    public static final String APPROVED_LOAD_MODEL =
            "BOUNDED_CLOSED_LOOP_CONTINUOUS_REFILL";
    /** Total lifecycle samples required by the formal matrix. */
    public static final int APPROVED_LIFECYCLE_SAMPLES = 60;
    /** Short public-path workload used only by the Quick readiness lane. */
    public static final int QUICK_COMMAND_COUNT = 256;

    /** Creates and validates a performance matrix. */
    public GaPerformanceMatrix {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(runDuration, "runDuration");
        if (version.isBlank() || profile.isBlank() || seed < 0 || runCount <= 0
                || runCount > 100 || runDuration.isNegative() || runDuration.isZero()
                || lifecycleSamples < 0 || lifecycleSamples > 1000
                || quickCommandCount <= 0 || quickCommandCount > 10_000) {
            throw new IllegalArgumentException("performance matrix is outside its bounds");
        }
    }

    /** Returns the frozen future formal G4 matrix without executing it. */
    public static GaPerformanceMatrix approved() {
        return new GaPerformanceMatrix(
                APPROVED_VERSION,
                APPROVED_PROFILE,
                APPROVED_SEED,
                APPROVED_RUN_COUNT,
                APPROVED_RUN_DURATION,
                APPROVED_LIFECYCLE_SAMPLES,
                QUICK_COMMAND_COUNT);
    }

    /** Returns the bounded Quick readiness matrix; it is never formal evidence. */
    public static GaPerformanceMatrix quick() {
        return new GaPerformanceMatrix(
                "ga-g4-performance-quick-v1",
                APPROVED_PROFILE,
                APPROVED_SEED,
                1,
                Duration.ofMillis(1),
                1,
                QUICK_COMMAND_COUNT);
    }

    /** Returns a tiny deterministic matrix for unit tests only. */
    public static GaPerformanceMatrix test() {
        return new GaPerformanceMatrix(
                "ga-g4-performance-test-v1",
                APPROVED_PROFILE,
                APPROVED_SEED,
                1,
                Duration.ofMillis(1),
                2,
                8);
    }

    /** Returns whether this matrix is the frozen formal matrix. */
    public boolean isApproved() {
        return APPROVED_VERSION.equals(version)
                && APPROVED_PROFILE.equals(profile)
                && seed == APPROVED_SEED
                && runCount == APPROVED_RUN_COUNT
                && APPROVED_RUN_DURATION.equals(runDuration)
                && lifecycleSamples == APPROVED_LIFECYCLE_SAMPLES;
    }

    /** Returns the warmup interval excluded from formal measurements. */
    public Duration warmupDuration() {
        return APPROVED_WARMUP_DURATION;
    }

    /** Returns the immutable list of formal SLO thresholds in nanoseconds. */
    public List<Long> latencyThresholdsNanos() {
        return List.of(
                GaPerformanceEvaluator.MAX_P50_NANOS,
                GaPerformanceEvaluator.MAX_P99_NANOS,
                GaPerformanceEvaluator.MAX_P999_NANOS);
    }
}
