package com.ultralatency.matching.qualification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Focused child-process restart and acknowledged-boundary termination evidence. */
class QualificationRestartCampaignRunnerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void gracefulAndForcedCyclesConvergeThroughPublicBoundary() throws Exception {
        final QualificationRestartCampaignConfiguration configuration =
                QualificationRestartCampaignConfiguration.test(
                        temporaryDirectory.resolve("qualification-results"));

        final QualificationRestartCampaignResult campaign =
                new QualificationRestartCampaignRunner().run(configuration);
        final QualificationRestartCampaignResult repeated =
                new QualificationRestartCampaignRunner().run(configuration);

        assertTrue(campaign.success());
        assertEquals(2, campaign.gracefulRestartCycles());
        assertEquals(2, campaign.forcedTerminationCycles());
        assertEquals(configuration.workloadConfiguration().commandCount(),
                campaign.result().acceptedCommands());
        assertEquals(configuration.totalCycles(), campaign.cycles().size());
        assertTrue(campaign.cycles().stream().allMatch(
                QualificationRestartCycle::convergencePassed));
        assertTrue(campaign.cycles().stream().allMatch(
                QualificationRestartCycle::acknowledgedBoundary));
        assertEquals(campaign.result().checkpointDigestHex(),
                repeated.result().checkpointDigestHex());
        assertEquals(campaign.result().transcriptDigestHex(),
                repeated.result().transcriptDigestHex());
        assertEquals(campaign.result().publicProbeDigestHex(),
                repeated.result().publicProbeDigestHex());
        assertEquals(campaign.result().measurements().get("walCommandDigestHex"),
                repeated.result().measurements().get("walCommandDigestHex"));
        assertEquals("NOT_CLAIMED",
                campaign.result().measurements().get("claim.ambiguousOutcomesExactlyOnce"));
        assertTrue(Files.isRegularFile(
                campaign.artifactDirectory().resolve("qualification-campaign-summary-v1.txt")));
        assertTrue(Files.isRegularFile(
                campaign.artifactDirectory().resolve("artifact-hashes-v1.txt")));
        assertFalse(campaign.result().measurements().containsKey("retryUntilPass"));
    }
}
