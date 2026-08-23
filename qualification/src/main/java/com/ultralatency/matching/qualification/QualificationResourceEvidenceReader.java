package com.ultralatency.matching.qualification;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Recalculates resource evidence from an immutable raw CSV artifact. */
public final class QualificationResourceEvidenceReader {

    private QualificationResourceEvidenceReader() {
    }

    /** Reads raw samples and recalculates the chronological heap guard. */
    public static QualificationResourceEvidence read(
            final Path path,
            final int minimumPostGcSamples) throws IOException {
        final List<String> lines = Files.readAllLines(path);
        final Map<String, String> metadata = new HashMap<>();
        final List<QualificationResourceSample> samples = new ArrayList<>();
        boolean dataStarted = false;
        for (final String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            if (line.startsWith("#")) {
                final int separator = line.indexOf('=');
                if (separator > 1) {
                    metadata.put(line.substring(1, separator), line.substring(separator + 1));
                }
                continue;
            }
            if (!dataStarted) {
                if (!line.startsWith("timestamp,")) {
                    throw new IOException("resource evidence CSV header is invalid");
                }
                dataStarted = true;
                continue;
            }
            samples.add(parseSample(line));
        }
        if (!dataStarted) {
            throw new IOException("resource evidence CSV header is missing");
        }
        final List<QualificationResourceSample> natural =
                QualificationHeapGuard.naturalPostGcSamples(samples);
        final boolean assessed = minimumPostGcSamples > 0
                && natural.size() >= minimumPostGcSamples;
        return new QualificationResourceEvidence(
                samples,
                natural.stream().map(QualificationResourceSample::naturalPostGcHeapBytes).toList(),
                parseLong(metadata, "baselineThreadCount"),
                parseLong(metadata, "finalThreadCount"),
                splitThreads(metadata.get("baselineRuntimeThreads")),
                splitThreads(metadata.get("finalRuntimeThreads")),
                Boolean.parseBoolean(metadata.getOrDefault("threadBaselineRestored", "false")),
                assessed,
                assessed && QualificationHeapGuard.passes(samples, minimumPostGcSamples));
    }

    private static QualificationResourceSample parseSample(final String line) throws IOException {
        final String[] columns = line.split(",", -1);
        if (columns.length != 7) {
            throw new IOException("resource evidence CSV row has invalid column count");
        }
        try {
            return new QualificationResourceSample(
                    Instant.parse(columns[0]),
                    Long.parseLong(columns[1]),
                    Long.parseLong(columns[2]),
                    Long.parseLong(columns[3]),
                    Long.parseLong(columns[4]),
                    Long.parseLong(columns[5]),
                    columns[6].isBlank() ? null : Long.parseLong(columns[6]));
        } catch (final RuntimeException exception) {
            throw new IOException("resource evidence CSV row is invalid", exception);
        }
    }

    private static long parseLong(final Map<String, String> metadata, final String key)
            throws IOException {
        final String value = metadata.get(key);
        if (value == null) {
            throw new IOException("resource evidence metadata is missing " + key);
        }
        try {
            return Long.parseLong(value);
        } catch (final NumberFormatException exception) {
            throw new IOException("resource evidence metadata is invalid " + key, exception);
        }
    }

    private static List<String> splitThreads(final String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split("\\|"));
    }
}
