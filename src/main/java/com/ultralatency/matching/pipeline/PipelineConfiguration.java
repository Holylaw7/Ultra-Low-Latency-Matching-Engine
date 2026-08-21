package com.ultralatency.matching.pipeline;

import java.util.Objects;

/**
 * Immutable configuration for the bounded event pipeline.
 *
 * <p>This foundation validates configuration only. It does not allocate a ring, create a
 * thread, or start a consumer.</p>
 *
 * @param capacity power-of-two number of preallocated pipeline slots
 * @param waitMode project-owned waiting policy name
 */
public record PipelineConfiguration(int capacity, PipelineWaitMode waitMode) {

    /** Smallest supported non-trivial bounded ring capacity. */
    public static final int MIN_CAPACITY = 2;

    /** Default capacity used by {@link #defaults()}. */
    public static final int DEFAULT_CAPACITY = 1024;

    /**
     * Validates the immutable configuration contract.
     */
    public PipelineConfiguration {
        if (capacity < MIN_CAPACITY) {
            throw new IllegalArgumentException(
                    "Pipeline capacity must be at least " + MIN_CAPACITY);
        }
        if (Integer.bitCount(capacity) != 1) {
            throw new IllegalArgumentException("Pipeline capacity must be a power of two");
        }
        Objects.requireNonNull(waitMode, "waitMode");
    }

    /**
     * Returns the approved portable default configuration.
     *
     * @return blocking configuration with the default capacity
     */
    public static PipelineConfiguration defaults() {
        return new PipelineConfiguration(DEFAULT_CAPACITY, PipelineWaitMode.BLOCKING);
    }
}
