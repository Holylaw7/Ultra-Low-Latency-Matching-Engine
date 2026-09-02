package com.ultralatency.matching.qualification.ga.observability;

import com.ultralatency.matching.qualification.ga.soak.GaNaturalGcSample;
import com.ultralatency.matching.qualification.ga.soak.GaSoakMatrix.Stage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Immutable authoritative JFR GC evidence for one physical run. */
public record GaGcEvidence(
        List<GaNaturalGcSample> samples,
        boolean parsed,
        boolean authoritativeJfr,
        boolean identityBound,
        String failureCode) {

    /** Validates one GC evidence container. */
    public GaGcEvidence {
        samples = List.copyOf(Objects.requireNonNull(samples, "samples"));
        if (failureCode == null || failureCode.isBlank()) {
            throw new IllegalArgumentException("GC failure code must not be blank");
        }
        for (GaNaturalGcSample sample : samples) {
            Objects.requireNonNull(sample, "GC sample");
        }
    }

    /** Returns whether this source can be used by the formal natural-GC guard. */
    public boolean applicable() {
        return parsed && authoritativeJfr && identityBound;
    }

    /** Returns whether all samples belong to one physical execution and stage. */
    public boolean belongsTo(final String physicalExecutionId, final Stage stage) {
        Objects.requireNonNull(physicalExecutionId, "physicalExecutionId");
        Objects.requireNonNull(stage, "stage");
        return samples.stream().allMatch(sample ->
                physicalExecutionId.equals(sample.physicalExecutionId()) && stage == sample.stage());
    }

    /** Returns a valid empty Quick fixture. */
    public static GaGcEvidence quick(final String failureCode) {
        return new GaGcEvidence(List.of(), true, true, true, failureCode);
    }

    /**
     * Reads the bounded natural-GC artifact emitted by the JFR child.  The
     * parent consumes this small qualification record only after the child has
     * exited; it never opens the untrusted JFR recording itself.
     */
    public static GaGcEvidence fromChildArtifact(
            final Path artifact,
            final String physicalExecutionId,
            final Stage stage) {
        Objects.requireNonNull(artifact, "artifact");
        Objects.requireNonNull(physicalExecutionId, "physicalExecutionId");
        Objects.requireNonNull(stage, "stage");
        try {
            final String text = Files.readString(
                    artifact.toAbsolutePath().normalize(), StandardCharsets.US_ASCII);
            if (!text.startsWith("gcId,sequence,afterGcHeapBytes\n")
                    || !text.endsWith("\n") || text.indexOf('\r') >= 0) {
                return failure();
            }
            final String[] lines = text.split("\n", -1);
            final List<GaNaturalGcSample> samples = new java.util.ArrayList<>();
            long previousSequence = -1L;
            for (int index = 1; index < lines.length - 1; index++) {
                final String[] fields = lines[index].split(",", -1);
                if (fields.length != 3) {
                    return failure();
                }
                final long gcId = nonNegative(fields[0]);
                final long sequence = nonNegative(fields[1]);
                final long heap = nonNegative(fields[2]);
                if (sequence <= previousSequence) {
                    return failure();
                }
                previousSequence = sequence;
                samples.add(new GaNaturalGcSample(physicalExecutionId, stage, sequence,
                        sequence, gcId, heap, true));
            }
            return new GaGcEvidence(samples, true, true, true, "NONE");
        } catch (final IOException | RuntimeException failure) {
            return failure();
        }
    }

    private static long nonNegative(final String value) {
        try {
            final long parsed = Long.parseLong(value);
            if (parsed < 0L) {
                throw new IllegalArgumentException("negative GC value");
            }
            return parsed;
        } catch (final NumberFormatException exception) {
            throw new IllegalArgumentException("invalid GC value", exception);
        }
    }

    private static GaGcEvidence failure() {
        return new GaGcEvidence(List.of(), false, false, false, "B3");
    }
}
