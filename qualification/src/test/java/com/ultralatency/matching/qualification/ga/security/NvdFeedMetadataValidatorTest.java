package com.ultralatency.matching.qualification.ga.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** Tests malformed, stale and digest-mismatched NVD feed evidence fail closed. */
class NvdFeedMetadataValidatorTest {

    private static final byte[] CONTENT = "{\"vulnerabilities\":[]}".getBytes(StandardCharsets.UTF_8);

    @Test
    void acceptsOfficialMetadataAndMatchingContent() {
        final String digest = NvdFeedMetadataValidator.sha256(CONTENT);
        final NvdFeedMetadataValidator.Metadata metadata = NvdFeedMetadataValidator.parse(
                "lastModifiedDate:2026-08-25T10:00:00.000Z\n"
                        + "size:" + CONTENT.length + "\n"
                        + "zipSize:99\n"
                        + "gzSize:88\n"
                        + "sha256:" + digest + "\n");
        assertEquals(CONTENT.length, metadata.size());
        assertEquals(Duration.ofHours(2), NvdFeedMetadataValidator.validateFreshness(
                metadata, Instant.parse("2026-08-25T12:00:00.000Z"), Duration.ofHours(24)));
        NvdFeedMetadataValidator.validateContent(metadata, CONTENT);
    }

    @Test
    void canonicalizesOfficialUppercaseSha256Metadata() {
        final String digest = NvdFeedMetadataValidator.sha256(CONTENT).toUpperCase(Locale.ROOT);
        final NvdFeedMetadataValidator.Metadata metadata = NvdFeedMetadataValidator.parse(
                "lastModifiedDate:2026-08-25T10:00:00.000Z\n"
                        + "size:" + CONTENT.length + "\n"
                        + "zipSize:99\n"
                        + "gzSize:88\n"
                        + "sha256:" + digest + "\n");
        assertEquals(digest.toLowerCase(Locale.ROOT), metadata.sha256());
    }

    @Test
    void rejectsMissingDuplicateAndMalformedMetadata() {
        assertThrows(IllegalArgumentException.class, () -> NvdFeedMetadataValidator.parse(
                "lastModifiedDate:2026-08-25T10:00:00Z\nsize:1\n"));
        assertThrows(IllegalArgumentException.class, () -> NvdFeedMetadataValidator.parse(
                "lastModifiedDate:2026-08-25T10:00:00Z\nsize:1\nsize:1\n"
                        + "zipSize:1\ngzSize:1\nsha256:bad\n"));
        assertThrows(IllegalArgumentException.class, () -> NvdFeedMetadataValidator.parse(
                "lastModifiedDate:not-an-instant\nsize:1\nzipSize:1\n"
                        + "gzSize:1\nsha256:0000000000000000000000000000000000000000000000000000000000000000\n"));
    }

    @Test
    void rejectsStaleFutureAndMismatchedContent() {
        final String digest = NvdFeedMetadataValidator.sha256(CONTENT);
        final NvdFeedMetadataValidator.Metadata metadata = NvdFeedMetadataValidator.parse(
                "lastModifiedDate:2026-08-24T10:00:00Z\nsize:" + CONTENT.length
                        + "\nzipSize:99\ngzSize:88\nsha256:" + digest + "\n");
        assertThrows(IllegalArgumentException.class, () -> NvdFeedMetadataValidator.validateFreshness(
                metadata, Instant.parse("2026-08-25T12:01:00Z"), Duration.ofHours(24)));
        assertThrows(IllegalArgumentException.class, () -> NvdFeedMetadataValidator.validateFreshness(
                metadata, Instant.parse("2026-08-24T09:59:00Z"), Duration.ofHours(24)));
        assertThrows(IllegalArgumentException.class, () -> NvdFeedMetadataValidator.validateContent(
                metadata, "different".getBytes(StandardCharsets.UTF_8)));
    }
}
