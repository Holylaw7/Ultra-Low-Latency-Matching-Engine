package com.ultralatency.matching.qualification;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Atomic, immutable publication for v2 manifests and their artifact-hash sidecar. */
public final class QualificationManifestV2Store {

    private QualificationManifestV2Store() {
    }

    /** Reads and validates a canonical manifest. */
    public static QualificationManifestV2 read(final Path target) throws IOException {
        return QualificationManifestV2.read(target);
    }

    /** Publishes once; existing destinations and unsupported atomic moves fail closed. */
    public static QualificationManifestV2 publish(
            final Path target,
            final QualificationManifestV2 manifest) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(manifest, "manifest");
        final Path absoluteTarget = target.toAbsolutePath().normalize();
        final Path parent = Objects.requireNonNull(absoluteTarget.getParent(), "target parent");
        Files.createDirectories(parent);
        if (Files.exists(absoluteTarget, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("immutable evidence already exists: " + absoluteTarget);
        }
        final Path temporary = Files.createTempFile(parent, absoluteTarget.getFileName() + ".", ".tmp");
        boolean moved = false;
        try {
            writeAndForce(temporary, manifest.canonicalBytes());
            final QualificationManifestV2 readBack = QualificationManifestV2.read(temporary);
            if (!readBack.sha256Hex().equals(manifest.sha256Hex())) {
                throw new IOException("manifest read-back digest mismatch");
            }
            try {
                Files.move(temporary, absoluteTarget, StandardCopyOption.ATOMIC_MOVE);
                moved = true;
            } catch (final AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic publication is required", exception);
            }
            return readBack;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    /** Publishes a deterministic artifact hash sidecar without overwriting an existing file. */
    public static String publishArtifactHashes(
            final Path target,
            final Map<String, Path> artifacts) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(artifacts, "artifacts");
        if (artifacts.isEmpty()) {
            throw new IllegalArgumentException("artifact sidecar cannot be empty");
        }
        final TreeMap<String, String> lines = new TreeMap<>();
        for (final Map.Entry<String, Path> entry : artifacts.entrySet()) {
            final String name = entry.getKey();
            if (name == null || name.isBlank() || name.contains("\n") || name.contains("\t")) {
                throw new IllegalArgumentException("invalid artifact sidecar name");
            }
            QualificationV2CanonicalCodec.rejectPathValue(name);
            final Path file = Objects.requireNonNull(entry.getValue(), name);
            lines.put(name, QualificationArtifactHasher.sha256(file));
        }
        final StringBuilder text = new StringBuilder();
        lines.forEach((name, digest) -> text.append(name).append('\t').append(digest).append('\n'));
        final QualificationV2CanonicalCodecDigest digest =
                new QualificationV2CanonicalCodecDigest(text.toString().getBytes(
                        java.nio.charset.StandardCharsets.UTF_8));
        publishRaw(target, text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (!lines.equals(readArtifactHashes(target))) {
            throw new IOException("artifact sidecar read-back mismatch");
        }
        return digest.hex();
    }

    /** Reads and validates a deterministic artifact hash sidecar. */
    public static Map<String, String> readArtifactHashes(final Path target) throws IOException {
        final byte[] bytes = Files.readAllBytes(Objects.requireNonNull(target, "target"));
        final String text = decodeUtf8(bytes);
        if (text.startsWith("\uFEFF") || !text.isEmpty() && !text.endsWith("\n")
                || text.indexOf('\r') >= 0) {
            throw new IOException("artifact sidecar is not canonical");
        }
        final TreeMap<String, String> values = new TreeMap<>();
        if (!text.isEmpty()) {
            final String[] lines = text.split("\\n", -1);
            for (int index = 0; index < lines.length - 1; index++) {
                final String line = lines[index];
                final int separator = line.indexOf('\t');
                if (separator <= 0 || separator != line.lastIndexOf('\t')) {
                    throw new IOException("malformed artifact sidecar line");
                }
                final String name = line.substring(0, separator);
                final String digest = line.substring(separator + 1);
                if (name.isBlank() || !digest.matches("[0-9a-f]{64}")) {
                    throw new IOException("invalid artifact sidecar entry");
                }
                try {
                    QualificationV2CanonicalCodec.rejectPathValue(name);
                } catch (final IllegalArgumentException exception) {
                    throw new IOException("invalid artifact sidecar path", exception);
                }
                if (values.put(name, digest) != null) {
                    throw new IOException("duplicate artifact sidecar entry");
                }
            }
        }
        final String canonical = values.entrySet().stream()
                .map(entry -> entry.getKey() + "\t" + entry.getValue() + "\n")
                .collect(java.util.stream.Collectors.joining());
        if (!canonical.equals(text)) {
            throw new IOException("artifact sidecar is not sorted/canonical");
        }
        return Map.copyOf(values);
    }

    private static void publishRaw(final Path target, final byte[] bytes) throws IOException {
        final Path absoluteTarget = target.toAbsolutePath().normalize();
        final Path parent = Objects.requireNonNull(absoluteTarget.getParent(), "target parent");
        Files.createDirectories(parent);
        if (Files.exists(absoluteTarget, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("immutable evidence already exists: " + absoluteTarget);
        }
        final Path temporary = Files.createTempFile(parent, absoluteTarget.getFileName() + ".", ".tmp");
        boolean moved = false;
        try {
            writeAndForce(temporary, bytes);
            try {
                Files.move(temporary, absoluteTarget, StandardCopyOption.ATOMIC_MOVE);
                moved = true;
            } catch (final AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic publication is required", exception);
            }
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
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

    private static String decodeUtf8(final byte[] bytes) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (final CharacterCodingException exception) {
            throw new IOException("artifact sidecar is not valid UTF-8", exception);
        }
    }

    /** Small private digest wrapper to keep sidecar hashing independent from artifact files. */
    private record QualificationV2CanonicalCodecDigest(byte[] bytes) {
        String hex() {
            try {
                final java.security.MessageDigest digest =
                        java.security.MessageDigest.getInstance("SHA-256");
                return java.util.HexFormat.of().formatHex(digest.digest(bytes));
            } catch (final java.security.NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is required by the JDK", exception);
            }
        }
    }
}
