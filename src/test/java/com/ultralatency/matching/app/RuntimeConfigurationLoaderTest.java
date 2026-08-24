package com.ultralatency.matching.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigurationLoaderTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsStrictUtf8FileWithDefaultsAndDoesNotCreateStorage() throws Exception {
        final Path configurationFile = write(""
                + "# comments are whole-line only\n"
                + "storage.wal.directory=wal\n"
                + "storage.snapshot.directory=snapshot\n"
                + "recovery.mode=PURE_WAL\n"
                + "protocol.port=9000\n");

        final RuntimeConfiguration configuration = RuntimeConfigurationLoader.load(
                configurationFile);

        assertEquals(65_536, configuration.walSegmentSizeBytes());
        assertEquals("127.0.0.1", configuration.protocolBindAddress().getHostAddress());
        assertEquals(9_001, configuration.managementPort());
        assertTrue(configuration.canonicalText().contains(
                "storage.wal.directory=" + temporaryDirectory.resolve("wal").toAbsolutePath()));
        assertTrue(Files.notExists(temporaryDirectory.resolve("wal")));
        assertTrue(Files.notExists(temporaryDirectory.resolve("snapshot")));
    }

    @Test
    void rejectsDuplicateMalformedAndEscapedLines() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> RuntimeConfigurationLoader.load(
                write(validPrefix() + "protocol.port=9000\nprotocol.port=9001\n")));
        assertThrows(IllegalArgumentException.class, () -> RuntimeConfigurationLoader.load(
                write(validPrefix() + "protocol.port\n")));
        assertThrows(IllegalArgumentException.class, () -> RuntimeConfigurationLoader.load(
                write(validPrefix() + "protocol.port=9000\\n")));
        assertThrows(IllegalArgumentException.class, () -> RuntimeConfigurationLoader.load(
                write(validPrefix() + "protocol.port=9000=extra\n")));
    }

    @Test
    void rejectsMalformedUtf8AndExistingNonDirectoryStorage() throws Exception {
        final Path invalidUtf8 = temporaryDirectory.resolve("invalid.properties");
        Files.write(invalidUtf8, new byte[] {(byte) 0xC3, (byte) 0x28});
        assertThrows(IllegalArgumentException.class,
                () -> RuntimeConfigurationLoader.load(invalidUtf8));

        final Path file = temporaryDirectory.resolve("wal-file");
        Files.writeString(file, "not a directory", StandardCharsets.UTF_8);
        final String content = validPrefix().replace(
                "storage.wal.directory=wal", "storage.wal.directory=wal-file")
                + "protocol.port=9000\n";
        assertThrows(IllegalArgumentException.class,
                () -> RuntimeConfigurationLoader.load(write(content)));
    }

    private Path write(final String content) throws IOException {
        final Path file = temporaryDirectory.resolve("runtime.properties");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private static String validPrefix() {
        return "storage.wal.directory=wal\n"
                + "storage.snapshot.directory=snapshot\n"
                + "recovery.mode=PURE_WAL\n";
    }
}
