package com.ultralatency.matching.qualification;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/** Immutable WAL/Snapshot inventory captured as part of qualification evidence. */
public record QualificationStorageInventory(
        List<String> walFiles,
        long walBytes,
        List<String> snapshotFiles,
        long snapshotBytes,
        long temporaryFileCount) {

    /** Captures regular files and temporary-file count below the two owned directories. */
    public static QualificationStorageInventory capture(
            final Path walDirectory,
            final Path snapshotDirectory) throws IOException {
        Objects.requireNonNull(walDirectory, "walDirectory");
        Objects.requireNonNull(snapshotDirectory, "snapshotDirectory");
        final DirectoryInventory wal = captureDirectory(walDirectory);
        final DirectoryInventory snapshots = captureDirectory(snapshotDirectory);
        return new QualificationStorageInventory(
                wal.files(),
                wal.bytes(),
                snapshots.files(),
                snapshots.bytes(),
                wal.temporaryFiles() + snapshots.temporaryFiles());
    }

    /** Returns whether the captured inventory is suitable for a completed run. */
    public boolean stable() {
        return temporaryFileCount == 0 && !walFiles.isEmpty();
    }

    /** Returns the number of WAL regular files. */
    public long walFileCount() {
        return walFiles.size();
    }

    /** Returns the number of Snapshot regular files. */
    public long snapshotFileCount() {
        return snapshotFiles.size();
    }

    /** Creates a deterministic compact representation for manifests. */
    public String walFilesText() {
        return String.join("|", walFiles);
    }

    /** Creates a deterministic compact representation for manifests. */
    public String snapshotFilesText() {
        return String.join("|", snapshotFiles);
    }

    private static DirectoryInventory captureDirectory(final Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return new DirectoryInventory(List.of(), 0L, 0L);
        }
        final List<String> files = new ArrayList<>();
        long bytes = 0L;
        long temporaryFiles = 0L;
        try (Stream<Path> paths = Files.walk(directory)) {
            for (final Path path : paths.filter(Files::isRegularFile).sorted(Comparator.naturalOrder())
                    .toList()) {
                final String relative = directory.relativize(path).toString().replace('\\', '/');
                if (relative.endsWith(".tmp")) {
                    temporaryFiles++;
                }
                files.add(relative);
                bytes = Math.addExact(bytes, Files.size(path));
            }
        }
        return new DirectoryInventory(List.copyOf(files), bytes, temporaryFiles);
    }

    private record DirectoryInventory(List<String> files, long bytes, long temporaryFiles) {
    }
}
