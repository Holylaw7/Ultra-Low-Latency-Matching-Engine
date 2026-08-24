package com.ultralatency.matching.qualification;

import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.persistence.snapshot.RecoveryLease;
import com.ultralatency.matching.persistence.wal.CommandWalReader;
import com.ultralatency.matching.persistence.wal.WalConfiguration;
import com.ultralatency.matching.recovery.online.RecoveryMode;
import com.ultralatency.matching.recovery.online.RecoveryPlanner;
import com.ultralatency.matching.recovery.online.RecoveryResult;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Runs one approved RC_ASSEMBLED_RUNTIME_V1 Full evidence unit through the packaged runtime. */
public final class ReleaseCandidateAssembledFullRunner {

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration STATUS_INTERVAL = Duration.ofSeconds(5);

    /** Runs exactly one immutable assembled-runtime Full evidence unit. */
    public ReleaseCandidateAssembledFullRun run(
            final Path packagedArtifact,
            final Path outputDirectory,
            final String gitSha,
            final String baselineTag) throws IOException {
        requireArtifact(packagedArtifact);
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        Objects.requireNonNull(gitSha, "gitSha");
        Objects.requireNonNull(baselineTag, "baselineTag");
        final QualificationFullConfiguration full =
                QualificationFullConfiguration.memorySteadyStateFull(outputDirectory);
        final Path root = outputDirectory.toAbsolutePath().normalize();
        Files.createDirectories(root);
        final Path artifactDirectory = Files.createDirectory(
                root.resolve("rc-assembled-full-" + UUID.randomUUID()));
        final Path walDirectory = artifactDirectory.resolve("wal");
        final Path snapshotDirectory = artifactDirectory.resolve("snapshots");
        final Path evidenceDirectory = artifactDirectory.resolve("process-evidence");
        Files.createDirectories(walDirectory);
        Files.createDirectories(snapshotDirectory);
        final Path configurationFile = writeConfiguration(
                artifactDirectory, walDirectory, snapshotDirectory);
        final Path resultFile = artifactDirectory.resolve("qualification-result-v1.txt");
        final Path manifestFile = artifactDirectory.resolve("qualification-manifest-v2.txt");
        final Path artifactHashesFile = artifactDirectory.resolve("artifact-hashes-v2.txt");
        final QualificationConfiguration workload = full.workloadConfiguration();
        final QualificationStreamingAccumulator streaming =
                new QualificationStreamingAccumulator(QualificationRunner.PUBLIC_PROBE_SUFFIX_LENGTH);
        final QualificationPublicStateTracker publicState = new QualificationPublicStateTracker();
        final Instant started = Instant.now();
        final Map<String, String> runtimeProvenance =
                QualificationIdentity.runtimeProvenance(walDirectory);
        long commandIndex = 0L;
        long statusCount = 0L;
        boolean ready = false;
        int exitCode = -1;
        try (ReleaseCandidateQualificationProcess child =
                ReleaseCandidateQualificationProcess.start(
                        packagedArtifact, configurationFile, evidenceDirectory, STARTUP_TIMEOUT)) {
            final String managementReady = ReleaseCandidateManagementClient.request(
                    child.managementPort(), "READY", COMMAND_TIMEOUT);
            ReleaseCandidateManagementClient.requireReady(managementReady);
            ready = true;
            final Instant deadline = started.plus(full.minimumDuration());
            Instant nextStatus = started;
            try (ProtocolV1QualificationClient client = new ProtocolV1QualificationClient(
                    new java.net.InetSocketAddress("127.0.0.1", child.protocolPort()),
                    COMMAND_TIMEOUT)) {
                while (commandIndex < full.commandCount() || Instant.now().isBefore(deadline)) {
                    final EngineCommand command = QualificationWorkloadV1.commandAtForRun(
                            workload, commandIndex);
                    final QualificationExchange exchange = client.exchange(
                            command, commandIndex + 1L);
                    streaming.accept(command, exchange);
                    publicState.accept(command, exchange);
                    commandIndex++;
                    if (!Instant.now().isBefore(nextStatus)) {
                        ReleaseCandidateManagementClient.request(
                                child.managementPort(), "STATUS", COMMAND_TIMEOUT);
                        statusCount++;
                        nextStatus = nextStatus.plus(STATUS_INTERVAL);
                    }
                }
            }
            exitCode = child.gracefulShutdown(PROCESS_TIMEOUT);
        } catch (final IOException | RuntimeException failure) {
            writeFailure(artifactDirectory, started, commandIndex, failure);
            throw failure;
        }
        final Instant completed = Instant.now();
        final Duration elapsed = Duration.between(started, completed);
        final QualificationStreamingSummary stream = streaming.finish();
        final QualificationPublicStateTracker.Summary publicSummary = publicState.finish();
        final Path resourceFile = evidenceDirectory.resolve("resource-evidence.csv");
        final Path jfrFile = evidenceDirectory.resolve("qualification.jfr");
        final QualificationResourceEvidence resources =
                QualificationResourceEvidenceReader.read(
                        resourceFile, full.minimumPostGcSamples());
        if (!Files.isRegularFile(jfrFile)) {
            throw new IOException("assembled runtime did not publish JFR evidence");
        }
        final WalConfiguration wal = WalConfiguration.defaults(walDirectory);
        final List<EngineCommand> persisted = CommandWalReader.read(wal);
        if (!QualificationWorkloadV1.matches(persisted, workload)
                || persisted.size() != commandIndex) {
            throw new IOException("assembled WAL differs from deterministic workload");
        }
        final RecoveryResult recovered = RecoveryPlanner.create(
                wal, snapshotDirectory).recover(RecoveryMode.PURE_WAL);
        if (recovered.walEndSequence() != persisted.size()
                || recovered.nextCommandSequence() != persisted.size() + 1L) {
            throw new IOException("assembled runtime recovery did not converge");
        }
        final boolean listenerRebound = verifyRebind(
                packagedArtifact, configurationFile);
        final boolean leaseReacquired;
        try (RecoveryLease lease = RecoveryLease.acquire(walDirectory)) {
            leaseReacquired = lease.isHeld();
        }
        final QualificationStorageInventory inventory =
                QualificationStorageInventory.capture(walDirectory, snapshotDirectory);
        final boolean fullCriteria = ready
                && commandIndex >= QualificationFullConfiguration.FULL_MINIMUM_COMMANDS
                && elapsed.compareTo(QualificationFullConfiguration.FULL_MINIMUM_DURATION) >= 0
                && resources.heapGuardAssessed()
                && resources.heapGuardPassed()
                && publicSummary.boundPassed()
                && listenerRebound
                && leaseReacquired
                && inventory.stable()
                && exitCode == 0;
        final Map<String, String> measurements = new LinkedHashMap<>();
        measurements.put("profile", full.profile().name());
        measurements.put("elapsedMillis", Long.toString(elapsed.toMillis()));
        measurements.put("acceptedCommands", Long.toString(commandIndex));
        measurements.put("managementStatusCount", Long.toString(statusCount));
        measurements.put("walCommandDigestHex", stream.commandDigestHex());
        measurements.put("checkpointDigestHex", recovered.checkpointDigestHex());
        measurements.put("publicStateMaximumActiveOrderCount",
                Integer.toString(publicSummary.maximumActiveOrderCount()));
        measurements.put("publicStateFinalActiveOrderCount",
                Integer.toString(publicSummary.finalActiveOrderCount()));
        measurements.put("heapGuardAssessed", Boolean.toString(resources.heapGuardAssessed()));
        measurements.put("heapGuardPassed", Boolean.toString(resources.heapGuardPassed()));
        measurements.put("listenerRebound", Boolean.toString(listenerRebound));
        measurements.put("recoveryLeaseReacquired", Boolean.toString(leaseReacquired));
        measurements.put("inventoryStable", Boolean.toString(inventory.stable()));
        measurements.put("fullCriteriaPassed", Boolean.toString(fullCriteria));
        measurements.put("claim.productionRtoOrAvailability", "NOT_CLAIMED");
        measurements.put("claim.hardwarePowerLoss", "NOT_CLAIMED");
        final QualificationResult result = new QualificationResult(
                fullCriteria,
                commandIndex,
                stream.responseCount(),
                stream.tradeCount(),
                recovered.checkpointDigestHex(),
                stream.transcriptDigestHex(),
                stream.publicProbeDigestHex(),
                measurements);
        Files.writeString(resultFile, resultText(
                full, result, elapsed, completed, recovered, resources,
                inventory, listenerRebound, leaseReacquired, statusCount),
                StandardCharsets.UTF_8);
        final QualificationIdentity.Pair identities = QualificationIdentity.forRun(
                full, runtimeProvenance, gitSha, baselineTag);
        final Map<String, QualificationManifestV2Factory.ArtifactReference> artifacts =
                artifactReferences(artifactDirectory, configurationFile, resultFile,
                        resourceFile, jfrFile);
        final QualificationManifest manifest = legacyManifest(
                full, gitSha, baselineTag, result);
        final QualificationManifestV2 v2 = manifestV2(
                full, manifest, identities, result, resources, inventory,
                started, completed, runtimeProvenance, artifacts);
        QualificationManifestV2Store.publish(manifestFile, v2);
        QualificationManifestV2Store.publishArtifactHashes(
                artifactHashesFile,
                Map.of(
                        "qualification-manifest-v2.txt", manifestFile,
                        "qualification-result-v1.txt", resultFile,
                        "runtime.properties", configurationFile,
                        "resource-evidence.csv", resourceFile,
                        "qualification.jfr", jfrFile));
        return new ReleaseCandidateAssembledFullRun(
                artifactDirectory, manifestFile, artifactHashesFile,
                v2.sha256Hex(), fullCriteria, elapsed, commandIndex);
    }

    private static void requireArtifact(final Path artifact) {
        Objects.requireNonNull(artifact, "packagedArtifact");
        if (!Files.isRegularFile(artifact)
                || !artifact.getFileName().toString().endsWith(".jar")) {
            throw new IllegalArgumentException("packagedArtifact must be a regular JAR");
        }
    }

    private static boolean verifyRebind(
            final Path artifact,
            final Path configuration) throws IOException {
        try (ReleaseCandidateQualificationProcess rebound =
                ReleaseCandidateQualificationProcess.start(
                        artifact, configuration, STARTUP_TIMEOUT)) {
            ReleaseCandidateManagementClient.requireReady(
                    ReleaseCandidateManagementClient.request(
                            rebound.managementPort(), "READY", COMMAND_TIMEOUT));
            return rebound.gracefulShutdown(PROCESS_TIMEOUT) == 0;
        }
    }

    private static QualificationManifest legacyManifest(
            final QualificationFullConfiguration full,
            final String gitSha,
            final String baselineTag,
            final QualificationResult result) {
        final QualificationConfiguration compact = new QualificationConfiguration(
                full.profile(), full.seed(), 1, full.commandTimeout(), full.outputDirectory());
        return QualificationManifest.initial(
                compact,
                QualificationWorkloadV1.generate(compact),
                "legacy-" + UUID.randomUUID(),
                gitSha,
                baselineTag).withResult(result);
    }

    private static QualificationManifestV2 manifestV2(
            final QualificationFullConfiguration full,
            final QualificationManifest legacy,
            final QualificationIdentity.Pair identities,
            final QualificationResult result,
            final QualificationResourceEvidence resources,
            final QualificationStorageInventory inventory,
            final Instant started,
            final Instant completed,
            final Map<String, String> runtimeProvenance,
            final Map<String, QualificationManifestV2Factory.ArtifactReference> artifacts) {
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put("schemaVersion", QualificationV2CanonicalCodec.MANIFEST_SCHEMA);
        fields.put("canonicalizationVersion", QualificationV2CanonicalCodec.CANONICALIZATION_VERSION);
        fields.put("source.runId", legacy.runId());
        fields.put("source.gitSha", legacy.gitSha());
        fields.put("source.baselineTag", legacy.baselineTag());
        fields.put("source.startedAtUtc", started.toString());
        fields.put("source.completedAtUtc", completed.toString());
        fields.put("identity.configurationIdentitySha256",
                identities.configurationIdentitySha256());
        fields.put("identity.comparabilityIdentitySha256",
                identities.comparabilityIdentitySha256());
        fields.put("configuration.lane", full.lane().name());
        fields.put("configuration.profile", full.profile().name());
        fields.put("configuration.workloadVersion", QualificationWorkloadV1.MEMORY_STEADY_STATE_VERSION);
        fields.put("configuration.seed", Long.toString(full.seed()));
        fields.put("configuration.commandCount", Integer.toString(full.commandCount()));
        fields.put("configuration.minimumDuration", full.minimumDuration().toString());
        fields.put("configuration.commandTimeout", full.commandTimeout().toString());
        fields.put("configuration.sampleInterval", full.sampleInterval().toString());
        fields.put("configuration.minimumPostGcSamples",
                Integer.toString(full.minimumPostGcSamples()));
        fields.put("result.status", result.success() ? "PASS" : "FAIL");
        fields.put("result.failureType", result.success() ? "NONE" : "QUALIFICATION_CRITERION");
        fields.put("result.failureMessage", result.success() ? "none" : "Full criteria failed");
        fields.put("result.failureMessageDigest",
                QualificationIdentity.digest(Map.of(
                        "message", result.success() ? "none" : "Full criteria failed")));
        fields.put("result.elapsedMillis",
                result.measurements().getOrDefault("elapsedMillis", "0"));
        fields.put("result.responseCount", Long.toString(result.responseCount()));
        fields.put("result.tradeCount", Long.toString(result.tradeCount()));
        fields.put("result.acceptedCommands", Long.toString(result.acceptedCommands()));
        fields.put("result.checkpointDigestHex", result.checkpointDigestHex());
        fields.put("result.transcriptDigestHex", result.transcriptDigestHex());
        fields.put("result.publicProbeDigestHex", result.publicProbeDigestHex());
        fields.put("result.resultDigestHex", result.digestHex());
        fields.put("result.heapGuardAssessed", Boolean.toString(resources.heapGuardAssessed()));
        fields.put("result.heapGuardPassed", Boolean.toString(resources.heapGuardPassed()));
        fields.put("result.naturalPostGcSampleCount",
                Integer.toString(resources.naturalPostGcHeapBytes().size()));
        fields.put("result.threadBaselineRestored",
                Boolean.toString(resources.threadBaselineRestored()));
        fields.put("result.listenerRebound",
                result.measurements().getOrDefault("listenerRebound", "true"));
        fields.put("result.recoveryLeaseReacquired",
                result.measurements().getOrDefault("recoveryLeaseReacquired", "true"));
        fields.put("result.inventoryStable",
                result.measurements().getOrDefault("inventoryStable", "true"));
        fields.put("result.walCommandDigestHex",
                result.measurements().getOrDefault("walCommandDigestHex",
                        QualificationCanonicalizer.EMPTY_DIGEST));
        fields.put("result.checkpointActiveOrderCount",
                result.measurements().getOrDefault("publicStateFinalActiveOrderCount", "0"));
        fields.put("result.walFileCount", Long.toString(inventory.walFileCount()));
        fields.put("result.walBytes", Long.toString(inventory.walBytes()));
        fields.put("result.snapshotFileCount", Long.toString(inventory.snapshotFileCount()));
        fields.put("result.snapshotBytes", Long.toString(inventory.snapshotBytes()));
        fields.put("result.temporaryFileCount", Long.toString(inventory.temporaryFileCount()));
        fields.put("claims.qualificationOnly", "true");
        fields.put("claims.hardwarePowerLossGuarantee", "NOT_CLAIMED");
        fields.put("claims.productionRtoOrAvailability", "NOT_CLAIMED");
        fields.put("claims.memoryLeakFreedom", "NOT_CLAIMED");
        fields.putAll(runtimeProvenance);
        artifacts.forEach((name, value) -> {
            final String prefix = "artifact." + name;
            fields.put(prefix + ".relativePath", value.relativePath());
            fields.put(prefix + ".size", Long.toString(value.size()));
            fields.put(prefix + ".sha256", value.sha256());
        });
        return QualificationManifestV2.of(fields);
    }

    private static Map<String, QualificationManifestV2Factory.ArtifactReference> artifactReferences(
            final Path root,
            final Path configuration,
            final Path result,
            final Path resource,
            final Path jfr) throws IOException {
        return Map.of(
                "config", reference(root, configuration),
                "result", reference(root, result),
                "resourceEvidence", reference(root, resource),
                "jfr", reference(root, jfr));
    }

    private static QualificationManifestV2Factory.ArtifactReference reference(
            final Path root,
            final Path file) throws IOException {
        return new QualificationManifestV2Factory.ArtifactReference(
                root.relativize(file).toString().replace('\\', '/'),
                file.toFile().length(),
                QualificationArtifactHasher.sha256(file));
    }

    private static Path writeConfiguration(
            final Path directory,
            final Path walDirectory,
            final Path snapshotDirectory) throws IOException {
        int protocolPort = freePort();
        int managementPort = freePort();
        while (managementPort == protocolPort) {
            managementPort = freePort();
        }
        final String text = "storage.wal.directory=wal\n"
                + "storage.snapshot.directory=snapshots\n"
                + "recovery.mode=PURE_WAL\n"
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
        Files.writeString(target, text, StandardCharsets.UTF_8);
        if (!walDirectory.getParent().equals(directory)
                || !snapshotDirectory.getParent().equals(directory)) {
            throw new IOException("assembled storage path escaped artifact directory");
        }
        return target;
    }

    private static String resultText(
            final QualificationFullConfiguration full,
            final QualificationResult result,
            final Duration elapsed,
            final Instant completed,
            final RecoveryResult recovered,
            final QualificationResourceEvidence resources,
            final QualificationStorageInventory inventory,
            final boolean listenerRebound,
            final boolean leaseReacquired,
            final long statusCount) {
        return "schemaVersion=rc-assembled-runtime-v1\n"
                + "status=" + (result.success() ? "PASS" : "FAIL") + "\n"
                + "completedAtUtc=" + completed + "\n"
                + "profile=" + full.profile() + "\n"
                + "seed=" + full.seed() + "\n"
                + "acceptedCommands=" + result.acceptedCommands() + "\n"
                + "elapsedMillis=" + elapsed.toMillis() + "\n"
                + "responseCount=" + result.responseCount() + "\n"
                + "tradeCount=" + result.tradeCount() + "\n"
                + "checkpointDigestHex=" + recovered.checkpointDigestHex() + "\n"
                + "transcriptDigestHex=" + result.transcriptDigestHex() + "\n"
                + "publicProbeDigestHex=" + result.publicProbeDigestHex() + "\n"
                + "naturalPostGcSamples=" + resources.naturalPostGcHeapBytes().size() + "\n"
                + "heapGuardPassed=" + resources.heapGuardPassed() + "\n"
                + "listenerRebound=" + listenerRebound + "\n"
                + "recoveryLeaseReacquired=" + leaseReacquired + "\n"
                + "inventoryStable=" + inventory.stable() + "\n"
                + "managementStatusCount=" + statusCount + "\n";
    }

    private static void writeFailure(
            final Path directory,
            final Instant started,
            final long acceptedCommands,
            final Throwable failure) {
        try {
            Files.writeString(
                    directory.resolve("failure-report.txt"),
                    "startedAtUtc=" + started + "\n"
                            + "acceptedCommands=" + acceptedCommands + "\n"
                            + "type=" + failure.getClass().getName() + "\n"
                            + "message=" + String.valueOf(failure.getMessage()) + "\n",
                    StandardCharsets.UTF_8);
        } catch (final IOException ignored) {
            // Preserve the primary qualification failure.
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }
}
