package com.ultralatency.matching.qualification.ga.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests the amended offline G11 policy and evidence boundary. */
class GaOfflineSupplyChainPolicyTest {

    @Test
    void acceptsApprovedV2WithoutNvdOrDependencyCheck() throws Exception {
        final Path policy = Path.of("..", "docs", "release",
                "ga-security-toolchain-v2.properties").toAbsolutePath().normalize();
        if (!Files.isRegularFile(policy)) {
            return;
        }
        final GaOfflineSupplyChainPolicy parsed = GaOfflineSupplyChainPolicy.load(policy);
        assertEquals(GaOfflineSupplyChainPolicy.APPROVED_PROPERTIES_SHA256, parsed.sha256());
        assertEquals("OFFLINE_SUPPLY_CHAIN_SECURITY_V1", parsed.value("gate.version"));
        assertEquals("g11-offline-supply-chain-evidence-v1", parsed.value("evidence.schema"));
        assertTrue(parsed.acceptedRuntimeLicenses().contains("Apache-2.0"));
        assertFalse(parsed.values().keySet().stream().anyMatch(key -> key.startsWith("dependencyCheck")));
        assertFalse(parsed.values().keySet().stream().anyMatch(key -> key.startsWith("nvd")));
        assertEquals("mvn|-B|-ntp|-pl|core|-am|package|-DskipTests",
                parsed.value("applicationJar.buildCommand"));
        assertEquals("true", parsed.value("license.executeOnlyOnRootModule"));
        assertEquals("true", parsed.value("license.reactorAlsoMake"));
        assertEquals("core", parsed.value("license.reactorProject"));
        assertEquals("target/reports/aggregate-third-party-report.html",
                parsed.value("license.reportPath"));
    }

    @Test
    void rejectsMutationAndNvdBackfill() throws Exception {
        final Path policy = Path.of("..", "docs", "release",
                "ga-security-toolchain-v2.properties").toAbsolutePath().normalize();
        if (!Files.isRegularFile(policy)) {
            return;
        }
        final byte[] approved = Files.readAllBytes(policy);
        final byte[] mutated = new String(approved, StandardCharsets.US_ASCII)
                .replace("gate.id=G11", "gate.id=G10").getBytes(StandardCharsets.US_ASCII);
        final byte[] nvdBackfill = (new String(approved, StandardCharsets.US_ASCII)
                + "nvd.freshnessHours=24\n").getBytes(StandardCharsets.US_ASCII);
        assertThrows(IllegalArgumentException.class,
                () -> GaOfflineSupplyChainPolicy.parse(mutated));
        assertThrows(IllegalArgumentException.class,
                () -> GaOfflineSupplyChainPolicy.parse(nvdBackfill));
    }

    @Test
    void validatesConjunctiveInventoryAndArtifactContract() {
        final List<String> coordinates = List.of("group:artifact:1.0", "group:other:2.0");
        final List<String> artifacts = new ArrayList<>(
                OfflineSupplyChainEvidenceValidator.REQUIRED_ARTIFACTS);
        assertTrue(OfflineSupplyChainEvidenceValidator.REQUIRED_ARTIFACTS.contains(
                "license/plugin-reports/aggregate-third-party-report.html"));
        OfflineSupplyChainEvidenceValidator.validate(
                coordinates, coordinates, coordinates, artifacts);
        assertThrows(IllegalArgumentException.class,
                () -> OfflineSupplyChainEvidenceValidator.validate(
                        coordinates, List.of("group:artifact:1.0"), coordinates, artifacts));
        assertThrows(IllegalArgumentException.class,
                () -> OfflineSupplyChainEvidenceValidator.validate(
                        coordinates, coordinates, List.of("group:artifact:1.0"), artifacts));
        artifacts.remove("gitleaks/history.json");
        assertThrows(IllegalArgumentException.class,
                () -> OfflineSupplyChainEvidenceValidator.validate(
                        coordinates, coordinates, coordinates, artifacts));
    }

    @Test
    void workflowExcludesNormativeNvdAndKeepsFailClosedEvidence() throws Exception {
        final Path workflow = Path.of("..", ".github", "workflows", "ga-security.yml")
                .toAbsolutePath().normalize();
        if (!Files.isRegularFile(workflow)) {
            return;
        }
        final String yaml = Files.readString(workflow, StandardCharsets.UTF_8);
        assertTrue(yaml.contains("OFFLINE_SUPPLY_CHAIN_SECURITY_V1"));
        assertTrue(yaml.contains("g11-offline-supply-chain-evidence-v1"));
        assertTrue(yaml.contains("Validate SBOM and normalized supply-chain inventories"));
        assertTrue(yaml.contains("sha256sum --check --strict SHA256SUMS"));
        assertTrue(yaml.contains("if-no-files-found: error"));
        assertTrue(yaml.contains("gitleaks/gitleaks@sha256:"));
        assertTrue(yaml.contains("-pl core -am package -DskipTests"));
        assertTrue(yaml.contains("org.codehaus.mojo:license-maven-plugin:2.7.1:aggregate-third-party-report"));
        assertTrue(yaml.contains("-Dlicense.executeOnlyOnRootModule=true"));
        assertTrue(yaml.contains("-Dlicense.reactorAlsoMake=true"));
        assertTrue(yaml.contains("-Dlicense.reactorProject=core"));
        assertTrue(yaml.contains("target/reports/aggregate-third-party-report.html"));
        assertTrue(yaml.contains("license/plugin-reports/aggregate-third-party-report.html"));
        assertTrue(yaml.contains("test ! -e core/target/reports"));
        assertFalse(yaml.contains("test -d core/target/reports"));
        assertFalse(yaml.contains("cp -R core/target/reports"));
        assertTrue(yaml.contains("-pl core"));
        assertFalse(yaml.contains("-f core/pom.xml"));
        assertFalse(yaml.contains("dependency-check-maven"));
        assertFalse(yaml.contains("NVD_API_KEY"));
        assertFalse(yaml.contains("nvd.nist.gov"));
    }
}
