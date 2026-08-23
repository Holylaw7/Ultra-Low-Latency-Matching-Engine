package com.ultralatency.matching.qualification;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests chronological per-run heap evidence without forcing GC. */
class QualificationHeapGuardTest {

    @Test
    void descendingHeapObservationsPassInChronologicalOrder() {
        final List<QualificationResourceSample> samples = samples(
                500L, 400L, 300L, 200L, 100L);

        assertTrue(QualificationHeapGuard.passes(samples, 2));
    }

    @Test
    void retainedHeapGrowthFailsInChronologicalOrder() {
        final List<QualificationResourceSample> samples = samples(
                100L, 200L, 300L, 400L, 500L);

        assertFalse(QualificationHeapGuard.passes(samples, 2));
    }

    @Test
    void fewerThanPerRunMinimumSamplesCannotPass() {
        assertFalse(QualificationHeapGuard.passes(samples(500L), 2));
    }

    private static List<QualificationResourceSample> samples(final long... heapMiB) {
        final Instant start = Instant.parse("2026-08-23T00:00:00Z");
        return java.util.stream.LongStream.range(0, heapMiB.length)
                .mapToObj(index -> new QualificationResourceSample(
                        start.plusSeconds(index), 10, 10, index + 1, index,
                        heapMiB[(int) index] * 1024L * 1024L,
                        heapMiB[(int) index] * 1024L * 1024L))
                .toList();
    }
}
