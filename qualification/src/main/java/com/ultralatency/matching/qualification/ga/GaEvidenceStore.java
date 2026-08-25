package com.ultralatency.matching.qualification.ga;

import com.ultralatency.matching.qualification.QualificationArtifactHasher;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable, force-before-publish storage for GA evidence documents. */
public final class GaEvidenceStore {

    private GaEvidenceStore() {
    }

    /** Publishes one validated evidence document exactly once and returns its SHA-256. */
    public static String publish(
            final Path target,
            final GaEvidenceCodec.Schema schema,
            final Map<String, String> fields) throws IOException {
        Objects.requireNonNull(schema, "schema");
        final byte[] bytes = GaEvidenceCodec.encode(schema, fields);
        final Path temporary = prepare(target, bytes);
        boolean moved = false;
        try {
            final Map<String, String> readBack = read(temporary, schema);
            final byte[] readBackBytes = GaEvidenceCodec.encode(schema, readBack);
            if (!java.security.MessageDigest.isEqual(bytes, readBackBytes)) {
                throw new IOException("evidence read-back mismatch");
            }
            movePrepared(temporary, target);
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
        return GaEvidenceCodec.sha256Bytes(schema, bytes);
    }

    /** Reads and strictly validates one evidence document. */
    public static Map<String, String> read(
            final Path target, final GaEvidenceCodec.Schema schema) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(schema, "schema");
        try {
            return GaEvidenceCodec.decode(schema, Files.readAllBytes(target));
        } catch (final IllegalArgumentException exception) {
            throw new IOException("invalid GA evidence document: " + target, exception);
        }
    }

    /** Publishes a sorted two-space SHA256SUMS-style sidecar exactly once. */
    public static String publishArtifactSidecar(
            final Path target, final Map<String, Path> artifacts) throws IOException {
        Objects.requireNonNull(artifacts, "artifacts");
        if (artifacts.isEmpty()) {
            throw new IllegalArgumentException("artifact sidecar cannot be empty");
        }
        final TreeMap<String, String> expected = new TreeMap<>();
        for (Map.Entry<String, Path> entry : artifacts.entrySet()) {
            validateBasename(entry.getKey());
            expected.put(entry.getKey(), QualificationArtifactHasher.sha256(
                    Objects.requireNonNull(entry.getValue(), entry.getKey())));
        }
        final StringBuilder text = new StringBuilder();
        expected.forEach((name, digest) -> text.append(digest).append("  ")
                .append(name).append('\n'));
        final byte[] bytes = text.toString().getBytes(StandardCharsets.US_ASCII);
        final Path temporary = prepare(target, bytes);
        boolean moved = false;
        try {
            final Map<String, String> actual = readArtifactSidecar(temporary);
            if (!expected.equals(actual)) {
                throw new IOException("artifact sidecar read-back mismatch");
            }
            movePrepared(temporary, target);
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
        return digest(bytes);
    }

    /** Reads and validates a sorted two-space SHA256SUMS-style sidecar. */
    public static Map<String, String> readArtifactSidecar(final Path target) throws IOException {
        final byte[] bytes = Files.readAllBytes(Objects.requireNonNull(target, "target"));
        final String text = new String(bytes, StandardCharsets.US_ASCII);
        if (!java.util.Arrays.equals(bytes, text.getBytes(StandardCharsets.US_ASCII))
                || text.indexOf('\r') >= 0 || !text.endsWith("\n")) {
            throw new IOException("artifact sidecar is not canonical ASCII");
        }
        final TreeMap<String, String> result = new TreeMap<>();
        final String[] lines = text.split("\n", -1);
        for (int index = 0; index < lines.length - 1; index++) {
            final String line = lines[index];
            if (line.length() < 68 || line.charAt(64) != ' '
                    || line.charAt(65) != ' ') {
                throw new IOException("malformed artifact sidecar line");
            }
            final String digest = line.substring(0, 64);
            final String name = line.substring(66);
            if (!digest.matches("[0-9a-f]{64}")) {
                throw new IOException("invalid artifact digest");
            }
            validateBasename(name);
            if (result.put(name, digest) != null) {
                throw new IOException("duplicate artifact sidecar name");
            }
        }
        final StringBuilder canonical = new StringBuilder();
        result.forEach((name, digest) -> canonical.append(digest).append("  ")
                .append(name).append('\n'));
        if (!canonical.toString().equals(text)) {
            throw new IOException("artifact sidecar is not sorted/canonical");
        }
        return Map.copyOf(result);
    }

    private static Path prepare(final Path target, final byte[] bytes) throws IOException {
        Objects.requireNonNull(target, "target");
        final Path absolute = target.toAbsolutePath().normalize();
        final Path parent = Objects.requireNonNull(absolute.getParent(), "target parent");
        Files.createDirectories(parent);
        if (Files.exists(absolute, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("immutable evidence already exists: " + absolute);
        }
        final Path temporary = Files.createTempFile(parent, absolute.getFileName() + ".", ".tmp");
        try {
            writeAndForce(temporary, bytes);
            return temporary;
        } catch (final IOException exception) {
            Files.deleteIfExists(temporary);
            throw exception;
        }
    }

    private static void movePrepared(final Path temporary, final Path target) throws IOException {
        final Path absolute = target.toAbsolutePath().normalize();
        if (Files.exists(absolute, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("immutable evidence already exists: " + absolute);
        }
        try {
            Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE);
        } catch (final AtomicMoveNotSupportedException exception) {
            throw new IOException("atomic evidence publication is required", exception);
        }
    }

    private static void writeAndForce(final Path path, final byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(
                path, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            final ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    private static void validateBasename(final String name) {
        if (name == null || name.isBlank() || name.length() > 240
                || name.contains("/") || name.contains("\\")
                || name.equals(".") || name.equals("..")
                || name.contains("\n") || name.contains("\r")) {
            throw new IllegalArgumentException("invalid artifact basename");
        }
    }

    private static String digest(final byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (final java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JDK", exception);
        }
    }
}
