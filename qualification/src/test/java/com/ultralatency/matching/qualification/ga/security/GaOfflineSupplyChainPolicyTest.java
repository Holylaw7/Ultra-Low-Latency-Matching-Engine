package com.ultralatency.matching.qualification.ga.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
        assertEquals("repository-relative-v1", parsed.value("gitleaks.candidatePathContract"));
        assertEquals("working-directory", parsed.value("gitleaks.candidateScanMode"));
        assertEquals("docs/release/ga-gitleaks-false-positive-dispositions-v1.properties",
                parsed.value("gitleaks.dispositionManifest"));
        assertEquals("0854c43f9138d8073f640fe1e37f97c7d482f01bcbe3e8280534ee3cbc70466c",
                parsed.value("gitleaks.dispositionManifestSha256"));
        assertEquals("ga-gitleaks-false-positive-disposition-v1",
                parsed.value("gitleaks.dispositionSchema"));
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
        assertTrue(OfflineSupplyChainEvidenceValidator.REQUIRED_ARTIFACTS.contains(
                "gitleaks/approved-dispositions.properties"));
        assertTrue(OfflineSupplyChainEvidenceValidator.REQUIRED_ARTIFACTS.contains(
                "gitleaks/disposition-evaluation.txt"));
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
        assertTrue(yaml.contains("--workdir=/repo"));
        assertTrue(yaml.contains("dir . --max-target-megabytes=10"));
        assertFalse(yaml.contains("dir /repo --max-target-megabytes=10"));
        assertTrue(yaml.contains("-pl core -am package -DskipTests"));
        assertTrue(yaml.contains("org.codehaus.mojo:license-maven-plugin:2.7.1:aggregate-third-party-report"));
        assertTrue(yaml.contains("-Dlicense.executeOnlyOnRootModule=true"));
        assertTrue(yaml.contains("-Dlicense.reactorAlsoMake=true"));
        assertTrue(yaml.contains("-Dlicense.reactorProject=core"));
        assertTrue(yaml.contains("target/reports/aggregate-third-party-report.html"));
        assertTrue(yaml.contains("license/plugin-reports/aggregate-third-party-report.html"));
        assertTrue(yaml.contains("Validate root-reactor license report"));
        assertTrue(yaml.contains("from html.parser import HTMLParser"));
        assertTrue(yaml.contains("report.parseable=true"));
        assertTrue(yaml.contains("report.runtimeCoordinatesMatch=true"));
        assertTrue(yaml.contains("report-validation.txt"));
        assertTrue(yaml.contains("license report HTML parsing failed"));
        assertTrue(yaml.contains("(?:\\s+--\\s+.*)?\\s*$"));
        assertTrue(yaml.contains("approved-dispositions.properties"));
        assertTrue(yaml.contains("gitleaks_disposition_evaluator.py"));
        assertTrue(yaml.contains("HISTORY_EXIT=$?"));
        assertTrue(yaml.contains("CANDIDATE_EXIT=$?"));
        assertTrue(yaml.contains("candidate-bound secret scans"));
        assertTrue(yaml.contains("disposition-evaluation.txt"));
        assertFalse(yaml.contains(".gitleaksignore"));
        assertTrue(yaml.contains("test ! -e core/target/reports"));
        assertFalse(yaml.contains("test -d core/target/reports"));
        assertFalse(yaml.contains("cp -R core/target/reports"));
        assertTrue(yaml.contains("-pl core"));
        assertFalse(yaml.contains("-f core/pom.xml"));
        assertFalse(yaml.contains("dependency-check-maven"));
        assertFalse(yaml.contains("NVD_API_KEY"));
        assertFalse(yaml.contains("nvd.nist.gov"));
    }

    @Test
    void dispositionEvaluatorIsExplicitlyFailClosed() throws Exception {
        final Path evaluator = Path.of("src", "main", "python",
                "gitleaks_disposition_evaluator.py").toAbsolutePath().normalize();
        if (!Files.isRegularFile(evaluator)) {
            return;
        }
        final String source = Files.readString(evaluator, StandardCharsets.UTF_8);
        assertTrue(source.contains("parse_manifest"));
        assertTrue(source.contains("candidateBoundScan.executed=true"));
        assertTrue(source.contains("seen.issubset(expected)"));
        assertTrue(source.contains("unapproved {scope} Gitleaks finding count is non-zero"));
        assertTrue(source.contains("DEMONSTRABLE_NON_SECRET"));
        assertFalse(source.contains("Match"));
        assertFalse(source.contains("Secret"));
    }

    @Test
    void everyWorkflowRunDefinesShellVariablesLocally() throws Exception {
        final Path workflow = Path.of("..", ".github", "workflows", "ga-security.yml")
                .toAbsolutePath().normalize();
        if (!Files.isRegularFile(workflow)) {
            return;
        }
        final String yaml = Files.readString(workflow, StandardCharsets.UTF_8);
        final Set<String> external = Set.of(
                "ARTIFACT_DIGEST", "ARTIFACT_ID", "ARTIFACT_URL", "GITHUB_ENV",
                "GITHUB_PATH", "GITHUB_STEP_SUMMARY", "GITHUB_WORKSPACE", "PATH",
                "RUNNER_TEMP");
        final Pattern reference = Pattern.compile("\\$\\{?([A-Za-z_][A-Za-z0-9_]*)");
        final Pattern assignment = Pattern.compile(
                "(?:^|[;()\\s])(?:export\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s*=");
        final Pattern declaration = Pattern.compile(
                "\\bdeclare\\s+(?:-[A-Za-z]+\\s+)?([A-Za-z_][A-Za-z0-9_]*)");
        final Pattern loop = Pattern.compile(
                "\\bfor\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+in\\b");
        final Pattern read = Pattern.compile(
                "\\bread(?:\\s+-[^\\s]+)*\\s+([A-Za-z_][A-Za-z0-9_]*)");
        final List<String> blocks = shellRunBlocks(yaml);
        assertTrue(blocks.size() >= 10, "expected all GA security run blocks");
        for (int index = 0; index < blocks.size(); index++) {
            final String block = blocks.get(index);
            final Set<String> defined = new HashSet<>();
            collect(assignment, block, defined);
            collect(declaration, block, defined);
            collect(loop, block, defined);
            collect(read, block, defined);
            final Matcher references = reference.matcher(block);
            while (references.find()) {
                final String variable = references.group(1);
                assertTrue(defined.contains(variable) || external.contains(variable),
                        "run block " + (index + 1)
                                + " references undeclared shell variable " + variable);
            }
        }
    }

    private static void collect(
            final Pattern pattern,
            final String block,
            final Set<String> values) {
        final Matcher matcher = pattern.matcher(block);
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
    }

    private static List<String> shellRunBlocks(final String yaml) {
        final List<String> blocks = new ArrayList<>();
        StringBuilder current = null;
        int runIndent = -1;
        for (String line : yaml.split("\\R", -1)) {
            final int indent = leadingSpaces(line);
            final String trimmed = line.trim();
            if (current == null) {
                if ("run: |".equals(trimmed)) {
                    current = new StringBuilder();
                    runIndent = indent;
                }
            } else if (trimmed.isEmpty() || indent > runIndent) {
                if (trimmed.isEmpty()) {
                    current.append('\n');
                } else {
                    current.append(line.substring(Math.min(line.length(), runIndent + 2)))
                            .append('\n');
                }
            } else {
                blocks.add(current.toString());
                current = null;
                runIndent = -1;
            }
        }
        if (current != null) {
            blocks.add(current.toString());
        }
        return blocks;
    }

    private static int leadingSpaces(final String value) {
        int count = 0;
        while (count < value.length() && value.charAt(count) == ' ') {
            count++;
        }
        return count;
    }
}
