package com.ultralatency.matching.qualification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Public-boundary qualification tests using the real recoverable TCP server. */
class QualificationRunnerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void publicBoundaryRunRecoversAcrossThreeSessions() throws Exception {
        final QualificationConfiguration configuration = new QualificationConfiguration(
                QualificationProfile.CROSSING_MULTI_MATCH,
                20260823L,
                12,
                Duration.ofSeconds(5),
                temporaryDirectory.resolve("results"));

        final QualificationRun first = new QualificationRunner().run(configuration, 3);
        final QualificationRun second = new QualificationRunner().run(configuration, 3);

        assertTrue(first.result().success());
        assertEquals(12, first.result().acceptedCommands());
        assertEquals(first.result().responseCount(), second.result().responseCount());
        assertEquals(first.result().tradeCount(), second.result().tradeCount());
        assertEquals(first.result().checkpointDigestHex(), second.result().checkpointDigestHex());
        assertEquals(first.result().transcriptDigestHex(), second.result().transcriptDigestHex());
        assertEquals(first.result().publicProbeDigestHex(), second.result().publicProbeDigestHex());
        assertEquals(first.result().digestHex(), second.result().digestHex());
        assertEquals(3, first.restartCycles());
        assertFalse(Files.exists(configuration.outputDirectory()));
    }
}
