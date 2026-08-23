package com.ultralatency.matching.integration.recovery;

import com.ultralatency.matching.integration.durable.DurableConfiguration;
import com.ultralatency.matching.recovery.online.RecoveryMode;
import java.nio.file.Path;
import java.util.Objects;

/** Immutable configuration for one opt-in recovered live composition. */
public record RecoveryRuntimeConfiguration(
        RecoveryMode recoveryMode,
        Path snapshotDirectory,
        DurableConfiguration durableConfiguration) {

    /** Validates the recovery and live-durability boundaries. */
    public RecoveryRuntimeConfiguration {
        Objects.requireNonNull(recoveryMode, "recoveryMode");
        Objects.requireNonNull(snapshotDirectory, "snapshotDirectory");
        Objects.requireNonNull(durableConfiguration, "durableConfiguration");
    }

    /** Creates a configuration with the explicit recovery mode and WAL directory. */
    public static RecoveryRuntimeConfiguration of(
            final RecoveryMode recoveryMode,
            final Path snapshotDirectory,
            final DurableConfiguration durableConfiguration) {
        return new RecoveryRuntimeConfiguration(
                recoveryMode,
                snapshotDirectory,
                durableConfiguration);
    }
}
