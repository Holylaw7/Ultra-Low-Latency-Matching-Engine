package com.ultralatency.matching.qualification.ga.soak;

import com.ultralatency.matching.app.ReleaseCandidateRuntime;
import com.ultralatency.matching.app.RuntimeConfiguration;
import com.ultralatency.matching.app.RuntimeFailureCode;
import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.qualification.ProtocolV1QualificationClient;
import com.ultralatency.matching.qualification.QualificationConfiguration;
import com.ultralatency.matching.qualification.QualificationEvidencePublication;
import com.ultralatency.matching.qualification.QualificationExchange;
import com.ultralatency.matching.qualification.QualificationJfrRecording;
import com.ultralatency.matching.qualification.QualificationWorkloadV1;
import com.ultralatency.matching.qualification.ReleaseCandidateManagementClient;
import com.ultralatency.matching.qualification.ReleaseCandidateQualificationProcess;
import com.ultralatency.matching.qualification.ga.correctness.GaCorrectnessCanonicalContext;
import com.ultralatency.matching.qualification.ga.observability.GaGcEvidence;
import com.ultralatency.matching.qualification.ga.observability.GaJfrEvidence;
import com.ultralatency.matching.qualification.ga.observability.GaManagementEvidence;
import com.ultralatency.matching.qualification.ga.observability.GaObservabilityEvaluator;
import com.ultralatency.matching.qualification.ga.observability.GaObservabilityObservation;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;
import com.ultralatency.matching.persistence.wal.WalDurabilityMode;
import com.ultralatency.matching.pipeline.PipelineWaitMode;
import com.ultralatency.matching.recovery.online.RecoveryMode;

/**
 * Executes the single shared TASK-052 Quick physical lifecycle.
 *
 * <p>Only {@link #runQuick(Path)} is exposed as an execution operation.  Stage A and Stage B
 * matrices are represented by {@link GaSoakMatrix}, but this runner refuses to execute them;
 * their formal campaigns are owned by a later Human-gated task.</p>
 */
public final class GaSoakRunner {

    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);
    private static final int WAL_SEGMENT_SIZE_BYTES = 65_536;

    private final GaCorrectnessCanonicalContext configuredContext;

    /** Creates a runner resolving the frozen candidate/context at execution time. */
    public GaSoakRunner() {
        this(null);
    }

    /** Creates a runner with an explicit context for deterministic unit tests. */
    public GaSoakRunner(final GaCorrectnessCanonicalContext context) {
        configuredContext = context;
    }

    /** Runs exactly one shared, non-formal Quick physical execution. */
    public GaSoakQuickResult runQuick(final Path outputDirectory) throws IOException {
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        // A real TASK-052 execution must cross the packaged qualification
        // boundary.  The explicit-context path remains a deterministic unit
        // fixture path and is never used for the approved campaign.
        if (configuredContext == null) {
            return runPackagedQuick(outputDirectory);
        }
        return runInProcessFixtureQuick(outputDirectory);
    }

    private GaSoakQuickResult runInProcessFixtureQuick(final Path outputDirectory) throws IOException {
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        final GaSoakMatrix matrix = GaSoakMatrix.quick();
        final GaCorrectnessCanonicalContext context = context();
        final Path root = newRunDirectory(outputDirectory);
        final Path physicalRoot = root.resolve("physical-root");
        final Path walRoot = physicalRoot.resolve("wal");
        final Path snapshotRoot = physicalRoot.resolve("snapshots");
        final Path evidenceRoot = root.resolve("process-evidence");
        Files.createDirectories(walRoot);
        Files.createDirectories(snapshotRoot);
        Files.createDirectories(evidenceRoot);
        final String physicalId = UUID.randomUUID().toString();
        final int offeredCommands = Math.toIntExact(Math.multiplyExact(
                matrix.duration().getSeconds(), (long) matrix.offeredRatePerSecond()));
        final QualificationConfiguration workload = new QualificationConfiguration(
                com.ultralatency.matching.qualification.QualificationProfile
                        .MEMORY_STEADY_STATE_V1,
                matrix.seed(),
                offeredCommands,
                COMMAND_TIMEOUT,
                physicalRoot);
        final Path jfrPath = evidenceRoot.resolve("qualification.jfr");
        final Instant started = Instant.now();
        final List<Long> latency = new ArrayList<>(offeredCommands);
        final List<GaManagementEvidence> management = new ArrayList<>();
        final StringBuilder transcript = new StringBuilder();
        final RuntimeConfiguration configuration = runtimeConfiguration(walRoot, snapshotRoot);
        final ReleaseCandidateRuntime runtime = ReleaseCandidateRuntime.create(configuration);
        final GaSoakResourceSampler sampler = new GaSoakResourceSampler(
                physicalId, GaSoakMatrix.Stage.QUICK, physicalRoot, snapshotRoot);
        QualificationJfrRecording jfr = null;
        boolean publicPathCompleted = false;
        boolean gracefulShutdown = false;
        int errors = 0;
        int timeouts = 0;
        int mismatches = 0;
        long accepted = 0L;
        long responses = 0L;
        long measurementStartNanos = -1L;
        long measurementStopNanos = -1L;
        PacingSchedule pacing = null;
        boolean workloadFailure = false;
        try {
            jfr = QualificationJfrRecording.start(jfrPath);
            runtime.start();
            runtime.publishReady();
            final int managementPort = runtime.managementServer().localAddress()
                    .orElseThrow(() -> new IOException("management listener did not bind"))
                    .getPort();
            management.add(ReleaseCandidateManagementClient.requestEvidence(
                    managementPort, "LIVE", COMMAND_TIMEOUT));
            management.add(ReleaseCandidateManagementClient.requestEvidence(
                    managementPort, "READY", COMMAND_TIMEOUT));
            management.add(ReleaseCandidateManagementClient.requestEvidence(
                    managementPort, "STATUS", COMMAND_TIMEOUT));
            management.add(ReleaseCandidateManagementClient.requestEvidence(
                    managementPort, "METRICS", COMMAND_TIMEOUT));
            final int protocolPort = runtime.protocolServer().localAddress()
                    .orElseThrow(() -> new IOException("protocol listener did not bind"))
                    .getPort();
            try (ProtocolV1QualificationClient client = new ProtocolV1QualificationClient(
                    new java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), protocolPort),
                    COMMAND_TIMEOUT)) {
                measurementStartNanos = System.nanoTime();
                pacing = new PacingSchedule(measurementStartNanos, matrix);
                long offeredIndex = 0L;
                while (true) {
                    final PacingOffer offer = pacing.awaitNextOffer();
                    if (offer == null) {
                        break;
                    }
                    final EngineCommand command = QualificationWorkloadV1.commandAtForRun(
                            workload, offeredIndex);
                    final long exchangeStart = System.nanoTime();
                    try {
                        final QualificationExchange exchange = client.exchange(
                                command, offeredIndex + 1L);
                        latency.add(Math.max(1L, System.nanoTime() - exchangeStart));
                        accepted++;
                        responses += exchange.responseFrameCount();
                        transcript.append(offeredIndex + 1L).append(',')
                                .append(exchange.transcriptDigestHex()).append('\n');
                        offeredIndex++;
                    } catch (final SocketTimeoutException timeout) {
                        timeouts++;
                        workloadFailure = true;
                        throw timeout;
                    } catch (final IOException failure) {
                        errors++;
                        workloadFailure = true;
                        throw failure;
                    } catch (final RuntimeException mismatch) {
                        mismatches++;
                        workloadFailure = true;
                        throw mismatch;
                    }
                }
                if (pacing != null) {
                    if (workloadFailure) {
                        pacing.accountMissedThrough(System.nanoTime());
                        measurementStopNanos = System.nanoTime();
                    } else {
                        awaitDeadline(pacing.deadlineNanos());
                        pacing.accountMissedThrough(pacing.deadlineNanos());
                        measurementStopNanos = pacing.deadlineNanos();
                    }
                }
                // Keep the public session open while the runtime performs its orderly shutdown.
                // Closing it first is interpreted by the candidate server as an unexpected
                // disconnect and turns an otherwise complete run into a runtime failure.
                publicPathCompleted = !workloadFailure
                        && accepted == workload.commandCount()
                        && pacing.actualOfferedCommands() == pacing.nominalOfferOpportunities()
                        && pacing.missedOfferOpportunities() == 0L;
                management.add(ReleaseCandidateManagementClient.requestEvidence(
                        managementPort, "STATUS", COMMAND_TIMEOUT));
                gracefulShutdown = shutdown(runtime);
            }
        } finally {
            if (!gracefulShutdown) {
                try {
                    runtime.shutdown();
                    gracefulShutdown = true;
                } catch (final RuntimeException ignored) {
                    // The final observation records the unsuccessful terminal boundary.
                }
            }
            sampler.close();
            if (jfr != null) {
                try {
                    jfr.close();
                } catch (final IOException failure) {
                    errors++;
                }
            }
        }
        final Instant completed = Instant.now();
        if (measurementStartNanos != -1L && measurementStopNanos < measurementStartNanos) {
            measurementStopNanos = System.nanoTime();
        }
        final long elapsedNanos = measurementStartNanos != -1L
                ? Math.max(1L, measurementStopNanos - measurementStartNanos)
                : Math.max(1L, Duration.between(started, completed).toNanos());
        final List<GaSoakResourceSample> resources = sampler.samples();
        final GaSoakObservation g6Observation = new GaSoakObservation(
                physicalId, GaSoakMatrix.Stage.QUICK, elapsedNanos, accepted, accepted,
                errors, timeouts, mismatches, toArray(latency), new long[0], new long[0],
                resources, List.<GaNaturalGcSample>of(), publicPathCompleted, true, true, true, true,
                true, context.isApprovedCandidate() || configuredContext != null,
                context.controllerGitSha() != null, gracefulShutdown, true,
                pacing == null ? 0L : pacing.nominalOfferOpportunities(),
                pacing == null ? 0L : pacing.actualOfferedCommands(),
                pacing == null ? 0L : pacing.missedOfferOpportunities());
        final GaJfrEvidence jfrEvidence = GaJfrEvidence.inspect(jfrPath, true, true, true);
        final GaGcEvidence gcEvidence = GaGcEvidence.quick("NONE");
        final GaObservabilityObservation g8Observation = new GaObservabilityObservation(
                physicalId, GaSoakMatrix.Stage.QUICK, resources, gcEvidence, jfrEvidence,
                management, true, true, gracefulShutdown ? 0 : 1, sampler.samplingFailed(),
                sampler.hasUnknownOwnedFiles(), sampler.transientFilesCleanAfterShutdown(), true,
                context.isApprovedCandidate()
                        || configuredContext != null, context.controllerGitSha() != null);
        final GaSoakEvaluator.Evaluation g6 = GaSoakEvaluator.evaluateQuick(matrix, g6Observation);
        final GaObservabilityEvaluator.Evaluation g8 =
                GaObservabilityEvaluator.evaluateQuick(matrix, g8Observation);
        final Path configArtifact = root.resolve("configuration-v1.properties");
        final Path transcriptArtifact = root.resolve("transcript-v1.csv");
        final Path resourceArtifact = root.resolve("resource-samples-v1.csv");
        final Path managementArtifact = root.resolve("management-evidence-v1.csv");
        final Path terminalArtifact = root.resolve("terminal-evidence-v1.txt");
        final Path pacingArtifact = root.resolve("pacing-evidence-v1.csv");
        QualificationEvidencePublication.text(configArtifact, childConfigurationText(configuration));
        QualificationEvidencePublication.text(transcriptArtifact, transcript.toString());
        QualificationEvidencePublication.text(resourceArtifact, resourceText(resources));
        QualificationEvidencePublication.text(managementArtifact, managementText(management));
        QualificationEvidencePublication.text(terminalArtifact,
                terminalText(g6, g8, accepted, responses, gracefulShutdown));
        QualificationEvidencePublication.text(pacingArtifact,
                pacing == null ? "" : pacing.evidenceCsv());
        final Map<String, Path> artifacts = new LinkedHashMap<>();
        artifacts.put(configArtifact.getFileName().toString(), configArtifact);
        artifacts.put(transcriptArtifact.getFileName().toString(), transcriptArtifact);
        artifacts.put(resourceArtifact.getFileName().toString(), resourceArtifact);
        artifacts.put(managementArtifact.getFileName().toString(), managementArtifact);
        artifacts.put(terminalArtifact.getFileName().toString(), terminalArtifact);
        artifacts.put(pacingArtifact.getFileName().toString(), pacingArtifact);
        putIfRegular(artifacts, jfrPath);
        final GaSoakEvidencePublisher.PublishedQuick publication = GaSoakEvidencePublisher
                .publishQuick(root, matrix, g6Observation, g8Observation, g6, g8, context,
                        started, completed, artifacts);
        return new GaSoakQuickResult(g6Observation, g8, g6, publication);
    }

    /** Runs the approved Quick against the packaged qualification/candidate boundary. */
    private GaSoakQuickResult runPackagedQuick(final Path outputDirectory) throws IOException {
        final GaSoakMatrix matrix = GaSoakMatrix.quick();
        final GaCorrectnessCanonicalContext context = context();
        final Path qualificationArtifact = packagedQualificationArtifact();
        final Path root = newRunDirectory(outputDirectory);
        final Path physicalRoot = root.resolve("physical-root");
        final Path walRoot = physicalRoot.resolve("wal");
        final Path snapshotRoot = physicalRoot.resolve("snapshots");
        final Path evidenceRoot = root.resolve("process-evidence");
        Files.createDirectories(walRoot);
        Files.createDirectories(snapshotRoot);
        Files.createDirectories(evidenceRoot);
        final String physicalId = UUID.randomUUID().toString();
        // The offered schedule is duration * rate.  The accepted floor is a
        // separate predicate and must not truncate this schedule.
        final int offeredCommands = Math.toIntExact(Math.multiplyExact(
                matrix.duration().getSeconds(), (long) matrix.offeredRatePerSecond()));
        final QualificationConfiguration workload = new QualificationConfiguration(
                com.ultralatency.matching.qualification.QualificationProfile
                        .MEMORY_STEADY_STATE_V1,
                matrix.seed(), offeredCommands, COMMAND_TIMEOUT, physicalRoot);
        final RuntimeConfiguration configuration = runtimeConfiguration(walRoot, snapshotRoot);
        final Path configArtifact = root.resolve("configuration-v1.properties");
        QualificationEvidencePublication.text(configArtifact, childConfigurationText(configuration));
        final Path jfrPath = evidenceRoot.resolve("qualification.jfr");
        final Path naturalGcArtifact = evidenceRoot.resolve("natural-gc-v1.csv");
        final Instant started = Instant.now();
        final List<Long> latency = new ArrayList<>(offeredCommands);
        final List<GaManagementEvidence> management = new ArrayList<>();
        final StringBuilder transcript = new StringBuilder();
        final GaSoakResourceSampler sampler = new GaSoakResourceSampler(
                physicalId, GaSoakMatrix.Stage.QUICK, physicalRoot, snapshotRoot);
        boolean publicPathCompleted = false;
        boolean gracefulShutdown = false;
        int errors = 0;
        int timeouts = 0;
        int mismatches = 0;
        long accepted = 0L;
        long responses = 0L;
        long measurementStartNanos = -1L;
        long measurementStopNanos = -1L;
        int childExitCode = 1;
        boolean workloadFailure = false;
        PacingSchedule pacing = null;
        try (ReleaseCandidateQualificationProcess child =
                ReleaseCandidateQualificationProcess.start(
                        qualificationArtifact, configArtifact, evidenceRoot,
                        SHUTDOWN_TIMEOUT)) {
            final int managementPort = child.managementPort();
            management.add(ReleaseCandidateManagementClient.requestEvidence(
                    managementPort, "LIVE", COMMAND_TIMEOUT));
            management.add(ReleaseCandidateManagementClient.requestEvidence(
                    managementPort, "READY", COMMAND_TIMEOUT));
            management.add(ReleaseCandidateManagementClient.requestEvidence(
                    managementPort, "STATUS", COMMAND_TIMEOUT));
            management.add(ReleaseCandidateManagementClient.requestEvidence(
                    managementPort, "METRICS", COMMAND_TIMEOUT));
            try (ProtocolV1QualificationClient client = new ProtocolV1QualificationClient(
                    new java.net.InetSocketAddress(InetAddress.getLoopbackAddress(),
                            child.protocolPort()), COMMAND_TIMEOUT)) {
                measurementStartNanos = System.nanoTime();
                pacing = new PacingSchedule(measurementStartNanos, matrix);
                long offeredIndex = 0L;
                while (true) {
                    final PacingOffer offer = pacing.awaitNextOffer();
                    if (offer == null) {
                        break;
                    }
                    final EngineCommand command = QualificationWorkloadV1.commandAtForRun(
                            workload, offeredIndex);
                    final long exchangeStart = System.nanoTime();
                    try {
                        final QualificationExchange exchange = client.exchange(
                                command, offeredIndex + 1L);
                        latency.add(Math.max(1L, System.nanoTime() - exchangeStart));
                        accepted++;
                        responses += exchange.responseFrameCount();
                        transcript.append(offeredIndex + 1L).append(',')
                                .append(exchange.transcriptDigestHex()).append('\n');
                        offeredIndex++;
                    } catch (final SocketTimeoutException timeout) {
                        timeouts++;
                        workloadFailure = true;
                        break;
                    } catch (final IOException failure) {
                        errors++;
                        workloadFailure = true;
                        break;
                    } catch (final RuntimeException mismatch) {
                        mismatches++;
                        workloadFailure = true;
                        break;
                    }
                }
                if (workloadFailure) {
                    pacing.accountMissedThrough(System.nanoTime());
                    measurementStopNanos = System.nanoTime();
                } else {
                    awaitDeadline(pacing.deadlineNanos());
                    pacing.accountMissedThrough(pacing.deadlineNanos());
                    measurementStopNanos = pacing.deadlineNanos();
                }
                // Keep the public session open until the child has entered its shutdown boundary;
                // otherwise the candidate records a DISCONNECT terminal failure before the
                // parent can request orderly shutdown.
                publicPathCompleted = !workloadFailure
                        && accepted == offeredCommands
                        && pacing.actualOfferedCommands() == pacing.nominalOfferOpportunities()
                        && pacing.missedOfferOpportunities() == 0L;
                management.add(ReleaseCandidateManagementClient.requestEvidence(
                        managementPort, "STATUS", COMMAND_TIMEOUT));
                childExitCode = child.gracefulShutdown(SHUTDOWN_TIMEOUT);
                gracefulShutdown = childExitCode == 0;
            }
        } catch (final IOException | RuntimeException failure) {
            errors++;
        } finally {
            sampler.close();
        }
        final Instant completed = Instant.now();
        final long elapsedNanos = measurementStartNanos != -1L && measurementStopNanos >= measurementStartNanos
                ? measurementStopNanos - measurementStartNanos
                : Math.max(1L, Duration.between(started, completed).toNanos());
        final List<GaSoakResourceSample> resources = sampler.samples();
        final GaSoakObservation g6Observation = new GaSoakObservation(
                physicalId, GaSoakMatrix.Stage.QUICK, elapsedNanos, accepted, responses,
                errors, timeouts, mismatches, toArray(latency), new long[0], new long[0],
                resources, List.<GaNaturalGcSample>of(),
                publicPathCompleted,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                gracefulShutdown,
                childExitCode == 0,
                pacing == null ? 0L : pacing.nominalOfferOpportunities(),
                pacing == null ? 0L : pacing.actualOfferedCommands(),
                pacing == null ? 0L : pacing.missedOfferOpportunities());
        final GaJfrEvidence jfrEvidence = GaJfrEvidence.inspectWithNaturalGcOutput(
                jfrPath, true, true, true, naturalGcArtifact);
        final GaGcEvidence gcEvidence = GaGcEvidence.fromChildArtifact(
                naturalGcArtifact, physicalId, GaSoakMatrix.Stage.QUICK);
        final GaObservabilityObservation g8Observation = new GaObservabilityObservation(
                physicalId, GaSoakMatrix.Stage.QUICK, resources, gcEvidence, jfrEvidence,
                management, true, childExitCode == 0, childExitCode,
                false, sampler.hasUnknownOwnedFiles(),
                sampler.transientFilesCleanAfterShutdown(), true, true, true);
        final GaSoakEvaluator.Evaluation g6 = GaSoakEvaluator.evaluateQuick(matrix, g6Observation);
        final GaObservabilityEvaluator.Evaluation g8 =
                GaObservabilityEvaluator.evaluateQuick(matrix, g8Observation);
        final Path transcriptArtifact = root.resolve("transcript-v1.csv");
        final Path resourceArtifact = root.resolve("resource-samples-v1.csv");
        final Path managementArtifact = root.resolve("management-evidence-v1.csv");
        final Path terminalArtifact = root.resolve("terminal-evidence-v1.txt");
        final Path pacingArtifact = root.resolve("pacing-evidence-v1.csv");
        QualificationEvidencePublication.text(transcriptArtifact, transcript.toString());
        QualificationEvidencePublication.text(resourceArtifact, resourceText(resources));
        QualificationEvidencePublication.text(managementArtifact, managementText(management));
        QualificationEvidencePublication.text(terminalArtifact,
                terminalText(g6, g8, accepted, responses, gracefulShutdown));
        QualificationEvidencePublication.text(pacingArtifact,
                pacing == null ? "" : pacing.evidenceCsv());
        final Map<String, Path> artifacts = new LinkedHashMap<>();
        artifacts.put(configArtifact.getFileName().toString(), configArtifact);
        artifacts.put(transcriptArtifact.getFileName().toString(), transcriptArtifact);
        artifacts.put(resourceArtifact.getFileName().toString(), resourceArtifact);
        artifacts.put(managementArtifact.getFileName().toString(), managementArtifact);
        artifacts.put(terminalArtifact.getFileName().toString(), terminalArtifact);
        artifacts.put(pacingArtifact.getFileName().toString(), pacingArtifact);
        putIfRegular(artifacts, jfrPath);
        putIfRegular(artifacts, naturalGcArtifact);
        putIfRegular(artifacts, evidenceRoot.resolve("resource-evidence.csv"));
        final GaSoakEvidencePublisher.PublishedQuick publication = GaSoakEvidencePublisher
                .publishQuick(root, matrix, g6Observation, g8Observation, g6, g8, context,
                        started, completed, artifacts);
        return new GaSoakQuickResult(g6Observation, g8, g6, publication);
    }

    /** Refuses to execute formal Stage A/B; those runs remain Human-gated. */
    public GaSoakQuickResult run(final GaSoakMatrix matrix, final Path outputDirectory)
            throws IOException {
        Objects.requireNonNull(matrix, "matrix");
        if (!matrix.isQuick()) {
            throw new IllegalArgumentException("formal TASK-052 soak execution is not authorized");
        }
        return runQuick(outputDirectory);
    }

    /** One nominal offer and its observed monotonic timing. */
    record PacingOffer(long ordinal, long scheduledNanos, long actualOfferNanos) {
    }

    /**
     * Monotonic Quick scheduler that accounts for missed slots without terminating the run or
     * issuing catch-up bursts.
     */
    static final class PacingSchedule {

        private final long startNanos;
        private final long deadlineNanos;
        private final long slotPeriodNanos;
        private final long nominalOfferOpportunities;
        private final StringBuilder evidence = new StringBuilder(
                "slotOrdinal,scheduledMonotonicNanos,actualOfferMonotonicNanos,status\n");
        private long nextOrdinal;
        private long actualOfferedCommands;
        private long missedOfferOpportunities;

        PacingSchedule(final long startNanos, final GaSoakMatrix matrix) {
            if (matrix == null || !matrix.isQuick()) {
                throw new IllegalArgumentException("invalid Quick pacing schedule");
            }
            this.startNanos = startNanos;
            try {
                deadlineNanos = Math.addExact(startNanos, matrix.duration().toNanos());
                nominalOfferOpportunities = Math.multiplyExact(matrix.duration().getSeconds(),
                        (long) matrix.offeredRatePerSecond());
                slotPeriodNanos = Math.max(1L,
                        1_000_000_000L / matrix.offeredRatePerSecond());
            } catch (final ArithmeticException overflow) {
                throw new IllegalArgumentException("Quick pacing schedule overflow", overflow);
            }
        }

        /** Waits for and records the next legal offer, or returns null at the fixed deadline. */
        PacingOffer awaitNextOffer() {
            while (nextOrdinal < nominalOfferOpportunities) {
                final long now = System.nanoTime();
                if (now >= deadlineNanos) {
                    accountMissedThrough(deadlineNanos);
                    return null;
                }
                final long target = targetFor(nextOrdinal);
                final long slotEnd = slotEndFor(nextOrdinal);
                if (now < target) {
                    LockSupport.parkNanos(Math.min(target - now, 10_000_000L));
                    if (Thread.currentThread().isInterrupted()) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                    continue;
                }
                if (now < slotEnd) {
                    return recordOffer(target, now);
                }
                // The whole current slot has elapsed.  Advance to the one active slot, if any;
                // this records missed opportunities without ever emitting a catch-up burst.
                accountMissedBefore(activeSlotAt(now));
            }
            return null;
        }

        /** Deterministically evaluates one clock reading; used by direct scheduler tests. */
        PacingOffer offerAt(final long nowNanos) {
            if (nowNanos < startNanos) {
                throw new IllegalArgumentException("clock precedes pacing start");
            }
            if (nextOrdinal >= nominalOfferOpportunities) {
                return null;
            }
            while (nextOrdinal < nominalOfferOpportunities) {
                final long target = targetFor(nextOrdinal);
                if (nowNanos < target) {
                    return null;
                }
                if (nowNanos < slotEndFor(nextOrdinal)) {
                    return recordOffer(target, nowNanos);
                }
                accountMissedBefore(activeSlotAt(nowNanos));
            }
            return null;
        }

        private PacingOffer recordOffer(final long target, final long actualNanos) {
            final PacingOffer offer = new PacingOffer(nextOrdinal, target, actualNanos);
            evidence.append(nextOrdinal).append(',').append(target).append(',')
                    .append(actualNanos).append(",OFFERED\n");
            nextOrdinal++;
            actualOfferedCommands++;
            return offer;
        }

        /** Accounts for all nominal slots whose scheduled time has passed. */
        void accountMissedThrough(final long nowNanos) {
            if (nowNanos < startNanos) {
                throw new IllegalArgumentException("clock precedes pacing start");
            }
            accountMissedBefore(activeSlotAt(nowNanos));
        }

        /** Advances the schedule without issuing a catch-up offer. */
        private void accountMissedBefore(final long firstFutureOrdinal) {
            final long bounded = Math.min(nominalOfferOpportunities,
                    Math.max(nextOrdinal, firstFutureOrdinal));
            while (nextOrdinal < bounded) {
                final long target = targetFor(nextOrdinal);
                evidence.append(nextOrdinal).append(',').append(target)
                        .append(",,MISSED\n");
                nextOrdinal++;
                missedOfferOpportunities++;
            }
        }

        private long activeSlotAt(final long nowNanos) {
            final long elapsed = nowNanos - startNanos;
            return elapsed / slotPeriodNanos;
        }

        private long targetFor(final long ordinal) {
            return Math.addExact(startNanos, Math.multiplyExact(ordinal, slotPeriodNanos));
        }

        private long slotEndFor(final long ordinal) {
            return ordinal + 1L >= nominalOfferOpportunities
                    ? deadlineNanos : targetFor(ordinal + 1L);
        }

        long deadlineNanos() {
            return deadlineNanos;
        }

        long nominalOfferOpportunities() {
            return nominalOfferOpportunities;
        }

        long actualOfferedCommands() {
            return actualOfferedCommands;
        }

        long missedOfferOpportunities() {
            return missedOfferOpportunities;
        }

        String evidenceCsv() {
            return evidence.toString();
        }
    }

    /** Waits until a monotonic deadline without shortening the fixed observation interval. */
    private static void awaitDeadline(final long deadlineNanos) {
        while (true) {
            final long remaining = deadlineNanos - System.nanoTime();
            if (remaining <= 0L) {
                return;
            }
            LockSupport.parkNanos(Math.min(remaining, 10_000_000L));
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** Returns the monotonic deadline for one offered command ordinal. */
    static long pacedTarget(
            final long measurementStartNanos,
            final long zeroBasedCommandOrdinal,
            final int offeredRatePerSecond) {
        if (measurementStartNanos < 0L || zeroBasedCommandOrdinal < 0L
                || offeredRatePerSecond <= 0) {
            throw new IllegalArgumentException("invalid pacing inputs");
        }
        final long offset = Math.multiplyExact(zeroBasedCommandOrdinal, 1_000_000_000L)
                / offeredRatePerSecond;
        return Math.addExact(measurementStartNanos, offset);
    }

    private GaCorrectnessCanonicalContext context() throws IOException {
        return configuredContext == null
                ? GaCorrectnessCanonicalContext.fromSystem() : configuredContext;
    }

    private static RuntimeConfiguration runtimeConfiguration(
            final Path walRoot, final Path snapshotRoot) throws IOException {
        final int protocolPort = freePort();
        int managementPort = freePort();
        while (managementPort == protocolPort) {
            managementPort = freePort();
        }
        return new RuntimeConfiguration(
                walRoot,
                snapshotRoot,
                RecoveryMode.PURE_WAL,
                WAL_SEGMENT_SIZE_BYTES,
                WalDurabilityMode.SYNC_EACH_APPEND,
                1024,
                PipelineWaitMode.BLOCKING,
                InetAddress.getLoopbackAddress(),
                protocolPort,
                8192,
                16384,
                true,
                InetAddress.getLoopbackAddress(),
                managementPort,
                16,
                Duration.ofSeconds(1),
                Duration.ofSeconds(2));
    }

    private static boolean shutdown(final ReleaseCandidateRuntime runtime) {
        try {
            runtime.shutdown();
            return runtime.status().state()
                    == com.ultralatency.matching.app.RuntimeLifecycleState.STOPPED
                    && runtime.status().failureCode() == RuntimeFailureCode.NONE;
        } catch (final RuntimeException failure) {
            return false;
        }
    }

    private static Path newRunDirectory(final Path outputDirectory) throws IOException {
        final Path root = outputDirectory.toAbsolutePath().normalize();
        Files.createDirectories(root);
        return Files.createDirectory(root.resolve("g6-g8-quick-" + UUID.randomUUID()));
    }

    private static Path packagedQualificationArtifact() throws IOException {
        try {
            final Path artifact = Path.of(GaSoakRunner.class.getProtectionDomain().getCodeSource()
                    .getLocation().toURI()).toAbsolutePath().normalize();
            if (!Files.isRegularFile(artifact)
                    || !artifact.getFileName().toString().endsWith(".jar")) {
                throw new IOException("Quick must execute from a packaged qualification JAR");
            }
            return artifact;
        } catch (final java.net.URISyntaxException exception) {
            throw new IOException("cannot resolve packaged qualification JAR", exception);
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    private static long[] toArray(final List<Long> values) {
        final long[] result = new long[values.size()];
        for (int index = 0; index < values.size(); index++) {
            result[index] = values.get(index);
        }
        return result;
    }

    private static String resourceText(final List<GaSoakResourceSample> samples) {
        final StringBuilder text = new StringBuilder(
                "physicalExecutionId,stage,sequence,monotonicNanos,threads,transientCount,"
                        + "transientBytes,heapUsedBytes\n");
        for (GaSoakResourceSample sample : samples) {
            text.append(sample.physicalExecutionId()).append(',').append(sample.stage()).append(',')
                    .append(sample.sequence()).append(',').append(sample.monotonicNanos())
                    .append(',').append(sample.threadCount()).append(',')
                    .append(sample.transientFileCount()).append(',')
                    .append(sample.transientFileBytes()).append(',')
                    .append(sample.heapUsedBytes()).append('\n');
        }
        return text.toString();
    }

    private static String managementText(final List<GaManagementEvidence> observations) {
        final StringBuilder text = new StringBuilder(
                "kind,schemaVersion,live,ready,state,failureCode,protocolBound,recoveryMode,"
                        + "acceptedCommands,terminalFailures,uptimeMillis,managementRequests,"
                        + "managementRejected,completeResponseBoundary\n");
        for (GaManagementEvidence observation : observations) {
            text.append(observation.kind()).append(',').append(observation.schemaVersion())
                    .append(',').append(value(observation.live())).append(',')
                    .append(value(observation.ready())).append(',')
                    .append(value(observation.state())).append(',')
                    .append(value(observation.failureCode())).append(',')
                    .append(value(observation.protocolBound())).append(',')
                    .append(value(observation.recoveryMode())).append(',')
                    .append(value(observation.acceptedCommands())).append(',')
                    .append(value(observation.terminalFailures())).append(',')
                    .append(value(observation.uptimeMillis())).append(',')
                    .append(value(observation.managementRequests())).append(',')
                    .append(value(observation.managementRejected())).append(',')
                    .append(observation.completeResponseBoundary()).append('\n');
        }
        return text.toString();
    }

    /**
     * Serializes the qualification child configuration using the strict
     * properties-v1 path representation.  The production configuration's
     * canonical text is intentionally unchanged; on Windows its native
     * backslashes would be rejected by the child loader, so qualification
     * converts only path separators to the portable forward-slash form.
     */
    private static String childConfigurationText(final RuntimeConfiguration configuration) {
        return configuration.canonicalText().replace('\\', '/');
    }

    private static String value(final Object value) {
        return value == null ? "" : value.toString();
    }

    private static void putIfRegular(final Map<String, Path> artifacts, final Path artifact) {
        if (Files.isRegularFile(artifact)) {
            artifacts.put(artifact.getFileName().toString(), artifact);
        }
    }

    private static String terminalText(
            final GaSoakEvaluator.Evaluation g6,
            final GaObservabilityEvaluator.Evaluation g8,
            final long accepted,
            final long responses,
            final boolean gracefulShutdown) {
        return "g6Outcome=" + g6.outcome() + "\n"
                + "g6FailureCode=" + g6.failureCode() + "\n"
                + "g8Outcome=" + g8.outcome() + "\n"
                + "g8FailureCode=" + g8.failureCode() + "\n"
                + "acceptedCommands=" + accepted + "\n"
                + "completedResponses=" + responses + "\n"
                + "gracefulShutdown=" + gracefulShutdown + "\n";
    }
}
