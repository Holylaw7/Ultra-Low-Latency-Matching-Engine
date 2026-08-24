package com.ultralatency.matching.qualification;

import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.persistence.wal.CommandWalReader;
import com.ultralatency.matching.persistence.wal.WalConfiguration;
import com.ultralatency.matching.recovery.online.RecoveryMode;
import com.ultralatency.matching.recovery.online.RecoveryPlanner;
import com.ultralatency.matching.recovery.online.RecoveryResult;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Runs the TASK-038 child-process restart and termination campaign.
 *
 * <p>The parent drives every command through Protocol v1. A cycle is only considered an
 * acknowledged boundary after the complete ordered response has been read. The forced mode
 * terminates the child only after that boundary, so it does not make an exactly-once claim about
 * an in-flight request.</p>
 */
public final class QualificationRestartCampaignRunner {

    /** Number of public exchanges retained for the final probe digest. */
    public static final int PUBLIC_PROBE_SUFFIX_LENGTH = 2;

    /** Runs one immutable restart/termination campaign. */
    public QualificationRestartCampaignResult run(
            final QualificationRestartCampaignConfiguration configuration) throws IOException {
        Objects.requireNonNull(configuration, "configuration");
        final QualificationConfiguration workloadConfiguration =
                configuration.workloadConfiguration();
        final QualificationWorkload workload = QualificationWorkloadV1.generate(
                workloadConfiguration);
        final Path artifactDirectory = createArtifactDirectory(
                workloadConfiguration.outputDirectory());
        final Path walDirectory = artifactDirectory.resolve("wal");
        final Path snapshotDirectory = artifactDirectory.resolve("snapshots");
        final WalConfiguration walConfiguration = WalConfiguration.defaults(walDirectory);
        final QualificationStreamingAccumulator campaignEvidence =
                new QualificationStreamingAccumulator(PUBLIC_PROBE_SUFFIX_LENGTH);
        final List<QualificationRestartCycle> cycles = new ArrayList<>();
        final int totalCycles = configuration.totalCycles();
        boolean success = true;
        for (int cycleIndex = 0; cycleIndex < totalCycles; cycleIndex++) {
            final int firstCommand = cycleIndex * workload.commands().size() / totalCycles;
            final int endCommand = (cycleIndex + 1) * workload.commands().size() / totalCycles;
            final QualificationRestartMode mode = cycleIndex
                    < configuration.gracefulRestartCycles()
                    ? QualificationRestartMode.GRACEFUL_RESTART
                    : QualificationRestartMode.FORCED_TERMINATION;
            final QualificationRestartCycle cycle = runCycle(
                    configuration,
                    workload.commands(),
                    firstCommand,
                    endCommand,
                    cycleIndex + 1,
                    mode,
                    walConfiguration,
                    snapshotDirectory,
                    campaignEvidence,
                    artifactDirectory);
            cycles.add(cycle);
            success &= cycle.convergencePassed();
        }

        final List<EngineCommand> persisted = CommandWalReader.read(walConfiguration);
        if (!persisted.equals(workload.commands())) {
            throw new IOException("final WAL command stream differs from workload");
        }
        final RecoveryResult recovered = RecoveryPlanner.create(
                walConfiguration, snapshotDirectory).recover(RecoveryMode.PURE_WAL);
        if (recovered.walEndSequence() != persisted.size()
                || recovered.nextCommandSequence() != persisted.size() + 1L) {
            throw new IOException("final recovery sequence did not converge");
        }
        final QualificationStreamingSummary stream = campaignEvidence.finish();
        final String walDigest = QualificationCanonicalizer.digest(persisted);
        final String checkpointDigest = recovered.checkpointDigestHex();
        final String transcriptDigest = stream.transcriptDigestHex();
        final QualificationResult result = new QualificationResult(
                success,
                persisted.size(),
                stream.responseCount(),
                stream.tradeCount(),
                checkpointDigest,
                transcriptDigest,
                stream.publicProbeDigestHex(),
                measurements(configuration, cycles, walDigest, checkpointDigest));
        if (result.success() != success) {
            throw new IOException("campaign success state did not converge");
        }
        final Path summary = artifactDirectory.resolve("qualification-campaign-summary-v1.txt");
        final String summaryText = summaryText(
                configuration, workload, cycles, result, walDigest, checkpointDigest);
        publishImmutable(summary, summaryText);
        publishArtifactHashes(artifactDirectory, summary, cycles);
        final String summarySha256 = QualificationArtifactHasher.sha256(summary);
        return new QualificationRestartCampaignResult(
                success, result, cycles, artifactDirectory, summarySha256);
    }

    private static QualificationRestartCycle runCycle(
            final QualificationRestartCampaignConfiguration configuration,
            final List<EngineCommand> commands,
            final int firstCommand,
            final int endCommand,
            final int cycleNumber,
            final QualificationRestartMode mode,
            final WalConfiguration walConfiguration,
            final Path snapshotDirectory,
            final QualificationStreamingAccumulator campaignEvidence,
            final Path artifactDirectory) throws IOException {
        final QualificationStreamingAccumulator cycleEvidence =
                new QualificationStreamingAccumulator(PUBLIC_PROBE_SUFFIX_LENGTH);
        ChildProcess child = null;
        ProtocolV1QualificationClient client = null;
        int exitCode = -1;
        boolean acknowledgedBoundary = false;
        try {
            child = ChildProcess.start(
                    walConfiguration.directory(),
                    snapshotDirectory,
                    configuration.startupTimeout());
            client = new ProtocolV1QualificationClient(
                    child.address(), configuration.workloadConfiguration().commandTimeout());
            for (int index = firstCommand; index < endCommand; index++) {
                final EngineCommand command = commands.get(index);
                final long requestId = index - firstCommand + 1L;
                final QualificationExchange exchange = client.exchange(command, requestId);
                campaignEvidence.accept(command, exchange);
                cycleEvidence.accept(command, exchange);
                acknowledgedBoundary = true;
            }
            final QualificationStreamingSummary cycleSummary = cycleEvidence.finish();
            if (mode == QualificationRestartMode.GRACEFUL_RESTART) {
                child.gracefulShutdown(configuration.processTimeout());
            } else {
                child.forceTerminate(configuration.processTimeout());
            }
            exitCode = child.exitCode();
            final List<EngineCommand> persisted = CommandWalReader.read(walConfiguration);
            final List<EngineCommand> expectedPrefix = commands.subList(0, endCommand);
            if (!persisted.equals(expectedPrefix)) {
                throw new IOException("cycle WAL prefix differs at cycle " + cycleNumber);
            }
            final RecoveryResult recovered = RecoveryPlanner.create(
                    walConfiguration, snapshotDirectory).recover(RecoveryMode.PURE_WAL);
            final boolean convergence = acknowledgedBoundary
                    && persisted.size() == endCommand
                    && recovered.walEndSequence() == endCommand
                    && recovered.nextCommandSequence() == endCommand + 1L;
            final Path cycleArtifact = artifactDirectory.resolve(
                    String.format("cycle-%02d.txt", cycleNumber));
            final String cycleText = cycleText(
                    cycleNumber,
                    mode,
                    firstCommand,
                    endCommand - firstCommand,
                    cycleSummary,
                    exitCode,
                    acknowledgedBoundary,
                    convergence,
                    recovered,
                    QualificationCanonicalizer.digest(persisted));
            publishImmutable(cycleArtifact, cycleText);
            return new QualificationRestartCycle(
                    cycleNumber,
                    mode,
                    firstCommand,
                    endCommand - firstCommand,
                    endCommand - firstCommand,
                    exitCode,
                    acknowledgedBoundary,
                    convergence,
                    recovered.walEndSequence(),
                    QualificationCanonicalizer.digest(persisted),
                    recovered.checkpointDigestHex(),
                    cycleSummary.transcriptDigestHex(),
                    QualificationArtifactHasher.sha256(cycleArtifact));
        } catch (final IOException | RuntimeException failure) {
            writeFailureArtifact(artifactDirectory, cycleNumber, mode, failure);
            throw failure;
        } finally {
            if (client != null) {
                client.close();
            }
            if (child != null) {
                child.close();
            }
        }
    }

    private static Path createArtifactDirectory(final Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        return Files.createDirectory(outputDirectory.resolve(
                "restart-campaign-" + java.util.UUID.randomUUID()));
    }

    private static java.util.Map<String, String> measurements(
            final QualificationRestartCampaignConfiguration configuration,
            final List<QualificationRestartCycle> cycles,
            final String walDigest,
            final String checkpointDigest) {
        final java.util.Map<String, String> values = new java.util.TreeMap<>();
        values.put("gracefulRestartCycles", Integer.toString(
                configuration.gracefulRestartCycles()));
        values.put("forcedTerminationCycles", Integer.toString(
                configuration.forcedTerminationCycles()));
        values.put("cycleCount", Integer.toString(cycles.size()));
        values.put("walCommandDigestHex", walDigest);
        values.put("checkpointDigestHex", checkpointDigest);
        values.put("ambiguousInFlightOutcomes", "0");
        values.put("claim.ambiguousOutcomesExactlyOnce", "NOT_CLAIMED");
        values.put("claim.hardwarePowerLoss", "NOT_CLAIMED");
        return java.util.Map.copyOf(values);
    }

    private static String cycleText(
            final int cycleNumber,
            final QualificationRestartMode mode,
            final int firstCommand,
            final int commandCount,
            final QualificationStreamingSummary summary,
            final int exitCode,
            final boolean acknowledgedBoundary,
            final boolean convergence,
            final RecoveryResult recovered,
            final String walDigest) {
        final StringBuilder text = new StringBuilder();
        text.append("schemaVersion=qualification-restart-cycle-v1\n");
        text.append("cycleNumber=").append(cycleNumber).append('\n');
        text.append("mode=").append(mode).append('\n');
        text.append("firstCommandIndex=").append(firstCommand).append('\n');
        text.append("commandCount=").append(commandCount).append('\n');
        text.append("acknowledgedCommands=").append(commandCount).append('\n');
        text.append("processExitCode=").append(exitCode).append('\n');
        text.append("acknowledgedBoundary=").append(acknowledgedBoundary).append('\n');
        text.append("convergencePassed=").append(convergence).append('\n');
        text.append("walEndSequence=").append(recovered.walEndSequence()).append('\n');
        text.append("nextCommandSequence=").append(recovered.nextCommandSequence()).append('\n');
        text.append("walCommandDigestHex=").append(walDigest).append('\n');
        text.append("checkpointDigestHex=").append(recovered.checkpointDigestHex()).append('\n');
        text.append("transcriptDigestHex=").append(summary.transcriptDigestHex()).append('\n');
        text.append("publicProbeDigestHex=").append(summary.publicProbeDigestHex()).append('\n');
        text.append("ambiguousInFlightOutcome=NOT_APPLICABLE_ACKNOWLEDGED_BOUNDARY\n");
        return text.toString();
    }

    private static String summaryText(
            final QualificationRestartCampaignConfiguration configuration,
            final QualificationWorkload workload,
            final List<QualificationRestartCycle> cycles,
            final QualificationResult result,
            final String walDigest,
            final String checkpointDigest) {
        final StringBuilder text = new StringBuilder();
        text.append("schemaVersion=qualification-restart-campaign-v1\n");
        text.append("workloadVersion=").append(workload.version()).append('\n');
        text.append("profile=").append(workload.profile()).append('\n');
        text.append("seed=").append(workload.seed()).append('\n');
        text.append("gracefulRestartCycles=")
                .append(configuration.gracefulRestartCycles()).append('\n');
        text.append("forcedTerminationCycles=")
                .append(configuration.forcedTerminationCycles()).append('\n');
        text.append("cycleCount=").append(cycles.size()).append('\n');
        text.append("acceptedCommands=").append(result.acceptedCommands()).append('\n');
        text.append("responseCount=").append(result.responseCount()).append('\n');
        text.append("tradeCount=").append(result.tradeCount()).append('\n');
        text.append("walCommandDigestHex=").append(walDigest).append('\n');
        text.append("checkpointDigestHex=").append(checkpointDigest).append('\n');
        text.append("transcriptDigestHex=").append(result.transcriptDigestHex()).append('\n');
        text.append("publicProbeDigestHex=").append(result.publicProbeDigestHex()).append('\n');
        text.append("campaign.result=").append(result.success()).append('\n');
        text.append("claim.ambiguousOutcomesExactlyOnce=NOT_CLAIMED\n");
        text.append("claim.hardwarePowerLoss=NOT_CLAIMED\n");
        text.append("java.version=")
                .append(System.getProperty("java.version", "unknown")).append('\n');
        text.append("java.vm.inputArguments=")
                .append(String.join(" ", java.lang.management.ManagementFactory
                        .getRuntimeMXBean().getInputArguments())).append('\n');
        text.append("os.name=")
                .append(System.getProperty("os.name", "unknown")).append('\n');
        text.append("os.arch=")
                .append(System.getProperty("os.arch", "unknown")).append('\n');
        for (final QualificationRestartCycle cycle : cycles) {
            final String prefix = String.format("cycle.%02d.", cycle.cycleNumber());
            text.append(prefix).append("mode=").append(cycle.mode()).append('\n');
            text.append(prefix).append("artifactPath=cycle-")
                    .append(String.format("%02d", cycle.cycleNumber())).append(".txt\n");
            text.append(prefix).append("artifactSha256=")
                    .append(cycle.artifactSha256()).append('\n');
            text.append(prefix).append("convergencePassed=")
                    .append(cycle.convergencePassed()).append('\n');
        }
        return text.toString();
    }

    private static void publishArtifactHashes(
            final Path artifactDirectory,
            final Path summary,
            final List<QualificationRestartCycle> cycles) throws IOException {
        final StringBuilder text = new StringBuilder();
        text.append("qualification-campaign-summary-v1.txt\t")
                .append(QualificationArtifactHasher.sha256(summary)).append('\n');
        for (final QualificationRestartCycle cycle : cycles) {
            text.append(String.format("cycle-%02d.txt", cycle.cycleNumber()))
                    .append('\t').append(cycle.artifactSha256()).append('\n');
        }
        publishImmutable(artifactDirectory.resolve("artifact-hashes-v1.txt"), text.toString());
    }

    private static void writeFailureArtifact(
            final Path artifactDirectory,
            final int cycleNumber,
            final QualificationRestartMode mode,
            final Throwable failure) {
        try {
            final Path failurePath = artifactDirectory.resolve(
                    String.format("cycle-%02d-failure.txt", cycleNumber));
            final String text = "schemaVersion=qualification-restart-cycle-failure-v1\n"
                    + "cycleNumber=" + cycleNumber + "\n"
                    + "mode=" + mode + "\n"
                    + "status=FAIL\n"
                    + "failureType=" + failure.getClass().getName() + "\n"
                    + "failureMessage=" + String.valueOf(failure.getMessage()) + "\n";
            publishImmutable(failurePath, text);
        } catch (final IOException ignored) {
            // Preserve the original campaign failure.
        }
    }

    private static void publishImmutable(final Path target, final String text) throws IOException {
        final Path absoluteTarget = target.toAbsolutePath().normalize();
        final Path parent = Objects.requireNonNull(absoluteTarget.getParent(), "target parent");
        Files.createDirectories(parent);
        if (Files.exists(absoluteTarget)) {
            throw new IOException("immutable restart evidence already exists: " + absoluteTarget);
        }
        final Path temporary = Files.createTempFile(parent, absoluteTarget.getFileName() + ".", ".tmp");
        boolean moved = false;
        try {
            final byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(
                    temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                final ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(temporary, absoluteTarget, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                moved = true;
            } catch (final AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic restart evidence publication is required", exception);
            }
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static final class ChildProcess implements AutoCloseable {

        private final Process process;
        private final BufferedWriter control;
        private final BufferedReader output;
        private final java.net.InetSocketAddress address;

        private ChildProcess(
                final Process process,
                final BufferedWriter control,
                final BufferedReader output,
                final java.net.InetSocketAddress address) {
            this.process = process;
            this.control = control;
            this.output = output;
            this.address = address;
        }

        static ChildProcess start(
                final Path walDirectory,
                final Path snapshotDirectory,
                final Duration startupTimeout) throws IOException {
            final String javaExecutable = Path.of(
                    System.getProperty("java.home"),
                    "bin",
                    System.getProperty("os.name", "").toLowerCase().contains("win")
                            ? "java.exe" : "java").toString();
            final String classPath = System.getProperty(
                    "surefire.test.class.path",
                    System.getProperty("java.class.path"));
            final Process process = new ProcessBuilder(
                    javaExecutable,
                    "-cp",
                    classPath,
                    QualificationChildProcessMain.class.getName(),
                    walDirectory.toAbsolutePath().toString(),
                    snapshotDirectory.toAbsolutePath().toString(),
                    "0")
                    .redirectErrorStream(true)
                    .start();
            final BufferedReader output = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            final ExecutorService readerExecutor = Executors.newSingleThreadExecutor(runnable -> {
                final Thread thread = new Thread(runnable, "qualification-child-ready-reader");
                thread.setDaemon(true);
                return thread;
            });
            try {
                final Future<String> readyFuture = readerExecutor.submit(output::readLine);
                final String ready = readyFuture.get(
                        startupTimeout.toMillis(), TimeUnit.MILLISECONDS);
                if (ready == null || !ready.startsWith("READY ")) {
                    throw new IOException("child did not publish READY: " + ready);
                }
                final int port = Integer.parseInt(ready.substring("READY ".length()).trim());
                if (port <= 0 || port > 65_535) {
                    throw new IOException("child published invalid listener port: " + port);
                }
                return new ChildProcess(
                        process,
                        new BufferedWriter(new OutputStreamWriter(
                                process.getOutputStream(), StandardCharsets.UTF_8)),
                        output,
                        new java.net.InetSocketAddress("127.0.0.1", port));
            } catch (final InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while waiting for child", exception);
            } catch (final ExecutionException | java.util.concurrent.TimeoutException
                    | NumberFormatException exception) {
                throw new IOException("child did not become ready", exception);
            } finally {
                readerExecutor.shutdownNow();
            }
        }

        java.net.InetSocketAddress address() {
            return address;
        }

        void gracefulShutdown(final Duration timeout) throws IOException {
            control.write("SHUTDOWN");
            control.newLine();
            control.flush();
            waitForExit(timeout);
        }

        void forceTerminate(final Duration timeout) throws IOException {
            process.destroyForcibly();
            waitForExit(timeout);
        }

        int exitCode() throws IOException {
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
                // The process lifecycle result remains authoritative.
            }
            try {
                output.close();
            } catch (final IOException ignored) {
                // The process lifecycle result remains authoritative.
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
}
