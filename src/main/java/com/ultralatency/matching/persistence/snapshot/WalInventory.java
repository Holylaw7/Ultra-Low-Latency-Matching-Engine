package com.ultralatency.matching.persistence.snapshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/** Immutable WAL segment-name and exact-file-size inventory at one scan point. */
public final class WalInventory {

    private final List<Segment> segments;

    private WalInventory(final List<Segment> segments) {
        this.segments = List.copyOf(segments);
    }

    /** Captures all named WAL segment paths and exact sizes. */
    public static WalInventory capture(final Path walDirectory) throws IOException {
        Objects.requireNonNull(walDirectory, "walDirectory");
        if (!Files.exists(walDirectory)) {
            return new WalInventory(List.of());
        }
        if (!Files.isDirectory(walDirectory)) {
            throw new IOException("WAL path is not a directory: " + walDirectory);
        }
        try (Stream<Path> paths = Files.list(walDirectory)) {
            final List<Segment> segments = paths
                    .filter(path -> {
                        final String name = path.getFileName().toString();
                        return name.startsWith("wal-") && name.endsWith(".log");
                    })
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(path -> size(path))
                    .toList();
            return new WalInventory(segments);
        }
    }

    /** @return immutable segment inventory in filename order */
    public List<Segment> segments() {
        return segments;
    }

    /** @return whether this inventory exactly matches another inventory */
    public boolean matches(final WalInventory other) {
        return equals(other);
    }

    private static Segment size(final Path path) {
        try {
            return new Segment(path.getFileName().toString(), Files.size(path));
        } catch (final IOException exception) {
            throw new InventoryReadException(path, exception);
        }
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WalInventory that)) {
            return false;
        }
        return segments.equals(that.segments);
    }

    @Override
    public int hashCode() {
        return segments.hashCode();
    }

    /** One named WAL segment and its exact byte size. */
    public record Segment(String fileName, long sizeBytes) {

        /** Validates one inventory entry. */
        public Segment {
            Objects.requireNonNull(fileName, "fileName");
            if (sizeBytes < 0) {
                throw new IllegalArgumentException("WAL segment size must not be negative");
            }
        }
    }

    private static final class InventoryReadException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private InventoryReadException(final Path path, final IOException cause) {
            super("Unable to inspect WAL segment: " + path, cause);
        }
    }
}
