package com.ultralatency.matching.network.netty.durable;

import com.ultralatency.matching.integration.durable.DurableConfiguration;
import com.ultralatency.matching.network.netty.gateway.NetworkConfiguration;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Immutable binding configuration for the opt-in Phase 7 durable server.
 *
 * <p>The durable settings remain separate from the transport settings. This prevents a protocol
 * or socket option from silently changing WAL durability semantics while still allowing the
 * durable server to reuse the frozen Phase 6 network bounds.</p>
 *
 * @param bindAddress address on which the server binds
 * @param port TCP port, where zero requests an ephemeral port
 * @param writeBufferLowWaterMark low outbound write watermark
 * @param writeBufferHighWaterMark high outbound write watermark
 * @param durableConfiguration fresh-WAL and pipeline configuration
 */
public record DurableNetworkConfiguration(
        InetAddress bindAddress,
        int port,
        int writeBufferLowWaterMark,
        int writeBufferHighWaterMark,
        DurableConfiguration durableConfiguration) {

    /** Default low outbound write watermark. */
    public static final int DEFAULT_LOW_WATERMARK = NetworkConfiguration.DEFAULT_LOW_WATERMARK;

    /** Default high outbound write watermark. */
    public static final int DEFAULT_HIGH_WATERMARK = NetworkConfiguration.DEFAULT_HIGH_WATERMARK;

    /**
     * Validates transport and durable composition bounds without opening files or sockets.
     */
    public DurableNetworkConfiguration {
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
        Objects.requireNonNull(durableConfiguration, "durableConfiguration");
    }

    /**
     * Creates a durable loopback configuration using a fresh WAL directory.
     *
     * @param directory empty directory reserved for this live WAL
     * @return validated durable network configuration
     */
    public static DurableNetworkConfiguration defaults(final Path directory) {
        return new DurableNetworkConfiguration(
                InetAddress.getLoopbackAddress(),
                0,
                DEFAULT_LOW_WATERMARK,
                DEFAULT_HIGH_WATERMARK,
                DurableConfiguration.defaults(Objects.requireNonNull(directory, "directory")));
    }

    /**
     * Returns the transport-only settings in the existing Phase 6 representation.
     *
     * @return network settings using the durable pipeline configuration
     */
    public NetworkConfiguration networkConfiguration() {
        return new NetworkConfiguration(
                bindAddress,
                port,
                writeBufferLowWaterMark,
                writeBufferHighWaterMark,
                durableConfiguration.shutdownTimeout(),
                durableConfiguration.pipelineConfiguration());
    }
}
