package com.ultralatency.matching.qualification;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable persisted campaign summary referencing immutable v2 run manifests. */
public final class QualificationCampaignSummary {

    private static final long MIN_QUALIFYING_ELAPSED_MILLIS = 60L * 60L * 1000L;
    private static final long MIN_QUALIFYING_ACCEPTED_COMMANDS = 1_000_000L;
    private static final long MIN_QUALIFYING_NATURAL_SAMPLES = 2L;

    private final Map<String, String> fields;
    private final byte[] canonicalBytes;
    private final String sha256Hex;

    private QualificationCampaignSummary(final Map<String, String> fields) {
        this.fields = Map.copyOf(Objects.requireNonNull(fields, "fields"));
        validate(this.fields);
        this.canonicalBytes = QualificationV2CanonicalCodec.encode(this.fields);
        this.sha256Hex = QualificationV2CanonicalCodec.sha256(this.fields);
    }

    /** Creates a validated summary from canonical flat fields. */
    public static QualificationCampaignSummary of(final Map<String, String> fields) {
        return new QualificationCampaignSummary(fields);
    }

    /** Reads and validates one persisted summary. */
    public static QualificationCampaignSummary read(final Path path) throws java.io.IOException {
        final QualificationCampaignSummary summary =
                fromCanonicalBytes(Files.readAllBytes(path));
        requirePersistedReferences(summary.fields);
        return summary;
    }

    /** Parses canonical summary bytes. */
    public static QualificationCampaignSummary fromCanonicalBytes(final byte[] bytes) {
        return new QualificationCampaignSummary(QualificationV2CanonicalCodec.decode(bytes));
    }

    /** Builds a summary using sorted, hashed member manifests and no timeline merging. */
    public static QualificationCampaignSummary fromManifests(
            final String campaignId,
            final String evaluatorVersion,
            final List<QualificationManifestV2> manifests,
            final int requiredRunCount,
            final int requiredCumulativeSamples,
            final boolean evaluatorResult) {
        Objects.requireNonNull(campaignId, "campaignId");
        Objects.requireNonNull(evaluatorVersion, "evaluatorVersion");
        Objects.requireNonNull(manifests, "manifests");
        if (manifests.isEmpty() || requiredRunCount <= 0 || requiredCumulativeSamples < 0) {
            throw new IllegalArgumentException("campaign summary requirements are invalid");
        }
        final List<QualificationManifestV2> sorted = new ArrayList<>(manifests);
        sorted.sort(Comparator.comparing(manifest -> manifest.value("source.runId")));
        for (int index = 1; index < sorted.size(); index++) {
            if (sorted.get(index - 1).value("source.runId")
                    .equals(sorted.get(index).value("source.runId"))) {
                throw new IllegalArgumentException("campaign contains duplicate runId");
            }
        }
        final Map<String, String> fields = new java.util.TreeMap<>();
        fields.put("schemaVersion", QualificationV2CanonicalCodec.CAMPAIGN_SCHEMA);
        fields.put("canonicalizationVersion",
                QualificationV2CanonicalCodec.CANONICALIZATION_VERSION);
        fields.put("campaign.campaignId", campaignId);
        fields.put("campaign.evaluatorVersion", evaluatorVersion);
        fields.put("campaign.requiredRunCount", Integer.toString(requiredRunCount));
        fields.put("campaign.requiredCumulativeNaturalPostGcSamples",
                Integer.toString(requiredCumulativeSamples));
        fields.put("campaign.participatingRunCount", Integer.toString(sorted.size()));
        fields.put("campaign.qualifyingRunCount", Integer.toString(qualifyingRunCount(sorted)));
        fields.put("campaign.cumulativeNaturalPostGcSamples",
                Integer.toString(cumulativeSamples(sorted)));
        fields.put("campaign.result", Boolean.toString(evaluatorResult));
        final String configurationIdentity = identity(sorted, "identity.configurationIdentitySha256");
        final String comparabilityIdentity = identity(sorted, "identity.comparabilityIdentitySha256");
        fields.put("identity.configurationIdentitySha256", configurationIdentity);
        fields.put("identity.comparabilityIdentitySha256", comparabilityIdentity);
        for (int index = 0; index < sorted.size(); index++) {
            final QualificationManifestV2 manifest = sorted.get(index);
            final String prefix = "run." + String.format("%04d", index + 1);
            fields.put(prefix + ".runId", manifest.value("source.runId"));
            fields.put(prefix + ".manifestSha256", manifest.sha256Hex());
            fields.put(prefix + ".configurationIdentitySha256",
                    manifest.value("identity.configurationIdentitySha256"));
            fields.put(prefix + ".comparabilityIdentitySha256",
                    manifest.value("identity.comparabilityIdentitySha256"));
            fields.put(prefix + ".status", manifest.value("result.status"));
            fields.put(prefix + ".elapsedMillis", manifest.valueOrDefault("result.elapsedMillis", "0"));
            fields.put(prefix + ".acceptedCommands",
                    manifest.valueOrDefault("result.acceptedCommands", "0"));
            fields.put(prefix + ".naturalPostGcSampleCount",
                    manifest.valueOrDefault("result.naturalPostGcSampleCount", "0"));
            fields.put(prefix + ".heapGuardAssessed",
                    manifest.valueOrDefault("result.heapGuardAssessed", "false"));
            fields.put(prefix + ".heapGuardPassed",
                    manifest.valueOrDefault("result.heapGuardPassed", "false"));
            fields.put(prefix + ".threadBaselineRestored",
                    manifest.valueOrDefault("result.threadBaselineRestored", "false"));
            fields.put(prefix + ".listenerRebound",
                    manifest.valueOrDefault("result.listenerRebound", "false"));
            fields.put(prefix + ".recoveryLeaseReacquired",
                    manifest.valueOrDefault("result.recoveryLeaseReacquired", "false"));
            fields.put(prefix + ".inventoryStable",
                    manifest.valueOrDefault("result.inventoryStable", "false"));
        }
        if (evaluatorResult
                && (qualifyingRunCount(sorted) < requiredRunCount
                || cumulativeSamples(sorted) < requiredCumulativeSamples)) {
            throw new IllegalArgumentException("campaign result cannot pass its thresholds");
        }
        return new QualificationCampaignSummary(fields);
    }

    /** Returns immutable fields. */
    public Map<String, String> fields() {
        return fields;
    }

    /** Returns canonical summary bytes. */
    public byte[] canonicalBytes() {
        return canonicalBytes.clone();
    }

    /** Returns SHA-256 over canonical summary bytes. */
    public String sha256Hex() {
        return sha256Hex;
    }

    @Override
    public String toString() {
        return new String(canonicalBytes, StandardCharsets.US_ASCII);
    }

    private static void validate(final Map<String, String> values) {
        values.keySet().forEach(QualificationCampaignSummary::validateKey);
        if (!QualificationV2CanonicalCodec.CAMPAIGN_SCHEMA.equals(values.get("schemaVersion"))) {
            throw new IllegalArgumentException("campaign summary schema mismatch");
        }
        if (!QualificationV2CanonicalCodec.CANONICALIZATION_VERSION.equals(
                values.get("canonicalizationVersion"))) {
            throw new IllegalArgumentException("campaign summary canonicalization mismatch");
        }
        requireText(values, "campaign.campaignId");
        requireText(values, "campaign.evaluatorVersion");
        requireDigest(values, "identity.configurationIdentitySha256");
        requireDigest(values, "identity.comparabilityIdentitySha256");
        final String result = values.get("campaign.result");
        if (!"true".equals(result) && !"false".equals(result)) {
            throw new IllegalArgumentException("campaign result must be boolean");
        }
        final int runs = integer(values, "campaign.participatingRunCount");
        if (runs <= 0) {
            throw new IllegalArgumentException("campaign must reference at least one run");
        }
        final int requiredRuns = integer(values, "campaign.requiredRunCount");
        final int requiredSamples = integer(
                values, "campaign.requiredCumulativeNaturalPostGcSamples");
        final int qualifyingRuns = integer(values, "campaign.qualifyingRunCount");
        final int cumulativeSamples = integer(
                values, "campaign.cumulativeNaturalPostGcSamples");
        if (requiredRuns <= 0 || requiredSamples < 0 || qualifyingRuns < 0
                || qualifyingRuns > runs || cumulativeSamples < 0) {
            throw new IllegalArgumentException("campaign counters are invalid");
        }
        final java.util.HashSet<String> runIds = new java.util.HashSet<>();
        int computedQualifyingRuns = 0;
        long computedCumulativeSamples = 0L;
        for (int index = 1; index <= runs; index++) {
            final String prefix = "run." + String.format("%04d", index);
            requireText(values, prefix + ".runId");
            if (!runIds.add(values.get(prefix + ".runId"))) {
                throw new IllegalArgumentException("campaign contains duplicate runId");
            }
            requireDigest(values, prefix + ".manifestSha256");
            requireDigest(values, prefix + ".configurationIdentitySha256");
            requireDigest(values, prefix + ".comparabilityIdentitySha256");
            if (!values.get("identity.configurationIdentitySha256")
                    .equals(values.get(prefix + ".configurationIdentitySha256"))
                    || !values.get("identity.comparabilityIdentitySha256")
                    .equals(values.get(prefix + ".comparabilityIdentitySha256"))) {
                throw new IllegalArgumentException("campaign member identity mismatch");
            }
            requireText(values, prefix + ".status");
            final String status = values.get(prefix + ".status");
            if (!status.equals("PASS") && !status.equals("FAIL") && !status.equals("ABORTED")) {
                throw new IllegalArgumentException("invalid campaign member status");
            }
            final long elapsedMillis = nonNegativeLong(values, prefix + ".elapsedMillis");
            final long acceptedCommands = nonNegativeLong(values, prefix + ".acceptedCommands");
            final long naturalSamples = nonNegativeLong(
                    values, prefix + ".naturalPostGcSampleCount");
            computedCumulativeSamples += naturalSamples;
            booleanText(values, prefix + ".heapGuardAssessed");
            booleanText(values, prefix + ".heapGuardPassed");
            booleanText(values, prefix + ".threadBaselineRestored");
            booleanText(values, prefix + ".listenerRebound");
            booleanText(values, prefix + ".recoveryLeaseReacquired");
            booleanText(values, prefix + ".inventoryStable");
            final String relativePath = values.get(prefix + ".manifestRelativePath");
            final String artifactPath = values.get(prefix + ".artifactHashesRelativePath");
            final String artifactDigest = values.get(prefix + ".artifactHashesSha256");
            if ((relativePath == null) != (artifactPath == null)
                    || (artifactPath == null) != (artifactDigest == null)) {
                throw new IllegalArgumentException("campaign member artifact references must be paired");
            }
            if (relativePath != null) {
                QualificationV2CanonicalCodec.rejectPathValue(relativePath);
                QualificationV2CanonicalCodec.rejectPathValue(artifactPath);
                requireDigest(values, prefix + ".artifactHashesSha256");
            }
            if (qualifyingFields(values, prefix, status, elapsedMillis,
                    acceptedCommands, naturalSamples)) {
                computedQualifyingRuns++;
            }
        }
        if (qualifyingRuns != computedQualifyingRuns
                || cumulativeSamples != computedCumulativeSamples) {
            throw new IllegalArgumentException("campaign counters do not match member evidence");
        }
        if ("true".equals(result)
                && (qualifyingRuns < requiredRuns || cumulativeSamples < requiredSamples)) {
            throw new IllegalArgumentException("campaign result cannot pass its thresholds");
        }
    }

    private static void validateKey(final String key) {
        final Set<String> fixed = Set.of(
                "schemaVersion", "canonicalizationVersion", "campaign.campaignId",
                "campaign.evaluatorVersion", "campaign.requiredRunCount",
                "campaign.requiredCumulativeNaturalPostGcSamples",
                "campaign.participatingRunCount", "campaign.qualifyingRunCount",
                "campaign.cumulativeNaturalPostGcSamples", "campaign.result",
                "identity.configurationIdentitySha256", "identity.comparabilityIdentitySha256",
                "failure.count");
        if (fixed.contains(key)) {
            return;
        }
        if (key.startsWith("run.")) {
            final String suffix = key.substring("run.".length());
            final int separator = suffix.indexOf('.');
            if (separator == 4 && suffix.substring(0, separator).matches("[0-9]{4}")) {
                final String field = suffix.substring(separator + 1);
                if (Set.of("runId", "manifestSha256", "configurationIdentitySha256",
                        "comparabilityIdentitySha256", "status", "elapsedMillis",
                        "acceptedCommands", "naturalPostGcSampleCount", "heapGuardAssessed",
                        "heapGuardPassed", "threadBaselineRestored", "listenerRebound",
                        "recoveryLeaseReacquired", "inventoryStable", "manifestRelativePath",
                        "artifactHashesRelativePath", "artifactHashesSha256").contains(field)) {
                    return;
                }
            }
        }
        if (key.startsWith("failure.")) {
            final String suffix = key.substring("failure.".length());
            final int separator = suffix.indexOf('.');
            if (separator == 4 && suffix.substring(0, separator).matches("[0-9]{4}")
                    && Set.of("code", "runId").contains(suffix.substring(separator + 1))) {
                return;
            }
        }
        throw new IllegalArgumentException("unknown campaign summary field: " + key);
    }

    static void requirePersistedReferences(final Map<String, String> values) {
        final int runs = integer(values, "campaign.participatingRunCount");
        for (int index = 1; index <= runs; index++) {
            final String prefix = "run." + String.format("%04d", index);
            requireText(values, prefix + ".manifestRelativePath");
            requireText(values, prefix + ".artifactHashesRelativePath");
            requireDigest(values, prefix + ".artifactHashesSha256");
        }
    }

    private static int qualifyingRunCount(final List<QualificationManifestV2> manifests) {
        return (int) manifests.stream()
                .filter(QualificationCampaignSummary::qualifying)
                .count();
    }

    private static boolean qualifying(final QualificationManifestV2 manifest) {
        return qualifyingFields(
                manifest.fields(), "result", manifest.value("result.status"),
                nonNegativeLong(manifest.valueOrDefault("result.elapsedMillis", "0"),
                        "result.elapsedMillis"),
                nonNegativeLong(manifest.valueOrDefault("result.acceptedCommands", "0"),
                        "result.acceptedCommands"),
                nonNegativeLong(manifest.valueOrDefault("result.naturalPostGcSampleCount", "0"),
                        "result.naturalPostGcSampleCount"));
    }

    private static boolean qualifyingFields(
            final Map<String, String> values,
            final String prefix,
            final String status,
            final long elapsedMillis,
            final long acceptedCommands,
            final long naturalSamples) {
        return "PASS".equals(status)
                && elapsedMillis >= MIN_QUALIFYING_ELAPSED_MILLIS
                && acceptedCommands >= MIN_QUALIFYING_ACCEPTED_COMMANDS
                && naturalSamples >= MIN_QUALIFYING_NATURAL_SAMPLES
                && Boolean.parseBoolean(values.getOrDefault(prefix + ".heapGuardAssessed", "false"))
                && Boolean.parseBoolean(values.getOrDefault(prefix + ".heapGuardPassed", "false"))
                && Boolean.parseBoolean(
                        values.getOrDefault(prefix + ".threadBaselineRestored", "false"))
                && Boolean.parseBoolean(values.getOrDefault(prefix + ".listenerRebound", "false"))
                && Boolean.parseBoolean(
                        values.getOrDefault(prefix + ".recoveryLeaseReacquired", "false"))
                && Boolean.parseBoolean(values.getOrDefault(prefix + ".inventoryStable", "false"));
    }

    private static int cumulativeSamples(final List<QualificationManifestV2> manifests) {
        return manifests.stream()
                .mapToInt(manifest -> nonNegative(
                        manifest.valueOrDefault("result.naturalPostGcSampleCount", "0"),
                        "result.naturalPostGcSampleCount"))
                .sum();
    }

    private static int nonNegative(final String value, final String key) {
        try {
            final int parsed = Integer.parseInt(value);
            if (parsed < 0) {
                throw new IllegalArgumentException(key + " must be non-negative");
            }
            return parsed;
        } catch (final NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be an integer", exception);
        }
    }

    private static long nonNegativeLong(final Map<String, String> values, final String key) {
        return nonNegativeLong(values.get(key), key);
    }

    private static long nonNegativeLong(final String value, final String key) {
        try {
            final long parsed = Long.parseLong(value);
            if (parsed < 0) {
                throw new IllegalArgumentException(key + " must be non-negative");
            }
            return parsed;
        } catch (final NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be an integer", exception);
        }
    }

    private static String identity(
            final List<QualificationManifestV2> manifests,
            final String field) {
        final String identity = manifests.get(0).value(field);
        if (manifests.stream().anyMatch(manifest -> !identity.equals(manifest.value(field)))) {
            throw new IllegalArgumentException("campaign identity mismatch: " + field);
        }
        return identity;
    }

    private static void requireText(final Map<String, String> values, final String key) {
        final String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing summary field: " + key);
        }
    }

    private static void requireDigest(final Map<String, String> values, final String key) {
        final String value = values.get(key);
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(key + " must be lowercase SHA-256");
        }
    }

    private static int integer(final Map<String, String> values, final String key) {
        try {
            final int parsed = Integer.parseInt(values.get(key));
            if (parsed < 0) {
                throw new IllegalArgumentException(key + " must be non-negative");
            }
            return parsed;
        } catch (final RuntimeException exception) {
            if (exception instanceof IllegalArgumentException
                    && exception.getMessage() != null
                    && exception.getMessage().contains("must be non-negative")) {
                throw exception;
            }
            throw new IllegalArgumentException(key + " must be an integer", exception);
        }
    }

    private static void booleanText(final Map<String, String> values, final String key) {
        final String value = values.get(key);
        if (!"true".equals(value) && !"false".equals(value)) {
            throw new IllegalArgumentException(key + " must be boolean");
        }
    }
}
