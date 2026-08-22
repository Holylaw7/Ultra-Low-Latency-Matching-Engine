package com.ultralatency.matching.integration.durable;

import com.ultralatency.matching.persistence.wal.WalConfiguration;
import com.ultralatency.matching.pipeline.PipelineConfiguration;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Immutable configuration for the opt-in live durable composition.
 *
 * <p>The live boundary deliberately accepts only {@link LiveDurabilityMode#SYNC_EACH_APPEND}.
 * The existing WAL configuration still exposes {@code BUFFERED} for component benchmarks, but
 * this configuration prevents that mode from being described or used as live acceptance.</p>
 *
 * @param walConfiguration versioned command WAL configuration
 * @param pipelineConfiguration bounded event-pipeline configuration
 * @param shutdownTimeout maximum time allowed for a coordinated drain
 */
public record DurableConfiguration(
        LiveDurabilityMode durabilityMode,
        WalConfiguration walConfiguration,
        PipelineConfiguration pipelineConfiguration,
        Duration shutdownTimeout) {

    /** Default bounded drain timeout for the live composition. */
    public static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(2);

    /**
     * Validates the immutable live-durability boundary without allocating runtime resources.
     */
    public DurableConfiguration {
        Objects.requireNonNull(durabilityMode, "durabilityMode");
        Objects.requireNonNull(walConfiguration, "walConfiguration");
        if (walConfiguration.durabilityMode() != durabilityMode.walMode()) {
            throw new IllegalArgumentException(
                    "Live durability mode must match the WAL durability action");
        }
        Objects.requireNonNull(pipelineConfiguration, "pipelineConfiguration");
        Objects.requireNonNull(shutdownTimeout, "shutdownTimeout");
        if (shutdownTimeout.isNegative() || shutdownTimeout.isZero()) {
            throw new IllegalArgumentException("Shutdown timeout must be positive");
        }
    }

    /**
     * Creates a live configuration from an existing synchronous WAL configuration.
     *
     * @param walConfiguration synchronous WAL configuration
     * @param pipelineConfiguration bounded pipeline configuration
     * @param shutdownTimeout positive drain timeout
     */
    public DurableConfiguration(
            final WalConfiguration walConfiguration,
            final PipelineConfiguration pipelineConfiguration,
            final Duration shutdownTimeout) {
        this(LiveDurabilityMode.SYNC_EACH_APPEND,
                walConfiguration,
                pipelineConfiguration,
                shutdownTimeout);
    }

    /**
     * Creates a live configuration with the default pipeline and drain settings.
     *
     * @param durabilityMode live durability mode
     * @param walConfiguration synchronous WAL configuration
     */
    public DurableConfiguration(
            final LiveDurabilityMode durabilityMode,
            final WalConfiguration walConfiguration) {
        this(durabilityMode,
                walConfiguration,
                PipelineConfiguration.defaults(),
                DEFAULT_SHUTDOWN_TIMEOUT);
    }

    /**
     * Creates the same configuration with the mode placed after the WAL settings.
     *
     * @param walConfiguration synchronous WAL configuration
     * @param durabilityMode live durability mode
     * @param pipelineConfiguration bounded pipeline configuration
     * @param shutdownTimeout positive drain timeout
     */
    public DurableConfiguration(
            final WalConfiguration walConfiguration,
            final LiveDurabilityMode durabilityMode,
            final PipelineConfiguration pipelineConfiguration,
            final Duration shutdownTimeout) {
        this(durabilityMode, walConfiguration, pipelineConfiguration, shutdownTimeout);
    }

    /**
     * Creates a live configuration with the existing portable pipeline defaults.
     *
     * @param walConfiguration synchronous WAL configuration
     */
    public DurableConfiguration(final WalConfiguration walConfiguration) {
        this(LiveDurabilityMode.SYNC_EACH_APPEND,
                walConfiguration,
                PipelineConfiguration.defaults(),
                DEFAULT_SHUTDOWN_TIMEOUT);
    }

    /**
     * Creates a synchronous live configuration for a fresh WAL directory.
     *
     * @param directory directory for the command WAL
     * @return validated configuration
     */
    public static DurableConfiguration defaults(final Path directory) {
        return new DurableConfiguration(
                LiveDurabilityMode.SYNC_EACH_APPEND,
                WalConfiguration.defaults(Objects.requireNonNull(directory, "directory")));
    }

    /**
     * Returns the mode under the name used by live composition callers.
     *
     * @return the only configured live durability mode
     */
    public LiveDurabilityMode liveDurabilityMode() {
        return durabilityMode;
    }

    /**
     * Returns whether startup must reject a non-empty WAL.
     *
     * <p>Phase 7 starts from a genesis engine and intentionally does not provide online replay or
     * restart handoff. This is a fixed live boundary, not a caller-selectable option.</p>
     *
     * @return always {@code true}
     */
    public boolean requiresEmptyWal() {
        return true;
    }
}
