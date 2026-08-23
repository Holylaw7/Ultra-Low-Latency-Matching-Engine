package com.ultralatency.matching.qualification;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Manual Full Qualification entry point; never runs in ordinary CI. */
class QualificationFullCampaignTest {

    @Test
    void fullLaneMeetsTheApprovedQualificationGate() throws Exception {
        Assumptions.assumeTrue(
                Boolean.getBoolean("qualification.full"),
                "Full Qualification is an explicit manual evidence lane");
        final Path output = Path.of(System.getProperty(
                "qualification.output", "qualification-results"));
        final String profile = System.getProperty("qualification.profile", "LIFECYCLE_MIX");
        if (!"LIFECYCLE_MIX".equals(profile)
                && !"MEMORY_STEADY_STATE_V1".equals(profile)) {
            throw new IllegalArgumentException("unsupported qualification.profile: " + profile);
        }
        final boolean memorySteadyState = "MEMORY_STEADY_STATE_V1".equals(profile);
        final QualificationFullRun run = new QualificationFullRunner().run(
                memorySteadyState
                        ? QualificationFullConfiguration.memorySteadyStateFull(output)
                        : QualificationFullConfiguration.full(output));

        assertTrue(run.fullCriteriaPassed());
    }
}
