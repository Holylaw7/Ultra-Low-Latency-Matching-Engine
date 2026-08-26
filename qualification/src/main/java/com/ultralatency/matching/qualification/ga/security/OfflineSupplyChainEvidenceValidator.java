package com.ultralatency.matching.qualification.ga.security;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Fail-closed consistency checks for OFFLINE_SUPPLY_CHAIN_SECURITY_V1 evidence. */
public final class OfflineSupplyChainEvidenceValidator {

    /** Mandatory artifact paths in the immutable G11 evidence unit. */
    public static final Set<String> REQUIRED_ARTIFACTS = Set.of(
            "application/application-jar-provenance.txt",
            "application/matching-engine-rc.jar",
            "candidate-identity.txt",
            "dependency/runtime-dependencies.txt",
            "g11-gate-result.txt",
            "g11-offline-supply-chain-manifest.txt",
            "gitleaks/history.json",
            "gitleaks/working-tree.json",
            "java-version.txt",
            "javac-version.txt",
            "jdk-runtime-identity.txt",
            "license/runtime-license-inventory.txt",
            "maven-repository-inventory",
            "maven-version.txt",
            "policy/ga-security-toolchain-v2.properties",
            "sbom/bom.json",
            "sbom/normalized-components.txt",
            "tool-entry-sha256sums.txt");

    private OfflineSupplyChainEvidenceValidator() {
    }

    /** Validates normalized SBOM/inventory/license coordinates and required artifacts. */
    public static void validate(
            final Collection<String> sbomCoordinates,
            final Collection<String> dependencyCoordinates,
            final Collection<String> licensedCoordinates,
            final Collection<String> artifactPaths) {
        final Set<String> sbom = normalized(sbomCoordinates, "SBOM coordinates");
        final Set<String> dependencies = normalized(dependencyCoordinates,
                "dependency coordinates");
        final Set<String> licenses = normalized(licensedCoordinates, "license coordinates");
        if (sbom.isEmpty()) {
            throw new IllegalArgumentException("SBOM dependency inventory must not be empty");
        }
        if (!sbom.equals(dependencies)) {
            throw new IllegalArgumentException("SBOM and runtime dependency inventories differ");
        }
        if (!dependencies.equals(licenses)) {
            throw new IllegalArgumentException("runtime dependency and license inventories differ");
        }
        final Set<String> artifacts = normalized(artifactPaths, "artifact paths");
        if (!artifacts.containsAll(REQUIRED_ARTIFACTS)) {
            final Set<String> missing = new TreeSet<>(REQUIRED_ARTIFACTS);
            missing.removeAll(artifacts);
            throw new IllegalArgumentException("missing mandatory offline security artifacts: "
                    + missing);
        }
    }

    private static Set<String> normalized(final Collection<String> values, final String name) {
        Objects.requireNonNull(values, name);
        final Set<String> normalized = new TreeSet<>();
        for (String value : values) {
            if (value == null || value.isBlank() || !value.equals(value.trim())
                    || value.indexOf('\\') >= 0 || value.startsWith("/")
                    || value.contains("../") || value.contains("/../")) {
                throw new IllegalArgumentException(name + " contain a non-canonical value");
            }
            if (!normalized.add(value)) {
                throw new IllegalArgumentException(name + " contain a duplicate: " + value);
            }
        }
        return Set.copyOf(normalized);
    }
}
