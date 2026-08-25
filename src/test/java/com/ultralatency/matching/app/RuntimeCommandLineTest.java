package com.ultralatency.matching.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.MatchingEngineApplication;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeCommandLineTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void helpAndVersionAreStandaloneActions() {
        final Invocation help = invoke("--help");
        assertEquals(0, help.exitCode());
        assertTrue(help.output().contains("--validate-config"));

        final Invocation version = invoke("--version");
        assertEquals(0, version.exitCode());
        assertTrue(version.output().contains(MatchingEngineApplication.applicationVersion()));
    }

    @Test
    void validatesAndPrintsCanonicalConfiguration() throws Exception {
        final Path config = writeConfig();

        final Invocation validate = invoke("--config", config.toString(), "--validate-config");
        assertEquals(0, validate.exitCode());
        assertEquals("Configuration valid" + System.lineSeparator(), validate.output());

        final Invocation print = invoke("--print-effective-config", "--config=" + config);
        assertEquals(0, print.exitCode());
        assertTrue(print.output().startsWith("lifecycle.shutdown.timeout.ms="));
        assertTrue(print.output().contains("protocol.port=9000\n"));
        assertTrue(print.error().isEmpty());
    }

    @Test
    void rejectsMissingConfigUnknownOptionsAndConflictingActions() throws Exception {
        assertEquals(2, invoke().exitCode());
        assertEquals(2, invoke("--unknown").exitCode());
        assertEquals(2, invoke("--help", "--validate-config").exitCode());
        assertEquals(2, invoke("--config", "one", "--config", "two").exitCode());
        assertEquals(2, invoke("--config", writeConfig().toString(), "--validate-config",
                "--print-effective-config").exitCode());
    }

    private Path writeConfig() throws Exception {
        final Path config = temporaryDirectory.resolve("runtime.properties");
        Files.writeString(config, ""
                + "storage.wal.directory=wal\n"
                + "storage.snapshot.directory=snapshot\n"
                + "recovery.mode=PURE_WAL\n"
                + "protocol.port=9000\n", StandardCharsets.UTF_8);
        return config;
    }

    private Invocation invoke(final String... arguments) {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final ByteArrayOutputStream error = new ByteArrayOutputStream();
        final int exitCode = RuntimeCommandLine.execute(
                arguments,
                new PrintStream(output, true, StandardCharsets.UTF_8),
                new PrintStream(error, true, StandardCharsets.UTF_8));
        return new Invocation(
                exitCode,
                output.toString(StandardCharsets.UTF_8),
                error.toString(StandardCharsets.UTF_8));
    }

    private record Invocation(int exitCode, String output, String error) {
    }
}
