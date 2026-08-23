package com.ultralatency.matching.qualification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Short-lane integration evidence for the full qualification harness composition. */
class QualificationFullRunnerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void shortLaneProducesPublicBoundaryArtifactsWithoutClaimingFullQualification()
            throws Exception {
        final QualificationFullConfiguration configuration =
                QualificationFullConfiguration.test(temporaryDirectory.resolve("results"));

        final QualificationFullRun run = new QualificationFullRunner().run(configuration);

        assertTrue(run.qualificationRun().result().success());
        assertEquals(12, run.qualificationRun().result().acceptedCommands());
        assertTrue(run.listenerRebound());
        assertTrue(run.recoveryLeaseReacquired());
        assertTrue(run.inventoryStable());
        assertTrue(run.fullCriteriaPassed());
        assertFalse(run.resourceEvidence().heapGuardAssessed());
        assertTrue(Files.isRegularFile(run.artifactDirectory().resolve("qualification.jfr")));
        assertTrue(Files.isRegularFile(
                run.artifactDirectory().resolve("qualification-manifest.txt")));
        assertEquals(64, run.jfrDigestHex().length());
        assertEquals(64, run.manifestDigestHex().length());
        assertEquals(64, run.resourceEvidenceDigestHex().length());
        assertTrue(Files.isRegularFile(
                run.artifactDirectory().resolve("resource-evidence.csv")));
        assertFalse(Files.exists(run.artifactDirectory().resolve("failure-report.txt")));
    }
}
