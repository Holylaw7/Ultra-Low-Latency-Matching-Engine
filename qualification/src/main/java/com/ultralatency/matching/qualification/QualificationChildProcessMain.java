package com.ultralatency.matching.qualification;

import com.ultralatency.matching.network.netty.recovery.RecoverableDurableMatchingEngineTcpServer;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Qualification-only child JVM entry point for TASK-038.
 *
 * <p>The process exposes only the real Protocol v1 listener. Lifecycle control is a private
 * parent-to-child stdin channel and never enters the production runtime contract.</p>
 */
public final class QualificationChildProcessMain {

    private QualificationChildProcessMain() {
    }

    /** Starts one recoverable server and waits for an explicit graceful-stop command. */
    public static void main(final String[] arguments) {
        if (arguments.length != 3) {
            System.err.println("usage: <walDirectory> <snapshotDirectory> <port>");
            System.exit(64);
            return;
        }
        final Path walDirectory = Path.of(arguments[0]);
        final Path snapshotDirectory = Path.of(arguments[1]);
        final int port;
        try {
            port = Integer.parseInt(arguments[2]);
        } catch (final NumberFormatException exception) {
            System.err.println("invalid port: " + arguments[2]);
            System.exit(64);
            return;
        }
        final RecoverableDurableMatchingEngineTcpServer server =
                QualificationRunner.server(walDirectory, snapshotDirectory, port);
        try {
            server.start();
            final int boundPort = server.localAddress().orElseThrow().getPort();
            final PrintWriter output = new PrintWriter(
                    new java.io.OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
            output.println("READY " + boundPort);
            try (BufferedReader input = new BufferedReader(
                    new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                String command;
                while ((command = input.readLine()) != null) {
                    if ("SHUTDOWN".equals(command)) {
                        output.println("STOPPED " + server.shutdown());
                        return;
                    }
                    output.println("IGNORED " + command);
                }
                server.shutdown();
            }
        } catch (final Throwable failure) {
            try {
                server.shutdown();
            } catch (final Throwable ignored) {
                // Preserve the startup/runtime failure as the process result.
            }
            System.err.println("CHILD_FAILURE " + failure.getClass().getName()
                    + " " + String.valueOf(failure.getMessage()));
            System.exit(1);
        }
    }
}
