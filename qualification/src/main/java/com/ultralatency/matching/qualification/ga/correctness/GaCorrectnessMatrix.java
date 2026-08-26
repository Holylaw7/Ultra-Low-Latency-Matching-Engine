package com.ultralatency.matching.qualification.ga.correctness;

import com.ultralatency.matching.qualification.QualificationProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable, versioned G1/G2 correctness and deterministic-recovery matrix. */
public record GaCorrectnessMatrix(
        String version,
        int commandCount,
        int walSegmentSizeBytes,
        List<QualificationProfile> profiles,
        List<Long> seeds,
        int repetitions,
        List<Integer> snapshotPrefixes) {

    /** Frozen matrix identity used by the GA Blueprint. */
    public static final String APPROVED_VERSION = "ga-g1-g2-v1";

    /** Frozen command count for every profile/seed/repetition. */
    public static final int APPROVED_COMMAND_COUNT = 100_000;

    /** Frozen WAL segment size for G1/G2. */
    public static final int APPROVED_WAL_SEGMENT_SIZE_BYTES = 65_536;

    /** Frozen deterministic seeds. */
    public static final List<Long> APPROVED_SEEDS = List.of(20260823L, 20260824L, 20260825L);

    /** Frozen recovery prefixes. */
    public static final List<Integer> APPROVED_SNAPSHOT_PREFIXES =
            List.of(25_000, 50_000, 75_000);

    /** Creates and validates a matrix. */
    public GaCorrectnessMatrix {
        Objects.requireNonNull(version, "version");
        if (version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
        if (commandCount <= 0) {
            throw new IllegalArgumentException("commandCount must be positive");
        }
        if (walSegmentSizeBytes < 1) {
            throw new IllegalArgumentException("walSegmentSizeBytes must be positive");
        }
        profiles = List.copyOf(Objects.requireNonNull(profiles, "profiles"));
        seeds = List.copyOf(Objects.requireNonNull(seeds, "seeds"));
        snapshotPrefixes = List.copyOf(Objects.requireNonNull(snapshotPrefixes,
                "snapshotPrefixes"));
        if (profiles.isEmpty() || seeds.isEmpty() || repetitions <= 0) {
            throw new IllegalArgumentException("matrix dimensions must be non-empty");
        }
        if (profiles.stream().anyMatch(Objects::isNull)
                || seeds.stream().anyMatch(seed -> seed == null || seed < 0)) {
            throw new IllegalArgumentException("matrix identities must be valid");
        }
        if (snapshotPrefixes.stream().anyMatch(prefix -> prefix == null
                || prefix <= 0 || prefix >= commandCount)) {
            throw new IllegalArgumentException("snapshot prefixes must be within command count");
        }
        if (snapshotPrefixes.stream().distinct().count() != snapshotPrefixes.size()) {
            throw new IllegalArgumentException("snapshot prefixes must be unique");
        }
    }

    /** Returns the Human-approved fixed G1/G2 matrix. */
    public static GaCorrectnessMatrix approved() {
        return new GaCorrectnessMatrix(
                APPROVED_VERSION,
                APPROVED_COMMAND_COUNT,
                APPROVED_WAL_SEGMENT_SIZE_BYTES,
                List.of(QualificationProfile.values()),
                APPROVED_SEEDS,
                2,
                APPROVED_SNAPSHOT_PREFIXES);
    }

    /** Returns a small deterministic matrix for focused harness tests only. */
    public static GaCorrectnessMatrix test() {
        return new GaCorrectnessMatrix(
                "ga-g1-g2-test-v1",
                96,
                APPROVED_WAL_SEGMENT_SIZE_BYTES,
                List.of(QualificationProfile.CROSSING_MULTI_MATCH),
                List.of(20260823L),
                1,
                List.of(24, 48, 72));
    }

    /** Expands dimensions in deterministic profile/seed/repetition order. */
    public List<GaCorrectnessCase> cases() {
        final List<GaCorrectnessCase> values = new ArrayList<>(
                profiles.size() * seeds.size() * repetitions);
        for (final QualificationProfile profile : profiles) {
            for (final long seed : seeds) {
                for (int repetition = 1; repetition <= repetitions; repetition++) {
                    values.add(new GaCorrectnessCase(profile, seed, repetition));
                }
            }
        }
        return List.copyOf(values);
    }

    /** Returns the number of recovery observations required by this matrix. */
    public int recoveryObservationCount() {
        return cases().size() * (snapshotPrefixes.size() + 1);
    }
}
