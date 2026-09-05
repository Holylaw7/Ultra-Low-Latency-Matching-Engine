package com.ultralatency.matching.qualification.ga.performance;

import com.ultralatency.matching.qualification.QualificationArtifactHasher;
import com.ultralatency.matching.qualification.QualificationIdentity;
import com.ultralatency.matching.qualification.ga.correctness.GaCorrectnessCanonicalContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Frozen RC2 G4 contract and fail-closed identity checks.
 *
 * <p>This class is qualification-owned.  It deliberately keeps the production candidate
 * immutable while making the protocol, window, durability mode and load model part of the
 * formal runner's canonical configuration identity.</p>
 */
public final class GaFormalPerformanceContract {

    /** Formal G4 command-line campaign token. */
    public static final String CAMPAIGN = GaPerformanceMatrix.APPROVED_VERSION;
    /** Number of independent physical performance runs. */
    public static final int RUN_COUNT = 3;
    /** Number of fresh lifecycle cycles in the formal contract. */
    public static final int LIFECYCLE_CYCLES = 60;
    /** Warmup excluded from the performance measurement population. */
    public static final Duration WARMUP = Duration.ofSeconds(60);
    /** Performance measurement duration. */
    public static final Duration MEASUREMENT = Duration.ofMinutes(10);
    /** Management warmup duration. */
    public static final Duration MANAGEMENT_WARMUP = Duration.ofSeconds(60);
    /** Management measurement duration. */
    public static final Duration MANAGEMENT_MEASUREMENT = Duration.ofMinutes(5);
    /** Status request interval for the management trial. */
    public static final Duration MANAGEMENT_INTERVAL = Duration.ofSeconds(1);
    /** Exact number of measured STATUS requests in each five-minute status trial. */
    public static final int MANAGEMENT_STATUS_REQUESTS = (int) (
            MANAGEMENT_MEASUREMENT.toNanos() / MANAGEMENT_INTERVAL.toNanos());
    /** Command timeout for formal child-process traffic. */
    public static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(5);
    /** Child startup timeout. */
    public static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(30);
    /** Child shutdown timeout. */
    public static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(30);

    private GaFormalPerformanceContract() {
    }

    /**
     * Verifies that a formal invocation names exactly the immutable RC2 candidate and packaged
     * application artifact.  No default or branch/working-tree inference is accepted here.
     */
    public static void requireFrozenIdentity(
            final GaCorrectnessCanonicalContext context,
            final GaPerformanceMatrix matrix,
            final Path packagedArtifact) throws IOException {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(matrix, "matrix");
        Objects.requireNonNull(packagedArtifact, "packagedArtifact");
        if (!matrix.isApproved()) {
            throw new IOException("formal G4 requires the frozen RC2 performance matrix");
        }
        if (!context.isApprovedCandidate()) {
            throw new IOException("formal G4 candidate identity is not the frozen RC2 candidate");
        }
        if (!Files.isRegularFile(packagedArtifact)
                || !packagedArtifact.getFileName().toString().endsWith(".jar")) {
            throw new IOException("formal G4 requires a packaged candidate JAR");
        }
        final String actual = QualificationArtifactHasher.sha256(packagedArtifact);
        if (!context.candidate().applicationJarSha256().equals(actual)) {
            throw new IOException("formal G4 candidate JAR SHA-256 does not match frozen identity");
        }
        if (!GaPerformanceMatrix.APPROVED_PROTOCOL.equals(context.protocolVersion())
                || GaPerformanceMatrix.APPROVED_PROTOCOL_V2_WINDOW != context.protocolV2Window()
                || !GaPerformanceMatrix.APPROVED_WAL_MODE.equals(context.walMode())) {
            throw new IOException("formal G4 protocol/window/WAL identity is not frozen RC2");
        }
    }

    /** Returns the canonical material fields for the formal G4 invocation. */
    public static Map<String, String> configurationFields(
            final GaCorrectnessCanonicalContext context,
            final GaPerformanceMatrix matrix,
            final String candidateJarSha256,
            final String qualificationJarSha256) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(matrix, "matrix");
        requireDigest(candidateJarSha256, "candidateJarSha256");
        requireDigest(qualificationJarSha256, "qualificationJarSha256");
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put("contract.version", CAMPAIGN);
        fields.put("candidate.tag", context.candidate().tag());
        fields.put("candidate.tagObjectSha", context.candidate().tagObjectSha());
        fields.put("candidate.productionSha", context.candidate().productionSha());
        fields.put("candidate.productionTreeSha256", context.candidate().productionTreeSha256());
        fields.put("candidate.applicationJarSha256", candidateJarSha256);
        fields.put("qualification.controllerSha", context.controllerGitSha());
        fields.put("qualification.jarSha256", qualificationJarSha256);
        fields.put("protocol.version", GaPerformanceMatrix.APPROVED_PROTOCOL);
        fields.put("protocol.v2.window", Integer.toString(
                GaPerformanceMatrix.APPROVED_PROTOCOL_V2_WINDOW));
        fields.put("wal.mode", GaPerformanceMatrix.APPROVED_WAL_MODE);
        fields.put("workload.profile", matrix.profile());
        fields.put("workload.version", "qualification-memory-steady-state-v1");
        fields.put("workload.seed", Long.toString(matrix.seed()));
        fields.put("load.model", GaPerformanceMatrix.APPROVED_LOAD_MODEL);
        fields.put("run.count", Integer.toString(matrix.runCount()));
        fields.put("warmup.duration", WARMUP.toString());
        fields.put("measurement.duration", MEASUREMENT.toString());
        fields.put("latency.start", "actual-request-offer-write-handoff");
        fields.put("latency.end", "fully-validated-response");
        fields.put("latency.percentile", "nearest-rank");
        fields.put("throughput.minimumCommandsPerSecond", "500.0");
        fields.put("latency.p50MaximumNanos", Long.toString(
                GaPerformanceEvaluator.MAX_P50_NANOS));
        fields.put("latency.p99MaximumNanos", Long.toString(
                GaPerformanceEvaluator.MAX_P99_NANOS));
        fields.put("latency.p999MaximumNanos", Long.toString(
                GaPerformanceEvaluator.MAX_P999_NANOS));
        fields.put("lifecycle.cycles", Integer.toString(LIFECYCLE_CYCLES));
        fields.put("lifecycle.startupSamples", Integer.toString(LIFECYCLE_CYCLES));
        fields.put("lifecycle.shutdownSamples", Integer.toString(LIFECYCLE_CYCLES));
        fields.put("management.warmup", MANAGEMENT_WARMUP.toString());
        fields.put("management.measurement", MANAGEMENT_MEASUREMENT.toString());
        fields.put("management.statusInterval", MANAGEMENT_INTERVAL.toString());
        fields.put("management.statusRequestCount", Integer.toString(MANAGEMENT_STATUS_REQUESTS));
        return Map.copyOf(fields);
    }

    /** Computes the stable configuration identity used by formal run manifests. */
    public static String configurationIdentity(final Map<String, String> fields) {
        return QualificationIdentity.digest(Objects.requireNonNull(fields, "fields"));
    }

    private static void requireDigest(final String value, final String name) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must be lowercase SHA-256");
        }
    }
}
