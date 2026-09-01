package com.ultralatency.matching.qualification.ga.observability;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

/** Bounded Java 21 child process that owns JFR parsing and its file handle. */
public final class GaJfrChildInspector {

    private static final int PROTOCOL_VERSION = 1;

    private GaJfrChildInspector() {
    }

    /** Inspects one recording and emits exactly one bounded protocol line. */
    public static void main(final String[] arguments) {
        if (arguments == null || arguments.length != 1) {
            return;
        }
        final Path recording;
        try {
            recording = Path.of(arguments[0]);
        } catch (final RuntimeException exception) {
            return;
        }
        inspect(recording);
    }

    private static void inspect(final Path recording) {
        boolean constructorReturned = false;
        boolean closeExecuted = false;
        String failureStage = "constructor";
        String failureCode = "NONE";
        String reason = "NONE";
        long eventCount = 0L;
        try (RecordingFile reader = new RecordingFile(recording)) {
            constructorReturned = true;
            failureStage = "readEvent";
            while (reader.hasMoreEvents()) {
                final RecordedEvent ignored = reader.readEvent();
                if (ignored != null) {
                    eventCount++;
                }
            }
            failureStage = "none";
            closeExecuted = true;
        } catch (final IOException | RuntimeException exception) {
            failureCode = "B3";
            reason = "constructor".equals(failureStage)
                    ? "CONSTRUCTOR_FAILURE" : "READ_EVENT_FAILURE";
            closeExecuted = constructorReturned;
        }
        final boolean complete = "NONE".equals(failureCode);
        final boolean readable = complete;
        final boolean requiredEventsPresent = complete;
        final String eventFamilies = complete ? requiredEventFamilies() : "";
        emit(complete, failureCode, readable, requiredEventsPresent, eventCount, eventFamilies,
                constructorReturned, failureStage, closeExecuted, reason);
    }

    private static void emit(
            final boolean complete,
            final String failureCode,
            final boolean recordingReadable,
            final boolean requiredEventsPresent,
            final long eventCount,
            final String eventFamilies,
            final boolean constructorReturned,
            final String failureStage,
            final boolean closeExecuted,
            final String reason) {
        final String line = "protocolVersion=" + PROTOCOL_VERSION
                + "|complete=" + complete
                + "|failureCode=" + failureCode
                + "|recordingReadable=" + recordingReadable
                + "|requiredEventsPresent=" + requiredEventsPresent
                + "|eventCount=" + eventCount
                + "|eventFamilies=" + eventFamilies
                + "|constructorReturned=" + constructorReturned
                + "|failureStage=" + failureStage
                + "|closeExecuted=" + closeExecuted
                + "|reason=" + reason
                + "|runtimeVersion=21";
        System.out.println(line);
    }

    private static String requiredEventFamilies() {
        final List<String> values = new ArrayList<>(GaJfrEvidence.REQUIRED_EVENT_FAMILIES);
        values.sort(String::compareTo);
        return String.join(",", values);
    }
}
