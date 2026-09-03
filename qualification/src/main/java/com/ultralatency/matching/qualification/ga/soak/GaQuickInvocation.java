package com.ultralatency.matching.qualification.ga.soak;

import com.ultralatency.matching.qualification.QualificationIdentity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Canonical, run-independent invocation identity for a packaged Quick execution. */
public final class GaQuickInvocation {

    /** Canonical invocation document version. */
    public static final String VERSION = "qualification-quick-invocation-v1";
    private static final String WINDOW_KEY = "protocolV2.window";
    private static final String WINDOW_CONFIGURATION_KEY =
            "qualification.configurationIdentitySha256";
    private static final Set<String> WINDOW_DERIVED_KEYS = Set.of(
            WINDOW_KEY, WINDOW_CONFIGURATION_KEY);
    private static final List<String> PACED_REQUIRED_KEYS = List.of(
            "controller.gitSha", "candidate.applicationJarSha256", "candidate.productionSha",
            "candidate.productionTreeSha256", "qualification.jarSha256",
            "qualification.entrypoint", "qualification.client", "qualification.scheduler",
            "qualification.precisionWindowNanos", "qualification.configurationIdentitySha256",
            "protocol.version", "protocol.singleSession", "protocol.singleProducer",
            WINDOW_KEY, "quick.version", "quick.profile", "quick.seed", "quick.duration",
            "quick.offeredRatePerSecond", "quick.nominalOfferOpportunities", "quick.acceptedFloor",
            "quick.sampleRateHz", "runtime.javaExecutable", "runtime.javaRuntimeVersion",
            "runtime.javaVmName", "runtime.javaVmVersion", "runtime.javaVmArguments",
            "runtime.gcCollectors", "runtime.heapMaxBytes", "runtime.osName", "runtime.osVersion",
            "runtime.osArch", "runtime.logicalProcessors", "runtime.walMode",
            "runtime.recoveryMode", "runtime.walSegmentSizeBytes", "runtime.pipelineCapacity",
            "runtime.pipelineWaitMode", "runtime.protocolWriteLowWaterMark",
            "runtime.protocolWriteHighWaterMark", "runtime.managementEnabled",
            "runtime.managementMaxConnections", "runtime.managementRequestTimeout",
            "runtime.shutdownTimeout", "runtime.portPolicy", "runtime.pathPolicy");

    private GaQuickInvocation() {
    }

    /** Returns a canonical SHA-256 identity over the supplied material fields. */
    public static String identity(final Map<String, String> fields) {
        return QualificationIdentity.digest(requireFields(fields));
    }

    /** Serializes invocation fields in deterministic key order. */
    public static String canonicalText(final Map<String, String> fields) {
        final Map<String, String> copy = new TreeMap<>(requireFields(fields));
        final StringBuilder text = new StringBuilder();
        copy.forEach((key, value) -> text.append(key).append('=').append(value).append('\n'));
        return text.toString();
    }

    /** Writes a canonical invocation document and returns its identity. */
    public static String write(final Path path, final Map<String, String> fields)
            throws IOException {
        Objects.requireNonNull(path, "path");
        final Map<String, String> copy = requireFields(fields);
        Files.writeString(path, canonicalText(copy), StandardCharsets.UTF_8);
        return identity(copy);
    }

    /** Reads and validates one canonical invocation document. */
    public static Map<String, String> read(final Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        final List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        final Map<String, String> values = new TreeMap<>();
        for (String line : lines) {
            if (line.isBlank()) {
                throw new IOException("invocation document contains a blank line");
            }
            final int separator = line.indexOf('=');
            if (separator <= 0) {
                throw new IOException("invocation document contains an invalid line");
            }
            final String key = line.substring(0, separator);
            final String value = line.substring(separator + 1);
            if (value.isBlank() || values.put(key, value) != null) {
                throw new IOException("invocation document contains a duplicate/blank value");
            }
        }
        final Map<String, String> canonical = requireFields(values);
        if (!canonicalText(canonical).equals(Files.readString(path, StandardCharsets.UTF_8))) {
            throw new IOException("invocation document is not canonical");
        }
        return Map.copyOf(canonical);
    }

    /** Returns whether all material invocation fields are equal except the configured window. */
    public static boolean onlyWindowVaries(final List<Map<String, String>> invocations) {
        Objects.requireNonNull(invocations, "invocations");
        if (invocations.isEmpty()) {
            return false;
        }
        final Map<String, String> first = requireFields(invocations.get(0));
        final String firstWindow = first.get(WINDOW_KEY);
        if (firstWindow == null) {
            return false;
        }
        boolean changed = false;
        for (Map<String, String> candidate : invocations) {
            final Map<String, String> current = requireFields(candidate);
            if (!current.keySet().equals(first.keySet())) {
                return false;
            }
            for (String key : first.keySet()) {
                if (WINDOW_DERIVED_KEYS.contains(key)) {
                    changed |= !Objects.equals(first.get(key), current.get(key));
                } else if (!Objects.equals(first.get(key), current.get(key))) {
                    return false;
                }
            }
            if (current.containsKey(WINDOW_CONFIGURATION_KEY)) {
                final int window;
                try {
                    window = Integer.parseInt(current.get(WINDOW_KEY));
                } catch (NumberFormatException exception) {
                    return false;
                }
                if (window < 1 || !GaSoakMatrix.quick().configurationIdentitySha256(window)
                        .equals(current.get(WINDOW_CONFIGURATION_KEY))) {
                    return false;
                }
            }
        }
        return changed;
    }

    private static Map<String, String> requireFields(final Map<String, String> fields) {
        Objects.requireNonNull(fields, "fields");
        final Map<String, String> copy = new TreeMap<>();
        fields.forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null || value.isBlank()) {
                throw new IllegalArgumentException("invocation fields must be non-blank");
            }
            if (copy.put(key, value) != null) {
                throw new IllegalArgumentException("duplicate invocation field");
            }
        });
        if (!VERSION.equals(copy.get("invocation.schema"))) {
            throw new IllegalArgumentException("unexpected invocation schema");
        }
        if (!copy.containsKey(WINDOW_KEY)) {
            throw new IllegalArgumentException("invocation must bind protocolV2.window");
        }
        return Map.copyOf(copy);
    }

    static Map<String, String> requireForEvidence(final Map<String, String> fields) {
        return requireFields(fields);
    }

    /** Validates the complete material invocation contract for a paced Quick artifact. */
    static Map<String, String> requireCompletePaced(final Map<String, String> fields) {
        final Map<String, String> copy = requireFields(fields);
        for (String key : PACED_REQUIRED_KEYS) {
            if (!copy.containsKey(key)) {
                throw new IllegalArgumentException("paced invocation is missing " + key);
            }
        }
        return copy;
    }
}
