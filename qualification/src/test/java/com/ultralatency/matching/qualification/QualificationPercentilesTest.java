package com.ultralatency.matching.qualification;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class QualificationPercentilesTest {

    @Test
    void usesNearestRankAndRetainsEmptyManagementDistribution() {
        final QualificationPercentiles.Summary summary =
                QualificationPercentiles.summarize(new long[] {5L, 1L, 3L, 2L, 4L});

        assertEquals(5, summary.count());
        assertEquals(3L, summary.p50Nanos());
        assertEquals(5L, summary.p95Nanos());
        assertEquals(5L, summary.p99Nanos());
        assertEquals(5L, summary.p999Nanos());
        assertEquals(5L, summary.maxNanos());
        assertEquals(0, QualificationPercentiles.summarize(new long[0]).count());
    }
}
