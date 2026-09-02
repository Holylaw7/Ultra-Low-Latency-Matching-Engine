package com.ultralatency.matching.qualification.ga.observability;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
        final Set<String> observedEventFamilies = new LinkedHashSet<>();
        try (RecordingFile reader = new RecordingFile(recording)) {
            constructorReturned = true;
            failureStage = "readEvent";
            while (reader.hasMoreEvents()) {
                final RecordedEvent event = reader.readEvent();
                if (event != null) {
                    eventCount++;
                    final String eventName = event.getEventType().getName();
                    if (GaJfrEvidence.REQUIRED_EVENT_FAMILIES.contains(eventName)) {
                        observedEventFamilies.add(eventName);
                    }
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
        final boolean requiredEventsPresent = observedEventFamilies.containsAll(
                GaJfrEvidence.REQUIRED_EVENT_FAMILIES);
        final boolean readable = "NONE".equals(failureCode);
        final boolean complete = readable && requiredEventsPresent;
        final String eventFamilies = eventFamilies(observedEventFamilies);
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

    private static String eventFamilies(final Set<String> observedEventFamilies) {
        final List<String> values = new ArrayList<>(observedEventFamilies);
        values.sort(String::compareTo);
        return String.join(",", values);
    }
}
