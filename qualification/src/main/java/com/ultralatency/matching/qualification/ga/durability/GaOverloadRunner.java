package com.ultralatency.matching.qualification.ga.durability;

import com.ultralatency.matching.domain.Side;
import com.ultralatency.matching.domain.OrderId;
import com.ultralatency.matching.domain.Price;
import com.ultralatency.matching.domain.Quantity;
import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.engine.SubmitLimitCommand;
import com.ultralatency.matching.integration.durable.DurableConfiguration;
import com.ultralatency.matching.integration.durable.DurableCommandCoordinator;
import com.ultralatency.matching.integration.durable.DurableFailureStage;
import com.ultralatency.matching.integration.durable.DurableTerminalException;
import com.ultralatency.matching.network.netty.durable.DurableMatchingEngineTcpServer;
import com.ultralatency.matching.network.netty.durable.DurableNetworkConfiguration;
import com.ultralatency.matching.network.protocol.ProtocolConstants;
import com.ultralatency.matching.network.protocol.ProtocolErrorCode;
import com.ultralatency.matching.network.protocol.ClientRequestId;
import com.ultralatency.matching.app.RuntimeStatusSnapshot;
import com.ultralatency.matching.operations.ManagementProtocol;
import com.ultralatency.matching.persistence.wal.WalCommandCodec;
import com.ultralatency.matching.persistence.wal.WalConfiguration;
import com.ultralatency.matching.persistence.wal.WalDurabilityMode;
import com.ultralatency.matching.pipeline.MatchingEnginePipeline;
import com.ultralatency.matching.pipeline.PipelineConfiguration;
import com.ultralatency.matching.pipeline.PipelinePublishOutcome;
import com.ultralatency.matching.pipeline.PipelineState;
import com.ultralatency.matching.pipeline.PipelineWaitMode;
import com.ultralatency.matching.qualification.QualificationConfiguration;
import com.ultralatency.matching.qualification.QualificationIdentity;
import com.ultralatency.matching.qualification.QualificationProfile;
import com.ultralatency.matching.qualification.QualificationWorkloadV1;
import com.ultralatency.matching.qualification.ga.correctness.GaCorrectnessCanonicalContext;
import com.ultralatency.matching.network.netty.gateway.NetworkGatewayState;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Qualification-only G7 bounded-overload and resource-bound harness. */
public final class GaOverloadRunner {

    private static final String GATE = "G7";
    private static final String GATE_VERSION = "g7-v1";
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(2);
    private final GaCorrectnessCanonicalContext configuredContext;

    /** Creates a runner that verifies the frozen candidate for the approved matrix. */
    public GaOverloadRunner() {
        this(null);
    }

    /** Creates a runner with an explicit identity context for focused tests. */
    public GaOverloadRunner(final GaCorrectnessCanonicalContext context) {
        configuredContext = context;
    }

    /** Runs the approved G7 matrix. Formal execution remains Human-gated by governance. */
    public GaOverloadCampaignResult run(final Path outputDirectory) throws IOException {
        return run(GaOverloadMatrix.approved(), outputDirectory);
    }

    /** Runs one explicit matrix and publishes canonical G7 evidence. */
    public GaOverloadCampaignResult run(
            final GaOverloadMatrix matrix,
            final Path outputDirectory) throws IOException {
        Objects.requireNonNull(matrix, "matrix");
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        final GaCorrectnessCanonicalContext context = configuredContext == null
                ? GaCorrectnessCanonicalContext.fromSystem() : configuredContext;
        if (matrix.isApproved() && !context.isApprovedCandidate()) {
            throw new IOException("approved G7 matrix requires the frozen candidate context");
        }
        final Path root = createRoot(outputDirectory);
        final Instant started = Instant.now();
        final List<GaDurabilityEvidence.RunReference> runs = new ArrayList<>();
        final String matrixConfiguration = matrixConfigurationIdentity(matrix);
        for (GaOverloadScenario scenario : matrix.scenarios()) {
            final Path scenarioDirectory = root.resolve("scenario-"
                    + scenario.name().toLowerCase(Locale.ROOT));
            final Instant runStarted = Instant.now();
            boolean passed = false;
            String failure = "NONE";
            String raw;
            boolean aborted = false;
            try {
                passed = executeScenario(scenario, matrix, scenarioDirectory);
                if (!passed) {
                    failure = "B2";
                }
                raw = scenarioRaw(scenario, matrix, passed, null);
            } catch (final SemanticQualificationFailure failureCause) {
                failure = "B2";
                raw = scenarioRaw(scenario, matrix, "FAIL", failureCause);
            } catch (final Exception failureCause) {
                failure = "B3";
                aborted = true;
                raw = scenarioRaw(scenario, matrix, "ABORTED", failureCause);
            }
            if (aborted) {
                runs.add(GaDurabilityEvidence.publishAbortedRun(
                        scenarioDirectory,
                        GATE,
                        GATE_VERSION,
                        20_260_823L,
                        1,
                        WalCommandCodec.MIN_SEGMENT_SIZE_BYTES,
                        QualificationWorkloadV1.VERSION,
                        context,
                        runStarted,
                        Instant.now(),
                        failure,
                        raw,
                        matrixConfiguration));
            } else {
                runs.add(GaDurabilityEvidence.publishRun(
                        scenarioDirectory,
                        GATE,
                        GATE_VERSION,
                        20_260_823L,
                        1,
                        WalCommandCodec.MIN_SEGMENT_SIZE_BYTES,
                        QualificationWorkloadV1.VERSION,
                        context,
                        runStarted,
                        Instant.now(),
                        passed,
                        failure,
                        raw,
                        matrixConfiguration));
            }
        }
        final List<GaDurabilityEvidence.Criterion> criteria = matrix.scenarios().stream()
                .map(scenario -> criterionFor(scenario, runs))
                .toList();
        final boolean passed = runs.size() == matrix.scenarios().size()
                && runs.stream().allMatch(GaDurabilityEvidence.RunReference::passed);
        final Path gate = GaDurabilityEvidence.publishGate(
                root,
                GATE,
                GATE_VERSION,
                runs,
                context,
                started,
                Instant.now(),
                criteria,
                List.of(GaDurabilityEvidence.limitationOverload()));
        final Path campaign = GaDurabilityEvidence.publishCampaign(
                root,
                GATE,
                runs,
                context,
                started,
                Instant.now(),
                matrix.scenarios().size(),
                matrixConfiguration,
                passed);
        GaDurabilityEvidence.publishOverloadSummary(root, matrix.version(), runs, gate, passed);
        return new GaOverloadCampaignResult(matrix, runs, passed, root, gate, campaign);
    }

    private static GaDurabilityEvidence.Criterion criterionFor(
            final GaOverloadScenario scenario,
            final List<GaDurabilityEvidence.RunReference> runs) {
        final boolean result = runs.stream()
                .filter(reference -> reference.gate().equals(GATE))
                .anyMatch(reference -> reference.manifestPath().getParent().getFileName()
                        .toString().equals("scenario-" + scenarioDirectoryName(scenario))
                        && reference.passed());
        return new GaDurabilityEvidence.Criterion(
                scenarioId(scenario),
                Boolean.toString(result),
                "EQ",
                "true",
                result);
    }

    private static boolean executeScenario(
            final GaOverloadScenario scenario,
            final GaOverloadMatrix matrix,
            final Path directory) throws Exception {
        return switch (scenario) {
            case SECOND_SESSION -> secondSession(directory, matrix);
            case PIPELINED_REQUEST -> pipelinedRequests(directory, matrix);
            case FRAME_BOUND -> frameBound(directory, matrix);
            case PIPELINE_FULL -> pipelineFull(directory, matrix);
            case MANAGEMENT_BOUND -> managementBound(directory, matrix);
            case DURABLE_FULL -> durableFull(directory, matrix);
            case RESOURCE_BOUND -> resourceBound(directory, matrix);
        };
    }

    private static boolean secondSession(
            final Path directory,
            final GaOverloadMatrix matrix) throws IOException {
        final DurableMatchingEngineTcpServer server = server(directory, matrix.pipelineCapacity());
        server.start();
        final InetSocketAddress address = server.localAddress().orElseThrow();
        final Socket first = connect(address);
        final List<Socket> attempts = new ArrayList<>();
        try {
            int rejectedAttempts = 0;
            for (int attempt = 0; attempt < matrix.sessionAttempts(); attempt++) {
                final Socket second = connect(address);
                attempts.add(second);
                final byte[] response = readWireFrame(second);
                if (response.length == ProtocolConstants.ERROR_FRAME_LENGTH
                        && unsigned(response[5]) == ProtocolConstants.ERROR_TYPE
                        && shortAt(response, 24) == ProtocolErrorCode.SERVER_BUSY.code()) {
                    rejectedAttempts++;
                }
            }
            return rejectedAttempts == matrix.sessionAttempts();
        } finally {
            server.shutdown(SHUTDOWN_TIMEOUT);
            first.close();
            for (final Socket attempt : attempts) {
                attempt.close();
            }
        }
    }

    private static boolean pipelinedRequests(
            final Path directory,
            final GaOverloadMatrix matrix) throws IOException {
        final Path walDirectory = directory.resolve("wal");
        final WalConfiguration wal = new WalConfiguration(
                walDirectory, WalCommandCodec.MIN_SEGMENT_SIZE_BYTES,
                WalDurabilityMode.SYNC_EACH_APPEND);
        final DurableMatchingEngineTcpServer server = server(directory, matrix.pipelineCapacity());
        server.start();
        final Socket socket = connect(server.localAddress().orElseThrow());
        final QualificationConfiguration workload = new QualificationConfiguration(
                QualificationProfile.LIFECYCLE_MIX,
                20_260_823L,
                2,
                COMMAND_TIMEOUT,
                directory);
        try {
            if (matrix.pipelinedRequestCount() != 2) {
                throw semanticFailure("G7 pipelined probe requires exactly two requests");
            }
            final EngineCommand first = QualificationWorkloadV1.commandAtForRun(workload, 0);
            final EngineCommand second = QualificationWorkloadV1.commandAtForRun(workload, 1);
            final OutputStream output = socket.getOutputStream();
            output.write(encode(first, 1));
            output.write(encode(second, 2));
            output.flush();
            boolean completeResponseBoundaryObserved = false;
            int boundedRejections = 0;
            try {
                for (int responseIndex = 0; responseIndex < 2; responseIndex++) {
                    final byte[] response = readWireFrame(socket);
                    if (isInFlightRejection(response)) {
                        boundedRejections++;
                        completeResponseBoundaryObserved = true;
                    } else if (unsigned(response[5]) == ProtocolConstants.COMMAND_RESULT_TYPE
                            && longAt(response, 16) == 1L) {
                        consumeMatchFrames(socket, response, 1L);
                        completeResponseBoundaryObserved = true;
                    } else {
                        throw semanticFailure("pipelined response was not a bounded rejection");
                    }
                    // The frozen server closes the session after a complete in-flight
                    // rejection.  The rejection frame itself is the terminal protocol
                    // boundary; do not require a second frame or mistake the expected close
                    // for an incomplete response.
                    if (boundedRejections == 1) {
                        break;
                    }
                }
            } catch (final EOFException incompleteResponse) {
                throw semanticFailure(
                        "pipelined response ended before a complete response boundary",
                        incompleteResponse);
            }
            server.shutdown(SHUTDOWN_TIMEOUT);
            final int persisted = com.ultralatency.matching.persistence.wal.CommandWalReader
                    .read(wal).size();
            return completePipelinedResponseBoundary(
                    completeResponseBoundaryObserved, boundedRejections)
                    && persisted == 1;
        } finally {
            server.shutdown(SHUTDOWN_TIMEOUT);
            socket.close();
        }
    }

    private static boolean frameBound(
            final Path directory,
            final GaOverloadMatrix matrix) throws IOException {
        final DurableMatchingEngineTcpServer server = server(directory, matrix.pipelineCapacity());
        server.start();
        final Socket socket = connect(server.localAddress().orElseThrow());
        boolean rejected = false;
        try {
            final ByteBuffer header = ByteBuffer.allocate(ProtocolConstants.HEADER_LENGTH)
                    .order(ByteOrder.BIG_ENDIAN);
            header.putInt(ProtocolConstants.MAGIC)
                    .put((byte) ProtocolConstants.VERSION)
                    .put((byte) ProtocolConstants.SUBMIT_LIMIT_TYPE)
                    .putShort((short) 0)
                    .putInt(matrix.maxRequestFrameBytes() + 1)
                    .putInt(0);
            socket.getOutputStream().write(header.array());
            socket.getOutputStream().flush();
            try {
                final byte[] response = readWireFrame(socket);
                rejected = unsigned(response[5]) == ProtocolConstants.ERROR_TYPE;
            } catch (final IOException expectedClose) {
                // The frozen Protocol v1 decoder rejects an overlong frame by closing the
                // connection.  Accept only an observed EOF; a socket timeout is not rejection.
                rejected = isDeterministicFrameClose(expectedClose);
            }
            return rejected && server.state() != NetworkGatewayState.NEW;
        } finally {
            server.shutdown(SHUTDOWN_TIMEOUT);
            socket.close();
        }
    }

    private static boolean pipelineFull(
            final Path directory,
            final GaOverloadMatrix matrix) throws Exception {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicBoolean first = new AtomicBoolean(true);
        final MatchingEnginePipeline pipeline = new MatchingEnginePipeline(
                new PipelineConfiguration(matrix.pipelineCapacity(), PipelineWaitMode.BLOCKING),
                result -> {
                    if (first.compareAndSet(true, false)) {
                        entered.countDown();
                        try {
                            release.await(2, TimeUnit.SECONDS);
                        } catch (final InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("pipeline probe interrupted", interrupted);
                        }
                    }
                },
                failure -> { });
        pipeline.start();
        try {
            final QualificationConfiguration workload = new QualificationConfiguration(
                    QualificationProfile.LIFECYCLE_MIX,
                    20_260_823L,
                    Math.max(16, matrix.pipelineCapacity() * 2 + 1),
                    COMMAND_TIMEOUT,
                    directory);
            final PipelinePublishOutcome firstOutcome = pipeline.tryPublish(
                    QualificationWorkloadV1.commandAtForRun(workload, 0));
            if (firstOutcome != PipelinePublishOutcome.ACCEPTED
                    || !entered.await(2, TimeUnit.SECONDS)) {
                return false;
            }
            boolean full = false;
            for (int index = 1; index < workload.commandCount(); index++) {
                final PipelinePublishOutcome outcome = pipeline.tryPublish(
                        QualificationWorkloadV1.commandAtForRun(workload, index));
                if (outcome == PipelinePublishOutcome.FULL) {
                    full = true;
                    break;
                }
            }
            return full && pipeline.state() == PipelineState.RUNNING;
        } finally {
            release.countDown();
            if (pipeline.state() == PipelineState.RUNNING
                    || pipeline.state() == PipelineState.DRAINING) {
                pipeline.shutdown(SHUTDOWN_TIMEOUT);
            }
        }
    }

    private static boolean managementBound(
            final Path directory,
            final GaOverloadMatrix matrix) throws IOException {
        final byte[] oversized = new byte[matrix.maxManagementRequestBytes() + 1];
        oversized[oversized.length - 1] = '\n';
        boolean oversizedRequestRejected = false;
        try {
            ManagementProtocol.decode(oversized);
        } catch (final IllegalArgumentException expected) {
            oversizedRequestRejected = true;
        }
        final boolean requestBoundMatches = ManagementProtocol.MAX_REQUEST_BYTES
                == matrix.maxManagementRequestBytes();
        if (!oversizedRequestRejected || !requestBoundMatches) {
            throw semanticFailure(managementBoundDiagnostic(new ManagementBoundObservation(
                    oversizedRequestRejected,
                    requestBoundMatches,
                    null,
                    null,
                    null,
                    null,
                    -1,
                    -1,
                    -1)));
        }
        final Path walDirectory = directory.resolve("management-wal");
        final Path snapshotDirectory = directory.resolve("management-snapshots");
        Files.createDirectories(walDirectory);
        Files.createDirectories(snapshotDirectory);
        final int managementPort = freePort();
        final int protocolPort = freePortDifferent(managementPort);
        final com.ultralatency.matching.app.RuntimeConfiguration configuration =
                new com.ultralatency.matching.app.RuntimeConfiguration(
                        walDirectory,
                        snapshotDirectory,
                        com.ultralatency.matching.recovery.online.RecoveryMode.PURE_WAL,
                        WalCommandCodec.MIN_SEGMENT_SIZE_BYTES,
                        WalDurabilityMode.SYNC_EACH_APPEND,
                        matrix.pipelineCapacity(),
                        PipelineWaitMode.BLOCKING,
                        InetAddress.getLoopbackAddress(),
                        protocolPort,
                        DurableNetworkConfiguration.DEFAULT_LOW_WATERMARK,
                        DurableNetworkConfiguration.DEFAULT_HIGH_WATERMARK,
                        true,
                        InetAddress.getLoopbackAddress(),
                        managementPort,
                        1,
                        Duration.ofMillis(250),
                        SHUTDOWN_TIMEOUT);
        final com.ultralatency.matching.operations.ManagementServer management =
                new com.ultralatency.matching.operations.ManagementServer(
                        configuration, RuntimeStatusSnapshot::initial, failure -> { });
        management.start();
        final InetSocketAddress address = management.localAddress().orElseThrow();
        final Socket held = connect(address);
        final Socket rejected = connect(address);
        try {
            rejected.setSoTimeout(1_000);
            final boolean rejectedConnectionClosed = rejected.getInputStream().read() < 0;
            final long rejectionCount = management.managementRejected();
            final boolean rejectionCounterObserved = rejectionCount > 0;
            held.close();
            rejected.close();
            final Socket status = connect(address);
            try {
                status.getOutputStream().write("STATUS\n".getBytes(
                        java.nio.charset.StandardCharsets.US_ASCII));
                status.getOutputStream().flush();
                final byte[] response = readManagementResponse(status);
                final boolean statusResponseCompleted = response.length > 0;
                final boolean statusResponseWithinBound =
                        response.length <= ManagementProtocol.MAX_RESPONSE_BYTES;
                final ManagementBoundObservation observation = new ManagementBoundObservation(
                        oversizedRequestRejected,
                        requestBoundMatches,
                        rejectedConnectionClosed,
                        rejectionCounterObserved,
                        statusResponseCompleted,
                        statusResponseWithinBound,
                        response.length,
                        rejectionCount,
                        management.managementRequests());
                if (!observation.passed()) {
                    throw semanticFailure(managementBoundDiagnostic(observation));
                }
                return true;
            } finally {
                status.close();
            }
        } finally {
            held.close();
            rejected.close();
            management.shutdown(SHUTDOWN_TIMEOUT);
        }
    }

    private static byte[] readManagementResponse(final Socket socket) throws IOException {
        final InputStream input = socket.getInputStream();
        final java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        final byte[] buffer = new byte[ManagementProtocol.MAX_RESPONSE_BYTES];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read == 0) {
                continue;
            }
            output.write(buffer, 0, read);
            if (output.size() > ManagementProtocol.MAX_RESPONSE_BYTES) {
                throw semanticFailure("management response exceeded bound");
            }
        }
        return output.toByteArray();
    }

    private static int freePort() throws IOException {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0,
                1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    private static int freePortDifferent(final int other) throws IOException {
        int candidate;
        do {
            candidate = freePort();
        } while (candidate == other);
        return candidate;
    }

    private static boolean resourceBound(
            final Path directory,
            final GaOverloadMatrix matrix) throws IOException {
        Files.createDirectories(directory);
        for (int index = 0; index < 8; index++) {
            Files.writeString(directory.resolve("bounded-" + index + ".evidence"),
                    "bounded\n", java.nio.file.StandardOpenOption.CREATE_NEW);
        }
        final boolean liveRuntimeBounded = liveRuntimeResourceBound(directory, matrix);
        final long entryCount;
        try (var paths = Files.walk(directory)) {
            entryCount = paths.count();
        }
        // Exercise the same application-level bounds that protect the runtime from an
        // oversized pipeline or an excessive management admission count.
        final boolean pipelineBounded = rejectsRuntimeConfiguration(
                directory.resolve("pipeline-bound"),
                com.ultralatency.matching.app.RuntimeConfiguration.MAX_PIPELINE_CAPACITY * 2,
                1);
        final boolean managementBounded = rejectsRuntimeConfiguration(
                directory.resolve("management-bound"), 2, 65);
        return entryCount <= GaDurabilityEvidence.maxArtifactCount()
                && liveRuntimeBounded
                && pipelineBounded
                && managementBounded
                && matrix.maxRequestFrameBytes() <= ProtocolConstants.MAX_FRAME_LENGTH
                && matrix.maxManagementRequestBytes() == ManagementProtocol.MAX_REQUEST_BYTES;
    }

    /** Runs a live bounded-pipeline saturation probe and records its observable result. */
    private static boolean liveRuntimeResourceBound(
            final Path directory,
            final GaOverloadMatrix matrix) throws IOException {
        final Path probeDirectory = directory.resolve("live-runtime");
        Files.createDirectories(probeDirectory);
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final MatchingEnginePipeline pipeline = new MatchingEnginePipeline(
                new PipelineConfiguration(matrix.pipelineCapacity(), PipelineWaitMode.BLOCKING),
                result -> {
                    entered.countDown();
                    try {
                        release.await(30, TimeUnit.SECONDS);
                    } catch (final InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("resource-bound probe interrupted",
                                interrupted);
                    }
                },
                failure -> { });
        int accepted = 0;
        boolean fullObserved = false;
        PipelineState stateBeforeRelease = PipelineState.NEW;
        try {
            pipeline.start();
            final QualificationConfiguration workload = new QualificationConfiguration(
                    QualificationProfile.LIFECYCLE_MIX,
                    20_260_823L,
                    Math.min(QualificationConfiguration.MAX_COMMAND_COUNT,
                            matrix.pipelineCapacity() + 2),
                    COMMAND_TIMEOUT,
                    probeDirectory);
            if (pipeline.tryPublish(QualificationWorkloadV1.commandAtForRun(workload, 0))
                    != PipelinePublishOutcome.ACCEPTED
                    || !entered.await(2, TimeUnit.SECONDS)) {
                return false;
            }
            accepted = 1;
            for (int index = 1; index < workload.commandCount(); index++) {
                final PipelinePublishOutcome outcome = pipeline.tryPublish(
                        QualificationWorkloadV1.commandAtForRun(workload, index));
                if (outcome == PipelinePublishOutcome.FULL) {
                    fullObserved = true;
                    break;
                }
                accepted++;
            }
            stateBeforeRelease = pipeline.state();
            final boolean bounded = fullObserved
                    && accepted <= matrix.pipelineCapacity()
                    && stateBeforeRelease == PipelineState.RUNNING;
            final String observation = "schemaVersion=ga-g7-resource-bound-v1\n"
                    + "probe=LIVE_RUNTIME_PIPELINE_SATURATION\n"
                    + "pipelineCapacity=" + matrix.pipelineCapacity() + "\n"
                    + "acceptedBeforeFull=" + accepted + "\n"
                    + "fullObserved=" + fullObserved + "\n"
                    + "stateBeforeRelease=" + stateBeforeRelease + "\n"
                    + "bounded=" + bounded + "\n";
            Files.writeString(probeDirectory.resolve("resource-bound-live-observation-v1.txt"),
                    observation,
                    java.nio.charset.StandardCharsets.US_ASCII,
                    java.nio.file.StandardOpenOption.CREATE_NEW);
            return bounded;
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("resource-bound probe interrupted", interrupted);
        } finally {
            release.countDown();
            if (pipeline.state() == PipelineState.RUNNING
                    || pipeline.state() == PipelineState.DRAINING) {
                pipeline.shutdown(SHUTDOWN_TIMEOUT);
            }
        }
    }

    private static boolean rejectsRuntimeConfiguration(
            final Path directory,
            final int pipelineCapacity,
            final int managementMaxConnections) throws IOException {
        try {
            final int protocolPort = freePort();
            final int managementPort = freePortDifferent(protocolPort);
            new com.ultralatency.matching.app.RuntimeConfiguration(
                    directory.resolve("wal"),
                    directory.resolve("snapshots"),
                    com.ultralatency.matching.recovery.online.RecoveryMode.PURE_WAL,
                    WalCommandCodec.MIN_SEGMENT_SIZE_BYTES,
                    WalDurabilityMode.SYNC_EACH_APPEND,
                    pipelineCapacity,
                    PipelineWaitMode.BLOCKING,
                    InetAddress.getLoopbackAddress(),
                    protocolPort,
                    DurableNetworkConfiguration.DEFAULT_LOW_WATERMARK,
                    DurableNetworkConfiguration.DEFAULT_HIGH_WATERMARK,
                    true,
                    InetAddress.getLoopbackAddress(),
                    managementPort,
                    managementMaxConnections,
                    Duration.ofMillis(250),
                    SHUTDOWN_TIMEOUT);
            return false;
        } catch (final IllegalArgumentException expected) {
            return true;
        }
    }

    private static boolean durableFull(
            final Path directory,
            final GaOverloadMatrix matrix) throws Exception {
        final Path walDirectory = directory.resolve("durable-wal");
        final WalConfiguration wal = new WalConfiguration(
                walDirectory, WalCommandCodec.MIN_SEGMENT_SIZE_BYTES,
                WalDurabilityMode.SYNC_EACH_APPEND);
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final MatchingEnginePipeline pipeline = new MatchingEnginePipeline(
                new PipelineConfiguration(matrix.pipelineCapacity(), PipelineWaitMode.BLOCKING),
                result -> {
                    entered.countDown();
                    try {
                        // The durable-full probe intentionally holds the first published
                        // response while the bounded pipeline is filled.  The timeout is
                        // only a safety valve; it must not turn a slow but valid fill into
                        // an unrelated overload outcome.
                        release.await(30, TimeUnit.SECONDS);
                    } catch (final InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("durable-full probe interrupted", interrupted);
                    }
                },
                failure -> { });
        final java.util.concurrent.atomic.AtomicReference<Throwable> observedFailure =
                new java.util.concurrent.atomic.AtomicReference<>();
        final DurableCommandCoordinator coordinator = new DurableCommandCoordinator(
                (com.ultralatency.matching.engine.EngineCommand command) -> {
                    try (com.ultralatency.matching.persistence.wal.CommandWalWriter writer =
                            com.ultralatency.matching.persistence.wal.CommandWalWriter.open(wal)) {
                        writer.append(command);
                    }
                },
                pipeline::tryPublish,
                failure -> observedFailure.compareAndSet(null, failure.cause()));
        pipeline.start();
        coordinator.start();
        try {
            final QualificationConfiguration workload = new QualificationConfiguration(
                    QualificationProfile.LIFECYCLE_MIX, 20_260_823L,
                    Math.max(8, matrix.pipelineCapacity() + 3), COMMAND_TIMEOUT, directory);
            coordinator.accept(ClientRequestId.of(1), sequence -> commandFor(sequence.value(), workload, 0));
            if (!entered.await(2, TimeUnit.SECONDS)) {
                return false;
            }
            coordinator.accept(ClientRequestId.of(2), sequence -> commandFor(sequence.value(), workload, 1));
            for (int request = 3; request <= matrix.pipelineCapacity(); request++) {
                final int commandIndex = request - 1;
                coordinator.accept(ClientRequestId.of(request),
                        sequence -> commandFor(sequence.value(), workload, commandIndex));
            }
            boolean durableThenFull = false;
            try {
                coordinator.accept(ClientRequestId.of(matrix.pipelineCapacity() + 1L),
                        sequence -> commandFor(sequence.value(), workload,
                                matrix.pipelineCapacity()));
            } catch (final DurableTerminalException expected) {
                durableThenFull = expected.failure().stage() == DurableFailureStage.DURABLE_THEN_FULL;
            }
            boolean sameTerminalCause = false;
            try {
                coordinator.accept(ClientRequestId.of(matrix.pipelineCapacity() + 2L),
                        sequence -> commandFor(sequence.value(), workload,
                                matrix.pipelineCapacity() + 1));
            } catch (final DurableTerminalException expected) {
                sameTerminalCause = expected.failure().stage() == DurableFailureStage.DURABLE_THEN_FULL
                        && coordinator.terminalFailure().orElseThrow().stage()
                        == expected.failure().stage();
            }
            final int persisted = com.ultralatency.matching.persistence.wal.CommandWalReader
                    .read(wal).size();
            return durableThenFull && sameTerminalCause
                    && persisted == matrix.pipelineCapacity() + 1
                    && observedFailure.get() != null;
        } finally {
            release.countDown();
            if (coordinator.state() == com.ultralatency.matching.integration.durable.DurableLifecycleState.RUNNING) {
                coordinator.shutdown();
            }
            if (pipeline.state() == PipelineState.RUNNING
                    || pipeline.state() == PipelineState.DRAINING) {
                pipeline.shutdown(SHUTDOWN_TIMEOUT);
            }
        }
    }

    private static EngineCommand commandFor(
            final long sequence,
            final QualificationConfiguration configuration,
            final int index) {
        final EngineCommand template = QualificationWorkloadV1.commandAtForRun(configuration, index);
        final SubmitLimitCommand submit = template instanceof SubmitLimitCommand value
                ? value
                : new SubmitLimitCommand(
                        Sequence.of(index + 1L), OrderId.of(index + 1L), Side.BUY,
                        Price.of(100L), Quantity.of(1L));
        return new SubmitLimitCommand(
                new com.ultralatency.matching.domain.Sequence(sequence),
                submit.orderId(), submit.side(), submit.price(), submit.quantity());
    }

    private static DurableMatchingEngineTcpServer server(
            final Path directory,
            final int pipelineCapacity) {
        final Path walDirectory = directory.resolve("wal");
        final WalConfiguration wal = new WalConfiguration(
                walDirectory, WalCommandCodec.MIN_SEGMENT_SIZE_BYTES,
                WalDurabilityMode.SYNC_EACH_APPEND);
        final DurableConfiguration durable = new DurableConfiguration(
                wal,
                new PipelineConfiguration(pipelineCapacity, PipelineWaitMode.BLOCKING),
                SHUTDOWN_TIMEOUT);
        final DurableNetworkConfiguration network = new DurableNetworkConfiguration(
                InetAddress.getLoopbackAddress(),
                0,
                DurableNetworkConfiguration.DEFAULT_LOW_WATERMARK,
                DurableNetworkConfiguration.DEFAULT_HIGH_WATERMARK,
                durable);
        return new DurableMatchingEngineTcpServer(network);
    }

    private static Socket connect(final InetSocketAddress address) throws IOException {
        final Socket socket = new Socket();
        socket.connect(address, (int) COMMAND_TIMEOUT.toMillis());
        socket.setSoTimeout((int) COMMAND_TIMEOUT.toMillis());
        return socket;
    }

    private static byte[] readWireFrame(final Socket socket) throws IOException {
        final InputStream input = socket.getInputStream();
        final byte[] header = readFully(input, ProtocolConstants.HEADER_LENGTH);
        final int length = ByteBuffer.wrap(header, 8, Integer.BYTES)
                .order(ByteOrder.BIG_ENDIAN).getInt();
        if (length < ProtocolConstants.HEADER_LENGTH || length > ProtocolConstants.MAX_FRAME_LENGTH) {
            throw semanticFailure("invalid bounded response length");
        }
        final byte[] frame = new byte[length];
        System.arraycopy(header, 0, frame, 0, header.length);
        final byte[] remainder = readFully(input, length - header.length);
        System.arraycopy(remainder, 0, frame, header.length, remainder.length);
        return frame;
    }

    private static byte[] readFully(final InputStream input, final int length) throws IOException {
        final byte[] bytes = new byte[length];
        int offset = 0;
        while (offset < length) {
            final int read = input.read(bytes, offset, length - offset);
            if (read < 0) {
                throw new EOFException("connection closed before bounded response");
            }
            offset += read;
        }
        return bytes;
    }

    private static boolean isInFlightRejection(final byte[] frame) {
        return unsigned(frame[5]) == ProtocolConstants.ERROR_TYPE
                && longAt(frame, 16) >= 2L
                && shortAt(frame, 24) == ProtocolErrorCode.INVALID_FIELD.code();
    }

    /** Returns whether a frame-bound close is an observed deterministic EOF. */
    static boolean isDeterministicFrameClose(final IOException failure) {
        return failure instanceof EOFException;
    }

    /** Requires a complete protocol response and exactly one bounded rejection before PASS. */
    static boolean completePipelinedResponseBoundary(
            final boolean completeResponseBoundaryObserved,
            final int boundedRejections) {
        return completeResponseBoundaryObserved && boundedRejections == 1;
    }

    private static void consumeMatchFrames(
            final Socket socket,
            final byte[] commandResponse,
            final long requestId) throws IOException {
        if (commandResponse.length != ProtocolConstants.COMMAND_RESULT_FRAME_LENGTH) {
            throw semanticFailure("invalid command result frame");
        }
        final int matches = intAt(commandResponse, 36);
        if (matches < 0 || matches > ProtocolConstants.MAX_FRAME_LENGTH) {
            throw semanticFailure("invalid match count");
        }
        for (int index = 0; index < matches; index++) {
            final byte[] match = readWireFrame(socket);
            if (unsigned(match[5]) != ProtocolConstants.MATCH_RESULT_TYPE
                    || longAt(match, 16) != requestId
                    || intAt(match, 32) != index
                    || intAt(match, 36) != matches) {
                throw semanticFailure("pipelined response ordering is not deterministic");
            }
        }
    }

    private static SemanticQualificationFailure semanticFailure(final String message) {
        return new SemanticQualificationFailure(message);
    }

    private static SemanticQualificationFailure semanticFailure(
            final String message,
            final Throwable cause) {
        return new SemanticQualificationFailure(message, cause);
    }

    /** Captures each pre-existing MANAGEMENT_BOUND invariant for failure diagnosis. */
    record ManagementBoundObservation(
            Boolean oversizedRequestRejected,
            Boolean requestBoundMatches,
            Boolean rejectedConnectionClosed,
            Boolean rejectionCounterObserved,
            Boolean statusResponseCompleted,
            Boolean statusResponseWithinBound,
            int statusResponseBytes,
            long rejectionCount,
            long requestCount) {

        boolean passed() {
            return Boolean.TRUE.equals(oversizedRequestRejected)
                    && Boolean.TRUE.equals(requestBoundMatches)
                    && Boolean.TRUE.equals(rejectedConnectionClosed)
                    && Boolean.TRUE.equals(rejectionCounterObserved)
                    && Boolean.TRUE.equals(statusResponseCompleted)
                    && Boolean.TRUE.equals(statusResponseWithinBound);
        }
    }

    /** Returns a deterministic, single-line diagnosis without changing pass semantics. */
    static String managementBoundDiagnostic(final ManagementBoundObservation observation) {
        Objects.requireNonNull(observation, "observation");
        final StringBuilder failures = new StringBuilder();
        appendFailedInvariant(failures, "oversizedRequestRejected",
                observation.oversizedRequestRejected());
        appendFailedInvariant(failures, "requestBoundMatches", observation.requestBoundMatches());
        appendFailedInvariant(failures, "rejectedConnectionClosed",
                observation.rejectedConnectionClosed());
        appendFailedInvariant(failures, "rejectionCounterObserved",
                observation.rejectionCounterObserved());
        appendFailedInvariant(failures, "statusResponseCompleted",
                observation.statusResponseCompleted());
        appendFailedInvariant(failures, "statusResponseWithinBound",
                observation.statusResponseWithinBound());
        if (failures.length() == 0) {
            failures.append("NONE");
        }
        return "MANAGEMENT_BOUND_DIAGNOSTIC"
                + ";oversizedRequestRejected=" + diagnosticValue(
                        observation.oversizedRequestRejected())
                + ";requestBoundMatches=" + diagnosticValue(observation.requestBoundMatches())
                + ";rejectedConnectionClosed=" + diagnosticValue(
                        observation.rejectedConnectionClosed())
                + ";rejectionCounterObserved=" + diagnosticValue(
                        observation.rejectionCounterObserved())
                + ";statusResponseCompleted=" + diagnosticValue(
                        observation.statusResponseCompleted())
                + ";statusResponseWithinBound=" + diagnosticValue(
                        observation.statusResponseWithinBound())
                + ";statusResponseBytes=" + observation.statusResponseBytes()
                + ";rejectionCount=" + observation.rejectionCount()
                + ";requestCount=" + observation.requestCount()
                + ";failingInvariants=" + failures;
    }

    private static void appendFailedInvariant(
            final StringBuilder failures,
            final String name,
            final Boolean value) {
        if (!Boolean.TRUE.equals(value)) {
            if (failures.length() > 0) {
                failures.append(',');
            }
            failures.append(name).append('=').append(diagnosticValue(value));
        }
    }

    private static String diagnosticValue(final Boolean value) {
        return value == null ? "NOT_OBSERVED" : value.toString();
    }

    private static final class SemanticQualificationFailure extends IOException {

        private static final long serialVersionUID = 1L;

        private SemanticQualificationFailure(final String message) {
            super(message);
        }

        private SemanticQualificationFailure(final String message, final Throwable cause) {
            super(message, cause);
        }
    }

    private static byte[] encode(final EngineCommand command, final long requestId) {
        if (!(command instanceof SubmitLimitCommand submit)) {
            throw new IllegalArgumentException("G7 probe requires a submit-limit command");
        }
        final ByteBuffer buffer = ByteBuffer.allocate(ProtocolConstants.SUBMIT_LIMIT_FRAME_LENGTH)
                .order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(ProtocolConstants.MAGIC)
                .put((byte) ProtocolConstants.VERSION)
                .put((byte) ProtocolConstants.SUBMIT_LIMIT_TYPE)
                .putShort((short) 0)
                .putInt(ProtocolConstants.SUBMIT_LIMIT_FRAME_LENGTH)
                .putInt(0)
                .putLong(requestId)
                .putLong(submit.orderId().value())
                .put((byte) (submit.side() == Side.BUY ? 1 : 2))
                .put(new byte[7])
                .putLong(submit.price().ticks())
                .putLong(submit.quantity().units());
        return buffer.array();
    }

    private static String scenarioRaw(
            final GaOverloadScenario scenario,
            final GaOverloadMatrix matrix,
            final boolean passed,
            final Throwable failure) {
        return scenarioRaw(scenario, matrix, passed ? "PASS" : "FAIL", failure);
    }

    private static String scenarioRaw(
            final GaOverloadScenario scenario,
            final GaOverloadMatrix matrix,
            final String outcome,
            final Throwable failure) {
        final StringBuilder text = new StringBuilder();
        final String observable = switch (scenario) {
            case SECOND_SESSION -> "ERROR_SERVER_BUSY_ON_SECOND_SESSION";
            case PIPELINED_REQUEST -> "SECOND_IN_FLIGHT_REQUEST_DETERMINISTICALLY_REJECTED";
            case FRAME_BOUND -> "ERROR_OR_DETERMINISTIC_CLOSE_OVERSIZED_FRAME";
            case PIPELINE_FULL -> "PIPELINE_FULL_WITH_NO_UNBOUNDED_QUEUE";
            case MANAGEMENT_BOUND -> "REQUEST_BOUND_AND_RESPONSE_BOUND_ENFORCED";
            case DURABLE_FULL -> "DURABLE_THEN_FULL_TERMINAL_FAILURE_PRESERVED";
            case RESOURCE_BOUND -> "LIVE_RUNTIME_PIPELINE_SATURATION_AND_EVIDENCE_RESOURCE_BOUNDS_ENFORCED";
        };
        text.append("schemaVersion=ga-g7-scenario-v1\n")
                .append("matrixVersion=").append(matrix.version()).append('\n')
                .append("scenario=").append(scenario).append('\n')
                .append("pipelineCapacity=").append(matrix.pipelineCapacity()).append('\n')
                .append("maxRequestFrameBytes=").append(matrix.maxRequestFrameBytes()).append('\n')
                .append("maxManagementRequestBytes=")
                .append(matrix.maxManagementRequestBytes()).append('\n')
                .append("observableContract=").append(observable).append('\n')
                .append("transportBoundary=")
                .append(scenario == GaOverloadScenario.DURABLE_FULL
                        ? "QUALIFICATION_COORDINATOR_BOUNDARY_ONLY"
                        : "PUBLIC_OR_COMPONENT_BOUNDARY")
                .append('\n')
                .append("outcome=").append(outcome).append('\n')
                .append("claim.unboundedQueue=NOT_CLAIMED\n")
                .append("claim.secondProducer=NOT_CLAIMED\n");
        if (failure != null) {
            text.append("failureType=").append(failure.getClass().getName()).append('\n')
                    .append("failureMessage=").append(failure.getMessage()).append('\n');
        }
        return text.toString();
    }

    private static Path createRoot(final Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        return Files.createDirectory(outputDirectory.toAbsolutePath().normalize()
                .resolve("ga-g7-" + UUID.randomUUID()));
    }

    private static String scenarioDirectoryName(final GaOverloadScenario scenario) {
        return scenario.name().toLowerCase(Locale.ROOT);
    }

    private static String scenarioId(final GaOverloadScenario scenario) {
        return scenarioDirectoryName(scenario).replace('_', '-');
    }

    private static int unsigned(final byte value) {
        return Byte.toUnsignedInt(value);
    }

    private static int shortAt(final byte[] bytes, final int offset) {
        return Short.toUnsignedInt(ByteBuffer.wrap(bytes, offset, Short.BYTES)
                .order(ByteOrder.BIG_ENDIAN).getShort());
    }

    private static long longAt(final byte[] bytes, final int offset) {
        return ByteBuffer.wrap(bytes, offset, Long.BYTES)
                .order(ByteOrder.BIG_ENDIAN).getLong();
    }

    private static int intAt(final byte[] bytes, final int offset) {
        return ByteBuffer.wrap(bytes, offset, Integer.BYTES)
                .order(ByteOrder.BIG_ENDIAN).getInt();
    }

    private static String matrixConfigurationIdentity(final GaOverloadMatrix matrix) {
        final java.util.TreeMap<String, String> fields = new java.util.TreeMap<>();
        fields.put("gate.id", GATE);
        fields.put("gate.version", GATE_VERSION);
        fields.put("matrix.version", matrix.version());
        fields.put("matrix.pipelineCapacity", Integer.toString(matrix.pipelineCapacity()));
        fields.put("matrix.maxRequestFrameBytes", Integer.toString(matrix.maxRequestFrameBytes()));
        fields.put("matrix.maxManagementRequestBytes",
                Integer.toString(matrix.maxManagementRequestBytes()));
        fields.put("matrix.sessionAttempts", Integer.toString(matrix.sessionAttempts()));
        fields.put("matrix.pipelinedRequestCount", Integer.toString(matrix.pipelinedRequestCount()));
        fields.put("matrix.scenarios", matrix.scenarios().toString());
        return QualificationIdentity.digest(fields);
    }
}
