package com.ultralatency.matching.qualification.ga.performance;

import java.util.Arrays;
import java.util.Objects;

/** Immutable measurement set consumed by the G4 evaluator. */
public record GaPerformanceObservation(
        int commandCount,
        long acceptedCommands,
        long responseCount,
        long elapsedNanos,
        long[] responseLatencyNanos,
        long[] startupLatencyNanos,
        long[] shutdownLatencyNanos,
        double idleThroughputCommandsPerSecond,
        double statusThroughputCommandsPerSecond,
        long idleStatusP99Nanos,
        long statusP99Nanos,
        int errors,
        int timeouts,
        int mismatches,
        boolean publicPathCompleted,
        boolean configurationBound,
        boolean comparabilityBound,
        boolean candidateBound,
        boolean controllerBound) {

    /** Validates and defensively copies all measurement arrays. */
    public GaPerformanceObservation {
        if (commandCount <= 0 || acceptedCommands < 0 || responseCount < 0
                || elapsedNanos <= 0 || errors < 0 || timeouts < 0 || mismatches < 0
                || idleStatusP99Nanos < 0 || statusP99Nanos < 0
                || !Double.isFinite(idleThroughputCommandsPerSecond)
                || !Double.isFinite(statusThroughputCommandsPerSecond)
                || idleThroughputCommandsPerSecond < 0
                || statusThroughputCommandsPerSecond < 0) {
            throw new IllegalArgumentException("performance observation is outside its bounds");
        }
        responseLatencyNanos = copyAndValidate(responseLatencyNanos, "responseLatencyNanos");
        startupLatencyNanos = copyAndValidate(startupLatencyNanos, "startupLatencyNanos");
        shutdownLatencyNanos = copyAndValidate(shutdownLatencyNanos, "shutdownLatencyNanos");
    }

    /** Returns a defensive copy of response samples. */
    @Override
    public long[] responseLatencyNanos() {
        return responseLatencyNanos.clone();
    }

    /** Returns a defensive copy of startup samples. */
    @Override
    public long[] startupLatencyNanos() {
        return startupLatencyNanos.clone();
    }

    /** Returns a defensive copy of shutdown samples. */
    @Override
    public long[] shutdownLatencyNanos() {
        return shutdownLatencyNanos.clone();
    }

    /** Returns whether the measured public exchange population is complete. */
    public boolean completeResponsePopulation() {
        return acceptedCommands == commandCount
                && responseCount == commandCount
                && responseLatencyNanos.length == commandCount;
    }

    /** Returns a nearest-rank summary over every retained response sample. */
    public com.ultralatency.matching.qualification.QualificationPercentiles.Summary latency() {
        return com.ultralatency.matching.qualification.QualificationPercentiles
                .summarize(responseLatencyNanos);
    }

    private static long[] copyAndValidate(final long[] values, final String name) {
        Objects.requireNonNull(values, name);
        if (Arrays.stream(values).anyMatch(value -> value < 0)) {
            throw new IllegalArgumentException(name + " must contain non-negative values");
        }
        return values.clone();
    }
}
