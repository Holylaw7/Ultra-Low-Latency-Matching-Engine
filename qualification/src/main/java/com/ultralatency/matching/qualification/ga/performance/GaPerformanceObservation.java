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
        boolean controllerBound,
        GaPerformanceMeasurement measurement) {

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
        Objects.requireNonNull(measurement, "measurement");
        responseLatencyNanos = copyAndValidate(responseLatencyNanos, "responseLatencyNanos");
        startupLatencyNanos = copyAndValidate(startupLatencyNanos, "startupLatencyNanos");
        shutdownLatencyNanos = copyAndValidate(shutdownLatencyNanos, "shutdownLatencyNanos");
        if (measurement.completedCommands() != responseCount
                || measurement.completedCommands() != responseLatencyNanos.length) {
            throw new IllegalArgumentException(
                    "measurement completion count must match retained latency population");
        }
    }

    /** Compatibility constructor for pre-remediation qualification fixtures. */
    public GaPerformanceObservation(
            final int commandCount,
            final long acceptedCommands,
            final long responseCount,
            final long elapsedNanos,
            final long[] responseLatencyNanos,
            final long[] startupLatencyNanos,
            final long[] shutdownLatencyNanos,
            final double idleThroughputCommandsPerSecond,
            final double statusThroughputCommandsPerSecond,
            final long idleStatusP99Nanos,
            final long statusP99Nanos,
            final int errors,
            final int timeouts,
            final int mismatches,
            final boolean publicPathCompleted,
            final boolean configurationBound,
            final boolean comparabilityBound,
            final boolean candidateBound,
            final boolean controllerBound) {
        this(commandCount, acceptedCommands, responseCount, elapsedNanos, responseLatencyNanos,
                startupLatencyNanos, shutdownLatencyNanos, idleThroughputCommandsPerSecond,
                statusThroughputCommandsPerSecond, idleStatusP99Nanos, statusP99Nanos, errors,
                timeouts, mismatches, publicPathCompleted, configurationBound,
                comparabilityBound, candidateBound, controllerBound,
                GaPerformanceMeasurement.legacy(commandCount, acceptedCommands, responseCount,
                        responseLatencyNanos == null ? 0 : responseLatencyNanos.length));
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
        return measurement.complete();
    }

    /** Returns explicit offer/accept/complete and bounded-drain accounting. */
    public GaPerformanceMeasurement measurement() {
        return measurement;
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
