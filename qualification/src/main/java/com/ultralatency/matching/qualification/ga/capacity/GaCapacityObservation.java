package com.ultralatency.matching.qualification.ga.capacity;

/** Immutable result of one G5 scale observation. */
public record GaCapacityObservation(
        int commandCount,
        long acceptedCommands,
        long responseCount,
        long recoveredActiveOrders,
        long activePriceLevels,
        long walBytes,
        long snapshotBytes,
        long heapMaxBytes,
        long rssBytes,
        long elapsedNanos,
        boolean exactRecoveryConvergence,
        boolean outOfMemory,
        boolean sequenceGap,
        boolean invalidTrade,
        boolean timeout,
        boolean publicPathCompleted,
        boolean configurationBound,
        boolean candidateBound,
        boolean controllerBound) {

    /** Validates bounded scale evidence before evaluation. */
    public GaCapacityObservation {
        if (commandCount <= 0 || acceptedCommands < 0 || responseCount < 0
                || recoveredActiveOrders < 0 || activePriceLevels < 0 || walBytes < 0
                || snapshotBytes < 0 || heapMaxBytes < 0 || rssBytes < 0 || elapsedNanos <= 0) {
            throw new IllegalArgumentException("capacity observation is outside its bounds");
        }
    }

    /** Returns whether the public response population is complete for this scale. */
    public boolean completeResponsePopulation() {
        return acceptedCommands == commandCount && responseCount == commandCount;
    }
}
