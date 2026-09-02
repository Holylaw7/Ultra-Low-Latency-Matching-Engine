package com.ultralatency.matching.qualification;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import jdk.jfr.Recording;

/** JDK-only JFR capture for a qualification evidence unit. */
public final class QualificationJfrRecording implements AutoCloseable {

    private final Recording recording;
    private final Path destination;
    private boolean closed;

    private QualificationJfrRecording(final Recording recording, final Path destination) {
        this.recording = recording;
        this.destination = destination;
    }

    /** Starts a disk-backed recording with the frozen TASK-052 JDK 21 event families enabled. */
    public static QualificationJfrRecording start(final Path destination) throws IOException {
        return start(destination, false);
    }

    /** Starts characterization evidence with bounded allocation sampling enabled. */
    public static QualificationJfrRecording startCharacterization(final Path destination)
            throws IOException {
        return start(destination, true);
    }

    private static QualificationJfrRecording start(
            final Path destination,
            final boolean allocationSampling) throws IOException {
        if (destination == null) {
            throw new NullPointerException("destination");
        }
        final Path normalized = destination.toAbsolutePath().normalize();
        final Path parent = normalized.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        final Recording recording = new Recording();
        recording.enable("jdk.GarbageCollection").withoutStackTrace();
        recording.enable("jdk.GCHeapSummary").withoutStackTrace();
        recording.enable("jdk.CPULoad").withoutStackTrace();
        recording.enable("jdk.ResidentSetSize").withoutStackTrace();
        recording.enable("jdk.JavaThreadStatistics").withoutStackTrace();
        recording.enable("jdk.ThreadStart").withoutStackTrace();
        recording.enable("jdk.ThreadEnd").withoutStackTrace();
        recording.enable("jdk.ObjectAllocationSample")
                .with("throttle", "100/s")
                .withoutStackTrace();
        recording.enable("jdk.ThreadAllocationStatistics")
                .with("period", "1 s")
                .withoutStackTrace();
        recording.setToDisk(true);
        // A terminal path must still materialize the recording if the owning
        // process exits before the normal close path.  The child process is the
        // resource owner; dump-on-exit is an additional deterministic safety
        // boundary, not a substitute for close().
        recording.setDumpOnExit(true);
        recording.setDestination(normalized);
        recording.start();
        return new QualificationJfrRecording(recording, normalized);
    }

    /** Stops and closes the recording, preserving the raw artifact. */
    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        recording.stop();
        recording.close();
    }

    /** Returns the configured raw artifact path. */
    public Path destination() {
        return destination;
    }
}
