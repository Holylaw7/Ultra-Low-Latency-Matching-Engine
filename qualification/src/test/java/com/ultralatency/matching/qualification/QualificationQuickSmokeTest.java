package com.ultralatency.matching.qualification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Optional ten-thousand-command quick lane, enabled explicitly by qualification CI. */
@EnabledIfSystemProperty(named = "qualification.quick", matches = "true")
class QualificationQuickSmokeTest {

    @Test
    void executesTheApprovedQuickLane() throws Exception {
        final QualificationRun run = new QualificationRunner().run(
                QualificationConfiguration.quick(QualificationProfile.LIFECYCLE_MIX));

        assertTrue(run.result().success());
        assertEquals(10_000, run.result().acceptedCommands());
        assertEquals(3, run.restartCycles());
    }
}
