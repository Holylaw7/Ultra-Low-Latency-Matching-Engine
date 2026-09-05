package com.ultralatency.matching.qualification.ga.performance;

/**
 * Explicit accounting for the bounded measurement population of one formal run.
 *
 * <p>The measurement interval and the bounded drain are different boundaries. A response
 * observed while draining after the interval is retained in {@code postMeasurementDrainCommands}
 * but is not silently added to the measured latency population. The same is true for a warmup
 * request which crosses into the measurement interval is retained separately in the raw
 * evidence and does not contaminate this measurement partition.</p>
 */
public record GaPerformanceMeasurement(
        long offeredCommands,
        long acceptedCommands,
        long completedCommands,
        long postMeasurementDrainCommands,
        long crossBoundaryCommands,
        long unfinishedCommands,
        boolean boundedDrainComplete) {

    /** Validates the independently recomputable population counters. */
    public GaPerformanceMeasurement {
        if (offeredCommands < 0L || acceptedCommands < 0L || completedCommands < 0L
                || postMeasurementDrainCommands < 0L || crossBoundaryCommands < 0L
                || unfinishedCommands < 0L || acceptedCommands > offeredCommands
                || completedCommands > acceptedCommands) {
            throw new IllegalArgumentException("measurement population is outside its bounds");
        }
    }

    /**
     * Returns whether the retained formal latency population is closed.
     *
     * <p>This is deliberately independent of the offer and acceptance counters. Those counters
     * describe the wider bounded request population; {@code completedCommands} describes only
     * responses retained in the formal latency sample.</p>
     */
    public boolean complete() {
        return completedCommands > 0L && boundedDrainComplete;
    }

    /** Returns whether every measurement offer has an authoritative terminal response. */
    public boolean boundaryComplete() {
        return offeredCommands > 0L
                && acceptedCommands == offeredCommands
                && completedCommands + postMeasurementDrainCommands
                        + crossBoundaryCommands == acceptedCommands
                && unfinishedCommands == 0L
                && boundedDrainComplete;
    }

    /** Returns the number of authoritative responses completed outside the formal interval. */
    public long incompleteCommands() {
        return postMeasurementDrainCommands + crossBoundaryCommands + unfinishedCommands;
    }

    /** Creates the legacy all-in-interval population used by older unit fixtures. */
    public static GaPerformanceMeasurement legacy(
            final int commandCount,
            final long acceptedCommands,
            final long responseCount,
            final int latencySampleCount) {
        final long offered = Math.max(0L, commandCount);
        final long accepted = Math.max(0L, acceptedCommands);
        final long completed = Math.max(0L, responseCount);
        final boolean complete = completed > 0L && latencySampleCount == completed;
        return new GaPerformanceMeasurement(offered, Math.min(accepted, offered),
                Math.min(completed, Math.min(accepted, offered)), 0L, 0L,
                Math.max(0L, Math.min(accepted, offered) - Math.min(completed,
                        Math.min(accepted, offered))), complete);
    }
}
