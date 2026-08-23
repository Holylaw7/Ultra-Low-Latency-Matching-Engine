package com.ultralatency.matching.qualification;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests raw resource evidence re-evaluation using timestamp order. */
class QualificationResourceEvidenceReaderTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void recalculatesGuardFromRawChronologicalSamples() throws Exception {
        final Path path = temporaryDirectory.resolve("resource-evidence.csv");
        Files.writeString(path, """
                #baselineThreadCount=10
                #finalThreadCount=10
                #threadBaselineRestored=true
                #baselineRuntimeThreads=
                #finalRuntimeThreads=
                timestamp,threadCount,peakThreadCount,gcCollections,gcTimeMillis,heapUsed,naturalPostGcHeapUsed
                2026-08-23T00:00:00Z,10,10,1,1,104857600,524288000
                2026-08-23T00:00:01Z,10,10,2,2,83886080,419430400
                2026-08-23T00:00:02Z,10,10,3,3,62914560,314572800
                2026-08-23T00:00:03Z,10,10,4,4,41943040,209715200
                2026-08-23T00:00:04Z,10,10,5,5,20971520,104857600
                """);

        final QualificationResourceEvidence evidence =
                QualificationResourceEvidenceReader.read(path, 2);

        assertTrue(evidence.heapGuardAssessed());
        assertTrue(evidence.heapGuardPassed());
        assertTrue(evidence.naturalPostGcHeapBytes().size() == 5);
    }

    @Test
    void chronologicalGrowthFailsAfterRawRecalculation() throws Exception {
        final Path path = temporaryDirectory.resolve("resource-evidence.csv");
        Files.writeString(path, """
                #baselineThreadCount=10
                #finalThreadCount=10
                #threadBaselineRestored=true
                timestamp,threadCount,peakThreadCount,gcCollections,gcTimeMillis,heapUsed,naturalPostGcHeapUsed
                2026-08-23T00:00:00Z,10,10,1,1,104857600,104857600
                2026-08-23T00:00:01Z,10,10,2,2,209715200,209715200
                """);

        final QualificationResourceEvidence evidence =
                QualificationResourceEvidenceReader.read(path, 2);

        assertTrue(evidence.heapGuardAssessed());
        assertFalse(evidence.heapGuardPassed());
    }
}
