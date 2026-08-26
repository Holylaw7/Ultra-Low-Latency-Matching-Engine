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
        assertEquals("https://nvd.nist.gov/feeds/json/cve/2.0/nvdcve-2.0-{0}.json.gz",
                parsed.value("dependencyCheck.nvdDatafeedUrlTemplate"));
        assertEquals("https://nvd.nist.gov/feeds/json/cve/2.0",
                parsed.value("dependencyCheck.nvdFeedBaseUrl"));
        assertEquals("JSON_2.0_GZIP", parsed.value("dependencyCheck.nvdFeedFormat"));
        assertEquals("OFFICIAL", parsed.value("dependencyCheck.nvdFeedMode"));
        assertEquals("nvdcve-2.0-modified", parsed.value("dependencyCheck.nvdFeedName"));
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
    void preservesHistoricalV1NvdContractWithoutReinterpretingCurrentWorkflow() throws Exception {
        final Path policy = Path.of("..", "docs", "release", "ga-security-toolchain-v1.properties")
                .toAbsolutePath().normalize();
        if (!Files.isRegularFile(policy)) {
            return;
        }
        final String historical = Files.readString(policy, StandardCharsets.US_ASCII);
        assertTrue(historical.contains("schema.version=ga-security-toolchain-v1"));
        assertTrue(historical.contains("dependencyCheck.artifact="));
        assertTrue(historical.contains("dependencyCheck.nvdFeedMode=OFFICIAL"));
        assertFalse(historical.contains("OFFLINE_SUPPLY_CHAIN_SECURITY_V1"));
    }

    private static byte[] concat(final byte[] first, final byte[] second) {
        final byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
