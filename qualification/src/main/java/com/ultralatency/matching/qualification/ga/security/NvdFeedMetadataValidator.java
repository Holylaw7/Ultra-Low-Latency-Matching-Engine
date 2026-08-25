package com.ultralatency.matching.qualification.ga.security;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Fail-closed validation rules for the official NVD JSON 2.0 feed metadata. */
public final class NvdFeedMetadataValidator {

    private static final Set<String> REQUIRED_KEYS = Set.of(
            "lastModifiedDate", "size", "zipSize", "gzSize", "sha256");

    private NvdFeedMetadataValidator() {
    }

    /** Immutable metadata values published beside an NVD feed archive. */
    public record Metadata(Instant lastModifiedDate, long size, long zipSize, long gzSize,
            String sha256) {
        public Metadata {
            lastModifiedDate = Objects.requireNonNull(lastModifiedDate, "lastModifiedDate");
            if (size < 0 || zipSize < 0 || gzSize < 0) {
                throw new IllegalArgumentException("feed sizes must be non-negative");
            }
            sha256 = requireSha256(sha256);
        }
    }

    /** Parses the strict key/value format of an NVD .meta file. */
    public static Metadata parse(final String text) {
        Objects.requireNonNull(text, "text");
        final Map<String, String> values = new HashMap<>();
        final String[] lines = text.split("\\R", -1);
        for (final String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            final int separator = line.indexOf(':');
            if (separator <= 0 || separator == line.length() - 1) {
                throw new IllegalArgumentException("malformed NVD metadata line");
            }
            final String key = line.substring(0, separator).trim();
            final String value = line.substring(separator + 1).trim();
            if (key.isEmpty() || value.isEmpty() || values.put(key, value) != null) {
                throw new IllegalArgumentException("invalid or duplicate NVD metadata field: " + key);
            }
        }
        if (!values.keySet().containsAll(REQUIRED_KEYS)) {
            throw new IllegalArgumentException("NVD metadata is missing required fields");
        }
        try {
            return new Metadata(
                    Instant.parse(values.get("lastModifiedDate")),
                    parseSize(values.get("size")),
                    parseSize(values.get("zipSize")),
                    parseSize(values.get("gzSize")),
                    values.get("sha256"));
        } catch (final RuntimeException exception) {
            throw new IllegalArgumentException("invalid NVD metadata value", exception);
        }
    }

    /** Rejects future or stale metadata using the approved measured clock. */
    public static Duration validateFreshness(
            final Metadata metadata, final Instant now, final Duration maximumAge) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(maximumAge, "maximumAge");
        if (maximumAge.isNegative() || maximumAge.isZero()) {
            throw new IllegalArgumentException("maximumAge must be positive");
        }
        final Duration age = Duration.between(metadata.lastModifiedDate(), now);
        if (age.isNegative() || age.compareTo(maximumAge) > 0) {
            throw new IllegalArgumentException("NVD metadata is outside freshness bound");
        }
        return age;
    }

    /** Validates the uncompressed JSON bytes against the .meta size and digest. */
    public static void validateContent(final Metadata metadata, final byte[] content) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(content, "content");
        if (content.length != metadata.size()) {
            throw new IllegalArgumentException("NVD content size does not match metadata");
        }
        if (!metadata.sha256().equals(sha256(content))) {
            throw new IllegalArgumentException("NVD content digest does not match metadata");
        }
    }

    /** Returns a lowercase SHA-256 digest without exposing any secret material. */
    public static String sha256(final byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JDK", exception);
        }
    }

    private static long parseSize(final String value) {
        if (!value.matches("[0-9]+")) {
            throw new IllegalArgumentException("size must be decimal");
        }
        return Long.parseLong(value);
    }

    private static String requireSha256(final String value) {
        if (value == null || !value.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("sha256 must be hexadecimal");
        }
        return value.toLowerCase(java.util.Locale.ROOT);
    }
}
