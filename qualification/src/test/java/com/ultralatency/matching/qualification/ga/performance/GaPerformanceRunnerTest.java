package com.ultralatency.matching.qualification.ga.performance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.qualification.QualificationArtifactHasher;
import com.ultralatency.matching.qualification.ga.GaCandidateVerifier;
import com.ultralatency.matching.qualification.ga.correctness.GaCorrectnessCanonicalContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises the G4 public Protocol v1 Quick readiness path. */
class GaPerformanceRunnerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void quickSmokePublishesNonFormalEvidence() throws Exception {
        final GaPerformanceQuickResult result = new GaPerformanceRunner(testContext())
                .runQuick(temporaryDirectory);
        assertTrue(result.evaluation().passed());
        assertFalse(result.evaluation().formalEligible());
        assertTrue(Files.isRegularFile(result.manifestPath()));
        assertTrue(Files.isRegularFile(result.gateResultPath()));
    }

    @Test
    void formalPerformanceBindsCandidateAndQualificationJarsAtRealStartupBoundary()
            throws Exception {
        final Path repository = repositoryRoot();
        final Path candidate = repository.resolve("core/target/matching-engine-rc.jar")
                .toAbsolutePath().normalize();
        final Path qualification = repository.resolve(
                "qualification/target/matching-engine-qualification.jar")
                .toAbsolutePath().normalize();
        org.junit.jupiter.api.Assumptions.assumeTrue(
                Files.isRegularFile(candidate) && Files.isRegularFile(qualification));
        final String candidateSha = QualificationArtifactHasher.sha256(candidate);
        final String qualificationSha = QualificationArtifactHasher.sha256(qualification);
        final String previousJar = System.getProperty("qualification.jar");
        final String previousSha = System.getProperty("qualification.jarSha256");
        System.setProperty("qualification.jar", qualification.toString());
        System.setProperty("qualification.jarSha256", qualificationSha);
        final GaFormalPerformanceRunner.GaFormalRunResult result;
        try {
            result = GaFormalPerformanceRunner.runPerformance(
                    candidate, qualification, temporaryDirectory.resolve("formal-run"),
                    rc2Context(candidateSha), GaPerformanceMatrix.test(), qualificationSha, 1);
        } finally {
            restoreProperty("qualification.jar", previousJar);
            restoreProperty("qualification.jarSha256", previousSha);
        }

        assertTrue(Files.isRegularFile(result.publishedRun().manifestPath()));
        assertTrue(Files.isRegularFile(result.publishedRun().gateResultPath()));
        assertTrue(Files.isDirectory(result.publishedRun().evidenceDirectory()));
        final GaFormalPerformanceEvidenceVerifier.Verification verification =
                GaFormalPerformanceEvidenceVerifier.verifyRun(
                        result.publishedRun().evidenceDirectory());
        assertTrue(verification.passed(), verification.findings()::toString);
        final Path raw = result.publishedRun().evidenceDirectory()
                .resolve("raw-evidence-v2.txt");
        Files.writeString(raw, "tampered=true\n", java.nio.file.StandardOpenOption.APPEND);
        assertFalse(GaFormalPerformanceEvidenceVerifier.verifyRun(
                result.publishedRun().evidenceDirectory()).passed());
    }

    @Test
    void lifecycleBlockerPreventsAnyManagementExecution() {
        final GaFormalPerformanceRunner.LifecycleSample failed =
                new GaFormalPerformanceRunner.LifecycleSample(
                        1, "00000000-0000-0000-0000-000000000001", 1L, 1L, 0,
                        true, true, "READY", "FAILED", 1L, true, "0".repeat(64),
                        "1".repeat(64), "FAIL", "TERMINAL_FAILURE", true, true, true, true);
        final GaFormalPerformanceRunner.LifecycleResult result =
                new GaFormalPerformanceRunner.LifecycleResult(List.of(failed), false, "B1");

        assertFalse(GaFormalPerformanceRunner.shouldStartManagement(result));
    }

    @Test
    void lifecycleP99IsEvaluatedBeforeManagementCanStart() {
        final List<GaFormalPerformanceRunner.LifecycleSample> samples = new ArrayList<>();
        for (int cycle = 1; cycle <= GaFormalPerformanceContract.LIFECYCLE_CYCLES; cycle++) {
            samples.add(new GaFormalPerformanceRunner.LifecycleSample(
                    cycle, String.format("00000000-0000-0000-0000-%012d", cycle),
                    cycle == 1 ? GaPerformanceEvaluator.MAX_LIFECYCLE_P99_NANOS + 1L : 1L,
                    1L, 0, true, true, "READY", "NONE", 0L, true,
                    "0".repeat(64), "1".repeat(64), "PASS", "NONE", true, true, true, true));
        }
        final GaFormalPerformanceRunner.LifecycleResult result =
                new GaFormalPerformanceRunner.LifecycleResult(samples, true, "NONE");

        assertFalse(GaFormalPerformanceRunner.shouldStartManagement(result));
    }

    @Test
    void managementBlockersCannotBeHiddenBySyntheticPassMetrics() {
        assertEquals("B1", GaFormalPerformanceRunner.managementBlocker(
                true, true, true, false, true, true, true, true, 0, 0));
        assertEquals("B1", GaFormalPerformanceRunner.managementBlocker(
                true, true, true, true, true, true, true, true, 1, 0));
        assertEquals("B3", GaFormalPerformanceRunner.managementBlocker(
                true, true, true, true, false, true, true, true, 0, 0));
    }

    @Test
    void statusTrialRequiresExactlyOnePollPerSecondAndIdleHasNoPolls() {
        assertTrue(GaFormalPerformanceRunner.statusWorkloadComplete(true,
                GaFormalPerformanceContract.MANAGEMENT_STATUS_REQUESTS));
        assertFalse(GaFormalPerformanceRunner.statusWorkloadComplete(true,
                GaFormalPerformanceContract.MANAGEMENT_STATUS_REQUESTS - 1));
        assertTrue(GaFormalPerformanceRunner.statusWorkloadComplete(false, 0));
        assertFalse(GaFormalPerformanceRunner.statusWorkloadComplete(false, 1));
    }

    @Test
    void statusSamplesMustStartAndCompleteInsideMeasurementBoundary() {
        final long start = 1_000L;
        final long end = 2_000L;
        assertTrue(GaFormalPerformanceRunner.statusSampleWithinMeasurement(
                start, end, 1_100L, 1_900L));
        assertFalse(GaFormalPerformanceRunner.statusSampleWithinMeasurement(
                start, end, end, end));
        assertFalse(GaFormalPerformanceRunner.statusSampleWithinMeasurement(
                start, end, 1_900L, end));
        assertFalse(GaFormalPerformanceRunner.statusSampleWithinMeasurement(
                start, end, 2_001L, 2_002L));
    }

    @Test
    void firstBlockedPhysicalStagePreventsEveryLaterLaunch() throws Exception {
        final int[] launches = {0};

        final int executed = GaFormalPerformanceRunner.executeUntilBlocker(3, ordinal -> {
            launches[0]++;
            assertEquals(1, ordinal);
            return false;
        });

        assertEquals(1, executed);
        assertEquals(1, launches[0]);
    }

    @Test
    void secondBlockedPhysicalStagePreventsThirdLaunch() throws Exception {
        final int[] launches = {0};

        final int executed = GaFormalPerformanceRunner.executeUntilBlocker(3, ordinal -> {
            launches[0]++;
            return ordinal < 2;
        });

        assertEquals(2, executed);
        assertEquals(2, launches[0]);
    }

    private GaCorrectnessCanonicalContext testContext() {
        final String digest = "0".repeat(64);
        return new GaCorrectnessCanonicalContext(
                temporaryDirectory,
                "2".repeat(40),
                new GaCandidateVerifier.Verified(
                        "v0.9.0-rc.1", "0".repeat(40), "1".repeat(40), digest, digest));
    }

    private GaCorrectnessCanonicalContext rc2Context(final String candidateSha) {
        return new GaCorrectnessCanonicalContext(
                Path.of("."),
                "2".repeat(40),
                new GaCandidateVerifier.Verified(
                        "v0.9.0-rc.2",
                        "9e2a67ada0e3b6220b730131d0bae79dc03073ed",
                        "740e8a3dea0a759c707c597778c26c41e9bb3e47",
                        "ef1d9f4cb64a9d6e331fb326ebe8f3b0abb29a53bf6045a5d4999a53e73b4bbc",
                        candidateSha));
    }

    private Path repositoryRoot() {
        Path candidate = Path.of(System.getProperty("user.dir"))
                .toAbsolutePath().normalize();
        while (candidate != null && !Files.isDirectory(candidate.resolve("core"))) {
            candidate = candidate.getParent();
        }
        return candidate == null ? Path.of(".").toAbsolutePath().normalize() : candidate;
    }

    private static void restoreProperty(final String name, final String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
