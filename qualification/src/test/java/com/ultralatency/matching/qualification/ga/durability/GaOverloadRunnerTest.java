package com.ultralatency.matching.qualification.ga.durability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.app.RuntimeConfiguration;
import com.ultralatency.matching.app.RuntimeStatusSnapshot;
import com.ultralatency.matching.operations.ManagementServer;
import com.ultralatency.matching.network.netty.durable.DurableNetworkConfiguration;
import com.ultralatency.matching.network.protocol.ProtocolConstants;
import com.ultralatency.matching.network.protocol.ProtocolErrorCode;
import com.ultralatency.matching.qualification.ga.GaCandidateVerifier;
import com.ultralatency.matching.qualification.ga.GaEvidenceCodec;
import com.ultralatency.matching.qualification.ga.GaEvidenceStore;
import com.ultralatency.matching.qualification.ga.GaGateEvaluator;
import com.ultralatency.matching.qualification.ga.correctness.GaCorrectnessCanonicalContext;
import com.ultralatency.matching.persistence.wal.WalCommandCodec;
import com.ultralatency.matching.persistence.wal.WalDurabilityMode;
import com.ultralatency.matching.pipeline.PipelineWaitMode;
import com.ultralatency.matching.recovery.online.RecoveryMode;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises the bounded public-boundary G7 probes and evidence publication. */
class GaOverloadRunnerTest {

    @Test
    void timeoutIsNotEvidenceOfDeterministicFrameRejection() {
        assertTrue(GaOverloadRunner.isDeterministicFrameClose(
                new EOFException("peer closed after rejection")));
        assertFalse(GaOverloadRunner.isDeterministicFrameClose(
                new SocketTimeoutException("no response")));
    }

    @Test
    void pipelinedPassRequiresCompleteResponseBoundary() {
        assertTrue(GaOverloadRunner.completePipelinedResponseBoundary(true, 1));
        assertFalse(GaOverloadRunner.completePipelinedResponseBoundary(false, 1));
        assertFalse(GaOverloadRunner.completePipelinedResponseBoundary(true, 0));
    }

    @Test
    void pipelinedUnexpectedFrameDiagnosticIdentifiesFrameAndPredicates() {
        final byte[] response = new byte[ProtocolConstants.ERROR_FRAME_LENGTH];
        response[5] = (byte) ProtocolConstants.ERROR_TYPE;
        ByteBuffer.wrap(response).order(ByteOrder.BIG_ENDIAN)
                .putLong(16, 1L)
                .putShort(24, (short) ProtocolErrorCode.INVALID_FIELD.code());

        final String diagnostic = GaOverloadRunner.pipelinedResponseDiagnostic(
                0, response, false, 0);

        assertTrue(diagnostic.contains("frameLength=32"));
        assertTrue(diagnostic.contains("frameType=224(ERROR)"));
        assertTrue(diagnostic.contains("payloadKind=ERROR"));
        assertTrue(diagnostic.contains("requestId=1"));
        assertTrue(diagnostic.contains("errorCode=4(INVALID_FIELD)"));
        assertTrue(diagnostic.contains("responseBoundary=COMPLETE_FRAME"));
        assertTrue(diagnostic.contains("decodedAsInFlightRejection=false"));
        assertTrue(diagnostic.contains("decodedAsCommandResult=false"));
        assertTrue(diagnostic.contains(
                "firstFailedPredicate=inFlightRejection.requestIdAtLeast2"));
    }

    @Test
    void pipelinedRequestDiagnosticCapturesActualFrameWhenObserved(@TempDir final Path output)
            throws Exception {
        final GaOverloadMatrix matrix = new GaOverloadMatrix(
                "ga-g7-pipelined-diagnostic-test-v1",
                2,
                GaOverloadMatrix.APPROVED_MAX_REQUEST_FRAME_BYTES,
                GaOverloadMatrix.APPROVED_MAX_MANAGEMENT_REQUEST_BYTES,
                GaOverloadMatrix.APPROVED_SESSION_ATTEMPTS,
                GaOverloadMatrix.APPROVED_PIPELINED_REQUEST_COUNT,
                java.util.List.of(GaOverloadScenario.PIPELINED_REQUEST));
        final GaOverloadCampaignResult result = new GaOverloadRunner(testContext(output))
                .run(matrix, output);
        final GaDurabilityEvidence.RunReference reference = result.runs().get(0);
        final Path rawPath = reference.manifestPath().getParent().resolve("raw-evidence-v1.txt");
        final String raw = Files.readString(rawPath);
        if (reference.passed()) {
            assertTrue(raw.contains("outcome=PASS"), raw);
        } else {
            assertTrue(raw.contains("PIPELINED_RESPONSE_DIAGNOSTIC"), raw);
            System.out.println("PIPELINED_REQUEST_DIAGNOSTIC_RAW=" + rawPath);
            System.out.println(raw);
        }
    }

    @Test
    void managementBoundThenPipelinedRequestHasNoObservedStateLeak(@TempDir final Path output)
            throws Exception {
        final GaOverloadMatrix matrix = new GaOverloadMatrix(
                "ga-g7-management-then-pipelined-isolation-test-v1",
                2,
                GaOverloadMatrix.APPROVED_MAX_REQUEST_FRAME_BYTES,
                GaOverloadMatrix.APPROVED_MAX_MANAGEMENT_REQUEST_BYTES,
                GaOverloadMatrix.APPROVED_SESSION_ATTEMPTS,
                GaOverloadMatrix.APPROVED_PIPELINED_REQUEST_COUNT,
                java.util.List.of(
                        GaOverloadScenario.MANAGEMENT_BOUND,
                        GaOverloadScenario.PIPELINED_REQUEST));
        final GaOverloadCampaignResult result = new GaOverloadRunner(testContext(output))
                .run(matrix, output);
        final GaDurabilityEvidence.RunReference pipeline = result.runs().get(1);
        final Path rawPath = pipeline.manifestPath().getParent().resolve("raw-evidence-v1.txt");
        final String raw = Files.readString(rawPath);
        assertTrue(pipeline.passed(), raw);
    }

    @Test
    void managementBoundDiagnosticIdentifiesFailedInvariant() {
        final GaOverloadRunner.ManagementBoundObservation observation =
                new GaOverloadRunner.ManagementBoundObservation(
                        true, true, true, true, false, true, 0, 1, 0);

        assertFalse(observation.passed());
        final String diagnostic = GaOverloadRunner.managementBoundDiagnostic(observation);
        assertTrue(diagnostic.contains("statusResponseCompleted=false"));
        assertTrue(diagnostic.contains("statusResponseBytes=0"));
        assertTrue(diagnostic.contains("rejectionCount=1"));
        assertTrue(diagnostic.contains(
                "failingInvariants=releaseObserved=NOT_OBSERVED,statusResponseCompleted=false"));
    }

    @Test
    void managementBoundDiagnosticDistinguishesUnobservedState() {
        final GaOverloadRunner.ManagementBoundObservation observation =
                new GaOverloadRunner.ManagementBoundObservation(
                        true, false, null, null, null, null, -1, -1, -1);

        assertFalse(observation.passed());
        final String diagnostic = GaOverloadRunner.managementBoundDiagnostic(observation);
        assertTrue(diagnostic.contains("requestBoundMatches=false"));
        assertTrue(diagnostic.contains("rejectedConnectionClosed=NOT_OBSERVED"));
        assertTrue(diagnostic.contains("failingInvariants=requestBoundMatches=false"));
        assertTrue(diagnostic.contains("rejectedConnectionClosed=NOT_OBSERVED"));
    }

    @Test
    void managementBoundDiagnosticIdentifiesFirstMissingStatusStage() {
        final GaOverloadRunner.ManagementBoundObservation observation =
                new GaOverloadRunner.ManagementBoundObservation(
                        true, true, true, true, false, true, 0, 1, 0,
                        true, true, true, false, true, true, false, true,
                        1, 2, 0, 0);

        final String diagnostic = GaOverloadRunner.managementBoundDiagnostic(observation);

        assertTrue(diagnostic.contains("statusConnectionEstablished=true"));
        assertTrue(diagnostic.contains("statusRequestIssued=true"));
        assertTrue(diagnostic.contains("statusRequestObserved=false"));
        assertTrue(diagnostic.contains("statusConnectionRejected=true"));
        assertTrue(diagnostic.contains("firstMissingStatusStage=statusConnectionAdmission"));
    }

    @Test
    void managementBoundStatusRequiresObservableServerRelease(@TempDir final Path output)
            throws Exception {
        final RuntimeConfiguration configuration = managementConfiguration(output);
        final ManagementServer management = new ManagementServer(
                configuration, RuntimeStatusSnapshot::initial, failure -> { });
        management.start();
        final InetSocketAddress address = management.localAddress().orElseThrow();
        try (Socket held = connect(address); Socket rejected = connect(address)) {
            rejected.setSoTimeout(2_000);
            assertEquals(-1, rejected.getInputStream().read());

            final long rejectedBeforeStatus = management.managementRejected();
            try (Socket beforeRelease = connect(address)) {
                beforeRelease.setSoTimeout(2_000);
                assertEquals(-1, beforeRelease.getInputStream().read());
            }
            assertTrue(management.managementRejected() > rejectedBeforeStatus);
            assertEquals(0, management.managementRequests());

            held.getOutputStream().write("LIVE\n".getBytes(StandardCharsets.US_ASCII));
            held.getOutputStream().flush();
            final byte[] releaseResponse = readUntilEof(held);
            assertTrue(releaseResponse.length > 0);
            assertTrue(management.managementRequests() > 0);

            try (Socket afterRelease = connect(address)) {
                afterRelease.getOutputStream().write(
                        "STATUS\n".getBytes(StandardCharsets.US_ASCII));
                afterRelease.getOutputStream().flush();
                final byte[] statusResponse = readUntilEof(afterRelease);
                assertTrue(statusResponse.length > 0);
                assertTrue(new String(statusResponse, StandardCharsets.UTF_8)
                        .contains("\"schemaVersion\":1"));
            }
        } finally {
            management.shutdown(Duration.ofSeconds(2));
        }
    }

    @Test
    void semanticPipelinedConfigurationFailureIsFailB2(@TempDir final Path output) throws Exception {
        final GaOverloadMatrix matrix = new GaOverloadMatrix(
                "ga-g7-pipelined-semantic-failure-test-v1",
                2,
                GaOverloadMatrix.APPROVED_MAX_REQUEST_FRAME_BYTES,
                GaOverloadMatrix.APPROVED_MAX_MANAGEMENT_REQUEST_BYTES,
                GaOverloadMatrix.APPROVED_SESSION_ATTEMPTS,
                3,
                java.util.List.of(GaOverloadScenario.PIPELINED_REQUEST));
        final GaOverloadCampaignResult result = new GaOverloadRunner(testContext(output))
                .run(matrix, output);

        final GaDurabilityEvidence.RunReference reference = result.runs().get(0);
        assertFalse(reference.passed());
        assertEquals("FAIL", reference.outcome());
        final Map<String, String> fields = GaEvidenceStore.read(
                reference.manifestPath(), GaEvidenceCodec.Schema.RUN);
        assertEquals("B2", fields.get("evidence.failureCode"));
        assertEquals("FAIL", fields.get("evidence.outcome"));
    }

    @Test
    void focusedMatrixProvesBoundedRejection(@TempDir final Path output) throws Exception {
        final GaOverloadCampaignResult result = new GaOverloadRunner(testContext(output))
                .run(GaOverloadMatrix.test(), output);

        assertTrue(result.passed(), () -> result.runs().stream()
                .map(reference -> {
                    try {
                        return reference.manifestPath().getParent().resolve("raw-evidence-v1.txt")
                                + "=" + Files.readString(reference.manifestPath().getParent()
                                .resolve("raw-evidence-v1.txt"));
                    } catch (final Exception exception) {
                        return exception.toString();
                    }
                }).toList().toString());
        assertEquals(GaOverloadScenario.values().length, result.runs().size());
        assertTrue(Files.isRegularFile(result.gateResultPath()));
        assertTrue(Files.isRegularFile(result.summaryPath()));
        assertTrue(Files.readString(result.gateResultPath()).contains("evidence.outcome=PASS"));
        final Map<String, String> campaign = GaEvidenceCodec.decode(
                GaEvidenceCodec.Schema.CAMPAIGN,
                Files.readAllBytes(result.summaryPath()));
        assertEquals("G7", campaign.get("gate.id"));
        assertEquals(Integer.toString(GaOverloadScenario.values().length),
                campaign.get("campaign.requiredRunCount"));
        assertEquals(Integer.toString(GaOverloadScenario.values().length), campaign.get("run.count"));
        assertTrue(GaGateEvaluator.evaluateCampaign(campaign).passed());
        assertTrue(result.runs().stream().map(reference -> reference.manifestPath().getParent())
                .map(path -> path.resolve("raw-evidence-v1.txt"))
                .map(path -> {
                    try {
                        return Files.readString(path);
                    } catch (final Exception exception) {
                        return exception.toString();
                    }
                })
                .allMatch(raw -> raw.contains("observableContract=")));
    }

    @Test
    void resourceBoundUsesLiveRuntimeSaturationEvidence(@TempDir final Path output)
            throws Exception {
        final GaOverloadMatrix matrix = new GaOverloadMatrix(
                "ga-g7-resource-bound-test-v1",
                2,
                GaOverloadMatrix.APPROVED_MAX_REQUEST_FRAME_BYTES,
                GaOverloadMatrix.APPROVED_MAX_MANAGEMENT_REQUEST_BYTES,
                GaOverloadMatrix.APPROVED_SESSION_ATTEMPTS,
                GaOverloadMatrix.APPROVED_PIPELINED_REQUEST_COUNT,
                java.util.List.of(GaOverloadScenario.RESOURCE_BOUND));
        final GaOverloadCampaignResult result = new GaOverloadRunner(testContext(output))
                .run(matrix, output);

        assertTrue(result.passed(), result.runs()::toString);
        final Path runDirectory = result.runs().get(0).manifestPath().getParent();
        final String observation = Files.readString(runDirectory.resolve("live-runtime")
                .resolve("resource-bound-live-observation-v1.txt"));
        assertTrue(observation.contains("probe=LIVE_RUNTIME_PIPELINE_SATURATION"));
        assertTrue(observation.contains("fullObserved=true"));
        assertTrue(observation.contains("bounded=true"));
        assertTrue(Files.readString(runDirectory.resolve("raw-evidence-v1.txt"))
                .contains("LIVE_RUNTIME_PIPELINE_SATURATION"));
    }

    private static RuntimeConfiguration managementConfiguration(final Path output)
            throws Exception {
        return new RuntimeConfiguration(
                output.resolve("wal"),
                output.resolve("snapshots"),
                RecoveryMode.PURE_WAL,
                WalCommandCodec.MIN_SEGMENT_SIZE_BYTES,
                WalDurabilityMode.SYNC_EACH_APPEND,
                2,
                PipelineWaitMode.BLOCKING,
                InetAddress.getLoopbackAddress(),
                freePort(),
                DurableNetworkConfiguration.DEFAULT_LOW_WATERMARK,
                DurableNetworkConfiguration.DEFAULT_HIGH_WATERMARK,
                true,
                InetAddress.getLoopbackAddress(),
                freePort(),
                1,
                Duration.ofMillis(250),
                Duration.ofSeconds(2));
    }

    private static Socket connect(final InetSocketAddress address) throws Exception {
        final Socket socket = new Socket();
        socket.connect(address, 3_000);
        socket.setSoTimeout(3_000);
        return socket;
    }

    private static byte[] readUntilEof(final Socket socket) throws Exception {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final byte[] buffer = new byte[2_048];
        int read;
        while ((read = socket.getInputStream().read(buffer)) >= 0) {
            if (read > 0) {
                output.write(buffer, 0, read);
            }
        }
        return output.toByteArray();
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    private static GaCorrectnessCanonicalContext testContext(final Path repository) {
        return new GaCorrectnessCanonicalContext(
                repository,
                "2".repeat(40),
                new GaCandidateVerifier.Verified(
                        "v0.9.0-rc.1",
                        "0".repeat(40),
                        "1".repeat(40),
                        "2".repeat(64),
                        "3".repeat(64)));
    }
}
