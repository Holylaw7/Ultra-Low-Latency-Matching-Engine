package com.ultralatency.matching.app;

import com.ultralatency.matching.persistence.wal.WalDurabilityMode;
import com.ultralatency.matching.pipeline.PipelineWaitMode;
import com.ultralatency.matching.recovery.online.RecoveryMode;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Typed validator and key schema for strict-properties-v1 runtime input. */
public final class RuntimeConfigurationSchema {

    /** Required and optional keys accepted by the Phase 10 configuration contract. */
    public static final Set<String> KEYS = Set.of(
            "storage.wal.directory",
            "storage.snapshot.directory",
            "recovery.mode",
            "wal.segment.size.bytes",
            "wal.durability.mode",
            "pipeline.capacity",
            "pipeline.wait.mode",
            "protocol.bind.address",
            "protocol.port",
            "protocol.write.low.bytes",
            "protocol.write.high.bytes",
            "management.enabled",
            "management.bind.address",
            "management.port",
            "management.max.connections",
            "management.request.timeout.ms",
            "lifecycle.shutdown.timeout.ms");

    private RuntimeConfigurationSchema() {
    }

    /** Converts validated key/value strings into the immutable runtime record. */
    public static RuntimeConfiguration fromValues(
            final Map<String, String> values,
            final Path configurationDirectory) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(configurationDirectory, "configurationDirectory");
        final Set<String> unknown = new HashSet<>(values.keySet());
        unknown.removeAll(KEYS);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unknown runtime configuration key: " + unknown);
        }
        return new RuntimeConfiguration(
                requiredPath(values, "storage.wal.directory", configurationDirectory),
                requiredPath(values, "storage.snapshot.directory", configurationDirectory),
                enumValue(values, "recovery.mode", RecoveryMode.class, true),
                integer(values, "wal.segment.size.bytes", 65_536),
                enumValue(values, "wal.durability.mode", WalDurabilityMode.class, false),
                integer(values, "pipeline.capacity", 1_024),
                enumValue(values, "pipeline.wait.mode", PipelineWaitMode.class, false),
                address(values, "protocol.bind.address", "127.0.0.1"),
                integer(values, "protocol.port", -1),
                integer(values, "protocol.write.low.bytes", 8_192),
                integer(values, "protocol.write.high.bytes", 16_384),
                booleanValue(values, "management.enabled", true),
                address(values, "management.bind.address", "127.0.0.1"),
                integer(values, "management.port", 9_001),
                integer(values, "management.max.connections", 16),
                Duration.ofMillis(integer(values, "management.request.timeout.ms", 1_000)),
                Duration.ofMillis(integer(values, "lifecycle.shutdown.timeout.ms", 2_000)));
    }

    private static Path requiredPath(
            final Map<String, String> values,
            final String key,
            final Path base) {
        final String raw = values.get(key);
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Missing required runtime configuration key: " + key);
        }
        final Path path = Path.of(raw.trim());
        return path.isAbsolute() ? path : base.toAbsolutePath().normalize().resolve(path);
    }

    private static int integer(final Map<String, String> values, final String key, final int fallback) {
        final String raw = values.getOrDefault(key, Integer.toString(fallback));
        try {
            return Integer.parseInt(raw.trim());
        } catch (final NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid integer for runtime key: " + key, exception);
        }
    }

    private static boolean booleanValue(
            final Map<String, String> values,
            final String key,
            final boolean fallback) {
        final String raw = values.getOrDefault(key, Boolean.toString(fallback)).trim();
        if (!"true".equals(raw) && !"false".equals(raw)) {
            throw new IllegalArgumentException("Invalid boolean for runtime key: " + key);
        }
        return Boolean.parseBoolean(raw);
    }

    private static <T extends Enum<T>> T enumValue(
            final Map<String, String> values,
            final String key,
            final Class<T> type,
            final boolean required) {
        final String raw = values.get(key);
        if (raw == null && required) {
            throw new IllegalArgumentException("Missing required runtime configuration key: " + key);
        }
        final String value = raw == null ? (key.equals("wal.durability.mode")
                ? WalDurabilityMode.SYNC_EACH_APPEND.name() : PipelineWaitMode.BLOCKING.name()) : raw;
        try {
            return Enum.valueOf(type, value.trim());
        } catch (final IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid enum for runtime key: " + key, exception);
        }
    }

    private static InetAddress address(
            final Map<String, String> values,
            final String key,
            final String fallback) {
        final String host = values.getOrDefault(key, fallback).trim();
        try {
            return InetAddress.getByName(host);
        } catch (final UnknownHostException exception) {
            throw new IllegalArgumentException("Invalid address for runtime key: " + key, exception);
        }
    }
}
