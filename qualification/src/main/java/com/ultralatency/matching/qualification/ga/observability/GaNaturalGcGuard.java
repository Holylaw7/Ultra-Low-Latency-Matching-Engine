package com.ultralatency.matching.qualification.ga.observability;

import com.ultralatency.matching.qualification.ga.soak.GaNaturalGcSample;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import com.ultralatency.matching.qualification.ga.soak.GaSoakMatrix.Stage;

/** Chronological natural-GC guard for one physical G6/G8 run. */
public final class GaNaturalGcGuard {

    /** Minimum complete natural post-GC observations for a formal run. */
    public static final int MINIMUM_SAMPLES = 5;
    /** Minimum byte allowance retained for small heaps. */
    public static final long MINIMUM_ALLOWANCE_BYTES = 32L * 1024L * 1024L;

    private GaNaturalGcGuard() {
    }

    /** Immutable result of the natural-GC guard. */
    public record Evaluation(
            boolean passed,
            String outcome,
            String failureCode,
            int sampleCount,
            long firstMedianBytes,
            long finalMedianBytes,
            long allowanceBytes) {
        public Evaluation {
            if (!List.of("PASS", "FAIL", "ABORTED").contains(outcome)
                    || failureCode == null || failureCode.isBlank()
                    || sampleCount < 0 || firstMedianBytes < 0 || finalMedianBytes < 0
                    || allowanceBytes < 0) {
                throw new IllegalArgumentException("invalid natural-GC evaluation");
            }
            if (passed && (!"PASS".equals(outcome) || !"NONE".equals(failureCode))) {
                throw new IllegalArgumentException("invalid passing natural-GC evaluation");
            }
        }
    }

    /** Evaluates a formal sample list; no samples are synthesized or cross-run combined. */
    public static Evaluation evaluate(final List<GaNaturalGcSample> samples) {
        Objects.requireNonNull(samples, "samples");
        final List<GaNaturalGcSample> complete = new ArrayList<>();
        final Set<Long> sequences = new HashSet<>();
        final Set<Long> gcIds = new HashSet<>();
        long previousTimestamp = -1L;
        long previousSequence = -1L;
        for (GaNaturalGcSample sample : samples) {
            Objects.requireNonNull(sample, "natural GC sample");
            if (sample.monotonicNanos() < previousTimestamp
                    || sample.sequence() < previousSequence) {
                return new Evaluation(false, "FAIL", "B0", 0, 0L, 0L, 0L);
            }
            previousTimestamp = sample.monotonicNanos();
            previousSequence = sample.sequence();
            if (!sequences.add(sample.sequence()) || !gcIds.add(sample.gcId())) {
                return new Evaluation(false, "FAIL", "B0", 0, 0L, 0L, 0L);
            }
            if (sample.completeCycle()) {
                complete.add(sample);
            }
        }
        complete.sort(Comparator.comparingLong(GaNaturalGcSample::monotonicNanos)
                .thenComparingLong(GaNaturalGcSample::sequence));
        if (complete.size() < MINIMUM_SAMPLES) {
            return new Evaluation(false, "ABORTED", "B3", complete.size(), 0L, 0L, 0L);
        }
        final int cohort = Math.max(1, (complete.size() + 3) / 4);
        final long first = median(complete.subList(0, cohort));
        final long last = median(complete.subList(complete.size() - cohort, complete.size()));
        final long allowance = Math.max(MINIMUM_ALLOWANCE_BYTES, first / 5L);
        final boolean passed = last <= safeAdd(first, allowance);
        return new Evaluation(passed, passed ? "PASS" : "FAIL", passed ? "NONE" : "B1",
                complete.size(), first, last, allowance);
    }

    /**
     * Evaluates samples while enforcing the physical-run identity boundary.
     *
     * <p>Samples from another physical execution or stage are evidence-integrity failures;
     * they must not be silently sorted into the current run.</p>
     */
    public static Evaluation evaluate(
            final List<GaNaturalGcSample> samples,
            final String physicalExecutionId,
            final Stage stage) {
        Objects.requireNonNull(samples, "samples");
        Objects.requireNonNull(physicalExecutionId, "physicalExecutionId");
        Objects.requireNonNull(stage, "stage");
        for (GaNaturalGcSample sample : samples) {
            if (sample == null || !physicalExecutionId.equals(sample.physicalExecutionId())
                    || stage != sample.stage()) {
                return new Evaluation(false, "FAIL", "B0", 0, 0L, 0L, 0L);
            }
        }
        return evaluate(samples);
    }

    /** Alias for callers using the explicit formal name. */
    public static Evaluation evaluateFormal(final List<GaNaturalGcSample> samples) {
        return evaluate(samples);
    }

    /** Returns the frozen allowance for one first-cohort median. */
    public static long allowance(final long firstMedianBytes) {
        if (firstMedianBytes < 0) {
            throw new IllegalArgumentException("first median must be non-negative");
        }
        return Math.max(MINIMUM_ALLOWANCE_BYTES, firstMedianBytes / 5L);
    }

    /** Returns whether one first/final median pair meets the frozen guard. */
    public static boolean passes(final long firstMedianBytes, final long finalMedianBytes) {
        if (firstMedianBytes < 0 || finalMedianBytes < 0) {
            throw new IllegalArgumentException("heap medians must be non-negative");
        }
        return finalMedianBytes <= safeAdd(firstMedianBytes, allowance(firstMedianBytes));
    }

    private static long median(final List<GaNaturalGcSample> values) {
        final List<Long> sorted = values.stream().map(GaNaturalGcSample::afterGcHeapBytes)
                .sorted().toList();
        final int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }
        final long lower = sorted.get(middle - 1);
        final long upper = sorted.get(middle);
        return (lower / 2L) + (upper / 2L) + ((lower & 1L) + (upper & 1L)) / 2L;
    }

    private static long safeAdd(final long first, final long second) {
        if (Long.MAX_VALUE - first < second) {
            return Long.MAX_VALUE;
        }
        return first + second;
    }
}
