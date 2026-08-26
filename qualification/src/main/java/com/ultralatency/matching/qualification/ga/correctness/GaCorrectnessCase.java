package com.ultralatency.matching.qualification.ga.correctness;

import com.ultralatency.matching.qualification.QualificationProfile;
import java.util.Locale;
import java.util.Objects;

/** One deterministic profile/seed/repetition unit in the G1/G2 matrix. */
public record GaCorrectnessCase(
        QualificationProfile profile,
        long seed,
        int repetition) {

    /** Creates one validated matrix case. */
    public GaCorrectnessCase {
        Objects.requireNonNull(profile, "profile");
        if (seed < 0) {
            throw new IllegalArgumentException("seed must be non-negative");
        }
        if (repetition <= 0) {
            throw new IllegalArgumentException("repetition must be positive");
        }
    }

    /** Returns a stable path-safe identity. */
    public String id() {
        return profile.name().toLowerCase(Locale.ROOT)
                + "-seed-" + seed + "-repeat-" + repetition;
    }
}
