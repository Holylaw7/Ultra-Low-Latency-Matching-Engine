package com.ultralatency.matching.qualification.ga.observability;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Immutable JDK 21 JFR completeness/provenance observation. */
public record GaJfrEvidence(
        Path recording,
        boolean startedBeforeMeasurement,
        boolean stoppedBeforePublication,
        boolean structurallyReadable,
        boolean identityBound,
        Set<String> eventFamilies,
        boolean completeMeasurementInterval,
        String failureCode) {

    /** Required JDK 21 event families for TASK-052 G8. */
    public static final Set<String> REQUIRED_EVENT_FAMILIES = Set.of(
            "jdk.GarbageCollection", "jdk.GCHeapSummary", "jdk.CPULoad",
            "jdk.ResidentSetSize", "jdk.JavaThreadStatistics", "jdk.ThreadStart",
            "jdk.ThreadEnd", "jdk.ThreadAllocationStatistics", "jdk.ObjectAllocationSample");

    private static final String CHILD_CLASS =
            "com.ultralatency.matching.qualification.ga.observability.GaJfrChildInspector";
    private static final int CHILD_PROTOCOL_VERSION = 1;
    private static final int MAX_CHILD_OUTPUT_BYTES = 16 * 1024;
    private static final Duration CHILD_TIMEOUT = Duration.ofSeconds(30);
    private static final Set<String> CHILD_FIELDS = Set.of(
            "protocolVersion", "complete", "failureCode", "recordingReadable",
            "requiredEventsPresent", "eventCount", "eventFamilies", "constructorReturned",
            "failureStage", "closeExecuted", "reason", "runtimeVersion");

    /** Validates an immutable JFR observation. */
    public GaJfrEvidence {
        if (recording == null || eventFamilies == null || failureCode == null
                || failureCode.isBlank() || !Set.copyOf(eventFamilies).stream()
                .allMatch(value -> value != null && !value.isBlank())) {
            throw new IllegalArgumentException("JFR evidence fields must not be blank");
        }
        eventFamilies = Set.copyOf(eventFamilies);
    }

    /** Returns whether all required event families and boundaries are present. */
    public boolean complete() {
        return startedBeforeMeasurement && stoppedBeforePublication && structurallyReadable
                && identityBound && completeMeasurementInterval
                && eventFamilies.containsAll(REQUIRED_EVENT_FAMILIES);
    }

    /** Returns whether the recording was readable without requiring every formal event family. */
    public boolean readable() {
        return startedBeforeMeasurement && stoppedBeforePublication && structurallyReadable
                && identityBound && "NONE".equals(failureCode);
    }

    /** Returns a valid fixture observation. */
    public static GaJfrEvidence valid(final Path recording) {
        return new GaJfrEvidence(recording, true, true, true, true,
                REQUIRED_EVENT_FAMILIES, true, "NONE");
    }

    /** Reads one recording through the bounded Java 21 child inspector. */
    public static GaJfrEvidence inspect(
            final Path recording,
            final boolean startedBeforeMeasurement,
            final boolean stoppedBeforePublication,
            final boolean identityBound) {
        return inspectDetailed(recording, startedBeforeMeasurement, stoppedBeforePublication,
                identityBound).evidence();
    }

    /** Returns child protocol details for qualification-only lifecycle diagnostics. */
    static ChildInspection inspectDetailed(
            final Path recording,
            final boolean startedBeforeMeasurement,
            final boolean stoppedBeforePublication,
            final boolean identityBound) {
        if (recording == null) {
            throw new NullPointerException("recording");
        }
        final Path javaExecutable = javaExecutable();
        if (javaExecutable == null || !Files.isRegularFile(javaExecutable)) {
            return failure(recording, startedBeforeMeasurement, stoppedBeforePublication,
                    identityBound, "B3", "launcher", "JDK21_LAUNCHER_UNAVAILABLE");
        }
        final String classPath = System.getProperty("java.class.path", "");
        if (classPath.isBlank()) {
            return failure(recording, startedBeforeMeasurement, stoppedBeforePublication,
                    identityBound, "B3", "launcher", "CHILD_CLASSPATH_UNAVAILABLE");
        }
        final Process process;
        try {
            final ProcessBuilder builder = new ProcessBuilder(
                    javaExecutable.toString(), "-cp", classPath, CHILD_CLASS,
                    recording.toAbsolutePath().normalize().toString());
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);
            process = builder.start();
        } catch (final IOException | RuntimeException exception) {
            return failure(recording, startedBeforeMeasurement, stoppedBeforePublication,
                    identityBound, "B3", "launcher", "CHILD_LAUNCH_FAILED");
        }

        final boolean finished;
        try {
            finished = process.waitFor(CHILD_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (final InterruptedException exception) {
            terminate(process);
            Thread.currentThread().interrupt();
            return failure(recording, startedBeforeMeasurement, stoppedBeforePublication,
                    identityBound, "B3", "launcher", "CHILD_INTERRUPTED");
        }
        if (!finished) {
            terminate(process);
            return failure(recording, startedBeforeMeasurement, stoppedBeforePublication,
                    identityBound, "B3", "timeout", "CHILD_TIMEOUT");
        }

        final String output;
        try (InputStream stdout = process.getInputStream()) {
            output = readBounded(stdout);
        } catch (final IOException exception) {
            return failure(recording, startedBeforeMeasurement, stoppedBeforePublication,
                    identityBound, "B3", "launcher", "CHILD_OUTPUT_READ_FAILED");
        }
        return classifyChildExecution(recording, startedBeforeMeasurement, stoppedBeforePublication,
                identityBound, output, process.exitValue(), false);
    }

    /** Classifies a bounded child result; package-private for deterministic contract tests. */
    static ChildInspection classifyChildExecution(
            final Path recording,
            final boolean startedBeforeMeasurement,
            final boolean stoppedBeforePublication,
            final boolean identityBound,
            final String output,
            final int exitCode,
            final boolean timedOut) {
        if (timedOut) {
            return failure(recording, startedBeforeMeasurement, stoppedBeforePublication,
                    identityBound, "B3", "timeout", "CHILD_TIMEOUT");
        }
        if (output == null || output.isBlank()) {
            final String code = exitCode == 0 ? "B2" : "B3";
            final String stage = exitCode == 0 ? "protocol" : "crash";
            return failure(recording, startedBeforeMeasurement, stoppedBeforePublication,
                    identityBound, code, stage, "CHILD_OUTPUT_MISSING");
        }
        final ChildProtocol protocol;
        try {
            protocol = parseChildProtocol(output);
        } catch (final IllegalArgumentException exception) {
            return failure(recording, startedBeforeMeasurement, stoppedBeforePublication,
                    identityBound, "B2", "protocol", "CHILD_PROTOCOL_INVALID");
        }
        if (exitCode != 0) {
            return failure(recording, startedBeforeMeasurement, stoppedBeforePublication,
                    identityBound, "B3", "crash", "CHILD_NONZERO_WITH_PROTOCOL");
        }
        final String failureCode = protocol.failureCode();
        final boolean readable = protocol.recordingReadable() && "NONE".equals(failureCode);
        final boolean completeInterval = protocol.complete() && readable;
        return new ChildInspection(new GaJfrEvidence(recording, startedBeforeMeasurement,
                stoppedBeforePublication, readable, identityBound, protocol.eventFamilies(),
                completeInterval, failureCode), protocol.constructorReturned(),
                protocol.failureStage(), protocol.closeExecuted(), protocol.reason(),
                protocol.eventCount());
    }

    /** Parses the strict one-line child protocol; package-private for malformed-output tests. */
    static ChildProtocol parseChildProtocol(final String output) {
        if (output == null || output.length() > MAX_CHILD_OUTPUT_BYTES) {
            throw new IllegalArgumentException("child output is missing or oversized");
        }
        String line = output;
        if (line.endsWith("\r\n")) {
            line = line.substring(0, line.length() - 2);
        } else if (line.endsWith("\n")) {
            line = line.substring(0, line.length() - 1);
        }
        if (line.isEmpty() || line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("child protocol must contain one line");
        }
        final Map<String, String> fields = new LinkedHashMap<>();
        for (final String field : line.split("\\|", -1)) {
            final int separator = field.indexOf('=');
            if (separator <= 0) {
                throw new IllegalArgumentException("malformed child field");
            }
            final String key = field.substring(0, separator);
            final String value = field.substring(separator + 1);
            if (!CHILD_FIELDS.contains(key) || (value.isEmpty() && !"eventFamilies".equals(key))
                    || fields.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("unknown or duplicate child field");
            }
        }
        if (!fields.keySet().equals(CHILD_FIELDS)) {
            throw new IllegalArgumentException("child field set is incomplete");
        }
        final int protocolVersion = parseInt(fields, "protocolVersion");
        if (protocolVersion != CHILD_PROTOCOL_VERSION || !"21".equals(fields.get("runtimeVersion"))) {
            throw new IllegalArgumentException("unsupported child protocol/runtime");
        }
        final boolean complete = parseBoolean(fields, "complete");
        final String failureCode = fields.get("failureCode");
        if (!Set.of("NONE", "B2", "B3").contains(failureCode)) {
            throw new IllegalArgumentException("unknown child failure code");
        }
        final boolean readable = parseBoolean(fields, "recordingReadable");
        final boolean requiredEventsPresent = parseBoolean(fields, "requiredEventsPresent");
        final long eventCount = parseLong(fields, "eventCount");
        if (eventCount < 0L) {
            throw new IllegalArgumentException("negative child event count");
        }
        final Set<String> eventFamilies = parseEventFamilies(fields.get("eventFamilies"));
        if (requiredEventsPresent != eventFamilies.containsAll(REQUIRED_EVENT_FAMILIES)) {
            throw new IllegalArgumentException("child required-event summary mismatch");
        }
        final boolean constructorReturned = parseBoolean(fields, "constructorReturned");
        final String failureStage = fields.get("failureStage");
        if (!Set.of("none", "constructor", "readEvent").contains(failureStage)) {
            throw new IllegalArgumentException("unknown child failure stage");
        }
        final boolean closeExecuted = parseBoolean(fields, "closeExecuted");
        final String reason = fields.get("reason");
        if (!reason.matches("[A-Z0-9_]{1,64}")) {
            throw new IllegalArgumentException("invalid child reason");
        }
        if (complete != ("NONE".equals(failureCode) && readable && requiredEventsPresent)) {
            throw new IllegalArgumentException("child completion mismatch");
        }
        if (!constructorReturned && closeExecuted) {
            throw new IllegalArgumentException("constructor-failure close mismatch");
        }
        return new ChildProtocol(complete, failureCode, readable, requiredEventsPresent,
                eventCount, eventFamilies, constructorReturned, failureStage, closeExecuted, reason);
    }

    /** Returns the configured required event names in deterministic order. */
    public static Set<String> requiredEventFamilies() {
        return Set.copyOf(new LinkedHashSet<>(REQUIRED_EVENT_FAMILIES));
    }

    private static ChildInspection failure(
            final Path recording,
            final boolean startedBeforeMeasurement,
            final boolean stoppedBeforePublication,
            final boolean identityBound,
            final String failureCode,
            final String failureStage,
            final String reason) {
        return new ChildInspection(new GaJfrEvidence(recording, startedBeforeMeasurement,
                stoppedBeforePublication, false, identityBound, Collections.emptySet(), false,
                failureCode), false, failureStage, false, reason, 0L);
    }

    private static Path javaExecutable() {
        final String javaHome = System.getProperty("java.home", "");
        if (javaHome.isBlank()) {
            return null;
        }
        final boolean windows = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT).contains("win");
        return Path.of(javaHome, "bin", windows ? "java.exe" : "java");
    }

    private static void terminate(final Process process) {
        process.destroyForcibly();
        try {
            process.waitFor();
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static String readBounded(final InputStream stream) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final byte[] buffer = new byte[1024];
        int read;
        while ((read = stream.read(buffer)) >= 0) {
            if (output.size() + read > MAX_CHILD_OUTPUT_BYTES) {
                return null;
            }
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private static int parseInt(final Map<String, String> fields, final String name) {
        try {
            return Integer.parseInt(fields.get(name));
        } catch (final NumberFormatException exception) {
            throw new IllegalArgumentException("invalid integer field: " + name, exception);
        }
    }

    private static long parseLong(final Map<String, String> fields, final String name) {
        try {
            return Long.parseLong(fields.get(name));
        } catch (final NumberFormatException exception) {
            throw new IllegalArgumentException("invalid long field: " + name, exception);
        }
    }

    private static boolean parseBoolean(final Map<String, String> fields, final String name) {
        final String value = fields.get(name);
        if (!"true".equals(value) && !"false".equals(value)) {
            throw new IllegalArgumentException("invalid boolean field: " + name);
        }
        return Boolean.parseBoolean(value);
    }

    private static Set<String> parseEventFamilies(final String encoded) {
        if (encoded.isEmpty()) {
            return Set.of();
        }
        final List<String> values = new ArrayList<>(Arrays.asList(encoded.split(",", -1)));
        if (values.stream().anyMatch(value -> value.isBlank()
                || !REQUIRED_EVENT_FAMILIES.contains(value))) {
            throw new IllegalArgumentException("unknown event family");
        }
        if (values.size() != new LinkedHashSet<>(values).size()) {
            throw new IllegalArgumentException("duplicate event family");
        }
        return Set.copyOf(values);
    }

    /** Parsed strict child protocol. */
    record ChildProtocol(
            boolean complete,
            String failureCode,
            boolean recordingReadable,
            boolean requiredEventsPresent,
            long eventCount,
            Set<String> eventFamilies,
            boolean constructorReturned,
            String failureStage,
            boolean closeExecuted,
            String reason) {
    }

    /** Child protocol plus parent-side lifecycle details. */
    record ChildInspection(
            GaJfrEvidence evidence,
            boolean constructorReturned,
            String failureStage,
            boolean closeExecuted,
            String reason,
            long eventCount) {
    }
}
