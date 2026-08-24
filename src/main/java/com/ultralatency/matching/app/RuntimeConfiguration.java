package com.ultralatency.matching.app;

import com.ultralatency.matching.persistence.wal.WalDurabilityMode;
import com.ultralatency.matching.pipeline.PipelineConfiguration;
import com.ultralatency.matching.pipeline.PipelineWaitMode;
import com.ultralatency.matching.recovery.online.RecoveryMode;
import java.net.InetAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable typed configuration for the Phase 10 application boundary. */
public record RuntimeConfiguration(
        Path walDirectory,
        Path snapshotDirectory,
        RecoveryMode recoveryMode,
        int walSegmentSizeBytes,
        WalDurabilityMode walDurabilityMode,
        int pipelineCapacity,
        PipelineWaitMode pipelineWaitMode,
        InetAddress protocolBindAddress,
        int protocolPort,
        int protocolWriteLowWaterMark,
        int protocolWriteHighWaterMark,
        boolean managementEnabled,
        InetAddress managementBindAddress,
        int managementPort,
        int managementMaxConnections,
        Duration managementRequestTimeout,
        Duration shutdownTimeout) {

    /** Maximum WAL segment size accepted by the application schema. */
    public static final int MAX_WAL_SEGMENT_SIZE_BYTES = 1_073_741_824;

    /** Maximum pipeline capacity accepted by the application schema. */
    public static final int MAX_PIPELINE_CAPACITY = 1_048_576;

    /** Creates and validates an immutable application configuration. */
    public RuntimeConfiguration {
        walDirectory = normalizeDirectory(walDirectory, "walDirectory");
        snapshotDirectory = normalizeDirectory(snapshotDirectory, "snapshotDirectory");
        if (walDirectory.equals(snapshotDirectory)) {
            throw new IllegalArgumentException("WAL and Snapshot directories must differ");
        }
        Objects.requireNonNull(recoveryMode, "recoveryMode");
        if (walSegmentSizeBytes < com.ultralatency.matching.persistence.wal.WalCommandCodec
                .MIN_SEGMENT_SIZE_BYTES
                || walSegmentSizeBytes > MAX_WAL_SEGMENT_SIZE_BYTES) {
            throw new IllegalArgumentException("WAL segment size is outside application bounds");
        }
        if (walDurabilityMode != WalDurabilityMode.SYNC_EACH_APPEND) {
            throw new IllegalArgumentException("Live runtime requires SYNC_EACH_APPEND");
        }
        new PipelineConfiguration(pipelineCapacity, pipelineWaitMode);
        if (pipelineWaitMode != PipelineWaitMode.BLOCKING) {
            throw new IllegalArgumentException("Live runtime requires BLOCKING pipeline wait mode");
        }
        if (pipelineCapacity > MAX_PIPELINE_CAPACITY) {
            throw new IllegalArgumentException("Pipeline capacity is outside application bounds");
        }
        requireLoopback(protocolBindAddress, "protocolBindAddress");
        requirePort(protocolPort, "protocolPort");
        requireWatermarks(protocolWriteLowWaterMark, protocolWriteHighWaterMark);
        requireLoopback(managementBindAddress, "managementBindAddress");
        if (managementEnabled) {
            requirePort(managementPort, "managementPort");
            if (protocolPort == managementPort) {
                throw new IllegalArgumentException("Protocol and management ports must differ");
            }
        }
        if (managementMaxConnections < 1 || managementMaxConnections > 64) {
            throw new IllegalArgumentException("Management connection count is outside bounds");
        }
        requireDuration(managementRequestTimeout, 100, 10_000, "managementRequestTimeout");
        requireDuration(shutdownTimeout, 100, 60_000, "shutdownTimeout");
    }

    /** Returns the canonical sorted effective configuration fields. */
    public Map<String, String> canonicalProperties() {
        final Map<String, String> values = new TreeMap<>();
        values.put("lifecycle.shutdown.timeout.ms", Long.toString(shutdownTimeout.toMillis()));
        values.put("management.bind.address", managementBindAddress.getHostAddress());
        values.put("management.enabled", Boolean.toString(managementEnabled));
        values.put("management.max.connections", Integer.toString(managementMaxConnections));
        values.put("management.port", Integer.toString(managementPort));
        values.put("management.request.timeout.ms",
                Long.toString(managementRequestTimeout.toMillis()));
        values.put("pipeline.capacity", Integer.toString(pipelineCapacity));
        values.put("pipeline.wait.mode", pipelineWaitMode.name());
        values.put("protocol.bind.address", protocolBindAddress.getHostAddress());
        values.put("protocol.port", Integer.toString(protocolPort));
        values.put("protocol.write.high.bytes", Integer.toString(protocolWriteHighWaterMark));
        values.put("protocol.write.low.bytes", Integer.toString(protocolWriteLowWaterMark));
        values.put("recovery.mode", recoveryMode.name());
        values.put("storage.snapshot.directory", snapshotDirectory.toString());
        values.put("storage.wal.directory", walDirectory.toString());
        values.put("wal.durability.mode", walDurabilityMode.name());
        values.put("wal.segment.size.bytes", Integer.toString(walSegmentSizeBytes));
        return Map.copyOf(values);
    }

    /** Returns canonical key/value text with a final newline. */
    public String canonicalText() {
        final StringBuilder text = new StringBuilder();
        canonicalProperties().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> text.append(entry.getKey()).append('=').append(entry.getValue())
                        .append('\n'));
        return text.toString();
    }

    private static Path normalizeDirectory(final Path path, final String name) {
        Objects.requireNonNull(path, name);
        if (path.toString().isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return path.toAbsolutePath().normalize();
    }

    private static void requireLoopback(final InetAddress address, final String name) {
        Objects.requireNonNull(address, name);
        if (!address.isLoopbackAddress()) {
            throw new IllegalArgumentException(name + " must be loopback");
        }
    }

    private static void requirePort(final int port, final String name) {
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException(name + " must be between 1 and 65535");
        }
    }

    private static void requireWatermarks(final int low, final int high) {
        if (low < 0 || low > 16_777_215 || high <= low || high > 16_777_216) {
            throw new IllegalArgumentException("Protocol write watermarks are outside bounds");
        }
    }

    private static void requireDuration(
            final Duration value,
            final long minimumMillis,
            final long maximumMillis,
            final String name) {
        Objects.requireNonNull(value, name);
        if (value.toMillis() < minimumMillis || value.toMillis() > maximumMillis
                || !Duration.ofMillis(value.toMillis()).equals(value)) {
            throw new IllegalArgumentException(name + " must be an integral bounded duration");
        }
    }
}
