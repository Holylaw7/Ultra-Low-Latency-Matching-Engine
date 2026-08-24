package com.ultralatency.matching.app;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Loads the strict UTF-8 properties-v1 runtime configuration. */
public final class RuntimeConfigurationLoader {

    private RuntimeConfigurationLoader() {
    }

    /**
     * Reads, parses and validates one configuration file without creating storage directories.
     *
     * @param configurationFile UTF-8 properties-v1 file
     * @return validated immutable runtime configuration
     */
    public static RuntimeConfiguration load(final Path configurationFile) {
        Objects.requireNonNull(configurationFile, "configurationFile");
        final Path normalized = configurationFile.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) {
            throw new IllegalArgumentException("Configuration file is not a regular file");
        }
        final String text = readUtf8(normalized);
        final Map<String, String> values = parse(text);
        final RuntimeConfiguration configuration = RuntimeConfigurationSchema.fromValues(
                values,
                requireParent(normalized));
        rejectNonDirectoryStorage(configuration.walDirectory(), "WAL");
        rejectNonDirectoryStorage(configuration.snapshotDirectory(), "Snapshot");
        return configuration;
    }

    private static String readUtf8(final Path file) {
        try {
            final byte[] bytes = Files.readAllBytes(file);
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (final IOException exception) {
            throw new IllegalArgumentException("Configuration file cannot be read as UTF-8", exception);
        }
    }

    private static Map<String, String> parse(final String text) {
        final Map<String, String> values = new LinkedHashMap<>();
        final String[] lines = text.split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            final String line = lines[index].trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                continue;
            }
            final int separator = line.indexOf('=');
            if (separator <= 0 || separator != line.lastIndexOf('=')) {
                throw syntax(index + 1);
            }
            final String key = line.substring(0, separator).trim();
            final String value = line.substring(separator + 1).trim();
            if (key.isEmpty() || line.indexOf('\\') >= 0 || key.indexOf(':') >= 0) {
                throw syntax(index + 1);
            }
            if (values.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("Duplicate runtime configuration key: " + key);
            }
        }
        return Map.copyOf(values);
    }

    private static Path requireParent(final Path configurationFile) {
        final Path parent = configurationFile.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("Configuration file has no parent directory");
        }
        return parent;
    }

    private static void rejectNonDirectoryStorage(final Path path, final String label) {
        if (Files.exists(path) && !Files.isDirectory(path)) {
            throw new IllegalArgumentException(label + " storage path is not a directory");
        }
    }

    private static IllegalArgumentException syntax(final int line) {
        return new IllegalArgumentException(
                "Invalid strict-properties-v1 syntax at configuration line " + line);
    }
}
