package com.ultralatency.matching.app;

/** Stable process outcomes for the release-candidate application entrypoint. */
public enum RuntimeExitCode {
    /** The application stopped without a terminal failure. */
    CLEAN(0),
    /** Command-line or configuration rejection. */
    CONFIGURATION(2),
    /** Storage, recovery or startup convergence failure. */
    STARTUP_RECOVERY(3),
    /** Required Protocol listener bind failure. */
    PROTOCOL_BIND(4),
    /** Terminal runtime failure after startup. */
    RUNTIME_FAILURE(5),
    /** Cooperative shutdown exceeded its configured bound. */
    SHUTDOWN_TIMEOUT(6);

    private final int code;

    RuntimeExitCode(final int code) {
        this.code = code;
    }

    /** @return the scriptable process exit integer */
    public int code() {
        return code;
    }

    /** Maps a sanitized failure category to the corresponding process outcome. */
    public static RuntimeExitCode forFailure(final RuntimeFailureCode failureCode) {
        return switch (failureCode) {
            case NONE -> CLEAN;
            case CONFIG -> CONFIGURATION;
            case RECOVERY -> STARTUP_RECOVERY;
            case PROTOCOL_BIND -> PROTOCOL_BIND;
            case MANAGEMENT_BIND, RUNTIME -> RUNTIME_FAILURE;
            case SHUTDOWN_TIMEOUT -> SHUTDOWN_TIMEOUT;
        };
    }
}
