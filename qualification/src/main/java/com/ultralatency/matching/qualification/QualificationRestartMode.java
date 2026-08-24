package com.ultralatency.matching.qualification;

/** Process-lifecycle boundary used by the TASK-038 qualification campaign. */
public enum QualificationRestartMode {
    /** The child receives an explicit shutdown command after the response boundary. */
    GRACEFUL_RESTART,
    /** The child is terminated after a complete acknowledged response boundary. */
    FORCED_TERMINATION
}
