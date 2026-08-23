package com.ultralatency.matching.qualification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests immutable qualification manifest and result contracts. */
class QualificationManifestTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void initialManifestCapturesWorkloadAndDoesNotCreateOutput() {
        final Path outputDirectory = temporaryDirectory.resolve("qualification-results");
        final QualificationConfiguration configuration = new QualificationConfiguration(
                QualificationProfile.RESTING_DEPTH, 20260823L, 8,
                Duration.ofSeconds(1), outputDirectory);
        final QualificationWorkload workload = QualificationWorkloadV1.generate(configuration);

        final QualificationManifest manifest = QualificationManifest.initial(
                configuration, workload, "run-1", "abc123", "v0.7.0-engineering-baseline");

        assertEquals(workload, manifest.workload());
        assertEquals(configuration, manifest.configuration());
        assertEquals(Map.of(), manifest.environment());
        assertEquals(configuration.outputDirectory(), manifest.outputDirectory());
        assertEquals(QualificationCanonicalizer.digest(configuration),
                manifest.configurationDigestHex());
        assertEquals(QualificationCanonicalizer.EMPTY_DIGEST, manifest.resultDigestHex());
        assertFalse(Files.exists(manifest.outputDirectory()));
    }

    @Test
    void resultRejectsMalformedDigests() {
        assertThrows(IllegalArgumentException.class, () -> new QualificationResult(
                true, 1, 1, 0, "bad", validDigest(), validDigest(), Map.of()));
    }

    @Test
    void resultDigestCanBeBoundWithoutCreatingOutput() {
        final Path outputDirectory = temporaryDirectory.resolve("results");
        final QualificationConfiguration configuration = new QualificationConfiguration(
                QualificationProfile.LIFECYCLE_MIX, 1, 1,
                Duration.ofSeconds(1), outputDirectory);
        final QualificationWorkload workload = QualificationWorkloadV1.generate(configuration);
        final QualificationManifest manifest = QualificationManifest.initial(
                configuration, workload, "run-1", "abc123", "v0.7.0-engineering-baseline");
        final QualificationResult result = new QualificationResult(
                true, 1, 1, 0, validDigest(), validDigest(), validDigest(), Map.of());

        assertEquals(result.digestHex(), manifest.withResult(result).resultDigestHex());
        assertFalse(Files.exists(outputDirectory));
    }

    @Test
    void manifestRejectsWorkloadConfigurationIdentityMismatch() {
        final QualificationConfiguration configuration = new QualificationConfiguration(
                QualificationProfile.LIFECYCLE_MIX, 1, 1,
                Duration.ofSeconds(1), temporaryDirectory.resolve("results"));
        final QualificationConfiguration otherConfiguration = new QualificationConfiguration(
                QualificationProfile.RESTING_DEPTH, 1, 1,
                Duration.ofSeconds(1), temporaryDirectory.resolve("results"));
        final QualificationWorkload workload = QualificationWorkloadV1.generate(
                otherConfiguration);

        assertThrows(IllegalArgumentException.class, () -> new QualificationManifest(
                "run-1", "abc123", "v0.7.0-engineering-baseline", workload,
                configuration, configuration.outputDirectory(), Map.of(),
                QualificationCanonicalizer.digest(configuration),
                QualificationCanonicalizer.EMPTY_DIGEST, Instant.now()));
    }

    private static String validDigest() {
        return "0000000000000000000000000000000000000000000000000000000000000000";
    }
}
