package com.ultralatency.matching.integration.recovery;

/** Lifecycle states for the listener-last recovered durable runtime. */
public enum RecoveryRuntimeState {
    /** No recovery or live resources have been created. */
    NEW,
    /** Strict offline recovery is in progress. */
    RECOVERING,
    /** Recovery completed and the recovered state is ready for composition. */
    RECOVERED,
    /** Pipeline and coordinator resources are being started. */
    STARTING,
    /** All live components are ready for a listener to bind. */
    RUNNING,
    /** A first-cause startup or runtime failure made the composition terminal. */
    FAILED,
    /** Resources were closed without a terminal failure. */
    STOPPED
}
