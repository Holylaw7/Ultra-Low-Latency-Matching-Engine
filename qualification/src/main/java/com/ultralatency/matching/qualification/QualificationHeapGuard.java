package com.ultralatency.matching.qualification;

import java.util.Comparator;
import java.util.List;

/** Chronological natural post-GC heap evidence evaluation for one run. */
public final class QualificationHeapGuard {

    /** Minimum natural samples needed to compare per-run early and late observations. */
    public static final int MINIMUM_PER_RUN_SAMPLES = 2;

    private static final long MEBIBYTE = 1024L * 1024L;

    private QualificationHeapGuard() {
    }

    /** Returns the natural post-GC observations in timestamp order. */
    public static List<QualificationResourceSample> naturalPostGcSamples(
            final List<QualificationResourceSample> samples) {
        return samples.stream()
                .filter(sample -> sample.naturalPostGcHeapBytes() != null)
                .sorted(Comparator.comparing(QualificationResourceSample::timestamp))
                .toList();
    }

    /** Evaluates one run without combining observations from another run. */
    public static boolean passes(
            final List<QualificationResourceSample> samples,
            final int minimumSamples) {
        if (minimumSamples < MINIMUM_PER_RUN_SAMPLES) {
            throw new IllegalArgumentException(
                    "minimumSamples must be at least " + MINIMUM_PER_RUN_SAMPLES);
        }
        final List<QualificationResourceSample> observations = naturalPostGcSamples(samples);
        if (observations.size() < minimumSamples) {
            return false;
        }
        final int firstEnd = Math.max(1, observations.size() / 4);
        final int lastStart = Math.min(
                observations.size() - 1, (observations.size() * 3) / 4);
        final long firstMedian = median(values(observations.subList(0, firstEnd)));
        final long lastMedian = median(values(observations.subList(lastStart, observations.size())));
        final long allowance = Math.max(32L * MEBIBYTE, firstMedian / 5L);
        return lastMedian <= firstMedian + allowance;
    }

    private static List<Long> values(final List<QualificationResourceSample> samples) {
        return samples.stream().map(QualificationResourceSample::naturalPostGcHeapBytes).toList();
    }

    private static long median(final List<Long> values) {
        final int middle = values.size() / 2;
        if (values.size() % 2 == 1) {
            return values.get(middle);
        }
        return values.get(middle - 1) / 2L + values.get(middle) / 2L;
    }
}
