package com.ultralatency.matching.qualification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests immutable qualification manifest and result contracts. */
class QualificationManifestTest {

    @Test
    void initialManifestCapturesWorkloadAndDoesNotCreateOutput() {
        final QualificationConfiguration configuration = new QualificationConfiguration(
                QualificationProfile.RESTING_DEPTH, 20260823L, 8,
                Duration.ofSeconds(1), Path.of("qualification-results"));
        final QualificationWorkload workload = QualificationWorkloadV1.generate(configuration);

        final QualificationManifest manifest = QualificationManifest.initial(
                configuration, workload, "run-1", "abc123", "v0.7.0-engineering-baseline");

        assertEquals(workload, manifest.workload());
        assertEquals(Map.of(), manifest.environment());
        assertEquals(Path.of("qualification-results").toAbsolutePath().normalize(),
                manifest.outputDirectory());
    }

    @Test
    void resultRejectsMalformedDigests() {
        assertThrows(IllegalArgumentException.class, () -> new QualificationResult(
                true, 1, 1, 0, "bad", validDigest(), validDigest(), Map.of()));
    }

    private static String validDigest() {
        return "0000000000000000000000000000000000000000000000000000000000000000";
    }
}
