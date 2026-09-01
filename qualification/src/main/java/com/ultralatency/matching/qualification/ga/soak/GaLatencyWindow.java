package com.ultralatency.matching.qualification.ga.soak;

import com.ultralatency.matching.qualification.QualificationPercentiles;
import java.util.Arrays;
import java.util.Objects;

/** Immutable accepted-command ordinal latency windows used by the G6 guard. */
public record GaLatencyWindow(
        long firstAcceptedOrdinal,
        long lastAcceptedOrdinal,
        long[] samplesNanos) {

    /** Accepted commands excluded as warmup by the frozen G6 contract. */
    public static final long WARMUP_ACCEPTED_COMMANDS = 60_000L;
    /** Number of samples in each formal comparison window. */
    public static final int COMPARISON_WINDOW_SAMPLES = 120_000;

    /** Validates an ordinal-owned immutable latency window. */
    public GaLatencyWindow {
        Objects.requireNonNull(samplesNanos, "samplesNanos");
        if (firstAcceptedOrdinal <= 0 || lastAcceptedOrdinal < firstAcceptedOrdinal
                || lastAcceptedOrdinal - firstAcceptedOrdinal + 1L != samplesNanos.length
                || samplesNanos.length == 0) {
            throw new IllegalArgumentException("latency window ordinals are invalid");
        }
        samplesNanos = samplesNanos.clone();
        if (Arrays.stream(samplesNanos).anyMatch(value -> value < 0)) {
            throw new IllegalArgumentException("latency samples must be non-negative");
        }
    }

    /** Returns a defensive copy of the window samples. */
    @Override
    public long[] samplesNanos() {
        return samplesNanos.clone();
    }

    /** Returns the nearest-rank P99 of this window. */
    public long p99Nanos() {
        return QualificationPercentiles.summarize(samplesNanos).p99Nanos();
    }

    /** Builds the frozen first comparison window from accepted-completion ordinals. */
    public static GaLatencyWindow first(final long[] samples) {
        Objects.requireNonNull(samples, "samples");
        if (samples.length != COMPARISON_WINDOW_SAMPLES) {
            throw new IllegalArgumentException("first latency window must contain 120000 samples");
        }
        return new GaLatencyWindow(
                WARMUP_ACCEPTED_COMMANDS + 1L,
                WARMUP_ACCEPTED_COMMANDS + samples.length,
                samples);
    }

    /** Builds the frozen final comparison window from the final accepted completions. */
    public static GaLatencyWindow finalWindow(final long totalAccepted, final long[] samples) {
        Objects.requireNonNull(samples, "samples");
        if (samples.length != COMPARISON_WINDOW_SAMPLES || totalAccepted < samples.length) {
            throw new IllegalArgumentException("final latency window is incomplete");
        }
        return new GaLatencyWindow(
                totalAccepted - samples.length + 1L,
                totalAccepted,
                samples);
    }

    /** Returns whether this window is after the frozen warmup boundary. */
    public boolean excludesWarmup() {
        return firstAcceptedOrdinal > WARMUP_ACCEPTED_COMMANDS;
    }
}
