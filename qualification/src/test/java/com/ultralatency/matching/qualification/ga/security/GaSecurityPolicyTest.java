package com.ultralatency.matching.qualification.ga.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests exact policy provenance and canonical lexical rules. */
class GaSecurityPolicyTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsApprovedPolicyAndExposesFrozenValues() throws Exception {
        final Path policy = Path.of("..", "docs", "release", "ga-security-toolchain-v1.properties")
                .toAbsolutePath().normalize();
        if (!Files.isRegularFile(policy)) {
            return;
        }
        final GaSecurityPolicy parsed = GaSecurityPolicy.load(policy);
        assertEquals(GaSecurityPolicy.APPROVED_PROPERTIES_SHA256, parsed.sha256());
        assertEquals("ubuntu-24.04", parsed.value("runner.image"));
        assertEquals("microsoft-jdk-21.0.12-linux-x64.tar.gz",
                parsed.value("jdk.archiveFilename"));
        assertEquals("f2a84ad31ebeaf3a26252dd86a4a8e1b74aefb6bfc8e55fd20190110d1353c0f",
                parsed.value("jdk.archiveSha256"));
        assertEquals("linux-x64", parsed.value("jdk.platform"));
        assertEquals("8000", parsed.value("dependencyCheck.nvdApiDelayAnonymousMillis"));
        assertEquals("3500", parsed.value("dependencyCheck.nvdApiDelayAuthenticatedMillis"));
        assertEquals("OPTIONAL", parsed.value("dependencyCheck.nvdCredentialMode"));
        assertTrue(parsed.acceptsRuntimeLicense("Apache-2.0"));
        assertTrue(!parsed.acceptsRuntimeLicense("GPL-3.0-only"));
    }

    @Test
    void rejectsEveryMutationBeforeItCanBecomePolicy() throws Exception {
        final Path source = Path.of("..", "docs", "release", "ga-security-toolchain-v1.properties")
                .toAbsolutePath().normalize();
        if (!Files.isRegularFile(source)) {
            return;
        }
        final byte[] bytes = Files.readAllBytes(source);
        for (byte[] mutation : new byte[][] {
            (new String(bytes, StandardCharsets.US_ASCII) + "unknown.key=value\n")
                    .getBytes(StandardCharsets.US_ASCII),
            new String(bytes, StandardCharsets.US_ASCII).replaceFirst("\\n", "\r\n")
                    .getBytes(StandardCharsets.US_ASCII),
            concat(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, bytes),
            new String(bytes, StandardCharsets.US_ASCII).replaceFirst(
                    "runner.image=ubuntu-24.04", "runner.image=ubuntu-22.04")
                    .getBytes(StandardCharsets.US_ASCII)
        }) {
            assertThrows(IllegalArgumentException.class, () -> GaSecurityPolicy.parse(mutation));
        }
    }

    @Test
    void keepsNvdCredentialModeOptionalAndExplicit() throws Exception {
        final Path workflow = Path.of("..", ".github", "workflows", "ga-security.yml")
                .toAbsolutePath().normalize();
        if (!Files.isRegularFile(workflow)) {
            return;
        }
        final String yaml = Files.readString(workflow, StandardCharsets.UTF_8);
        assertTrue(yaml.contains("mode=ANONYMOUS"));
        assertTrue(yaml.contains("mode=AUTHENTICATED"));
        assertTrue(yaml.contains("delay=8000"));
        assertTrue(yaml.contains("delay=3500"));
        assertTrue(yaml.contains("credential.mode=%s"));
        assertTrue(yaml.contains("nvd.credentialMode=%s"));
        assertTrue(yaml.contains("nvd-configuration-identity.txt"));
        assertTrue(yaml.contains("configuration.identitySha256=%s"));
        assertTrue(yaml.contains("nvd.configurationIdentitySha256=%s"));
        assertTrue(yaml.contains("dependencyCheck.nvdCredentialMode=%s"));
        assertTrue(yaml.contains("dependency-check-report.json"));
        assertTrue(yaml.contains("dependency-check-report.sarif"));
        assertTrue(yaml.contains("name: Resolve NVD credential mode"));
        assertTrue(yaml.contains("name: Execute authenticated Dependency-Check scan"));
        assertTrue(yaml.contains("name: Execute anonymous Dependency-Check scan"));
        assertTrue(yaml.contains("name: Finalize approved vulnerability evidence"));

        final String modeStep = section(yaml, "- name: Resolve NVD credential mode",
                "- name: Prepare approved vulnerability evidence");
        final String authenticatedStep = section(yaml,
                "- name: Execute authenticated Dependency-Check scan",
                "- name: Execute anonymous Dependency-Check scan");
        final String anonymousStep = section(yaml,
                "- name: Execute anonymous Dependency-Check scan",
                "- name: Finalize approved vulnerability evidence");

        assertTrue(modeStep.contains("QUALIFICATION_NVD_KEY: ${{ secrets.NVD_API_KEY }}"));
        assertTrue(authenticatedStep.contains("NVD_API_KEY: ${{ secrets.NVD_API_KEY }}"));
        assertTrue(authenticatedStep.contains("-DnvdApiKeyEnvironmentVariable=NVD_API_KEY"));
        assertTrue(authenticatedStep.contains("continue-on-error: true"));
        assertTrue(anonymousStep.contains("env -u NVD_API_KEY mvn"));
        assertTrue(anonymousStep.contains("${NVD_API_KEY+x}"));
        assertFalse(anonymousStep.contains("NVD_API_KEY: ${{ secrets.NVD_API_KEY }}"));
        assertFalse(anonymousStep.contains("nvdApiKeyEnvironmentVariable"));
        assertFalse(anonymousStep.contains("-DnvdApiKey"));
        assertTrue(yaml.contains("NVD_API_KEY_ENVIRONMENT_VARIABLE"));
        assertTrue(yaml.contains("dependency-check-args"));
        assertTrue(yaml.contains("nvd-scan-exit-code"));
    }

    private static String section(final String source, final String startMarker,
            final String endMarker) {
        final int start = source.indexOf(startMarker);
        final int end = source.indexOf(endMarker, start + startMarker.length());
        assertTrue(start >= 0, "Missing workflow marker: " + startMarker);
        assertTrue(end > start, "Missing workflow marker: " + endMarker);
        return source.substring(start, end);
    }

    private static byte[] concat(final byte[] first, final byte[] second) {
        final byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
