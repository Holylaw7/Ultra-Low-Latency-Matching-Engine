package com.ultralatency.matching.qualification;

import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.persistence.snapshot.OfflineSnapshotGenerator;
import com.ultralatency.matching.persistence.snapshot.RecoveryLease;
import com.ultralatency.matching.persistence.snapshot.SnapshotStore;
import com.ultralatency.matching.persistence.wal.CommandWalReader;
import com.ultralatency.matching.persistence.wal.CommandWalWriter;
import com.ultralatency.matching.persistence.wal.WalConfiguration;
import com.ultralatency.matching.recovery.online.RecoveryMode;
import com.ultralatency.matching.recovery.online.RecoveryPlanner;
import com.ultralatency.matching.recovery.online.RecoveryResult;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;

/** Runs the bounded pre-campaign lifecycle matrix through the packaged runtime boundary. */
public final class ReleaseCandidateLifecycleRunner {

    private static final QualificationConfiguration WORKLOAD =
            new QualificationConfiguration(
                    QualificationProfile.MEMORY_STEADY_STATE_V1,
                    20260823L,
                    64,
                    Duration.ofSeconds(5),
                    Path.of("qualification-results"));

    /** Runs empty, Snapshot-tail and approved forced-termination lifecycle samples. */
    public ReleaseCandidateLifecycleResult run(
            final ReleaseCandidateLifecycleConfiguration configuration) throws IOException {
        Objects.requireNonNull(configuration, "configuration");
        final Path artifactDirectory = configuration.outputDirectory().resolve(
                "rc-lifecycle-" + UUID.randomUUID());
        Files.createDirectories(artifactDirectory);
        final List<ReleaseCandidateLifecycleResult.Cycle> cycles = new ArrayList<>();
        for (int index = 1; index <= configuration.emptyPureWalStarts(); index++) {
            cycles.add(runEmptyCycle(configuration, artifactDirectory, index));
        }
        for (int index = 1; index <= configuration.snapshotTailStarts(); index++) {
            cycles.add(runSnapshotTailCycle(configuration, artifactDirectory, index));
        }
        cycles.addAll(runForcedCycles(configuration, artifactDirectory));
        final boolean success = cycles.stream().allMatch(ReleaseCandidateLifecycleRunner::cyclePassed);
        final Path summary = artifactDirectory.resolve("rc-lifecycle-summary-v1.txt");
        publishImmutable(summary, summaryText(configuration, cycles, success));
        final Path hashes = artifactDirectory.resolve("artifact-hashes-v1.txt");
        publishImmutable(hashes, hashesText(summary, artifactDirectory, cycles));
        return new ReleaseCandidateLifecycleResult(
                success,
                cycles,
                artifactDirectory,
                QualificationArtifactHasher.sha256(summary));
    }

    private ReleaseCandidateLifecycleResult.Cycle runEmptyCycle(
            final ReleaseCandidateLifecycleConfiguration configuration,
            final Path artifactDirectory,
            final int cycleNumber) throws IOException {
        final Path cycleDirectory = artifactDirectory.resolve(
                String.format("empty-pure-wal-%02d", cycleNumber));
        Files.createDirectories(cycleDirectory);
        final Path walDirectory = cycleDirectory.resolve("wal");
        final Path snapshotDirectory = cycleDirectory.resolve("snapshots");
        final Path configFile = writeConfiguration(
                cycleDirectory, walDirectory, snapshotDirectory, RecoveryMode.PURE_WAL);
        final String configDigest = QualificationArtifactHasher.sha256(configFile);
        boolean ready = false;
        boolean command = false;
        boolean recovery = false;
        boolean lease = false;
        boolean temporaryFiles = false;
        int exitCode = -1;
        try (ReleaseCandidateQualificationProcess child =
                ReleaseCandidateQualificationProcess.start(
                        configuration.packagedArtifact(), configFile,
                        configuration.startupTimeout())) {
            ready = ready(child, configuration.commandTimeout());
            command = exchange(child, 0, 1, configuration.commandTimeout());
            exitCode = child.gracefulShutdown(configuration.processTimeout());
            recovery = recoveryConvergedEventually(
                    walDirectory, snapshotDirectory, RecoveryMode.PURE_WAL,
                    configuration.processTimeout());
            lease = leaseReacquiredEventually(
                    walDirectory, configuration.processTimeout());
            temporaryFiles = noTemporaryFiles(cycleDirectory);
        }
        return publishCycle(
                artifactDirectory,
                "EMPTY_PURE_WAL",
                cycleNumber,
                false,
                exitCode,
                ready,
                command,
                recovery,
                lease,
                temporaryFiles,
                configDigest);
    }

    private ReleaseCandidateLifecycleResult.Cycle runSnapshotTailCycle(
            final ReleaseCandidateLifecycleConfiguration configuration,
            final Path artifactDirectory,
            final int cycleNumber) throws IOException {
        final Path cycleDirectory = artifactDirectory.resolve(
                String.format("snapshot-tail-%02d", cycleNumber));
        Files.createDirectories(cycleDirectory);
        final Path walDirectory = cycleDirectory.resolve("wal");
        final Path snapshotDirectory = cycleDirectory.resolve("snapshots");
        prepareSnapshotTail(walDirectory, snapshotDirectory);
        final Path configFile = writeConfiguration(
                cycleDirectory, walDirectory, snapshotDirectory, RecoveryMode.SNAPSHOT_THEN_WAL);
        final String configDigest = QualificationArtifactHasher.sha256(configFile);
        boolean ready = false;
        boolean command = false;
        boolean recovery = false;
        boolean lease = false;
        boolean temporaryFiles = false;
        int exitCode = -1;
        try (ReleaseCandidateQualificationProcess child =
                ReleaseCandidateQualificationProcess.start(
                        configuration.packagedArtifact(), configFile,
                        configuration.startupTimeout())) {
            ready = ready(child, configuration.commandTimeout());
            command = exchange(child, 2, 1, configuration.commandTimeout());
            exitCode = child.gracefulShutdown(configuration.processTimeout());
            recovery = recoveryConverged(
                    walDirectory, snapshotDirectory, RecoveryMode.SNAPSHOT_THEN_WAL);
            lease = leaseReacquired(walDirectory);
            temporaryFiles = noTemporaryFiles(cycleDirectory);
        }
        return publishCycle(
                artifactDirectory,
                "SNAPSHOT_THEN_WAL",
                cycleNumber,
                false,
                exitCode,
                ready,
                command,
                recovery,
                lease,
                temporaryFiles,
                configDigest);
    }

    private List<ReleaseCandidateLifecycleResult.Cycle> runForcedCycles(
            final ReleaseCandidateLifecycleConfiguration configuration,
            final Path artifactDirectory) throws IOException {
        final Path scenarioDirectory = artifactDirectory.resolve("forced-termination");
        Files.createDirectories(scenarioDirectory);
        final Path walDirectory = scenarioDirectory.resolve("wal");
        final Path snapshotDirectory = scenarioDirectory.resolve("snapshots");
        final List<ReleaseCandidateLifecycleResult.Cycle> cycles = new ArrayList<>();
        for (int cycleNumber = 1;
                cycleNumber <= configuration.forcedTerminationCycles();
                cycleNumber++) {
            final Path configFile = writeConfiguration(
                    scenarioDirectory, walDirectory, snapshotDirectory, RecoveryMode.PURE_WAL);
            final String configDigest = QualificationArtifactHasher.sha256(configFile);
            boolean ready = false;
            boolean command = false;
            boolean recovery = false;
            boolean lease = false;
            boolean temporaryFiles = false;
            int exitCode = -1;
            try (ReleaseCandidateQualificationProcess child =
                    ReleaseCandidateQualificationProcess.start(
                            configuration.packagedArtifact(), configFile,
                            configuration.startupTimeout())) {
                ready = ready(child, configuration.commandTimeout());
                command = exchange(
                        child, cycleNumber - 1, 1, configuration.commandTimeout());
                exitCode = child.forceTerminate(configuration.processTimeout());
                recovery = recoveryConvergedEventually(
                        walDirectory, snapshotDirectory, RecoveryMode.PURE_WAL,
                        configuration.processTimeout());
                lease = leaseReacquiredEventually(
                        walDirectory, configuration.processTimeout());
                temporaryFiles = noTemporaryFiles(scenarioDirectory);
            }
            cycles.add(publishCycle(
                    artifactDirectory,
                    "FORCED_TERMINATION",
                    cycleNumber,
                    true,
                    exitCode,
                    ready,
                    command,
                    recovery,
                    lease,
                    temporaryFiles,
                    configDigest));
        }
        return cycles;
    }

    private static boolean ready(
            final ReleaseCandidateQualificationProcess child,
            final Duration timeout) throws IOException {
        final String response = ReleaseCandidateManagementClient.request(
                child.managementPort(), "READY", timeout);
        ReleaseCandidateManagementClient.requireReady(response);
        return true;
    }

    private static boolean exchange(
            final ReleaseCandidateQualificationProcess child,
            final long workloadIndex,
            final long requestId,
            final Duration timeout) throws IOException {
        final EngineCommand command = QualificationWorkloadV1.commandAtForRun(
                WORKLOAD, workloadIndex);
        try (ProtocolV1QualificationClient client = new ProtocolV1QualificationClient(
                new java.net.InetSocketAddress("127.0.0.1", child.protocolPort()), timeout)) {
            client.exchange(command, requestId);
            return true;
        }
    }

    private static void prepareSnapshotTail(
            final Path walDirectory,
            final Path snapshotDirectory) throws IOException {
        final WalConfiguration wal = WalConfiguration.defaults(walDirectory);
        final EngineCommand prefix = QualificationWorkloadV1.commandAtForRun(WORKLOAD, 0);
        final EngineCommand tail = QualificationWorkloadV1.commandAtForRun(WORKLOAD, 1);
        try (CommandWalWriter writer = CommandWalWriter.open(wal)) {
            writer.append(prefix);
        }
        new OfflineSnapshotGenerator(wal, new SnapshotStore(snapshotDirectory)).generate();
        try (CommandWalWriter writer = CommandWalWriter.open(wal)) {
            writer.append(tail);
        }
    }

    private static boolean recoveryConverged(
            final Path walDirectory,
            final Path snapshotDirectory,
            final RecoveryMode mode) throws IOException {
        final WalConfiguration wal = WalConfiguration.defaults(walDirectory);
        final List<EngineCommand> commands = CommandWalReader.read(wal);
        final RecoveryResult result = RecoveryPlanner.create(wal, snapshotDirectory).recover(mode);
        return result.walEndSequence() == commands.size()
                && result.nextCommandSequence() == commands.size() + 1L;
    }

    private static boolean recoveryConvergedEventually(
            final Path walDirectory,
            final Path snapshotDirectory,
            final RecoveryMode mode,
            final Duration timeout) throws IOException {
        final long deadline = System.nanoTime() + timeout.toNanos();
        IOException lastFailure = null;
        while (System.nanoTime() < deadline) {
            try {
                return recoveryConverged(walDirectory, snapshotDirectory, mode);
            } catch (final IOException failure) {
                lastFailure = failure;
                LockSupport.parkNanos(Duration.ofMillis(25).toNanos());
            }
        }
        throw new IOException("WAL resources did not become readable after child exit", lastFailure);
    }

    private static boolean leaseReacquired(final Path walDirectory) throws IOException {
        try (RecoveryLease lease = RecoveryLease.acquire(walDirectory)) {
            return lease.isHeld();
        }
    }

    private static boolean leaseReacquiredEventually(
            final Path walDirectory,
            final Duration timeout) throws IOException {
        final long deadline = System.nanoTime() + timeout.toNanos();
        IOException lastFailure = null;
        while (System.nanoTime() < deadline) {
            try {
                return leaseReacquired(walDirectory);
            } catch (final IOException failure) {
                lastFailure = failure;
                LockSupport.parkNanos(Duration.ofMillis(25).toNanos());
            }
        }
        throw new IOException("recovery lease did not become available after child exit", lastFailure);
    }

    private static boolean noTemporaryFiles(final Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            return paths.noneMatch(path -> path.getFileName().toString().endsWith(".tmp"));
        }
    }

    private static Path writeConfiguration(
            final Path directory,
            final Path walDirectory,
            final Path snapshotDirectory,
            final RecoveryMode mode) throws IOException {
        Files.createDirectories(directory);
        final int protocolPort = freePort();
        int managementPort = freePort();
        while (managementPort == protocolPort) {
            managementPort = freePort();
        }
        final String text = "storage.wal.directory=wal\n"
                + "storage.snapshot.directory=snapshots\n"
                + "recovery.mode=" + mode + "\n"
                + "wal.segment.size.bytes=65536\n"
                + "wal.durability.mode=SYNC_EACH_APPEND\n"
                + "pipeline.capacity=1024\n"
                + "pipeline.wait.mode=BLOCKING\n"
                + "protocol.bind.address=127.0.0.1\n"
                + "protocol.port=" + protocolPort + "\n"
                + "protocol.write.low.bytes=8192\n"
                + "protocol.write.high.bytes=16384\n"
                + "management.enabled=true\n"
                + "management.bind.address=127.0.0.1\n"
                + "management.port=" + managementPort + "\n"
                + "management.max.connections=16\n"
                + "management.request.timeout.ms=1000\n"
                + "lifecycle.shutdown.timeout.ms=2000\n";
        final Path target = directory.resolve("runtime.properties");
        if (Files.exists(target)) {
            Files.delete(target);
        }
        Files.writeString(target, text, StandardCharsets.UTF_8);
        if (!walDirectory.getParent().equals(directory)
                || !snapshotDirectory.getParent().equals(directory)) {
            throw new IOException("lifecycle storage paths escaped cycle directory");
        }
        return target;
    }

    private static ReleaseCandidateLifecycleResult.Cycle publishCycle(
            final Path artifactDirectory,
            final String scenario,
            final int cycleNumber,
            final boolean forced,
            final int exitCode,
            final boolean ready,
            final boolean command,
            final boolean recovery,
            final boolean lease,
            final boolean temporaryFiles,
            final String configDigest) throws IOException {
        final String name = scenario.toLowerCase() + "-" + String.format("%02d", cycleNumber);
        final Path artifact = artifactDirectory.resolve(name + ".txt");
        final String text = "schemaVersion=phase10-rc-lifecycle-v1\n"
                + "scenario=" + scenario + "\n"
                + "cycleNumber=" + cycleNumber + "\n"
                + "forcedTermination=" + forced + "\n"
                + "processExitCode=" + exitCode + "\n"
                + "ready=" + ready + "\n"
                + "commandRoundTrip=" + command + "\n"
                + "recoveryConverged=" + recovery + "\n"
                + "leaseReacquired=" + lease + "\n"
                + "temporaryFilesClear=" + temporaryFiles + "\n"
                + "configurationSha256=" + configDigest + "\n";
        publishImmutable(artifact, text);
        return new ReleaseCandidateLifecycleResult.Cycle(
                scenario,
                cycleNumber,
                forced,
                exitCode,
                ready,
                command,
                recovery,
                lease,
                temporaryFiles,
                QualificationArtifactHasher.sha256(artifact));
    }

    private static boolean cyclePassed(final ReleaseCandidateLifecycleResult.Cycle cycle) {
        return cycle.ready() && cycle.commandRoundTrip() && cycle.recoveryConverged()
                && cycle.leaseReacquired() && cycle.temporaryFilesClear()
                && (cycle.forcedTermination() || cycle.processExitCode() == 0);
    }

    private static String summaryText(
            final ReleaseCandidateLifecycleConfiguration configuration,
            final List<ReleaseCandidateLifecycleResult.Cycle> cycles,
            final boolean success) {
        final StringBuilder text = new StringBuilder();
        text.append("schemaVersion=phase10-rc-lifecycle-summary-v1\n")
                .append("success=").append(success).append('\n')
                .append("emptyPureWalStarts=").append(configuration.emptyPureWalStarts()).append('\n')
                .append("snapshotTailStarts=").append(configuration.snapshotTailStarts()).append('\n')
                .append("forcedTerminationCycles=").append(configuration.forcedTerminationCycles())
                .append('\n');
        for (final ReleaseCandidateLifecycleResult.Cycle cycle : cycles) {
            text.append("cycle=").append(cycle.scenario()).append('#')
                    .append(cycle.cycleNumber()).append('\t')
                    .append(cycle.artifactSha256()).append('\t')
                    .append("passed=").append(cyclePassed(cycle)).append('\n');
        }
        return text.toString();
    }

    private static String hashesText(
            final Path summary,
            final Path artifactDirectory,
            final List<ReleaseCandidateLifecycleResult.Cycle> cycles) throws IOException {
        final StringBuilder text = new StringBuilder()
                .append(summary.getFileName()).append('\t')
                .append(QualificationArtifactHasher.sha256(summary)).append('\n');
        for (final ReleaseCandidateLifecycleResult.Cycle cycle : cycles) {
            final String name = cycle.scenario().toLowerCase() + "-"
                    + String.format("%02d", cycle.cycleNumber()) + ".txt";
            text.append(name).append('\t').append(cycle.artifactSha256()).append('\n');
        }
        return text.toString();
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    private static void publishImmutable(final Path target, final String text) throws IOException {
        final Path absoluteTarget = target.toAbsolutePath().normalize();
        final Path parent = Objects.requireNonNull(absoluteTarget.getParent(), "target parent");
        Files.createDirectories(parent);
        if (Files.exists(absoluteTarget)) {
            throw new IOException("lifecycle evidence already exists: " + absoluteTarget);
        }
        final Path temporary = Files.createTempFile(parent, absoluteTarget.getFileName() + ".", ".tmp");
        boolean moved = false;
        try {
            try (FileChannel channel = FileChannel.open(
                    temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                final ByteBuffer bytes = ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
                while (bytes.hasRemaining()) {
                    channel.write(bytes);
                }
                channel.force(true);
            }
            try {
                Files.move(temporary, absoluteTarget, StandardCopyOption.ATOMIC_MOVE);
                moved = true;
            } catch (final java.nio.file.AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic lifecycle evidence publication is required", exception);
            }
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }
}
