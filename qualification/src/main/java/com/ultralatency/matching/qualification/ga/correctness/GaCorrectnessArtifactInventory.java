package com.ultralatency.matching.qualification.ga.correctness;

import com.ultralatency.matching.qualification.QualificationArtifactHasher;
import com.ultralatency.matching.qualification.QualificationEvidencePublication;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Builds the bounded, canonical inventory used by one physical case view. */
final class GaCorrectnessArtifactInventory {

    static final int MAX_ARTIFACTS = 1000;

    private GaCorrectnessArtifactInventory() {
    }

    /** One immutable regular-file reference. */
    record Artifact(String path, long size, String sha256) {
    }

    /** Inventory publication and references to every payload member. */
    record Published(List<Artifact> artifacts, Path path, long size, String sha256) {
        Published {
            artifacts = List.copyOf(Objects.requireNonNull(artifacts, "artifacts"));
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(sha256, "sha256");
        }
    }

    /** Enumerates payload files and atomically publishes a SHA256SUMS inventory. */
    static Published publish(final Path root, final Path inventoryPath) throws IOException {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(inventoryPath, "inventoryPath");
        final Path normalizedRoot = root.toAbsolutePath().normalize();
        final Path normalizedInventory = inventoryPath.toAbsolutePath().normalize();
        if (!normalizedInventory.startsWith(normalizedRoot)
                || normalizedInventory.getParent() == null
                || !normalizedInventory.getParent().equals(normalizedRoot)) {
            throw new IOException("inventory must be directly below case root");
        }
        final List<Path> files = new ArrayList<>();
        try (var stream = Files.walk(normalizedRoot)) {
            stream.forEach(path -> {
                final Path normalized = path.toAbsolutePath().normalize();
                if (!normalized.startsWith(normalizedRoot)) {
                    throw new IllegalStateException("artifact escaped case root");
                }
                if (Files.isSymbolicLink(path)) {
                    throw new IllegalStateException("symbolic links are not evidence artifacts");
                }
                if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                        && !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                        && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IllegalStateException("unsupported evidence filesystem entry");
                }
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        && !normalized.equals(normalizedInventory)) {
                    final String fileName = normalized.getFileName().toString();
                    if ("SHA256SUMS".equals(fileName) || fileName.endsWith(".sha256")) {
                        throw new IllegalStateException("reserved evidence metadata path: "
                                + fileName);
                    }
                    files.add(normalized);
                }
            });
        } catch (final IllegalStateException exception) {
            throw new IOException(exception.getMessage(), exception);
        }
        files.sort(Comparator.comparing(path -> relative(normalizedRoot, path)));
        if (files.isEmpty()) {
            throw new IOException("case artifact inventory cannot be empty");
        }
        if (files.size() > MAX_ARTIFACTS) {
            throw new IOException("case artifact inventory exceeds 1000 members");
        }
        final List<Artifact> artifacts = new ArrayList<>(files.size());
        final StringBuilder text = new StringBuilder(files.size() * 100);
        for (Path file : files) {
            final String relative = relative(normalizedRoot, file);
            final long size = Files.size(file);
            final String digest = QualificationArtifactHasher.sha256(file);
            artifacts.add(new Artifact(relative, size, digest));
            text.append(digest).append("  ").append(relative).append('\n');
        }
        QualificationEvidencePublication.text(normalizedInventory, text.toString());
        for (final Artifact artifact : artifacts) {
            publishAdjacentSidecar(normalizedRoot.resolve(artifact.path()));
        }
        publishAdjacentSidecar(normalizedInventory);
        final byte[] bytes = text.toString().getBytes(StandardCharsets.US_ASCII);
        final String inventoryDigest = digest(bytes);
        return new Published(artifacts, normalizedInventory, bytes.length, inventoryDigest);
    }

    /** Publishes the schema-required sidecar for one payload artifact. */
    static String publishAdjacentSidecar(final Path artifact) throws IOException {
        Objects.requireNonNull(artifact, "artifact");
        final Path normalized = artifact.toAbsolutePath().normalize();
        final Path parent = Objects.requireNonNull(normalized.getParent(), "artifact parent");
        final String name = normalized.getFileName().toString();
        return com.ultralatency.matching.qualification.ga.GaEvidenceStore
                .publishArtifactSidecar(parent.resolve(name + ".sha256"),
                        Map.of(name, normalized));
    }

    private static String relative(final Path root, final Path file) {
        final Path normalized = file.toAbsolutePath().normalize();
        if (!normalized.startsWith(root) || normalized.equals(root)) {
            throw new IllegalArgumentException("artifact is outside case root");
        }
        final String relative = root.relativize(normalized).toString().replace('\\', '/');
        if (relative.isBlank() || relative.startsWith("/") || relative.contains("//")
                || relative.contains("\n") || relative.contains("\r")
                || !StandardCharsets.US_ASCII.newEncoder().canEncode(relative)) {
            throw new IllegalArgumentException("artifact path is not canonical");
        }
        for (String segment : relative.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("artifact path is not canonical");
            }
        }
        return relative;
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
