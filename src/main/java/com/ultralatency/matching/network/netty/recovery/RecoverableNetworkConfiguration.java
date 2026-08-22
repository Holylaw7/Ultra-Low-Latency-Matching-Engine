package com.ultralatency.matching.network.netty.recovery;

import com.ultralatency.matching.integration.durable.DurableConfiguration;
import com.ultralatency.matching.network.netty.durable.DurableNetworkConfiguration;
import com.ultralatency.matching.recovery.online.RecoveryMode;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.Objects;

/** Immutable transport, Snapshot and recovery-mode configuration for Phase 8 handoff. */
public record RecoverableNetworkConfiguration(
        InetAddress bindAddress,
        int port,
        int writeBufferLowWaterMark,
        int writeBufferHighWaterMark,
        Path snapshotDirectory,
        RecoveryMode recoveryMode,
        DurableConfiguration durableConfiguration) {

    /** Validates transport and recovery settings without opening resources. */
    public RecoverableNetworkConfiguration {
        Objects.requireNonNull(bindAddress, "bindAddress");
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("Port must be between 0 and 65535");
        }
        if (writeBufferLowWaterMark < 0) {
            throw new IllegalArgumentException("Low write watermark must not be negative");
        }
        if (writeBufferHighWaterMark <= writeBufferLowWaterMark) {
            throw new IllegalArgumentException("High write watermark must exceed low watermark");
        }
        Objects.requireNonNull(snapshotDirectory, "snapshotDirectory");
        Objects.requireNonNull(recoveryMode, "recoveryMode");
        Objects.requireNonNull(durableConfiguration, "durableConfiguration");
    }

    /** Creates a recoverable configuration from the existing durable transport bounds. */
    public static RecoverableNetworkConfiguration from(
            final DurableNetworkConfiguration networkConfiguration,
            final Path snapshotDirectory,
            final RecoveryMode recoveryMode) {
        Objects.requireNonNull(networkConfiguration, "networkConfiguration");
        return new RecoverableNetworkConfiguration(
                networkConfiguration.bindAddress(),
                networkConfiguration.port(),
                networkConfiguration.writeBufferLowWaterMark(),
                networkConfiguration.writeBufferHighWaterMark(),
                snapshotDirectory,
                recoveryMode,
                networkConfiguration.durableConfiguration());
    }
}
