package com.ultralatency.matching.qualification.ga.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.qualification.ga.soak.GaSoakMatrix;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests strict parsing of the existing public management schema. */
class GaManagementEvidenceTest {

    @Test
    void parsesOnlyExistingLiveReadyStatusAndMetricsFields() {
        final GaManagementEvidence live = GaManagementEvidence.parse(
                "{\"schemaVersion\":1,\"live\":true}\n");
        final GaManagementEvidence ready = GaManagementEvidence.parse(
                "{\"schemaVersion\":1,\"ready\":true}");
        final GaManagementEvidence status = GaManagementEvidence.parse(
                "{\"schemaVersion\":1,\"state\":\"READY\",\"live\":true,"
                        + "\"ready\":true,\"failureCode\":\"NONE\","
                        + "\"protocolBound\":true,\"recoveryMode\":\"PURE_WAL\","
                        + "\"acceptedCommands\":7,\"terminalFailures\":0,"
                        + "\"uptimeMillis\":42}");
        final GaManagementEvidence metrics = GaManagementEvidence.parse(
                "{\"schemaVersion\":1,\"state\":\"READY\",\"live\":true,"
                        + "\"ready\":true,\"failureCode\":\"NONE\","
                        + "\"protocolBound\":true,\"recoveryMode\":\"PURE_WAL\","
                        + "\"acceptedCommands\":7,\"terminalFailures\":0,"
                        + "\"uptimeMillis\":42,\"managementRequests\":4,"
                        + "\"managementRejected\":0}");

        assertEquals(GaManagementEvidence.Kind.LIVE, live.kind());
        assertEquals(GaManagementEvidence.Kind.READY, ready.kind());
        assertEquals(GaManagementEvidence.Kind.STATUS, status.kind());
        assertEquals(GaManagementEvidence.Kind.METRICS, metrics.kind());
        assertTrue(live.hasRequiredFields());
        assertTrue(ready.hasRequiredFields());
        assertTrue(status.hasRequiredFields());
        assertTrue(metrics.hasRequiredFields());
        assertTrue(metrics.nonRegressingFrom(metrics));
    }

    @Test
    void rejectsUnknownInventedFieldsMalformedTypesAndWrongSchema() {
        final String status = "{\"schemaVersion\":1,\"state\":\"READY\",\"live\":true,"
                + "\"ready\":true,\"failureCode\":\"NONE\",\"protocolBound\":true,"
                + "\"recoveryMode\":\"PURE_WAL\",\"acceptedCommands\":0,"
                + "\"terminalFailures\":0,\"uptimeMillis\":0}";
        assertThrows(IllegalArgumentException.class, () -> GaManagementEvidence.parse(
                status.replace("\"uptimeMillis\":0", "\"completedCommands\":0,"
                        + "\"uptimeMillis\":0")));
        assertThrows(IllegalArgumentException.class, () -> GaManagementEvidence.parse(
                status.replace("\"schemaVersion\":1", "\"schemaVersion\":2")));
        assertThrows(IllegalArgumentException.class, () -> GaManagementEvidence.parse(
                status.replace("\"live\":true", "\"live\":\"true\"")));
        assertThrows(IllegalArgumentException.class, () -> GaManagementEvidence.parse(
                status.replace("\"acceptedCommands\":0", "\"acceptedCommands\":-1")));
        assertThrows(IllegalArgumentException.class, () -> GaManagementEvidence.parse(
                status + "\ntrailing"));
    }

    @Test
    void incompleteBoundaryNeverCountsAsCompleteManagementEvidence() {
        final GaManagementEvidence incomplete = new GaManagementEvidence(
                GaManagementEvidence.Kind.READY, 1, null, true, null, null, null, null,
                null, null, null, null, null, false);
        final GaObservabilityObservation observation = new GaObservabilityObservation(
                "physical-a", GaSoakMatrix.Stage.QUICK, List.of(), GaGcEvidence.quick("NONE"),
                GaJfrEvidence.valid(Path.of("quick.jfr")), List.of(incomplete), true, true,
                0, false, false, true, true, true, true);

        assertFalse(incomplete.completeResponseBoundary());
        assertFalse(observation.managementComplete());
    }

    @Test
    void monotonicCountersCannotRegress() {
        final GaManagementEvidence previous = GaManagementEvidence.metrics(
                1, true, true, "READY", "NONE", true, "PURE_WAL", 9, 1, 100, 4, 1);
        final GaManagementEvidence current = GaManagementEvidence.metrics(
                1, true, true, "READY", "NONE", true, "PURE_WAL", 8, 1, 101, 5, 1);

        assertFalse(current.nonRegressingFrom(previous));
        assertTrue(previous.nonRegressingFrom(previous));
    }
}
