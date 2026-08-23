package com.ultralatency.matching.qualification;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable metadata required to reproduce one qualification run.
 *
 * @param runId stable external run identifier
 * @param gitSha source commit used for the run
 * @param baselineTag engineering baseline used for the run
 * @param workload immutable workload identity
 * @param outputDirectory reserved artifact directory
 * @param environment recorded host/JVM metadata
 * @param createdAt manifest creation time
 */
public record QualificationManifest(
        String runId,
        String gitSha,
        String baselineTag,
        QualificationWorkload workload,
        Path outputDirectory,
        Map<String, String> environment,
        Instant createdAt) {

    /** Creates a validated immutable manifest. */
    public QualificationManifest {
        requireText(runId, "runId");
        requireText(gitSha, "gitSha");
        requireText(baselineTag, "baselineTag");
        Objects.requireNonNull(workload, "workload");
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        outputDirectory = outputDirectory.toAbsolutePath().normalize();
        environment = Map.copyOf(Objects.requireNonNull(environment, "environment"));
        for (final Map.Entry<String, String> entry : environment.entrySet()) {
            requireText(entry.getKey(), "environment key");
            requireText(entry.getValue(), "environment value");
        }
        Objects.requireNonNull(createdAt, "createdAt");
    }

    /** Creates a manifest with the current timestamp and no environment claims. */
    public static QualificationManifest initial(
            final QualificationConfiguration configuration,
            final QualificationWorkload workload,
            final String runId,
            final String gitSha,
            final String baselineTag) {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(workload, "workload");
        return new QualificationManifest(
                runId,
                gitSha,
                baselineTag,
                workload,
                configuration.outputDirectory(),
                Map.of(),
                Instant.now());
    }

    private static void requireText(final String value, final String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
