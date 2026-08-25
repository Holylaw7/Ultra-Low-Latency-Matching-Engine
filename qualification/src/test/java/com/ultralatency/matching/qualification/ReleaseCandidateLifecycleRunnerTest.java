package com.ultralatency.matching.qualification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReleaseCandidateLifecycleRunnerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void boundedMatrixUsesOnlyPackagedRuntimePublicBoundaries() throws Exception {
        final ReleaseCandidateLifecycleConfiguration configuration =
                ReleaseCandidateLifecycleConfiguration.test(
                        temporaryDirectory.resolve("lifecycle-results"));

        final ReleaseCandidateLifecycleResult result =
                new ReleaseCandidateLifecycleRunner().run(configuration);

        assertTrue(result.success());
        assertEquals(3, result.cycles().size());
        assertEquals(1, result.cycles().stream()
                .filter(cycle -> cycle.scenario().equals("EMPTY_PURE_WAL")).count());
        assertEquals(1, result.cycles().stream()
                .filter(cycle -> cycle.scenario().equals("SNAPSHOT_THEN_WAL")).count());
        assertEquals(1, result.cycles().stream()
                .filter(ReleaseCandidateLifecycleResult.Cycle::forcedTermination).count());
        assertTrue(Files.isRegularFile(
                result.artifactDirectory().resolve("rc-lifecycle-summary-v1.txt")));
        assertTrue(Files.isRegularFile(
                result.artifactDirectory().resolve("artifact-hashes-v1.txt")));
        assertEquals(64, result.summarySha256().length());
    }
}
