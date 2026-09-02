package com.ultralatency.matching.qualification.ga.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.qualification.QualificationJfrRecording;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests Java 21 child ownership, strict protocol, and fail-closed JFR handling. */
class GaJfrEvidenceTest {

    private static final String VALID_CHILD_OUTPUT =
            "protocolVersion=1|complete=true|failureCode=NONE|recordingReadable=true"
                    + "|requiredEventsPresent=true|eventCount=1|eventFamilies="
                    + "jdk.CPULoad,jdk.GCHeapSummary,jdk.GarbageCollection,jdk.JavaThreadStatistics"
                    + ",jdk.ObjectAllocationSample,jdk.ResidentSetSize,jdk.ThreadAllocationStatistics"
                    + ",jdk.ThreadEnd,jdk.ThreadStart|constructorReturned=true|failureStage=none"
                    + "|closeExecuted=true|reason=NONE|runtimeVersion=21\n";

    @TempDir
    Path temporaryDirectory;

    @Test
    void recordingCoversTheBoundedQualificationEventConfiguration() throws Exception {
        final Path recording = temporaryDirectory.resolve("qualification.jfr");
        createRecording(recording);
        assertTrue(Files.isRegularFile(recording));

        final GaJfrEvidence evidence = GaJfrEvidence.inspect(recording, true, true, true);
        assertTrue(evidence.readable(), evidence.toString());
        assertEquals("NONE", evidence.failureCode());
        assertTrue(evidence.eventFamilies().stream().allMatch(
                GaJfrEvidence.REQUIRED_EVENT_FAMILIES::contains));
    }

    @Test
    void missingOrCorruptRecordingIsB3AndNeverComplete() throws Exception {
        final Path missing = temporaryDirectory.resolve("missing.jfr");
        final GaJfrEvidence missingEvidence = GaJfrEvidence.inspect(missing, true, true, true);
        assertFalse(missingEvidence.complete());
        assertEquals("B3", missingEvidence.failureCode());

        final Path corrupt = temporaryDirectory.resolve("corrupt.jfr");
        Files.writeString(corrupt, "not a JFR recording\n");
        final GaJfrEvidence corruptEvidence = GaJfrEvidence.inspect(corrupt, true, true, true);
        assertFalse(corruptEvidence.complete());
        assertEquals("B3", corruptEvidence.failureCode());
        assertEquals("DELETE_PASS", immediateDeleteOrMove(corrupt));
    }

    @Test
    void childOwnershipAConstructorFailureBParseFailureAndCValidControl() throws Exception {
        final Path diagnosticDirectory = Files.createTempDirectory("task052-jfr-child-");

        final Path constructorFailure = diagnosticDirectory.resolve("constructor-failure.jfr");
        Files.writeString(constructorFailure, "not a JFR recording\n");
        final GaJfrEvidence.ChildInspection constructorObservation = GaJfrEvidence.inspectDetailed(
                constructorFailure, true, true, true);
        final String constructorCleanup = immediateDeleteOrMove(constructorFailure);

        final Path parseFailure = diagnosticDirectory.resolve("parse-failure.jfr");
        createRecording(parseFailure);
        Files.write(parseFailure, new byte[] {0x42, 0x41, 0x44, 0x21}, StandardOpenOption.APPEND);
        final GaJfrEvidence.ChildInspection parseObservation = GaJfrEvidence.inspectDetailed(
                parseFailure, true, true, true);
        final String parseCleanup = immediateDeleteOrMove(parseFailure);

        final Path valid = diagnosticDirectory.resolve("valid.jfr");
        createRecording(valid);
        final GaJfrEvidence.ChildInspection validObservation = GaJfrEvidence.inspectDetailed(
                valid, true, true, true);
        final String validCleanup = immediateDeleteOrMove(valid);

        assertFalse(constructorObservation.evidence().complete());
        assertEquals("B3", constructorObservation.evidence().failureCode());
        assertFalse(constructorObservation.constructorReturned());
        assertEquals("constructor", constructorObservation.failureStage());
        assertFalse(constructorObservation.closeExecuted());
        assertEquals("DELETE_PASS", constructorCleanup);

        assertFalse(parseObservation.evidence().complete());
        assertEquals("B3", parseObservation.evidence().failureCode());
        assertTrue(parseObservation.constructorReturned());
        assertEquals("readEvent", parseObservation.failureStage());
        assertTrue(parseObservation.closeExecuted());
        assertEquals("DELETE_PASS", parseCleanup);

        assertTrue(validObservation.evidence().readable());
        assertEquals("NONE", validObservation.evidence().failureCode());
        assertTrue(validObservation.constructorReturned());
        assertEquals("none", validObservation.failureStage());
        assertTrue(validObservation.closeExecuted());
        assertEquals("DELETE_PASS", validCleanup);

        System.out.println("JFR_CHILD_DIAGNOSTIC_A=" + constructorObservation
                + ";cleanup=" + constructorCleanup);
        System.out.println("JFR_CHILD_DIAGNOSTIC_B=" + parseObservation
                + ";cleanup=" + parseCleanup);
        System.out.println("JFR_CHILD_DIAGNOSTIC_C=" + validObservation
                + ";cleanup=" + validCleanup);
    }

    @Test
    void parentRejectsMalformedDuplicateOversizedAndUnknownChildProtocol() {
        final Path fixture = temporaryDirectory.resolve("protocol.jfr");
        final String missing = VALID_CHILD_OUTPUT.replace("|reason=NONE", "");
        final String duplicate = VALID_CHILD_OUTPUT.replace(
                "|runtimeVersion=21\n", "|runtimeVersion=21|complete=true\n");
        final String unknownVersion = VALID_CHILD_OUTPUT.replace("protocolVersion=1", "protocolVersion=2");
        final String oversized = "x".repeat(16 * 1024 + 1);

        assertEquals("B2", classify(fixture, missing).evidence().failureCode());
        assertEquals("B2", classify(fixture, duplicate).evidence().failureCode());
        assertEquals("B2", classify(fixture, unknownVersion).evidence().failureCode());
        assertEquals("B2", classify(fixture, oversized).evidence().failureCode());
    }

    @Test
    void observedEventFamiliesDoNotGetFabricatedFromExpectedConfiguration() {
        final Path fixture = temporaryDirectory.resolve("protocol.jfr");
        final String missingFamily = VALID_CHILD_OUTPUT
                .replace("|complete=true", "|complete=false")
                .replace("|requiredEventsPresent=true", "|requiredEventsPresent=false")
                .replace(",jdk.ThreadStart", "");
        final GaJfrEvidence.ChildInspection result = classify(fixture, missingFamily);

        assertTrue(result.evidence().readable());
        assertFalse(result.evidence().complete());
        assertFalse(result.evidence().eventFamilies().contains("jdk.ThreadStart"));
        assertEquals("NONE", result.evidence().failureCode());

        final String emptyFailure = VALID_CHILD_OUTPUT
                .replace("complete=true", "complete=false")
                .replace("failureCode=NONE", "failureCode=B3")
                .replace("recordingReadable=true", "recordingReadable=false")
                .replace("requiredEventsPresent=true", "requiredEventsPresent=false")
                .replace("eventFamilies=jdk.CPULoad,jdk.GCHeapSummary,jdk.GarbageCollection,jdk.JavaThreadStatistics"
                        + ",jdk.ObjectAllocationSample,jdk.ResidentSetSize,jdk.ThreadAllocationStatistics"
                        + ",jdk.ThreadEnd,jdk.ThreadStart", "eventFamilies=");
        final GaJfrEvidence.ChildInspection empty = classify(fixture, emptyFailure);
        assertEquals("B3", empty.evidence().failureCode());
        assertTrue(empty.evidence().eventFamilies().isEmpty());
    }

    @Test
    void childCrashAndBoundedTimeoutAreAbortedWithoutRetry() {
        final Path fixture = temporaryDirectory.resolve("protocol-failure.jfr");
        final GaJfrEvidence.ChildInspection crash = GaJfrEvidence.classifyChildExecution(
                fixture, true, true, true, "", 137, false);
        final GaJfrEvidence.ChildInspection timeout = GaJfrEvidence.classifyChildExecution(
                fixture, true, true, true, "", -1, true);

        assertEquals("B3", crash.evidence().failureCode());
        assertEquals("crash", crash.failureStage());
        assertEquals("B3", timeout.evidence().failureCode());
        assertEquals("timeout", timeout.failureStage());
    }

    private static GaJfrEvidence.ChildInspection classify(
            final Path recording,
            final String output) {
        return GaJfrEvidence.classifyChildExecution(recording, true, true, true,
                output, 0, false);
    }

    private static void createRecording(final Path recording) throws IOException {
        try (QualificationJfrRecording ignored = QualificationJfrRecording.start(recording)) {
            // The recording close writes the deterministic fixture before child inspection.
        }
    }

    private static String immediateDeleteOrMove(final Path source) {
        try {
            Files.delete(source);
            return "DELETE_PASS";
        } catch (final IOException deleteFailure) {
            final Path moved = source.resolveSibling(source.getFileName() + ".moved");
            try {
                Files.move(source, moved);
                try {
                    Files.deleteIfExists(moved);
                } catch (final IOException ignored) {
                    // The move itself is the ownership observation; cleanup is best effort.
                }
                return "DELETE_FAIL_" + deleteFailure.getClass().getSimpleName()
                        + ";MOVE_PASS";
            } catch (final IOException moveFailure) {
                return "DELETE_FAIL_" + deleteFailure.getClass().getSimpleName()
                        + ";MOVE_FAIL_" + moveFailure.getClass().getSimpleName();
            }
        }
    }
}
