package com.ultralatency.matching.app;

/** Lifecycle states owned by the Phase 10 application composition boundary. */
public enum RuntimeLifecycleState {
    /** No configuration or runtime resource has been accepted. */
    NEW,
    /** Configuration has passed typed validation. */
    CONFIG_VALIDATED,
    /** Recovery and runtime resource startup is in progress. */
    STARTING,
    /** All required listeners are bound and admission is open. */
    READY,
    /** Admission is closed and owned resources are draining. */
    STOPPING,
    /** Resources have been closed after a clean or terminal shutdown. */
    STOPPED,
    /** A terminal failure prevents further admission. */
    FAILED
}
