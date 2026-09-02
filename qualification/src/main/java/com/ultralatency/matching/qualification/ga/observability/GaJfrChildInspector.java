package com.ultralatency.matching.qualification.ga.observability;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.consumer.RecordingFile;

/** Bounded Java 21 child process that owns JFR parsing and its file handle. */
public final class GaJfrChildInspector {

    private static final int PROTOCOL_VERSION = 1;

    private GaJfrChildInspector() {
    }

    /** Inspects one recording and emits exactly one bounded protocol line. */
    public static void main(final String[] arguments) {
        if (arguments == null || (arguments.length != 1 && arguments.length != 3)) {
            return;
        }
        final Path recording;
        try {
            recording = Path.of(arguments[0]);
        } catch (final RuntimeException exception) {
            return;
        }
        final Path gcOutput;
        if (arguments.length == 3 && "--gc-output".equals(arguments[1])) {
            try {
                gcOutput = Path.of(arguments[2]);
            } catch (final RuntimeException exception) {
                return;
            }
        } else if (arguments.length == 1) {
            gcOutput = null;
        } else {
            return;
        }
        inspect(recording, gcOutput);
    }

    private static void inspect(final Path recording, final Path gcOutput) {
        boolean constructorReturned = false;
        boolean closeExecuted = false;
        String failureStage = "constructor";
        String failureCode = "NONE";
        String reason = "NONE";
        long eventCount = 0L;
        final Set<String> observedEventFamilies = new LinkedHashSet<>();
        final Map<Long, Long> gcOrder = new LinkedHashMap<>();
        final Map<Long, Long> afterGcHeap = new LinkedHashMap<>();
        try (RecordingFile reader = new RecordingFile(recording)) {
            constructorReturned = true;
            failureStage = "readEvent";
            long eventSequence = 0L;
            while (reader.hasMoreEvents()) {
                final RecordedEvent event = reader.readEvent();
                if (event != null) {
                    eventCount++;
                    final String eventName = event.getEventType().getName();
                    if (GaJfrEvidence.REQUIRED_EVENT_FAMILIES.contains(eventName)) {
                        observedEventFamilies.add(eventName);
                    }
                    if ("jdk.GarbageCollection".equals(eventName)) {
                        final Long gcId = gcId(event, eventSequence);
                        gcOrder.putIfAbsent(gcId, eventSequence);
                    } else if ("jdk.GCHeapSummary".equals(eventName)) {
                        final Long gcId = gcId(event, eventSequence);
                        final Long heap = heapBytes(event);
                        // GCHeapSummary emits both a Before-GC and an After-GC
                        // event.  Only the latter is the frozen post-GC
                        // observation; accepting the former would make a
                        // merely paired summary look like a complete cycle.
                        if (heap != null && isAfterGc(event)) {
                            afterGcHeap.put(gcId, heap);
                        }
                    }
                    eventSequence++;
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
        if (gcOutput != null) {
            writeGcSamples(gcOutput, gcOrder, afterGcHeap);
        }
        emit(complete, failureCode, readable, requiredEventsPresent, eventCount, eventFamilies,
                constructorReturned, failureStage, closeExecuted, reason);
    }

    private static Long gcId(final RecordedEvent event, final long fallback) {
        final Object raw = value(event, "gcId");
        if (raw instanceof Number number && number.longValue() >= 0L) {
            return number.longValue();
        }
        if (raw != null) {
            final String text = raw.toString();
            final int start = firstDigit(text);
            if (start >= 0) {
                try {
                    return Long.parseLong(text.substring(start));
                } catch (final NumberFormatException ignored) {
                    // The sequence fallback below remains deterministic.
                }
            }
        }
        return fallback;
    }

    private static Long heapBytes(final RecordedEvent event) {
        for (String field : List.of("heapUsed", "heapUsedAfterGc", "heapUsedAfterGC")) {
            final Long direct = nonNegative(value(event, field));
            if (direct != null) {
                return direct;
            }
        }
        final Object space = value(event, "heapSpace");
        if (space instanceof RecordedObject recordedSpace) {
            for (String field : List.of("heapUsed", "heapUsedAfterGc", "heapUsedAfterGC")) {
                final Long nested = nonNegative(value(recordedSpace, field));
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private static boolean isAfterGc(final RecordedEvent event) {
        final Object when = value(event, "when");
        return when != null && "After GC".equals(when.toString());
    }

    private static Object value(final RecordedObject event, final String field) {
        try {
            return event.getValue(field);
        } catch (final RuntimeException ignored) {
            return null;
        }
    }

    private static Long nonNegative(final Object value) {
        if (value instanceof Number number && number.longValue() >= 0L) {
            return number.longValue();
        }
        return null;
    }

    private static int firstDigit(final String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isDigit(value.charAt(index))) {
                return index;
            }
        }
        return -1;
    }

    private static void writeGcSamples(
            final Path output,
            final Map<Long, Long> gcOrder,
            final Map<Long, Long> afterGcHeap) {
        try {
            final Path normalized = output.toAbsolutePath().normalize();
            final Path parent = normalized.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            final StringBuilder text = new StringBuilder(
                    "gcId,sequence,afterGcHeapBytes\n");
            for (Map.Entry<Long, Long> entry : gcOrder.entrySet()) {
                final Long heap = afterGcHeap.get(entry.getKey());
                if (heap != null) {
                    text.append(entry.getKey()).append(',').append(entry.getValue()).append(',')
                            .append(heap).append('\n');
                }
            }
            Files.writeString(normalized, text.toString(), StandardCharsets.US_ASCII);
        } catch (final IOException ignored) {
            // The parent treats a missing/invalid extraction artifact as an
            // incomplete qualification observation; no human-readable output
            // is allowed to masquerade as a valid result.
        }
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
