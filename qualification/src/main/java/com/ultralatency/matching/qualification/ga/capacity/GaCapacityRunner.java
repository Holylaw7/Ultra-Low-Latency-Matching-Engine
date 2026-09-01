package com.ultralatency.matching.qualification.ga.capacity;

import com.ultralatency.matching.qualification.QualificationConfiguration;
import com.ultralatency.matching.qualification.QualificationResult;
import com.ultralatency.matching.qualification.QualificationRun;
import com.ultralatency.matching.qualification.QualificationRunner;
import com.ultralatency.matching.qualification.QualificationWorkloadV1;
import com.ultralatency.matching.qualification.ga.correctness.GaCorrectnessCanonicalContext;
import com.ultralatency.matching.qualification.ga.performance.GaPerformanceEnvironment;
import com.ultralatency.matching.qualification.ga.performance.GaQuickEvidencePublisher;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Executes the bounded G5 public-path Quick readiness smoke. */
public final class GaCapacityRunner {

    private final GaCorrectnessCanonicalContext configuredContext;

    /** Creates a runner which resolves the frozen candidate at execution time. */
    public GaCapacityRunner() {
        this(null);
    }

    /** Creates a runner with an explicit context, primarily for deterministic tests. */
    public GaCapacityRunner(final GaCorrectnessCanonicalContext context) {
        configuredContext = context;
    }

    /** Runs the existing recovery-backed public workload as non-formal G5 evidence. */
    public GaCapacityQuickResult runQuick(final Path outputDirectory) throws IOException {
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        final GaCorrectnessCanonicalContext context = context();
        final GaCapacityMatrix matrix = GaCapacityMatrix.quick();
        final Path root = newRunDirectory(outputDirectory, "g5-quick");
        final QualificationConfiguration configuration = QualificationConfiguration.quick(
                com.ultralatency.matching.qualification.QualificationProfile.LIFECYCLE_MIX);
        final Instant started = Instant.now();
        final long startNanos = System.nanoTime();
        final QualificationRun run = new QualificationRunner().run(configuration);
        final long elapsedNanos = Math.max(1L, System.nanoTime() - startNanos);
        final QualificationResult result = run.result();
        final Map<String, String> environment = GaPerformanceEnvironment.capture(root);
        final GaCapacityObservation observation = new GaCapacityObservation(
                matrix.quickCommandCount(),
                result.acceptedCommands(),
                result.acceptedCommands(),
                parseLong(result.measurements(), "memoryStateActiveOrderCount"),
                parseLong(result.measurements(), "activePriceLevelCount"),
                parseLong(result.measurements(), "walBytes"),
                parseLong(result.measurements(), "snapshotBytes"),
                parseLong(environment, "heap.max.bytes"),
                0L,
                elapsedNanos,
                result.success(),
                false,
                false,
                false,
                false,
                true,
                true,
                true,
                true);
        final GaCapacityEvaluator.Evaluation evaluation =
                GaCapacityEvaluator.evaluateQuick(observation);
        final Instant completed = Instant.now();
        final Map<String, String> configurationFields = configurationFields(matrix, configuration);
        final String rawEvidence = rawEvidence(matrix, configuration, context, environment,
                result, observation);
        final GaQuickEvidencePublisher.RunInput input = new GaQuickEvidencePublisher.RunInput(
                "G5",
                "ga-g5-quick-v1",
                matrix.profile(),
                matrix.seed(),
                matrix.quickCommandCount(),
                QualificationWorkloadV1.VERSION,
                started,
                completed,
                evaluation.passed() ? "PASS" : "FAIL",
                evaluation.passed() ? "NONE" : "B2",
                result.acceptedCommands(),
                result.acceptedCommands(),
                result.tradeCount(),
                rawEvidence,
                new long[]{elapsedNanos},
                configurationFields,
                context);
        final GaQuickEvidencePublisher.PublishedRun published =
                GaQuickEvidencePublisher.publishRun(root, input);
        final Path gate = GaQuickEvidencePublisher.publishGate(
                root,
                "G5",
                "ga-g5-quick-v1",
                published,
                performanceCriteria(evaluation.criteria()),
                context,
                started,
                completed,
                "QUICK_READINESS_ONLY",
                "Quick public-path readiness evidence; not formal G5 qualification.");
        return new GaCapacityQuickResult(observation, evaluation, root,
                published.manifestPath(), gate);
    }

    private GaCorrectnessCanonicalContext context() throws IOException {
        return configuredContext == null
                ? GaCorrectnessCanonicalContext.fromSystem() : configuredContext;
    }

    private static Path newRunDirectory(final Path outputDirectory, final String prefix)
            throws IOException {
        final Path root = outputDirectory.toAbsolutePath().normalize();
        Files.createDirectories(root);
        return Files.createDirectory(root.resolve(prefix + "-" + java.util.UUID.randomUUID()));
    }

    private static long parseLong(final Map<String, String> values, final String key) {
        try {
            return Long.parseLong(values.getOrDefault(key, "0"));
        } catch (final NumberFormatException exception) {
            return 0L;
        }
    }

    private static Map<String, String> configurationFields(
            final GaCapacityMatrix matrix,
            final QualificationConfiguration configuration) {
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put("matrix.version", matrix.version());
        fields.put("lane", "QUICK");
        fields.put("profile", matrix.profile());
        fields.put("seed", Long.toString(matrix.seed()));
        fields.put("commandCount", Integer.toString(configuration.commandCount()));
        fields.put("walSegmentSizeBytes", Integer.toString(matrix.walSegmentSizeBytes()));
        fields.put("recovery", "PURE_WAL");
        fields.put("claim", "TESTED_SUPPORT_ENVELOPE");
        return Map.copyOf(fields);
    }

    private static String rawEvidence(
            final GaCapacityMatrix matrix,
            final QualificationConfiguration configuration,
            final GaCorrectnessCanonicalContext context,
            final Map<String, String> environment,
            final QualificationResult result,
            final GaCapacityObservation observation) {
        final StringBuilder output = new StringBuilder();
        output.append("schema=ga-g5-quick-readiness-v1\n")
                .append("formal=false\n")
                .append("claim=TESTED_SUPPORT_ENVELOPE\n")
                .append("matrix.version=").append(matrix.version()).append('\n')
                .append("profile=").append(matrix.profile()).append('\n')
                .append("seed=").append(matrix.seed()).append('\n')
                .append("commandCount=").append(configuration.commandCount()).append('\n')
                .append("acceptedCommands=").append(result.acceptedCommands()).append('\n')
                .append("responseCount=").append(result.acceptedCommands()).append('\n')
                .append("recovery.converged=").append(observation.exactRecoveryConvergence())
                .append('\n')
                .append("candidate.tag=").append(context.candidate().tag()).append('\n')
                .append("controller.gitSha=").append(context.controllerGitSha()).append('\n')
                .append("environment.identitySha256=")
                .append(GaPerformanceEnvironment.identity(environment)).append('\n');
        environment.forEach((key, value) -> output.append("environment.").append(key)
                .append('=').append(value).append('\n'));
        result.measurements().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> output.append("measurement.").append(entry.getKey())
                        .append('=').append(entry.getValue()).append('\n'));
        return output.toString();
    }

    private static List<com.ultralatency.matching.qualification.ga.performance
            .GaPerformanceEvaluator.Criterion> performanceCriteria(
                    final List<GaCapacityEvaluator.Criterion> criteria) {
        final List<com.ultralatency.matching.qualification.ga.performance
                .GaPerformanceEvaluator.Criterion> converted = new ArrayList<>(criteria.size());
        for (GaCapacityEvaluator.Criterion criterion : criteria) {
            converted.add(new com.ultralatency.matching.qualification.ga.performance
                    .GaPerformanceEvaluator.Criterion(
                            criterion.id(), criterion.actual(), criterion.operator(),
                            criterion.required(), criterion.passed()));
        }
        return List.copyOf(converted);
    }
}
