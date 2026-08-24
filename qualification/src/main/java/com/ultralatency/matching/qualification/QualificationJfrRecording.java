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

    /** Starts a disk-backed recording with GC and thread lifecycle events enabled. */
    public static QualificationJfrRecording start(final Path destination) throws IOException {
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
        recording.enable("jdk.ThreadStart").withoutStackTrace();
        recording.enable("jdk.ThreadEnd").withoutStackTrace();
        recording.setToDisk(true);
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
