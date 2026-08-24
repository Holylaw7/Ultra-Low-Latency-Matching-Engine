package com.ultralatency.matching;

import com.ultralatency.matching.app.ReleaseCandidateRuntime;
import com.ultralatency.matching.app.RuntimeConfiguration;
import java.util.Objects;

/**
 * Thin application entrypoint and bootstrap delegation for the release-candidate runtime.
 */
public final class MatchingEngineApplication {

    private static final String APPLICATION_NAME = "Ultra-Low-Latency Matching Engine";
    private static final String APPLICATION_VERSION = "0.1.0-SNAPSHOT";

    private MatchingEngineApplication() {
    }

    public static void main(final String[] args) {
        final int exitCode = com.ultralatency.matching.app.RuntimeCommandLine.execute(
                args, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    /**
     * Creates an unstarted composition root from an already validated configuration.
     *
     * <p>Strict file/CLI parsing is owned by TASK-043; this method keeps the application entry
     * point independent from configuration syntax while providing one production bootstrap path.
     *
     * @param configuration validated runtime configuration
     * @return unstarted release-candidate runtime
     */
    public static ReleaseCandidateRuntime createRuntime(
            final RuntimeConfiguration configuration) {
        return ReleaseCandidateRuntime.create(
                Objects.requireNonNull(configuration, "configuration"));
    }

    /**
     * Starts the approved composition root from a validated configuration.
     *
     * @param configuration validated runtime configuration
     * @return started runtime whose admission remains closed until readiness publication
     */
    public static ReleaseCandidateRuntime startRuntime(
            final RuntimeConfiguration configuration) {
        final ReleaseCandidateRuntime runtime = createRuntime(configuration);
        runtime.start();
        return runtime;
    }

    public static String applicationName() {
        return APPLICATION_NAME;
    }

    /** @return reproducible application version exposed by the packaged entrypoint */
    public static String applicationVersion() {
        return APPLICATION_VERSION;
    }
}
