package com.ultralatency.matching.pipeline;

/**
 * Waiting policy names exposed by the project-owned pipeline configuration.
 *
 * <p>{@link #BLOCKING} is the portable correctness default. The other modes are explicit
 * experimental variables and do not imply a production recommendation.</p>
 */
public enum PipelineWaitMode {
    /** Conservative wait policy and the default configuration. */
    BLOCKING,
    /** Cooperative yielding wait policy for explicit experiments. */
    YIELDING,
    /** CPU-intensive busy-spin wait policy for explicit experiments. */
    BUSY_SPIN
}
