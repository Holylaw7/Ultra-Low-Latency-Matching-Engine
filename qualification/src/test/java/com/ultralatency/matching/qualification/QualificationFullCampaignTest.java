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
        final QualificationFullRun run = new QualificationFullRunner().run(
                QualificationFullConfiguration.full(output));

        assertTrue(run.fullCriteriaPassed());
    }
}
