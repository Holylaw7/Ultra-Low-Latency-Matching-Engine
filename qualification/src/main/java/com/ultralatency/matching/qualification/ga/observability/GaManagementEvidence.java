package com.ultralatency.matching.qualification.ga.observability;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Strict representation of the existing public management schema. */
public record GaManagementEvidence(
        Kind kind,
        int schemaVersion,
        Boolean live,
        Boolean ready,
        String state,
        String failureCode,
        Boolean protocolBound,
        String recoveryMode,
        Long acceptedCommands,
        Long terminalFailures,
        Long uptimeMillis,
        Long managementRequests,
        Long managementRejected,
        boolean completeResponseBoundary) {

    /** Existing public management endpoint kinds. */
    public enum Kind {
        LIVE,
        READY,
        STATUS,
        METRICS
    }

    /** Version of the production public management JSON contract. */
    public static final int EXPECTED_SCHEMA_VERSION = 1;

    /** Validates endpoint-specific fields without inventing new counters. */
    public GaManagementEvidence {
        Objects.requireNonNull(kind, "kind");
        if (schemaVersion < 0) {
            throw new IllegalArgumentException("invalid management evidence");
        }
        if (acceptedCommands != null && acceptedCommands < 0
                || terminalFailures != null && terminalFailures < 0
                || uptimeMillis != null && uptimeMillis < 0
                || managementRequests != null && managementRequests < 0
                || managementRejected != null && managementRejected < 0) {
            throw new IllegalArgumentException("management counters must be non-negative");
        }
    }

    /** Creates a valid LIVE response observation. */
    public static GaManagementEvidence live(final int schemaVersion, final boolean live) {
        return new GaManagementEvidence(Kind.LIVE, schemaVersion, live, null, null, null,
                null, null, null, null, null, null, null, true);
    }

    /** Creates a valid READY response observation. */
    public static GaManagementEvidence ready(final int schemaVersion, final boolean ready) {
        return new GaManagementEvidence(Kind.READY, schemaVersion, null, ready, null, null,
                null, null, null, null, null, null, null, true);
    }

    /** Creates a valid STATUS response observation. */
    public static GaManagementEvidence status(
            final int schemaVersion,
            final boolean live,
            final boolean ready,
            final String state,
            final String failureCode,
            final boolean protocolBound,
            final String recoveryMode,
            final long acceptedCommands,
            final long terminalFailures,
            final long uptimeMillis) {
        return new GaManagementEvidence(Kind.STATUS, schemaVersion, live, ready, state,
                failureCode, protocolBound, recoveryMode, acceptedCommands, terminalFailures,
                uptimeMillis, null, null, true);
    }

    /** Creates a valid METRICS response observation. */
    public static GaManagementEvidence metrics(
            final int schemaVersion,
            final boolean live,
            final boolean ready,
            final String state,
            final String failureCode,
            final boolean protocolBound,
            final String recoveryMode,
            final long acceptedCommands,
            final long terminalFailures,
            final long uptimeMillis,
            final long managementRequests,
            final long managementRejected) {
        return new GaManagementEvidence(Kind.METRICS, schemaVersion, live, ready, state,
                failureCode, protocolBound, recoveryMode, acceptedCommands, terminalFailures,
                uptimeMillis, managementRequests, managementRejected, true);
    }

    /** Returns whether the endpoint contains exactly the fields required by its kind. */
    public boolean hasRequiredFields() {
        return schemaVersion == EXPECTED_SCHEMA_VERSION && switch (kind) {
            case LIVE -> live != null && ready == null && state == null && failureCode == null
                    && protocolBound == null && recoveryMode == null && acceptedCommands == null
                    && terminalFailures == null && uptimeMillis == null
                    && managementRequests == null && managementRejected == null;
            case READY -> ready != null && live == null && state == null && failureCode == null
                    && protocolBound == null && recoveryMode == null && acceptedCommands == null
                    && terminalFailures == null && uptimeMillis == null
                    && managementRequests == null && managementRejected == null;
            case STATUS -> statusFieldsPresent() && managementRequests == null
                    && managementRejected == null;
            case METRICS -> statusFieldsPresent() && managementRequests != null
                    && managementRejected != null;
        };
    }

    /** Returns whether all monotonic public counters in a pair do not regress. */
    public boolean nonRegressingFrom(final GaManagementEvidence previous) {
        Objects.requireNonNull(previous, "previous");
        if (schemaVersion != previous.schemaVersion || kind != previous.kind) {
            return false;
        }
        return nonRegressingCountersFrom(previous);
    }

    /** Returns whether every counter present in both endpoint observations does not regress. */
    public boolean nonRegressingCountersFrom(final GaManagementEvidence previous) {
        Objects.requireNonNull(previous, "previous");
        if (schemaVersion != previous.schemaVersion) {
            return false;
        }
        return nonRegressing(acceptedCommands, previous.acceptedCommands)
                && nonRegressing(terminalFailures, previous.terminalFailures)
                && nonRegressing(uptimeMillis, previous.uptimeMillis)
                && nonRegressing(managementRequests, previous.managementRequests)
                && nonRegressing(managementRejected, previous.managementRejected);
    }

    /** Returns whether this endpoint has a valid lifecycle state/value combination. */
    public boolean hasValidStateSemantics() {
        if (kind == Kind.LIVE || kind == Kind.READY) {
            return true;
        }
        if (!hasRequiredFields() || !Set.of(
                "NEW", "CONFIG_VALIDATED", "STARTING", "READY", "STOPPING", "STOPPED",
                "FAILED").contains(state)) {
            return false;
        }
        final boolean expectedLive = Set.of("STARTING", "READY", "STOPPING").contains(state);
        if (live != expectedLive) {
            return false;
        }
        if (ready != ("READY".equals(state))) {
            return false;
        }
        if (ready && (!Boolean.TRUE.equals(live) || !Boolean.TRUE.equals(protocolBound))) {
            return false;
        }
        if (!"NONE".equals(failureCode)
                && !Set.of("FAILED", "STOPPED").contains(state)) {
            return false;
        }
        if ("FAILED".equals(state) && "NONE".equals(failureCode)) {
            return false;
        }
        return !Set.of("FAILED", "STOPPED").contains(state)
                || !Boolean.TRUE.equals(protocolBound);
    }

    /** Returns whether this status can legally follow the previous status. */
    public boolean stateTransitionValidFrom(final GaManagementEvidence previous) {
        Objects.requireNonNull(previous, "previous");
        if (!hasValidStateSemantics() || !previous.hasValidStateSemantics()) {
            return false;
        }
        if (kind == Kind.LIVE || kind == Kind.READY
                || previous.kind == Kind.LIVE || previous.kind == Kind.READY) {
            return true;
        }
        return reachable(previous.state, state);
    }

    private static boolean reachable(final String from, final String to) {
        if (from.equals(to)) {
            return true;
        }
        return switch (from) {
            case "NEW" -> Set.of("CONFIG_VALIDATED", "STOPPED", "FAILED").contains(to);
            case "CONFIG_VALIDATED" -> Set.of("STARTING", "STOPPING", "FAILED").contains(to);
            case "STARTING" -> Set.of("READY", "STOPPING", "FAILED").contains(to);
            case "READY" -> Set.of("STOPPING", "FAILED").contains(to);
            case "STOPPING" -> Set.of("STOPPED", "FAILED").contains(to);
            case "FAILED" -> "STOPPED".equals(to);
            case "STOPPED" -> false;
            default -> false;
        };
    }

    private boolean statusFieldsPresent() {
        return live != null && ready != null && state != null && !state.isBlank()
                && failureCode != null && !failureCode.isBlank() && protocolBound != null
                && recoveryMode != null && !recoveryMode.isBlank()
                && acceptedCommands != null && terminalFailures != null && uptimeMillis != null;
    }

    private static boolean nonRegressing(final Long current, final Long previous) {
        return current == null || previous == null || current >= previous;
    }

    /** Returns only the allowed public field names for an endpoint. */
    public List<String> allowedFields() {
        return switch (kind) {
            case LIVE -> List.of("schemaVersion", "live");
            case READY -> List.of("schemaVersion", "ready");
            case STATUS -> List.of("schemaVersion", "state", "live", "ready", "failureCode",
                    "protocolBound", "recoveryMode", "acceptedCommands", "terminalFailures",
                    "uptimeMillis");
            case METRICS -> List.of("schemaVersion", "state", "live", "ready", "failureCode",
                    "protocolBound", "recoveryMode", "acceptedCommands", "terminalFailures",
                    "uptimeMillis", "managementRequests", "managementRejected");
        };
    }

    /**
     * Parses one complete JSON-line emitted by the existing management endpoint.
     *
     * <p>This deliberately supports only the flat, bounded public schema.  It does not
     * accept unknown fields or infer completion/error counters that are not part of the
     * production contract.</p>
     *
     * @param response UTF-8/ASCII JSON object, optionally terminated by one LF
     * @return strict management observation
     */
    public static GaManagementEvidence parse(final String response) {
        Objects.requireNonNull(response, "response");
        String text = response;
        if (text.endsWith("\n")) {
            text = text.substring(0, text.length() - 1);
        }
        if (text.isEmpty() || text.indexOf('\r') >= 0 || text.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("management response is not one JSON line");
        }
        final Map<String, Value> values = new JsonObjectParser(text).parse();
        if (!values.containsKey("schemaVersion")) {
            throw new IllegalArgumentException("management schemaVersion is missing");
        }
        final int schemaVersion = integer(values, "schemaVersion");
        final Kind kind;
        if (values.keySet().equals(Set.of("schemaVersion", "live"))) {
            kind = Kind.LIVE;
        } else if (values.keySet().equals(Set.of("schemaVersion", "ready"))) {
            kind = Kind.READY;
        } else {
            final Set<String> status = Set.of("schemaVersion", "state", "live", "ready",
                    "failureCode", "protocolBound", "recoveryMode", "acceptedCommands",
                    "terminalFailures", "uptimeMillis");
            final Set<String> metrics = Set.of("schemaVersion", "state", "live", "ready",
                    "failureCode", "protocolBound", "recoveryMode", "acceptedCommands",
                    "terminalFailures", "uptimeMillis", "managementRequests",
                    "managementRejected");
            if (values.keySet().equals(status)) {
                kind = Kind.STATUS;
            } else if (values.keySet().equals(metrics)) {
                kind = Kind.METRICS;
            } else {
                throw new IllegalArgumentException("unknown or incomplete management schema");
            }
        }
        final GaManagementEvidence result = switch (kind) {
            case LIVE -> live(schemaVersion, booleanValue(values, "live"));
            case READY -> ready(schemaVersion, booleanValue(values, "ready"));
            case STATUS -> status(schemaVersion, booleanValue(values, "live"),
                    booleanValue(values, "ready"), stringValue(values, "state"),
                    stringValue(values, "failureCode"), booleanValue(values, "protocolBound"),
                    stringValue(values, "recoveryMode"), longValue(values, "acceptedCommands"),
                    longValue(values, "terminalFailures"), longValue(values, "uptimeMillis"));
            case METRICS -> metrics(schemaVersion, booleanValue(values, "live"),
                    booleanValue(values, "ready"), stringValue(values, "state"),
                    stringValue(values, "failureCode"), booleanValue(values, "protocolBound"),
                    stringValue(values, "recoveryMode"), longValue(values, "acceptedCommands"),
                    longValue(values, "terminalFailures"), longValue(values, "uptimeMillis"),
                    longValue(values, "managementRequests"),
                    longValue(values, "managementRejected"));
        };
        if (!result.hasRequiredFields()) {
            throw new IllegalArgumentException("management response does not satisfy schema");
        }
        return result;
    }

    /** Alias used by evidence readers which name the operation after the wire boundary. */
    public static GaManagementEvidence parseResponse(final String response) {
        return parse(response);
    }

    private static int integer(final Map<String, Value> values, final String key) {
        final long number = longValue(values, key);
        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("management integer is outside bounds: " + key);
        }
        return (int) number;
    }

    private static long longValue(final Map<String, Value> values, final String key) {
        final Value value = requireValue(values, key);
        if (value.kind() != ValueKind.NUMBER || !value.text().matches("0|[1-9][0-9]*")) {
            throw new IllegalArgumentException("management field is not an unsigned integer: " + key);
        }
        try {
            return Long.parseLong(value.text());
        } catch (final NumberFormatException exception) {
            throw new IllegalArgumentException("management integer is too large: " + key,
                    exception);
        }
    }

    private static boolean booleanValue(final Map<String, Value> values, final String key) {
        final Value value = requireValue(values, key);
        if (value.kind() != ValueKind.BOOLEAN) {
            throw new IllegalArgumentException("management field is not boolean: " + key);
        }
        return Boolean.parseBoolean(value.text());
    }

    private static String stringValue(final Map<String, Value> values, final String key) {
        final Value value = requireValue(values, key);
        if (value.kind() != ValueKind.STRING || value.text().isBlank()) {
            throw new IllegalArgumentException("management field is not non-blank text: " + key);
        }
        return value.text();
    }

    private static Value requireValue(final Map<String, Value> values, final String key) {
        final Value value = values.get(key);
        if (value == null) {
            throw new IllegalArgumentException("management field is missing: " + key);
        }
        return value;
    }

    private enum ValueKind {
        NUMBER,
        BOOLEAN,
        STRING
    }

    private record Value(ValueKind kind, String text) {
    }

    /** Minimal strict parser for the bounded flat management object. */
    private static final class JsonObjectParser {

        private final String input;
        private int position;

        private JsonObjectParser(final String inputValue) {
            input = inputValue;
        }

        private Map<String, Value> parse() {
            skipWhitespace();
            expect('{');
            final Map<String, Value> result = new TreeMap<>();
            skipWhitespace();
            if (peek('}')) {
                position++;
                finish();
                return result;
            }
            while (true) {
                skipWhitespace();
                final String key = parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                final Value value = parseValue();
                if (result.put(key, value) != null) {
                    throw new IllegalArgumentException("duplicate management field: " + key);
                }
                skipWhitespace();
                if (peek('}')) {
                    position++;
                    finish();
                    return Map.copyOf(result);
                }
                expect(',');
            }
        }

        private Value parseValue() {
            if (peek('"')) {
                return new Value(ValueKind.STRING, parseString());
            }
            if (input.startsWith("true", position)) {
                position += 4;
                return new Value(ValueKind.BOOLEAN, "true");
            }
            if (input.startsWith("false", position)) {
                position += 5;
                return new Value(ValueKind.BOOLEAN, "false");
            }
            final int start = position;
            while (position < input.length() && Character.isDigit(input.charAt(position))) {
                position++;
            }
            if (start == position) {
                throw new IllegalArgumentException("unsupported management JSON value");
            }
            return new Value(ValueKind.NUMBER, input.substring(start, position));
        }

        private String parseString() {
            expect('"');
            final StringBuilder result = new StringBuilder();
            while (position < input.length()) {
                final char value = input.charAt(position++);
                if (value == '"') {
                    return result.toString();
                }
                if (value == '\\') {
                    if (position >= input.length()) {
                        throw new IllegalArgumentException("unterminated management string");
                    }
                    final char escaped = input.charAt(position++);
                    switch (escaped) {
                        case '"', '\\', '/' -> result.append(escaped);
                        case 'b' -> result.append('\b');
                        case 'f' -> result.append('\f');
                        case 'n' -> result.append('\n');
                        case 'r' -> result.append('\r');
                        case 't' -> result.append('\t');
                        case 'u' -> result.append(parseUnicode());
                        default -> throw new IllegalArgumentException(
                                "unsupported management escape");
                    }
                } else if (value < 0x20 || value > 0x7f) {
                    throw new IllegalArgumentException("management string is not ASCII");
                } else {
                    result.append(value);
                }
            }
            throw new IllegalArgumentException("unterminated management string");
        }

        private char parseUnicode() {
            if (position + 4 > input.length()) {
                throw new IllegalArgumentException("short management unicode escape");
            }
            final String hex = input.substring(position, position + 4);
            if (!hex.matches("[0-9a-fA-F]{4}")) {
                throw new IllegalArgumentException("invalid management unicode escape");
            }
            position += 4;
            return (char) Integer.parseInt(hex, 16);
        }

        private void expect(final char expected) {
            if (position >= input.length() || input.charAt(position) != expected) {
                throw new IllegalArgumentException("malformed management JSON");
            }
            position++;
        }

        private boolean peek(final char expected) {
            return position < input.length() && input.charAt(position) == expected;
        }

        private void skipWhitespace() {
            while (position < input.length() && Character.isWhitespace(input.charAt(position))) {
                position++;
            }
        }

        private void finish() {
            skipWhitespace();
            if (position != input.length()) {
                throw new IllegalArgumentException("trailing management JSON data");
            }
        }
    }
}
