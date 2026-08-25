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

/**
 * Strict reader for the Human-approved Phase 11 security toolchain policy.
 *
 * <p>The policy is an evidence input, not a general-purpose properties file:
 * it must be the exact approved ASCII/LF byte sequence, contain no duplicate
 * or unknown keys, and remain in lexical key order.</p>
 */
public final class GaSecurityPolicy {

    /** SHA-256 of the approved canonical toolchain properties file. */
    public static final String APPROVED_PROPERTIES_SHA256 =
            "7c6e36e0bc045fad38255be65a519ee8db19b877d786a5be26d399efbf4e5554";

    private static final Set<String> REQUIRED_KEYS = Set.of(
            "action.checkout.sha", "action.setupJava.sha", "checkout.fetchDepth",
            "checkout.fetchTags", "checkout.persistCredentials", "cyclonedx.artifact",
            "cyclonedx.goal", "cyclonedx.includeBomSerialNumber", "cyclonedx.includeCompileScope",
            "cyclonedx.includeLicenseText", "cyclonedx.includeProvidedScope",
            "cyclonedx.includeRuntimeScope", "cyclonedx.includeSystemScope",
            "cyclonedx.includeTestScope", "cyclonedx.jarSha256", "cyclonedx.outputFormat",
            "cyclonedx.outputName", "cyclonedx.projectType", "cyclonedx.schemaVersion",
            "cyclonedx.skipAttach", "dependencyCheck.artifact", "dependencyCheck.autoUpdate",
            "dependencyCheck.dataFreshnessHours", "dependencyCheck.enableExperimental",
            "dependencyCheck.enableRetired", "dependencyCheck.failBuildOnCvss",
            "dependencyCheck.failBuildOnUnusedSuppressionRule", "dependencyCheck.failOnError",
            "dependencyCheck.formats", "dependencyCheck.goal", "dependencyCheck.jarSha256",
            "dependencyCheck.scanDependencies", "dependencyCheck.scanPlugins",
            "dependencyCheck.skipDependencyManagement", "dependencyCheck.skipProvidedScope",
            "dependencyCheck.skipRuntimeScope", "dependencyCheck.skipSystemScope",
            "dependencyCheck.skipTestScope", "dependencyCheck.versionCheckEnabled",
            "gitleaks.config", "gitleaks.container", "gitleaks.digest", "gitleaks.exitCode",
            "gitleaks.historyLogOpts", "gitleaks.maxTargetMegabytes", "gitleaks.redactPercent",
            "gitleaks.reportFormat", "jdk.distribution", "jdk.version", "license.acceptedSpdx",
            "license.artifact", "license.encoding", "license.excludedScopes", "license.goal",
            "license.includeOptional", "license.includeTransitiveDependencies", "license.jarSha256",
            "maven.buildCommit", "maven.version", "reproducibility.artifactPath",
            "reproducibility.buildCommand", "reproducibility.buildCount",
            "reproducibility.candidateProductionSha", "reproducibility.candidateTag",
            "reproducibility.candidateTagObjectSha", "reproducibility.independentMavenRepositories",
            "reproducibility.mavenOpts", "reproducibility.projectBuildOutputTimestamp",
            "reproducibility.sourceTreeDigest", "runner.image", "schema.version");

    private final Map<String, String> values;
    private final String sha256;

    private GaSecurityPolicy(final Map<String, String> parsed, final String digest) {
        values = Collections.unmodifiableMap(new LinkedHashMap<>(parsed));
        sha256 = digest;
    }

    /** Reads and validates the exact approved policy file. */
    public static GaSecurityPolicy load(final Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        return parse(Files.readAllBytes(path));
    }

    /** Parses and validates canonical policy bytes. */
    public static GaSecurityPolicy parse(final byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        final String digest = digest(bytes);
        if (!APPROVED_PROPERTIES_SHA256.equals(digest)) {
            throw new IllegalArgumentException("security toolchain policy hash is not approved");
        }
        if (bytes.length == 0 || bytes[bytes.length - 1] != '\n') {
            throw new IllegalArgumentException("security toolchain policy must end with LF");
        }
        if (bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB
                && bytes[2] == (byte) 0xBF) {
            throw new IllegalArgumentException("security toolchain policy must not have a BOM");
        }
        final String text = new String(bytes, StandardCharsets.US_ASCII);
        if (!java.util.Arrays.equals(bytes, text.getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("security toolchain policy must be ASCII");
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
        return new GaSecurityPolicy(parsed, digest);
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

    /** Returns whether a runtime license identifier is explicitly accepted. */
    public boolean acceptsRuntimeLicense(final String spdx) {
        return spdx != null && acceptedRuntimeLicenses().contains(spdx);
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
