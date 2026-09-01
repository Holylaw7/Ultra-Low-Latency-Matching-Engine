package com.ultralatency.matching.qualification.ga.soak;

import com.ultralatency.matching.qualification.QualificationIdentity;
import java.time.Duration;
import java.util.Objects;

/** Immutable G6 staged-soak contract.  Formal stages are never run by TASK-052. */
public record GaSoakMatrix(
        String version,
        String profile,
        long seed,
        int offeredRatePerSecond,
        Duration duration,
        long acceptedFloor,
        int sampleRateHz,
        Stage stage) {

    /** Frozen soak profile. */
    public static final String APPROVED_PROFILE = "MEMORY_STEADY_STATE_V1";
    /** Frozen soak seed. */
    public static final long APPROVED_SEED = 20_260_823L;
    /** Frozen offered rate; accepted count is a separate predicate. */
    public static final int APPROVED_OFFERED_RATE_PER_SECOND = 200;
    /** Stage A duration. */
    public static final Duration STAGE_A_DURATION = Duration.ofHours(2);
    /** Stage B duration. */
    public static final Duration STAGE_B_DURATION = Duration.ofHours(6);
    /** Stage A accepted floor. */
    public static final long STAGE_A_ACCEPTED_FLOOR = 1_440_000L;
    /** Stage B accepted floor. */
    public static final long STAGE_B_ACCEPTED_FLOOR = 4_320_000L;
    /** Quick readiness duration. */
    public static final Duration QUICK_DURATION = Duration.ofSeconds(60);
    /** Quick readiness accepted floor. */
    public static final long QUICK_ACCEPTED_FLOOR = 10_000L;
    /** Frozen resource sampling frequency. */
    public static final int SAMPLE_RATE_HZ = 1;
    /** Frozen matrix version for the shared Quick lane. */
    public static final String QUICK_VERSION = "ga-g6-g8-soak-quick-v1";
    /** Frozen matrix version for Stage A. */
    public static final String STAGE_A_VERSION = "ga-g6-g8-soak-stage-a-v1";
    /** Frozen matrix version for Stage B. */
    public static final String STAGE_B_VERSION = "ga-g6-g8-soak-stage-b-v1";

    /** Execution lanes represented by this contract. */
    public enum Stage {
        /** Non-formal TASK-052 readiness lane. */
        QUICK,
        /** Future, separately Human-authorized two-hour run. */
        STAGE_A,
        /** Future, separately Human-authorized six-hour run. */
        STAGE_B
    }

    /** Creates and validates one immutable soak contract. */
    public GaSoakMatrix {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(stage, "stage");
        if (version.isBlank() || profile.isBlank() || seed < 0
                || offeredRatePerSecond <= 0 || offeredRatePerSecond > 100_000
                || duration.isZero() || duration.isNegative()
                || acceptedFloor < 0 || sampleRateHz <= 0 || sampleRateHz > 1000) {
            throw new IllegalArgumentException("soak matrix is outside its bounds");
        }
    }

    /** Returns the frozen Quick readiness matrix. */
    public static GaSoakMatrix quick() {
        return new GaSoakMatrix(
                QUICK_VERSION,
                APPROVED_PROFILE,
                APPROVED_SEED,
                APPROVED_OFFERED_RATE_PER_SECOND,
                QUICK_DURATION,
                QUICK_ACCEPTED_FLOOR,
                SAMPLE_RATE_HZ,
                Stage.QUICK);
    }

    /** Returns the future Stage A contract without authorizing its execution. */
    public static GaSoakMatrix stageA() {
        return new GaSoakMatrix(
                STAGE_A_VERSION,
                APPROVED_PROFILE,
                APPROVED_SEED,
                APPROVED_OFFERED_RATE_PER_SECOND,
                STAGE_A_DURATION,
                STAGE_A_ACCEPTED_FLOOR,
                SAMPLE_RATE_HZ,
                Stage.STAGE_A);
    }

    /** Returns the future independent Stage B contract without authorizing execution. */
    public static GaSoakMatrix stageB() {
        return new GaSoakMatrix(
                STAGE_B_VERSION,
                APPROVED_PROFILE,
                APPROVED_SEED,
                APPROVED_OFFERED_RATE_PER_SECOND,
                STAGE_B_DURATION,
                STAGE_B_ACCEPTED_FLOOR,
                SAMPLE_RATE_HZ,
                Stage.STAGE_B);
    }

    /** Returns whether this is the non-formal TASK-052 lane. */
    public boolean isQuick() {
        return stage == Stage.QUICK;
    }

    /** Returns whether this is the future Stage A contract. */
    public boolean isStageA() {
        return stage == Stage.STAGE_A;
    }

    /** Returns whether this is the future Stage B contract. */
    public boolean isStageB() {
        return stage == Stage.STAGE_B;
    }

    /** Returns whether all frozen formal matrix fields match this contract. */
    public boolean isApprovedFormal() {
        return ((isStageA() && duration.equals(STAGE_A_DURATION)
                && acceptedFloor == STAGE_A_ACCEPTED_FLOOR)
                || (isStageB() && duration.equals(STAGE_B_DURATION)
                && acceptedFloor == STAGE_B_ACCEPTED_FLOOR))
                && ((isStageA() && STAGE_A_VERSION.equals(version))
                || (isStageB() && STAGE_B_VERSION.equals(version)))
                && APPROVED_PROFILE.equals(profile)
                && seed == APPROVED_SEED
                && offeredRatePerSecond == APPROVED_OFFERED_RATE_PER_SECOND
                && sampleRateHz == SAMPLE_RATE_HZ;
    }

    /** Returns whether all frozen Quick fields, including its version, match exactly. */
    public boolean isApprovedQuick() {
        return QUICK_VERSION.equals(version) && isQuick()
                && APPROVED_PROFILE.equals(profile) && seed == APPROVED_SEED
                && offeredRatePerSecond == APPROVED_OFFERED_RATE_PER_SECOND
                && QUICK_DURATION.equals(duration) && acceptedFloor == QUICK_ACCEPTED_FLOOR
                && sampleRateHz == SAMPLE_RATE_HZ;
    }

    /** Returns canonical configuration fields used for provenance binding. */
    public java.util.Map<String, String> configurationFields() {
        return java.util.Map.of(
                "acceptedFloor", Long.toString(acceptedFloor),
                "duration", duration.toString(),
                "offeredRatePerSecond", Integer.toString(offeredRatePerSecond),
                "profile", profile,
                "sampleRateHz", Integer.toString(sampleRateHz),
                "seed", Long.toString(seed),
                "stage", stage.name(),
                "version", version);
    }

    /** Returns the SHA-256 identity of the immutable matrix configuration. */
    public String configurationIdentitySha256() {
        return QualificationIdentity.digest(configurationFields());
    }
}
