package com.ultralatency.matching.operations;

import com.ultralatency.matching.app.RuntimeStatusSnapshot;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Strict bounded wire codec for the Phase 10 loopback management protocol. */
public final class ManagementProtocol {

    /** Maximum request size, including the terminating line-feed. */
    public static final int MAX_REQUEST_BYTES = 32;

    /** Maximum encoded response size. */
    public static final int MAX_RESPONSE_BYTES = 2_048;

    private static final byte LINE_FEED = (byte) '\n';

    private ManagementProtocol() {
    }

    /** Decodes one complete ASCII command line. */
    public static Request decode(final byte[] frame) {
        Objects.requireNonNull(frame, "frame");
        if (frame.length == 0 || frame.length > MAX_REQUEST_BYTES
                || frame[frame.length - 1] != LINE_FEED) {
            throw new IllegalArgumentException("Management request is not one bounded line");
        }
        for (int index = 0; index < frame.length - 1; index++) {
            final int value = frame[index] & 0xff;
            if (value > 0x7f || value == '\r' || value == '\n' || value < 0x20) {
                throw new IllegalArgumentException("Management request is not strict ASCII");
            }
        }
        final String command = new String(frame, 0, frame.length - 1, StandardCharsets.US_ASCII);
        try {
            return Request.valueOf(command);
        } catch (final IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown management request", exception);
        }
    }

    /** Encodes a bounded canonical JSON-line response. */
    public static byte[] encode(
            final Request request,
            final RuntimeStatusSnapshot status,
            final long managementRequests,
            final long managementRejected) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(status, "status");
        if (managementRequests < 0 || managementRejected < 0) {
            throw new IllegalArgumentException("Management counters must not be negative");
        }
        final String text = switch (request) {
            case LIVE -> "{\"schemaVersion\":1,\"live\":" + status.live() + "}\n";
            case READY -> "{\"schemaVersion\":1,\"ready\":" + status.ready() + "}\n";
            case STATUS -> statusJson(status, false, managementRequests, managementRejected);
            case METRICS -> statusJson(status, true, managementRequests, managementRejected);
        };
        final byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_RESPONSE_BYTES) {
            throw new IllegalStateException("Management response exceeds bounded size");
        }
        return bytes;
    }

    /** Encodes a safe invalid-request response and never includes input data. */
    public static byte[] invalidResponse() {
        return "{\"schemaVersion\":1,\"error\":\"INVALID_REQUEST\"}\n"
                .getBytes(StandardCharsets.UTF_8);
    }

    private static String statusJson(
            final RuntimeStatusSnapshot status,
            final boolean metrics,
            final long managementRequests,
            final long managementRejected) {
        final StringBuilder json = new StringBuilder(512);
        json.append("{\"schemaVersion\":").append(status.schemaVersion())
                .append(",\"state\":\"").append(status.state()).append("\"")
                .append(",\"live\":").append(status.live())
                .append(",\"ready\":").append(status.ready())
                .append(",\"failureCode\":\"").append(status.failureCode()).append("\"")
                .append(",\"protocolBound\":").append(status.protocolBound())
                .append(",\"recoveryMode\":\"").append(quote(status.recoveryMode()))
                .append("\"")
                .append(",\"acceptedCommands\":").append(status.acceptedCommands())
                .append(",\"terminalFailures\":").append(status.terminalFailures())
                .append(",\"uptimeMillis\":").append(status.uptimeMillis());
        if (metrics) {
            json.append(",\"managementRequests\":").append(managementRequests)
                    .append(",\"managementRejected\":").append(managementRejected);
        }
        return json.append("}\n").toString();
    }

    private static String quote(final String value) {
        final StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            final char character = value.charAt(index);
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }

    /** Supported one-request-per-connection commands. */
    public enum Request {
        /** Process/runtime liveness. */
        LIVE,
        /** Admission readiness. */
        READY,
        /** Full immutable status snapshot. */
        STATUS,
        /** Full status plus management counters. */
        METRICS
    }
}
