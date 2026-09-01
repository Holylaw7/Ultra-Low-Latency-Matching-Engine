package com.ultralatency.matching.qualification.ga.observability;

import com.ultralatency.matching.qualification.ga.soak.GaSoakResourceSample;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToLongFunction;
import com.ultralatency.matching.qualification.ga.soak.GaSoakMatrix.Stage;

/** Deterministic first/final resource-window guards for one physical run. */
public final class GaResourceGuards {

    /** One-Hz sample window size fixed by the formal contract. */
    public static final int WINDOW_SIZE = 300;
    /** Allowed relative drift for hard resource guards. */
    public static final int MAX_DRIFT_PERCENT = 20;

    /** Hard-gated resource fields. */
    public enum Metric {
        THREADS,
        TRANSIENT_FILE_COUNT,
        TRANSIENT_FILE_BYTES
    }

    /** Immutable resource drift result. */
    public record Evaluation(
            Metric metric,
            boolean passed,
            long firstMedian,
            long finalMedian,
            String outcome,
            String failureCode) {
        public Evaluation {
            Objects.requireNonNull(metric, "metric");
            if (firstMedian < 0 || finalMedian < 0
                    || !List.of("PASS", "FAIL", "ABORTED").contains(outcome)
                    || failureCode == null || failureCode.isBlank()) {
                throw new IllegalArgumentException("invalid resource evaluation");
            }
        }
    }

    private GaResourceGuards() {
    }

    /** Evaluates one metric over exactly the formal first/final sample windows. */
    public static Evaluation evaluate(
            final Metric metric,
            final List<GaSoakResourceSample> samples) {
        Objects.requireNonNull(metric, "metric");
        Objects.requireNonNull(samples, "samples");
        if (samples.size() < WINDOW_SIZE * 2) {
            return new Evaluation(metric, false, 0L, 0L, "ABORTED", "B3");
        }
        final List<GaSoakResourceSample> ordered = new ArrayList<>();
        String physicalExecutionId = null;
        Stage stage = null;
        final Set<Long> sequences = new HashSet<>();
        long previousTimestamp = -1L;
        long previousSequence = -1L;
        for (GaSoakResourceSample sample : samples) {
            final GaSoakResourceSample value = Objects.requireNonNull(sample, "resource sample");
            if (physicalExecutionId == null) {
                physicalExecutionId = value.physicalExecutionId();
                stage = value.stage();
            }
            if (!physicalExecutionId.equals(value.physicalExecutionId()) || stage != value.stage()
                    || value.monotonicNanos() < previousTimestamp
                    || value.sequence() < previousSequence || !sequences.add(value.sequence())) {
                return new Evaluation(metric, false, 0L, 0L, "FAIL", "B0");
            }
            previousTimestamp = value.monotonicNanos();
            previousSequence = value.sequence();
            ordered.add(value);
        }
        final long first = median(ordered.subList(0, WINDOW_SIZE), metric);
        final long last = median(ordered.subList(ordered.size() - WINDOW_SIZE, ordered.size()), metric);
        final boolean passed = driftPasses(first, last);
        return new Evaluation(metric, passed, first, last, passed ? "PASS" : "FAIL",
                passed ? "NONE" : "B1");
    }

    /** Evaluates one metric while rejecting cross-run or cross-stage sample mixing. */
    public static Evaluation evaluate(
            final Metric metric,
            final List<GaSoakResourceSample> samples,
            final String physicalExecutionId,
            final Stage stage) {
        Objects.requireNonNull(metric, "metric");
        Objects.requireNonNull(samples, "samples");
        Objects.requireNonNull(physicalExecutionId, "physicalExecutionId");
        Objects.requireNonNull(stage, "stage");
        for (GaSoakResourceSample sample : samples) {
            if (sample == null || !physicalExecutionId.equals(sample.physicalExecutionId())
                    || stage != sample.stage()) {
                return new Evaluation(metric, false, 0L, 0L, "FAIL", "B0");
            }
        }
        return evaluate(metric, samples);
    }

    /** Returns the exact integer-safe <=20% drift predicate. */
    public static boolean driftPasses(final long baseline, final long finalValue) {
        if (baseline < 0 || finalValue < 0) {
            throw new IllegalArgumentException("resource values must be non-negative");
        }
        if (baseline == 0L) {
            return finalValue == 0L;
        }
        return BigInteger.valueOf(finalValue).multiply(BigInteger.valueOf(100L))
                .compareTo(BigInteger.valueOf(baseline).multiply(BigInteger.valueOf(120L))) <= 0;
    }

    /** Returns a deterministic median without floating-point rounding. */
    public static long median(final List<Long> values) {
        Objects.requireNonNull(values, "values");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("median needs values");
        }
        final List<Long> sorted = values.stream().map(Objects::requireNonNull).sorted().toList();
        final int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }
        return sorted.get(middle - 1) / 2L + sorted.get(middle) / 2L;
    }

    /** Evaluates one pair of values as a named resource metric. */
    public static Evaluation evaluatePair(
            final Metric metric,
            final long baseline,
            final long finalValue) {
        final boolean passed = driftPasses(baseline, finalValue);
        return new Evaluation(metric, passed, baseline, finalValue,
                passed ? "PASS" : "FAIL", passed ? "NONE" : "B1");
    }

    private static long median(
            final List<GaSoakResourceSample> values,
            final Metric metric) {
        final ToLongFunction<GaSoakResourceSample> extractor = switch (metric) {
            case THREADS -> GaSoakResourceSample::threadCount;
            case TRANSIENT_FILE_COUNT -> GaSoakResourceSample::transientFileCount;
            case TRANSIENT_FILE_BYTES -> GaSoakResourceSample::transientFileBytes;
        };
        return median(values.stream().map(extractor::applyAsLong).toList());
    }
}
