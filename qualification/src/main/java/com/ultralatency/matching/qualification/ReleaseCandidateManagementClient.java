package com.ultralatency.matching.qualification;

import com.ultralatency.matching.qualification.ga.observability.GaManagementEvidence;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/** Minimal bounded client for the Phase 10 loopback management protocol. */
public final class ReleaseCandidateManagementClient {

    private ReleaseCandidateManagementClient() {
    }

    /** Sends one management request and returns the complete UTF-8 JSON-line response. */
    public static String request(
            final int port,
            final String command,
            final Duration timeout) throws IOException {
        Objects.requireNonNull(command, "command");
        final Duration bounded = requireTimeout(timeout);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), millis(bounded));
            socket.setSoTimeout(millis(bounded));
            final OutputStream output = socket.getOutputStream();
            output.write((command + "\n").getBytes(StandardCharsets.US_ASCII));
            output.flush();
            final InputStream input = socket.getInputStream();
            final StringBuilder response = new StringBuilder(256);
            int value;
            while ((value = input.read()) >= 0) {
                if (value == '\n') {
                    break;
                }
                if (value > 0x7f) {
                    throw new IOException("management response is not UTF-8 ASCII JSON");
                }
                response.append((char) value);
                if (response.length() > 2_048) {
                    throw new IOException("management response exceeded bound");
                }
            }
            if (response.isEmpty()) {
                throw new IOException("management response was empty");
            }
            return response.toString();
        }
    }

    /** Requires the bounded READY response shape used by lifecycle evidence. */
    public static void requireReady(final String response) throws IOException {
        final GaManagementEvidence evidence;
        try {
            evidence = GaManagementEvidence.parse(response);
        } catch (final IllegalArgumentException failure) {
            throw new IOException("management READY response was malformed", failure);
        }
        if (evidence.kind() != GaManagementEvidence.Kind.READY
                || !Boolean.TRUE.equals(evidence.ready())) {
            throw new IOException("management READY response was not ready: " + response);
        }
    }

    /** Sends and strictly parses one existing public management endpoint response. */
    public static GaManagementEvidence requestEvidence(
            final int port,
            final String command,
            final Duration timeout) throws IOException {
        final String response = request(port, command, timeout);
        try {
            final GaManagementEvidence evidence = GaManagementEvidence.parse(response);
            if (!evidence.kind().name().equals(command)) {
                throw new IOException("management response kind does not match request");
            }
            return evidence;
        } catch (final IllegalArgumentException failure) {
            throw new IOException("management response failed strict schema validation", failure);
        }
    }

    private static Duration requireTimeout(final Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()
                || timeout.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException("management timeout is outside bounds");
        }
        return timeout;
    }

    private static int millis(final Duration timeout) {
        final long value = timeout.toMillis();
        if (value <= 0 || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("management timeout is outside socket bounds");
        }
        return (int) value;
    }
}
