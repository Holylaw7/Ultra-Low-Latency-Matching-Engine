package com.ultralatency.matching.qualification.ga.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Strict reader for the Human-approved offline G11 toolchain policy. */
public final class GaOfflineSupplyChainPolicy {

    /** SHA-256 of the approved canonical v2 toolchain properties file. */
    public static final String APPROVED_PROPERTIES_SHA256 =
            "7ab79aa16313ed363a6c2576a0b18dcaa545666f2478d092c1cf44b581be9c30";

    private static final Set<String> REQUIRED_KEYS = Set.of(
            "action.checkout.sha", "action.uploadArtifact.sha", "applicationJar.buildCommand",
            "applicationJar.path", "artifact.compressionLevel", "artifact.ifNoFilesFound",
            "artifact.name", "artifact.overwrite", "artifact.retentionDays",
            "candidate.productionSha", "candidate.tag", "candidate.tagObjectSha",
            "cyclonedx.artifact", "cyclonedx.goal", "cyclonedx.includeBomSerialNumber",
            "cyclonedx.includeCompileScope", "cyclonedx.includeLicenseText",
            "cyclonedx.includeProvidedScope", "cyclonedx.includeRuntimeScope",
            "cyclonedx.includeSystemScope", "cyclonedx.includeTestScope",
            "cyclonedx.jarSha256", "cyclonedx.outputFormat", "cyclonedx.outputName",
            "cyclonedx.projectType", "cyclonedx.schemaVersion", "cyclonedx.skipAttach",
            "dependencyInventory.artifact", "dependencyInventory.goal",
            "dependencyInventory.includeScope", "dependencyInventory.jarSha256",
            "dependencyInventory.outputType", "evidence.schema", "gate.id", "gate.version",
            "gitleaks.config", "gitleaks.container", "gitleaks.digest", "gitleaks.exitCode",
            "gitleaks.historyLogOpts", "gitleaks.maxTargetMegabytes",
            "gitleaks.redactPercent", "gitleaks.reportFormat", "jdk.archiveFilename",
            "jdk.archiveSha256", "jdk.archiveUrl", "jdk.checksumUrl", "jdk.distribution",
            "jdk.platform", "jdk.product", "jdk.version", "license.acceptedSpdx",
            "license.artifact", "license.encoding", "license.excludedScopes", "license.goal",
            "license.includeOptional", "license.includeTransitiveDependencies",
            "license.jarSha256", "maven.buildCommit", "maven.version", "runner.image",
            "schema.version", "secretScan.candidateBound", "secretScan.fullHistory");

    private final Map<String, String> values;
    private final String sha256;

    private GaOfflineSupplyChainPolicy(final Map<String, String> parsed, final String digest) {
        values = Collections.unmodifiableMap(new LinkedHashMap<>(parsed));
        sha256 = digest;
    }

    /** Reads and validates the exact approved v2 policy file. */
    public static GaOfflineSupplyChainPolicy load(final Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        return parse(Files.readAllBytes(path));
    }

    /** Parses and validates canonical policy bytes. */
    public static GaOfflineSupplyChainPolicy parse(final byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        final String digest = digest(bytes);
        if (!APPROVED_PROPERTIES_SHA256.equals(digest)) {
            throw new IllegalArgumentException("offline security toolchain policy hash is not approved");
        }
        if (bytes.length == 0 || bytes[bytes.length - 1] != '\n') {
            throw new IllegalArgumentException("offline security toolchain policy must end with LF");
        }
        if (bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB
                && bytes[2] == (byte) 0xBF) {
            throw new IllegalArgumentException("offline security toolchain policy must not have a BOM");
        }
        final String text = new String(bytes, StandardCharsets.US_ASCII);
        if (!java.util.Arrays.equals(bytes, text.getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("offline security toolchain policy must be ASCII");
        }
        final String[] lines = text.split("\\n", -1);
        final Map<String, String> parsed = new LinkedHashMap<>();
        String previousKey = null;
        for (int index = 0; index < lines.length - 1; index++) {
            final String line = lines[index];
            if (line.isEmpty() || line.indexOf('\r') >= 0) {
                throw new IllegalArgumentException("blank or CR policy line at " + (index + 1));
            }
            final int separator = line.indexOf('=');
            if (separator <= 0) {
                throw new IllegalArgumentException("malformed policy line at " + (index + 1));
            }
            final String key = line.substring(0, separator);
            final String value = line.substring(separator + 1);
            if (!key.matches("[a-z][a-zA-Z0-9]*(\\.[a-zA-Z0-9]+)*") || value.isEmpty()) {
                throw new IllegalArgumentException("invalid policy key/value at " + (index + 1));
            }
            if (previousKey != null && previousKey.compareTo(key) >= 0) {
                throw new IllegalArgumentException("policy keys must be strictly sorted");
            }
            if (parsed.put(key, value) != null) {
                throw new IllegalArgumentException("duplicate policy key: " + key);
            }
            previousKey = key;
        }
        final Set<String> actualKeys = new TreeSet<>(parsed.keySet());
        if (!actualKeys.equals(REQUIRED_KEYS)) {
            final List<String> missing = new ArrayList<>(REQUIRED_KEYS);
            missing.removeAll(actualKeys);
            final List<String> unknown = new ArrayList<>(actualKeys);
            unknown.removeAll(REQUIRED_KEYS);
            throw new IllegalArgumentException("policy key set mismatch; missing=" + missing
                    + ", unknown=" + unknown);
        }
        require("ga-security-toolchain-v2", parsed.get("schema.version"));
        require("G11", parsed.get("gate.id"));
        require("OFFLINE_SUPPLY_CHAIN_SECURITY_V1", parsed.get("gate.version"));
        require("g11-offline-supply-chain-evidence-v1", parsed.get("evidence.schema"));
        return new GaOfflineSupplyChainPolicy(parsed, digest);
    }

    /** Returns a policy value. */
    public String value(final String key) {
        return values.get(Objects.requireNonNull(key, "key"));
    }

    /** Returns the exact canonical property map. */
    public Map<String, String> values() {
        return values;
    }

    /** Returns the approved properties-file SHA-256. */
    public String sha256() {
        return sha256;
    }

    /** Returns accepted runtime SPDX identifiers from the frozen policy. */
    public Set<String> acceptedRuntimeLicenses() {
        return Set.of(value("license.acceptedSpdx").split(","));
    }

    private static void require(final String expected, final String actual) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("frozen policy identity mismatch");
        }
    }

    private static String digest(final byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JDK", exception);
        }
    }
}
