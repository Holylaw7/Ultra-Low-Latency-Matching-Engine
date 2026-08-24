package com.ultralatency.matching.qualification;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Canonical, deterministic UTF-8 codec shared by v2 qualification artifacts. */
public final class QualificationV2CanonicalCodec {

    /** Manifest wire schema identifier. */
    public static final String MANIFEST_SCHEMA = "qualification-run-manifest-v2";

    /** Campaign summary wire schema identifier. */
    public static final String CAMPAIGN_SCHEMA = "qualification-campaign-summary-v1";

    /** Canonical key/value encoding version. */
    public static final String CANONICALIZATION_VERSION = "ascii-key-value-lf-v1";

    private static final Pattern KEY = Pattern.compile("[A-Za-z][A-Za-z0-9._-]*");
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private QualificationV2CanonicalCodec() {
    }

    /** Encodes a flat field map as sorted ASCII keys and percent-encoded UTF-8 values. */
    public static byte[] encode(final Map<String, String> fields) {
        Objects.requireNonNull(fields, "fields");
        final List<String> keys = new ArrayList<>(fields.keySet());
        keys.forEach(QualificationV2CanonicalCodec::validateKey);
        Collections.sort(keys);
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (final String key : keys) {
            final String value = Objects.requireNonNull(fields.get(key), key);
            final byte[] keyBytes = key.getBytes(StandardCharsets.US_ASCII);
            output.writeBytes(keyBytes);
            output.write('=');
            output.writeBytes(percentEncode(value));
            output.write('\n');
        }
        return output.toByteArray();
    }

    /** Decodes and strictly validates canonical bytes, rejecting non-canonical input. */
    public static Map<String, String> decode(final byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        final String text = decodeUtf8(bytes);
        if (text.startsWith("\uFEFF") || text.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("BOM/CR is not permitted in canonical evidence");
        }
        if (!text.isEmpty() && !text.endsWith("\n")) {
            throw new IllegalArgumentException("canonical evidence must end with LF");
        }
        final Map<String, String> fields = new LinkedHashMap<>();
        if (!text.isEmpty()) {
            final String[] lines = text.split("\\n", -1);
            for (int index = 0; index < lines.length - 1; index++) {
                final String line = lines[index];
                final int separator = line.indexOf('=');
                if (separator <= 0 || separator != line.lastIndexOf('=')) {
                    throw new IllegalArgumentException("malformed canonical field line");
                }
                final String key = line.substring(0, separator);
                validateKey(key);
                if (fields.containsKey(key)) {
                    throw new IllegalArgumentException("duplicate canonical key: " + key);
                }
                fields.put(key, percentDecode(line.substring(separator + 1)));
            }
        }
        final byte[] canonical = encode(fields);
        if (!MessageDigest.isEqual(bytes, canonical)) {
            throw new IllegalArgumentException("evidence is not in canonical form");
        }
        return Map.copyOf(fields);
    }

    /** Returns a lowercase SHA-256 digest of canonical fields. */
    public static String sha256(final Map<String, String> fields) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(encode(fields)));
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JDK", exception);
        }
    }

    /** Rejects absolute or parent-traversal paths in evidence values. */
    public static void rejectPathValue(final String value) {
        Objects.requireNonNull(value, "value");
        if (value.startsWith("/") || value.startsWith("\\")
                || value.matches("[A-Za-z]:[\\\\/].*")
                || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("absolute/path traversal value is not allowed");
        }
        final String[] segments = value.replace('\\', '/').split("/", -1);
        for (final String segment : segments) {
            if (segment.equals("..")) {
                throw new IllegalArgumentException("absolute/path traversal value is not allowed");
            }
        }
    }

    private static void validateKey(final String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("invalid canonical key: " + key);
        }
    }

    private static byte[] percentEncode(final String value) {
        final byte[] input = value.getBytes(StandardCharsets.UTF_8);
        final ByteArrayOutputStream output = new ByteArrayOutputStream(input.length);
        for (final byte item : input) {
            final int unsigned = item & 0xFF;
            if ((unsigned >= 'A' && unsigned <= 'Z')
                    || (unsigned >= 'a' && unsigned <= 'z')
                    || (unsigned >= '0' && unsigned <= '9')
                    || unsigned == '-' || unsigned == '.' || unsigned == '_'
                    || unsigned == '~') {
                output.write(unsigned);
            } else {
                output.write('%');
                output.write(HEX[unsigned >>> 4]);
                output.write(HEX[unsigned & 0x0F]);
            }
        }
        return output.toByteArray();
    }

    private static String percentDecode(final String value) {
        final ByteArrayOutputStream output = new ByteArrayOutputStream(value.length());
        for (int index = 0; index < value.length(); index++) {
            final char current = value.charAt(index);
            if (current == '%') {
                if (index + 2 >= value.length()) {
                    throw new IllegalArgumentException("truncated percent escape");
                }
                final char high = value.charAt(index + 1);
                final char low = value.charAt(index + 2);
                if (!isUpperHex(high) || !isUpperHex(low)) {
                    throw new IllegalArgumentException("percent escapes must use uppercase hex");
                }
                output.write((hexValue(high) << 4) | hexValue(low));
                index += 2;
            } else if (current <= 0x7F && isUnreserved(current)) {
                output.write(current);
            } else {
                throw new IllegalArgumentException("non-canonical value encoding");
            }
        }
        return decodeUtf8(output.toByteArray());
    }

    private static boolean isUnreserved(final char value) {
        return (value >= 'A' && value <= 'Z')
                || (value >= 'a' && value <= 'z')
                || (value >= '0' && value <= '9')
                || value == '-' || value == '.' || value == '_' || value == '~';
    }

    private static boolean isUpperHex(final char value) {
        return (value >= '0' && value <= '9') || (value >= 'A' && value <= 'F');
    }

    private static int hexValue(final char value) {
        return value <= '9' ? value - '0' : value - 'A' + 10;
    }

    private static String decodeUtf8(final byte[] bytes) {
        try {
            final CharBuffer chars = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return chars.toString();
        } catch (final CharacterCodingException exception) {
            throw new IllegalArgumentException("evidence is not valid UTF-8", exception);
        }
    }
}
