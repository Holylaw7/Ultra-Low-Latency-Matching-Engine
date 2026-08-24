package com.ultralatency.matching.qualification;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Parent-side handle for one packaged release-candidate child process. */
public final class ReleaseCandidateQualificationProcess implements AutoCloseable {

    private final Process process;
    private final BufferedWriter control;
    private final BufferedReader output;
    private final int protocolPort;
    private final int managementPort;

    private ReleaseCandidateQualificationProcess(
            final Process process,
            final BufferedWriter control,
            final BufferedReader output,
            final int protocolPort,
            final int managementPort) {
        this.process = process;
        this.control = control;
        this.output = output;
        this.protocolPort = protocolPort;
        this.managementPort = managementPort;
    }

    /** Starts one child and waits for its bounded READY announcement. */
    public static ReleaseCandidateQualificationProcess start(
            final Path packagedArtifact,
            final Path configuration,
            final Duration startupTimeout) throws IOException {
        return start(packagedArtifact, configuration, null, startupTimeout);
    }

    /** Starts a child with an optional qualification-process evidence directory. */
    public static ReleaseCandidateQualificationProcess start(
            final Path packagedArtifact,
            final Path configuration,
            final Path evidenceDirectory,
            final Duration startupTimeout) throws IOException {
        Objects.requireNonNull(configuration, "configuration");
        final Duration timeout = requireTimeout(startupTimeout, "startupTimeout");
        final Process process = new ProcessBuilder(
                command(packagedArtifact, configuration, evidenceDirectory))
                .redirectErrorStream(true)
                .start();
        final BufferedReader output = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        final ExecutorService readerExecutor = Executors.newSingleThreadExecutor(runnable -> {
            final Thread thread = new Thread(runnable, "rc-qualification-ready-reader");
            thread.setDaemon(true);
            return thread;
        });
        try {
            final Future<String> readyFuture = readerExecutor.submit(output::readLine);
            final String ready = readyFuture.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            final int[] ports = parseReady(ready);
            return new ReleaseCandidateQualificationProcess(
                    process,
                    new BufferedWriter(new OutputStreamWriter(
                            process.getOutputStream(), StandardCharsets.UTF_8)),
                    output,
                    ports[0],
                    ports[1]);
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("interrupted while waiting for release-candidate child", exception);
        } catch (final ExecutionException | java.util.concurrent.TimeoutException
                | NumberFormatException exception) {
            process.destroyForcibly();
            throw new IOException("release-candidate child did not become ready", exception);
        } finally {
            readerExecutor.shutdownNow();
        }
    }

    /** @return Protocol listener port announced by the child */
    public int protocolPort() {
        return protocolPort;
    }

    /** @return loopback management listener port announced by the child */
    public int managementPort() {
        return managementPort;
    }

    /** Requests an orderly shutdown through the qualification-only control channel. */
    public int gracefulShutdown(final Duration timeout) throws IOException {
        control.write("SHUTDOWN");
        control.newLine();
        control.flush();
        return waitForExit(timeout);
    }

    /** Performs the explicitly approved post-response forced termination. */
    public int forceTerminate(final Duration timeout) throws IOException {
        process.destroyForcibly();
        return waitForExit(timeout);
    }

    /** @return whether the child is still running */
    public boolean isAlive() {
        return process.isAlive();
    }

    @Override
    public void close() {
        try {
            control.close();
        } catch (final IOException ignored) {
            // The process result remains authoritative.
        }
        try {
            output.close();
        } catch (final IOException ignored) {
            // The process result remains authoritative.
        }
        if (process.isAlive()) {
            process.destroyForcibly();
        }
    }

    private int waitForExit(final Duration timeout) throws IOException {
        final Duration bounded = requireTimeout(timeout, "processTimeout");
        try {
            if (!process.waitFor(bounded.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                if (!process.waitFor(bounded.toMillis(), TimeUnit.MILLISECONDS)) {
                    throw new IOException("release-candidate child did not exit within timeout");
                }
            }
            return process.exitValue();
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while waiting for release-candidate child", exception);
        }
    }

    private static String[] command(
            final Path artifact,
            final Path configuration,
            final Path evidenceDirectory) {
        final String javaExecutable = Path.of(
                System.getProperty("java.home"),
                "bin",
                System.getProperty("os.name", "").toLowerCase().contains("win")
                        ? "java.exe" : "java").toString();
        if (artifact != null) {
            final java.util.List<String> command = new java.util.ArrayList<>(java.util.List.of(
                javaExecutable,
                "-jar",
                artifact.toAbsolutePath().normalize().toString(),
                "child",
                "--config",
                configuration.toAbsolutePath().normalize().toString()));
            addEvidenceArgument(command, evidenceDirectory);
            return command.toArray(String[]::new);
        }
        final String classPath = System.getProperty(
                "surefire.test.class.path",
                System.getProperty("java.class.path"));
        final java.util.List<String> command = new java.util.ArrayList<>(java.util.List.of(
            javaExecutable,
            "-cp",
            classPath,
            ReleaseCandidateQualificationMain.class.getName(),
            "child",
            "--config",
            configuration.toAbsolutePath().normalize().toString()));
        addEvidenceArgument(command, evidenceDirectory);
        return command.toArray(String[]::new);
    }

    private static void addEvidenceArgument(
            final java.util.List<String> command,
            final Path evidenceDirectory) {
        if (evidenceDirectory != null) {
            command.add("--evidence");
            command.add(evidenceDirectory.toAbsolutePath().normalize().toString());
        }
    }

    private static int[] parseReady(final String line) throws IOException {
        if (line == null || !line.startsWith("READY ")) {
            throw new IOException("invalid release-candidate READY line: " + line);
        }
        final String[] parts = line.substring("READY ".length()).trim().split("\\s+");
        if (parts.length != 2) {
            throw new IOException("release-candidate READY must contain two ports");
        }
        final int protocol = Integer.parseInt(parts[0]);
        final int management = Integer.parseInt(parts[1]);
        if (protocol < 1 || protocol > 65_535 || management < 1 || management > 65_535) {
            throw new IOException("release-candidate READY contains invalid ports");
        }
        return new int[] {protocol, management};
    }

    private static Duration requireTimeout(final Duration timeout, final String name) {
        Objects.requireNonNull(timeout, name);
        if (timeout.isZero() || timeout.isNegative()
                || timeout.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException(name + " must be positive and bounded");
        }
        return timeout;
    }
}
