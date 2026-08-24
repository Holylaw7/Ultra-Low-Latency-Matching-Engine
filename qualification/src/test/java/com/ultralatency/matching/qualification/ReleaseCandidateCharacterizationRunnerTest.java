package com.ultralatency.matching.qualification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReleaseCandidateCharacterizationRunnerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void publishesBoundedLifecycleAndPairedTrialEvidenceThroughPublicRuntime() throws Exception {
        final ReleaseCandidateCharacterizationConfiguration configuration =
                ReleaseCandidateCharacterizationConfiguration.test(
                        null, temporaryDirectory.resolve("characterization-results"));

        final ReleaseCandidateCharacterizationResult result =
                new ReleaseCandidateCharacterizationRunner().run(configuration);

        assertTrue(result.success(), Files.readString(result.summaryPath()
                ) + result.lifecycleSamples());
        assertEquals(2, result.lifecycleSamples().size());
        assertTrue(result.lifecycleSamples().stream()
                .allMatch(ReleaseCandidateCharacterizationResult.LifecycleSample::passed),
                result.lifecycleSamples().toString());
        assertTrue(result.managementIdle().acceptedCommands() > 0);
        assertTrue(result.statusOneHz().acceptedCommands() > 0);
        assertTrue(Files.isRegularFile(result.summaryPath()));
        assertTrue(Files.isRegularFile(result.artifactHashesPath()));
        assertEquals(64, result.summarySha256().length());
        assertTrue(Files.readString(result.summaryPath()).contains(
                "claims.productionReady=NOT_CLAIMED"));
    }
}
