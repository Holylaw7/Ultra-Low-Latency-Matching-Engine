package com.ultralatency.matching.qualification;

import java.io.IOException;
import java.nio.file.Path;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

/** Reads bounded allocation-sampling evidence from a qualification JFR artifact. */
public final class QualificationJfrAllocationEvidence {

    private QualificationJfrAllocationEvidence() {
    }

    /** Writes a deterministic allocation-event summary beside the raw JFR file. */
    public static void write(final Path jfr, final Path summary) throws IOException {
        if (jfr == null || summary == null) {
            throw new NullPointerException("jfr/summary");
        }
        long allocationSamples = 0L;
        long threadStatistics = 0L;
        long sampledBytes = 0L;
        try (RecordingFile recording = new RecordingFile(jfr)) {
            while (recording.hasMoreEvents()) {
                final RecordedEvent event = recording.readEvent();
                final String name = event.getEventType().getName();
                if ("jdk.ObjectAllocationSample".equals(name)) {
                    allocationSamples++;
                    sampledBytes += fieldLong(event, "objectSize", "weight");
                } else if ("jdk.ThreadAllocationStatistics".equals(name)) {
                    threadStatistics++;
                }
            }
        }
        final String text = "schema=qualification-jfr-allocation-v1\n"
                + "rawJfr=" + jfr.getFileName() + "\n"
                + "eventConfiguration=jdk.ObjectAllocationSample throttle=100/s;"
                + "jdk.ThreadAllocationStatistics period=1s\n"
                + "objectAllocationSample.count=" + allocationSamples + "\n"
                + "objectAllocationSample.sampledBytes=" + sampledBytes + "\n"
                + "threadAllocationStatistics.count=" + threadStatistics + "\n";
        QualificationEvidencePublication.text(summary, text);
    }

    private static long fieldLong(final RecordedEvent event, final String... names) {
        for (String name : names) {
            try {
                return event.getLong(name);
            } catch (IllegalArgumentException ignored) {
                // JDK event schemas may expose a different weight field.
            }
        }
        return 0L;
    }
}
