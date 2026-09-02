package com.ultralatency.matching.qualification.ga.soak;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests the frozen owned-file classification boundary for G8 resource evidence. */
class GaSoakResourceSamplerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void classifiesOnlyDeclaredOwnedFilePatterns() throws Exception {
        final Path physicalRoot = temporaryDirectory.resolve("physical-root");
        final Path wal = Files.createDirectories(physicalRoot.resolve("wal"));
        final Path snapshots = Files.createDirectories(physicalRoot.resolve("snapshots"));
        final Path evidence = Files.createDirectories(physicalRoot.resolve("process-evidence"));

        assertEquals(GaSoakResourceSampler.FileClassification.DURABLE,
                classify(physicalRoot, wal.resolve("wal-00000000000000000001.log")));
        assertEquals(GaSoakResourceSampler.FileClassification.DURABLE,
                classify(physicalRoot, wal.resolve("recovery.lock")));
        assertEquals(GaSoakResourceSampler.FileClassification.DURABLE,
                classify(physicalRoot, snapshots.resolve("snapshot-00000000000000000001.bin")));
        assertEquals(GaSoakResourceSampler.FileClassification.TRANSIENT,
                classify(physicalRoot, snapshots.resolve("snapshot-00000000000000000001.tmp")));
        assertEquals(GaSoakResourceSampler.FileClassification.UNKNOWN,
                classify(physicalRoot, snapshots.resolve("random.tmp")));
        assertEquals(GaSoakResourceSampler.FileClassification.UNKNOWN,
                classify(physicalRoot, snapshots.resolve("snapshot-00000000000000000001.other")));
        assertEquals(GaSoakResourceSampler.FileClassification.EVIDENCE,
                classify(physicalRoot, evidence.resolve("qualification.jfr")));
    }

    @Test
    void nestedSnapshotFilesAreUnknownRatherThanTransient() throws Exception {
        final Path physicalRoot = temporaryDirectory.resolve("physical-root");
        final Path nested = Files.createDirectories(physicalRoot.resolve("snapshots/nested"))
                .resolve("snapshot-00000000000000000001.tmp");
        Files.writeString(nested, "fixture");
        assertEquals(GaSoakResourceSampler.FileClassification.UNKNOWN,
                GaSoakResourceSampler.classifyOwnedPath(physicalRoot, nested));
    }

    private static GaSoakResourceSampler.FileClassification classify(
            final Path root,
            final Path path) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, "fixture");
        return GaSoakResourceSampler.classifyOwnedPath(root, path);
    }
}
