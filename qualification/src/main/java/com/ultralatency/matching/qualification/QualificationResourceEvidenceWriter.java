package com.ultralatency.matching.qualification;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes the canonical raw resource evidence artifact for a qualification process. */
public final class QualificationResourceEvidenceWriter {

    private QualificationResourceEvidenceWriter() {
    }

    /** Writes resource metadata and chronological samples as UTF-8 CSV. */
    public static void write(
            final Path path,
            final QualificationResourceEvidence evidence) throws IOException {
        final StringBuilder output = new StringBuilder();
        output.append("#baselineThreadCount=").append(evidence.baselineThreadCount()).append('\n');
        output.append("#finalThreadCount=").append(evidence.finalThreadCount()).append('\n');
        output.append("#threadBaselineRestored=")
                .append(evidence.threadBaselineRestored()).append('\n');
        output.append("#heapGuardAssessed=").append(evidence.heapGuardAssessed()).append('\n');
        output.append("#heapGuardPassed=").append(evidence.heapGuardPassed()).append('\n');
        output.append("#baselineRuntimeThreads=")
                .append(String.join("|", evidence.baselineRuntimeThreads())).append('\n');
        output.append("#finalRuntimeThreads=")
                .append(String.join("|", evidence.finalRuntimeThreads())).append('\n');
        output.append("timestamp,threadCount,peakThreadCount,gcCollections,gcTimeMillis,heapUsed,"
                + "naturalPostGcHeapUsed\n");
        for (final QualificationResourceSample sample : evidence.samples()) {
            output.append(sample.timestamp()).append(',')
                    .append(sample.liveThreadCount()).append(',')
                    .append(sample.peakThreadCount()).append(',')
                    .append(sample.totalGcCollections()).append(',')
                    .append(sample.totalGcTimeMillis()).append(',')
                    .append(sample.heapUsedBytes()).append(',')
                    .append(sample.naturalPostGcHeapBytes() == null
                            ? "" : sample.naturalPostGcHeapBytes())
                    .append('\n');
        }
        Files.writeString(path, output.toString(), StandardCharsets.UTF_8);
    }
}
