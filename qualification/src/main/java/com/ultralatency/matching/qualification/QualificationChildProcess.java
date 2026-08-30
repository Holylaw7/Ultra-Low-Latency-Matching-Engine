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

/** Qualification-only child JVM wrapper used to prove a real process boundary. */
public final class QualificationChildProcess implements AutoCloseable {

    private final Process process;
    private final BufferedWriter control;
    private final BufferedReader output;
    private final java.net.InetSocketAddress address;
    private boolean forceTerminationObserved;

    private QualificationChildProcess(
            final Process child,
            final BufferedWriter controlWriter,
            final BufferedReader outputReader,
            final java.net.InetSocketAddress listener) {
        process = child;
        control = controlWriter;
        output = outputReader;
        address = listener;
    }

    /** Starts a child with an explicit legal WAL segment size and waits for READY. */
    public static QualificationChildProcess start(
            final Path walDirectory,
            final Path snapshotDirectory,
            final int walSegmentSize,
            final Duration startupTimeout) throws IOException {
        Objects.requireNonNull(walDirectory, "walDirectory");
        Objects.requireNonNull(snapshotDirectory, "snapshotDirectory");
        Objects.requireNonNull(startupTimeout, "startupTimeout");
        if (walSegmentSize < com.ultralatency.matching.persistence.wal.WalCommandCodec
                .MIN_SEGMENT_SIZE_BYTES) {
            throw new IllegalArgumentException("WAL segment size is below the supported minimum");
        }
        final String javaExecutable = Path.of(
                System.getProperty("java.home"),
                "bin",
                System.getProperty("os.name", "").toLowerCase().contains("win")
                        ? "java.exe" : "java").toString();
        final String classPath = System.getProperty(
                "surefire.test.class.path", System.getProperty("java.class.path"));
        final Process child = new ProcessBuilder(
                javaExecutable,
                "-cp",
                classPath,
                QualificationChildProcessMain.class.getName(),
                walDirectory.toAbsolutePath().toString(),
                snapshotDirectory.toAbsolutePath().toString(),
                "0",
                Integer.toString(walSegmentSize))
                .redirectErrorStream(true)
                .start();
        final BufferedReader childOutput = new BufferedReader(
                new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8));
        final ExecutorService readerExecutor = Executors.newSingleThreadExecutor(runnable -> {
            final Thread thread = new Thread(runnable, "qualification-child-ready-reader");
            thread.setDaemon(true);
            return thread;
        });
        try {
            final Future<String> readyFuture = readerExecutor.submit(childOutput::readLine);
            final String ready = readyFuture.get(startupTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (ready == null || !ready.startsWith("READY ")) {
                throw new IOException("child did not publish READY: " + ready);
            }
            final int port = Integer.parseInt(ready.substring("READY ".length()).trim());
            if (port <= 0 || port > 65_535) {
                throw new IOException("child published invalid listener port: " + port);
            }
            return new QualificationChildProcess(
                    child,
                    new BufferedWriter(new OutputStreamWriter(
                            child.getOutputStream(), StandardCharsets.UTF_8)),
                    childOutput,
                    new java.net.InetSocketAddress("127.0.0.1", port));
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            child.destroyForcibly();
            throw new IOException("interrupted while waiting for child", exception);
        } catch (final ExecutionException | java.util.concurrent.TimeoutException
                | NumberFormatException exception) {
            child.destroyForcibly();
            throw new IOException("child did not become ready", exception);
        } finally {
            readerExecutor.shutdownNow();
            if (!child.isAlive() && child.exitValue() != 0) {
                childOutput.close();
            }
        }
    }

    /** Returns the loopback Protocol v1 listener address. */
    public java.net.InetSocketAddress address() {
        return address;
    }

    /** Returns the operating-system process identifier. */
    public long pid() {
        return process.pid();
    }

    /** Requests the private graceful shutdown control path. */
    public void gracefulShutdown(final Duration timeout) throws IOException {
        control.write("SHUTDOWN");
        control.newLine();
        control.flush();
        waitForExit(timeout);
    }

    /** Forces process termination without invoking the graceful shutdown hook. */
    public void forceTerminate(final Duration timeout) throws IOException {
        forceTerminationObserved = true;
        process.destroyForcibly();
        waitForExit(timeout);
    }

    /** Returns whether this process was terminated through the forced path. */
    public boolean forceTerminationObserved() {
        return forceTerminationObserved;
    }

    /** Returns the child exit code after it has terminated. */
    public int exitCode() throws IOException {
        try {
            return process.exitValue();
        } catch (final IllegalThreadStateException exception) {
            throw new IOException("child process has not exited", exception);
        }
    }

    @Override
    public void close() {
        try {
            control.close();
        } catch (final IOException ignored) {
            // Preserve the lifecycle result.
        }
        try {
            output.close();
        } catch (final IOException ignored) {
            // Preserve the lifecycle result.
        }
        if (process.isAlive()) {
            process.destroyForcibly();
        }
    }

    private void waitForExit(final Duration timeout) throws IOException {
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    throw new IOException("child process did not exit within timeout");
                }
            }
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while waiting for child exit", exception);
        }
    }
}
