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
 * @param configuration immutable run configuration
 * @param outputDirectory reserved artifact directory
 * @param environment recorded host/JVM metadata
 * @param configurationDigestHex canonical configuration digest
 * @param resultDigestHex canonical result digest, or the empty placeholder
 * @param createdAt manifest creation time
 */
public record QualificationManifest(
        String runId,
        String gitSha,
        String baselineTag,
        QualificationWorkload workload,
        QualificationConfiguration configuration,
        Path outputDirectory,
        Map<String, String> environment,
        String configurationDigestHex,
        String resultDigestHex,
        Instant createdAt) {

    /** Creates a validated immutable manifest. */
    public QualificationManifest {
        requireText(runId, "runId");
        requireText(gitSha, "gitSha");
        requireText(baselineTag, "baselineTag");
        Objects.requireNonNull(workload, "workload");
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        outputDirectory = outputDirectory.toAbsolutePath().normalize();
        environment = Map.copyOf(Objects.requireNonNull(environment, "environment"));
        for (final Map.Entry<String, String> entry : environment.entrySet()) {
            requireText(entry.getKey(), "environment key");
            requireText(entry.getValue(), "environment value");
        }
        requireDigest(configurationDigestHex, "configurationDigestHex");
        requireDigest(resultDigestHex, "resultDigestHex");
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
                configuration,
                configuration.outputDirectory(),
                Map.of(),
                QualificationCanonicalizer.digest(configuration),
                QualificationCanonicalizer.EMPTY_DIGEST,
                Instant.now());
    }

    /** Returns a copy with the canonical digest of a completed result. */
    public QualificationManifest withResult(final QualificationResult result) {
        Objects.requireNonNull(result, "result");
        return new QualificationManifest(
                runId,
                gitSha,
                baselineTag,
                workload,
                configuration,
                outputDirectory,
                environment,
                configurationDigestHex,
                result.digestHex(),
                createdAt);
    }

    private static void requireDigest(final String value, final String name) {
        Objects.requireNonNull(value, name);
        if (value.length() != 64 || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must be a lowercase SHA-256 value");
        }
    }

    private static void requireText(final String value, final String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
