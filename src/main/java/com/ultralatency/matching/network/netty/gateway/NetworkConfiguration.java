package com.ultralatency.matching.network.netty.gateway;

import com.ultralatency.matching.pipeline.PipelineConfiguration;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Objects;

/**
 * Immutable resource and binding configuration for the single-session gateway.
 *
 * @param bindAddress address on which the TCP listener binds
 * @param port TCP port, where zero requests an ephemeral port
 * @param writeBufferLowWaterMark low outbound write-watermark in bytes
 * @param writeBufferHighWaterMark high outbound write-watermark in bytes
 * @param shutdownTimeout default bounded shutdown timeout
 * @param pipelineConfiguration frozen pipeline configuration
 */
public record NetworkConfiguration(
        InetAddress bindAddress,
        int port,
        int writeBufferLowWaterMark,
        int writeBufferHighWaterMark,
        Duration shutdownTimeout,
        PipelineConfiguration pipelineConfiguration) {

    /** Default low write watermark. */
    public static final int DEFAULT_LOW_WATERMARK = 8 * 1024;

    /** Default high write watermark. */
    public static final int DEFAULT_HIGH_WATERMARK = 16 * 1024;

    /** Default bounded shutdown timeout. */
    public static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(2);

    /**
     * Validates binding, watermark and lifecycle bounds.
     */
    public NetworkConfiguration {
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
        Objects.requireNonNull(shutdownTimeout, "shutdownTimeout");
        if (shutdownTimeout.isNegative() || shutdownTimeout.isZero()) {
            throw new IllegalArgumentException("Shutdown timeout must be positive");
        }
        Objects.requireNonNull(pipelineConfiguration, "pipelineConfiguration");
    }

    /**
     * Creates a configuration with the approved default resource bounds.
     *
     * @param bindAddress address on which to bind
     * @param port port, or zero for an ephemeral port
     * @return validated configuration
     */
    public static NetworkConfiguration of(final InetAddress bindAddress, final int port) {
        return new NetworkConfiguration(
                bindAddress,
                port,
                DEFAULT_LOW_WATERMARK,
                DEFAULT_HIGH_WATERMARK,
                DEFAULT_SHUTDOWN_TIMEOUT,
                PipelineConfiguration.defaults());
    }

    /**
     * Returns the approved loopback/ephemeral default.
     *
     * @return loopback configuration
     */
    public static NetworkConfiguration defaults() {
        try {
            return of(InetAddress.getLoopbackAddress(), 0);
        } catch (final RuntimeException exception) {
            throw new IllegalStateException("Unable to resolve loopback address", exception);
        }
    }

    /**
     * Creates a configuration with custom watermarks and the default pipeline.
     *
     * @param bindAddress address on which to bind
     * @param port port, or zero for an ephemeral port
     * @param lowWaterMark low outbound watermark
     * @param highWaterMark high outbound watermark
     * @param shutdownTimeout bounded shutdown timeout
     */
    public NetworkConfiguration(
            final InetAddress bindAddress,
            final int port,
            final int lowWaterMark,
            final int highWaterMark,
            final Duration shutdownTimeout) {
        this(
                bindAddress,
                port,
                lowWaterMark,
                highWaterMark,
                shutdownTimeout,
                PipelineConfiguration.defaults());
    }

    /**
     * Creates a configuration from a host name with default resource bounds.
     *
     * @param host bind host name
     * @param port port, or zero for an ephemeral port
     * @return validated configuration
     * @throws IllegalArgumentException when the host cannot be resolved
     */
    public static NetworkConfiguration of(final String host, final int port) {
        try {
            return of(InetAddress.getByName(Objects.requireNonNull(host, "host")), port);
        } catch (final UnknownHostException exception) {
            throw new IllegalArgumentException("Unknown bind host: " + host, exception);
        }
    }
}
