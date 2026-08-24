package com.ultralatency.matching.qualification;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable v2 run manifest backed by a closed canonical field map. */
public final class QualificationManifestV2 {

    private static final String SCHEMA_KEY = "schemaVersion";
    private static final String CANONICALIZATION_KEY = "canonicalizationVersion";
    private static final String RUN_ID_KEY = "source.runId";
    private static final String STATUS_KEY = "result.status";
    private static final String CONFIG_ID_KEY = "identity.configurationIdentitySha256";
    private static final String COMPARABILITY_ID_KEY =
            "identity.comparabilityIdentitySha256";
    private static final Set<String> FIXED_KEYS = Set.of(
            "schemaVersion", "canonicalizationVersion",
            "source.runId", "source.gitSha", "source.baselineTag",
            "source.startedAtUtc", "source.completedAtUtc",
            CONFIG_ID_KEY, COMPARABILITY_ID_KEY,
            "configuration.lane", "configuration.profile",
            "configuration.workloadVersion", "configuration.seed",
            "configuration.commandCount", "configuration.minimumDuration",
            "configuration.commandTimeout", "configuration.sampleInterval",
            "configuration.minimumPostGcSamples",
            "result.status", "result.failureType", "result.failureMessage",
            "result.failureMessageDigest", "result.elapsedMillis",
            "result.responseCount", "result.tradeCount", "result.acceptedCommands",
            "result.checkpointDigestHex", "result.transcriptDigestHex",
            "result.publicProbeDigestHex", "result.resultDigestHex",
            "result.heapGuardAssessed", "result.heapGuardPassed",
            "result.naturalPostGcSampleCount", "result.threadBaselineRestored",
            "result.listenerRebound", "result.recoveryLeaseReacquired",
            "result.inventoryStable", "result.walCommandDigestHex",
            "result.checkpointActiveOrderCount", "result.walFileCount",
            "result.walBytes", "result.snapshotFileCount", "result.snapshotBytes",
            "result.temporaryFileCount", "claims.qualificationOnly",
            "claims.hardwarePowerLossGuarantee", "claims.productionRtoOrAvailability",
            "claims.memoryLeakFreedom");
    private static final Set<String> RUNTIME_KEYS = Set.of(
            "runtime.mustMatch.java.vendor", "runtime.mustMatch.java.runtimeVersion",
            "runtime.mustMatch.java.vmName", "runtime.mustMatch.java.vmVersion",
            "runtime.mustMatch.java.vmInputArguments", "runtime.mustMatch.gcCollectors",
            "runtime.mustMatch.heapMaxBytes", "runtime.mustMatch.processors",
            "runtime.mustMatch.fileEncoding", "runtime.mustMatch.timezone",
            "runtime.mustMatch.locale", "runtime.mustMatch.osName",
            "runtime.mustMatch.osVersion", "runtime.mustMatch.osArch",
            "runtime.mustMatch.filesystem", "runtime.mustMatch.nettyVersion",
            "runtime.mustMatch.disruptorVersion", "runtime.mustMatch.jfrConfiguration",
            "runtime.recordOnly.host", "runtime.recordOnly.pid",
            "runtime.recordOnly.startTime");

    private final Map<String, String> fields;
    private final byte[] canonicalBytes;
    private final String sha256Hex;

    private QualificationManifestV2(final Map<String, String> fields) {
        this.fields = Map.copyOf(Objects.requireNonNull(fields, "fields"));
        validate(this.fields);
        this.canonicalBytes = QualificationV2CanonicalCodec.encode(this.fields);
        this.sha256Hex = QualificationV2CanonicalCodec.sha256(this.fields);
    }

    /** Creates a validated immutable v2 manifest. */
    public static QualificationManifestV2 of(final Map<String, String> fields) {
        return new QualificationManifestV2(fields);
    }

    /** Reads a canonical v2 manifest from disk without changing it. */
    public static QualificationManifestV2 read(final Path path) throws java.io.IOException {
        Objects.requireNonNull(path, "path");
        return fromCanonicalBytes(Files.readAllBytes(path));
    }

    /** Parses canonical bytes and rejects non-canonical or malformed evidence. */
    public static QualificationManifestV2 fromCanonicalBytes(final byte[] bytes) {
        return new QualificationManifestV2(QualificationV2CanonicalCodec.decode(bytes));
    }

    /** Returns an immutable field view. */
    public Map<String, String> fields() {
        return fields;
    }

    /** Returns one field or null when the optional field is absent. */
    public String value(final String key) {
        return fields.get(key);
    }

    /** Returns one field or the supplied fallback when it is absent. */
    public String valueOrDefault(final String key, final String fallback) {
        return fields.getOrDefault(key, fallback);
    }

    /** Returns canonical LF-delimited bytes. */
    public byte[] canonicalBytes() {
        return canonicalBytes.clone();
    }

    /** Returns the SHA-256 of the canonical bytes. */
    public String sha256Hex() {
        return sha256Hex;
    }

    /** Writes canonical bytes only; callers needing atomic publication use the store. */
    public void write(final Path path) throws java.io.IOException {
        Files.write(path, canonicalBytes, java.nio.file.StandardOpenOption.CREATE_NEW);
    }

    @Override
    public String toString() {
        return new String(canonicalBytes, StandardCharsets.US_ASCII);
    }

    private static void validate(final Map<String, String> values) {
        values.keySet().forEach(QualificationManifestV2::validateKey);
        requireValue(values, SCHEMA_KEY, QualificationV2CanonicalCodec.MANIFEST_SCHEMA);
        requireValue(values, CANONICALIZATION_KEY,
                QualificationV2CanonicalCodec.CANONICALIZATION_VERSION);
        requireNonBlank(values, RUN_ID_KEY);
        requireNonBlank(values, STATUS_KEY);
        requireDigest(values, CONFIG_ID_KEY);
        requireDigest(values, COMPARABILITY_ID_KEY);
        final String status = values.get(STATUS_KEY);
        if (!status.equals("PASS") && !status.equals("FAIL") && !status.equals("ABORTED")) {
            throw new IllegalArgumentException("manifest result.status must be PASS/FAIL/ABORTED");
        }
        values.forEach((key, value) -> {
            if (key.endsWith("relativePath") || key.endsWith("path")) {
                QualificationV2CanonicalCodec.rejectPathValue(value);
            }
        });
        values.forEach((key, value) -> {
            if (key.startsWith("artifact.") && key.endsWith(".relativePath")) {
                final String prefix = key.substring(0, key.length() - ".relativePath".length());
                requireNonBlank(values, prefix + ".size");
                requireDigest(values, prefix + ".sha256");
                try {
                    if (Long.parseLong(values.get(prefix + ".size")) < 0) {
                        throw new IllegalArgumentException(prefix + ".size must be non-negative");
                    }
                } catch (final NumberFormatException exception) {
                    throw new IllegalArgumentException(prefix + ".size must be an integer", exception);
                }
            }
        });
    }

    private static void validateKey(final String key) {
        if (FIXED_KEYS.contains(key) || RUNTIME_KEYS.contains(key)) {
            return;
        }
        if (key.startsWith("artifact.")) {
            final String suffix = key.substring("artifact.".length());
            final int separator = suffix.indexOf('.');
            if (separator > 0
                    && separator == suffix.lastIndexOf('.')
                    && suffix.substring(0, separator).matches("[A-Za-z][A-Za-z0-9_-]*")
                    && Set.of("relativePath", "size", "sha256")
                    .contains(suffix.substring(separator + 1))) {
                return;
            }
        }
        throw new IllegalArgumentException("unknown manifest field: " + key);
    }

    private static void requireValue(
            final Map<String, String> values,
            final String key,
            final String expected) {
        if (!expected.equals(values.get(key))) {
            throw new IllegalArgumentException(key + " does not match v2 schema");
        }
    }

    private static void requireNonBlank(final Map<String, String> values, final String key) {
        final String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing manifest field: " + key);
        }
    }

    private static void requireDigest(final Map<String, String> values, final String key) {
        final String value = values.get(key);
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(key + " must be lowercase SHA-256");
        }
    }
}
