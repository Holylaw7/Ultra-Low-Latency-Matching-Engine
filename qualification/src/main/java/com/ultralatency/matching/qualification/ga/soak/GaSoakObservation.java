package com.ultralatency.matching.qualification.ga.soak;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Immutable client-owned and public-path observations consumed by G6. */
public record GaSoakObservation(
        String physicalExecutionId,
        GaSoakMatrix.Stage stage,
        long elapsedNanos,
        long acceptedCommands,
        long completedResponses,
        int errors,
        int timeouts,
        int mismatches,
        long[] responseLatencyNanos,
        long[] firstWindowLatencyNanos,
        long[] finalWindowLatencyNanos,
        List<GaSoakResourceSample> resourceSamples,
        List<GaNaturalGcSample> naturalGcSamples,
        boolean publicPathCompleted,
        boolean correctnessPassed,
        boolean replayPassed,
        boolean transcriptPassed,
        boolean probePassed,
        boolean configurationBound,
        boolean candidateBound,
        boolean controllerBound,
        boolean gracefulShutdown,
        boolean terminalEvidenceComplete) {

    /** Validates and defensively copies one immutable soak observation. */
    public GaSoakObservation {
        if (physicalExecutionId == null || physicalExecutionId.isBlank()
                || stage == null || elapsedNanos <= 0 || acceptedCommands < 0
                || completedResponses < 0 || errors < 0 || timeouts < 0 || mismatches < 0) {
            throw new IllegalArgumentException("soak observation is outside its bounds");
        }
        Objects.requireNonNull(stage, "stage");
        responseLatencyNanos = copySamples(responseLatencyNanos, "responseLatencyNanos");
        firstWindowLatencyNanos = copySamples(
                firstWindowLatencyNanos, "firstWindowLatencyNanos");
        finalWindowLatencyNanos = copySamples(finalWindowLatencyNanos, "finalWindowLatencyNanos");
        resourceSamples = List.copyOf(Objects.requireNonNull(resourceSamples, "resourceSamples"));
        naturalGcSamples = List.copyOf(Objects.requireNonNull(naturalGcSamples, "naturalGcSamples"));
        for (GaSoakResourceSample sample : resourceSamples) {
            Objects.requireNonNull(sample, "resource sample");
        }
        for (GaNaturalGcSample sample : naturalGcSamples) {
            Objects.requireNonNull(sample, "natural GC sample");
        }
    }

    /** Convenience constructor for evaluator fixtures without resource evidence. */
    public GaSoakObservation(
            final String physicalExecutionId,
            final GaSoakMatrix.Stage stage,
            final long elapsedNanos,
            final long acceptedCommands,
            final long completedResponses,
            final int errors,
            final int timeouts,
            final int mismatches,
            final long[] firstWindowLatencyNanos,
            final long[] finalWindowLatencyNanos,
            final boolean publicPathCompleted,
            final boolean correctnessPassed,
            final boolean replayPassed,
            final boolean transcriptPassed,
            final boolean probePassed,
            final boolean configurationBound,
            final boolean candidateBound,
            final boolean controllerBound,
            final boolean gracefulShutdown,
            final boolean terminalEvidenceComplete) {
        this(physicalExecutionId, stage, elapsedNanos, acceptedCommands, completedResponses,
                errors, timeouts, mismatches,
                concatenate(firstWindowLatencyNanos, finalWindowLatencyNanos),
                firstWindowLatencyNanos, finalWindowLatencyNanos,
                List.of(), List.of(), publicPathCompleted, correctnessPassed, replayPassed,
                transcriptPassed, probePassed, configurationBound, candidateBound,
                controllerBound, gracefulShutdown, terminalEvidenceComplete);
    }

    @Override
    public long[] responseLatencyNanos() {
        return responseLatencyNanos.clone();
    }

    @Override
    public long[] firstWindowLatencyNanos() {
        return firstWindowLatencyNanos.clone();
    }

    @Override
    public long[] finalWindowLatencyNanos() {
        return finalWindowLatencyNanos.clone();
    }

    /** Returns whether the run meets the matrix duration without overflow. */
    public boolean durationSatisfied(final GaSoakMatrix matrix) {
        Objects.requireNonNull(matrix, "matrix");
        return elapsedNanos >= durationNanos(matrix.duration());
    }

    /** Returns whether the accepted floor is met independently of offered rate. */
    public boolean acceptedFloorSatisfied(final GaSoakMatrix matrix) {
        Objects.requireNonNull(matrix, "matrix");
        return acceptedCommands >= matrix.acceptedFloor();
    }

    /** Returns whether the response population contains all observed completions. */
    public boolean completeResponses() {
        return acceptedCommands == completedResponses
                && responseLatencyNanos.length == completedResponses;
    }

    /** Returns whether both frozen P99 windows have exactly the required size. */
    public boolean completeLatencyWindows() {
        return firstWindowLatencyNanos.length == GaLatencyWindow.COMPARISON_WINDOW_SAMPLES
                && finalWindowLatencyNanos.length == GaLatencyWindow.COMPARISON_WINDOW_SAMPLES;
    }

    /** Returns the first ordinal-owned latency window. */
    public GaLatencyWindow firstLatencyWindow() {
        return GaLatencyWindow.first(firstWindowLatencyNanos);
    }

    /** Returns the final ordinal-owned latency window. */
    public GaLatencyWindow finalLatencyWindow() {
        return GaLatencyWindow.finalWindow(acceptedCommands, finalWindowLatencyNanos);
    }

    private static long durationNanos(final Duration duration) {
        try {
            return duration.toNanos();
        } catch (final ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private static long[] copySamples(final long[] values, final String name) {
        Objects.requireNonNull(values, name);
        if (Arrays.stream(values).anyMatch(value -> value < 0)) {
            throw new IllegalArgumentException(name + " must contain non-negative values");
        }
        return values.clone();
    }

    private static long[] concatenate(final long[] first, final long[] second) {
        Objects.requireNonNull(first, "firstWindowLatencyNanos");
        Objects.requireNonNull(second, "finalWindowLatencyNanos");
        final long[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
