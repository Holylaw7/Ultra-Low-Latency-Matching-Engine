package com.ultralatency.matching.qualification;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ReleaseCandidateQualificationProcessTest {

    @Test
    void rejectsInvalidReadyAndTimeoutBoundsBeforeStartingAChild() {
        assertThrows(IllegalArgumentException.class, () ->
                ReleaseCandidateQualificationProcess.start(
                        null,
                        java.nio.file.Path.of("missing.properties"),
                        Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () ->
                ReleaseCandidateManagementClient.request(1, "READY", Duration.ZERO));
    }

}
