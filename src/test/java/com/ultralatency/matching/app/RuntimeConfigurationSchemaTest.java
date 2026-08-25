package com.ultralatency.matching.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.pipeline.PipelineWaitMode;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuntimeConfigurationSchemaTest {

    @Test
    void appliesOnlyApprovedDefaultsAndCanonicalizesPaths() {
        final RuntimeConfiguration configuration = RuntimeConfigurationSchema.fromValues(
                values(), Path.of("config"));

        assertEquals(Path.of("config/wal").toAbsolutePath().normalize(),
                configuration.walDirectory());
        assertEquals(PipelineWaitMode.BLOCKING, configuration.pipelineWaitMode());
        assertEquals(17, configuration.canonicalProperties().size());
        assertEquals("BLOCKING", configuration.canonicalProperties().get("pipeline.wait.mode"));
        assertEquals("127.0.0.1", configuration.canonicalProperties()
                .get("protocol.bind.address"));
        final String canonical = configuration.canonicalText();
        assertTrue(canonical.startsWith("lifecycle.shutdown.timeout.ms="));
        assertTrue(canonical.endsWith("wal.segment.size.bytes=65536\n"));
    }

    @Test
    void rejectsUnknownAndMissingKeys() {
        final Map<String, String> unknown = values();
        unknown.put("runtime.unknown", "true");
        assertThrows(IllegalArgumentException.class,
                () -> RuntimeConfigurationSchema.fromValues(unknown, Path.of("config")));

        final Map<String, String> missing = values();
        missing.remove("protocol.port");
        assertThrows(IllegalArgumentException.class,
                () -> RuntimeConfigurationSchema.fromValues(missing, Path.of("config")));
    }

    @Test
    void rejectsUnsafeDurabilityWaitModeAndNetworkAddress() {
        final Map<String, String> buffered = values();
        buffered.put("wal.durability.mode", "BUFFERED");
        assertThrows(IllegalArgumentException.class,
                () -> RuntimeConfigurationSchema.fromValues(buffered, Path.of("config")));

        final Map<String, String> yielding = values();
        yielding.put("pipeline.wait.mode", "YIELDING");
        assertThrows(IllegalArgumentException.class,
                () -> RuntimeConfigurationSchema.fromValues(yielding, Path.of("config")));

        final Map<String, String> publicAddress = values();
        publicAddress.put("protocol.bind.address", "8.8.8.8");
        assertThrows(IllegalArgumentException.class,
                () -> RuntimeConfigurationSchema.fromValues(publicAddress, Path.of("config")));
    }

    @Test
    void rejectsCrossFieldAndRangeViolations() {
        final Map<String, String> sameDirectory = values();
        sameDirectory.put("storage.snapshot.directory", "wal");
        assertThrows(IllegalArgumentException.class,
                () -> RuntimeConfigurationSchema.fromValues(sameDirectory, Path.of("config")));

        final Map<String, String> samePort = values();
        samePort.put("management.port", "9000");
        assertThrows(IllegalArgumentException.class,
                () -> RuntimeConfigurationSchema.fromValues(samePort, Path.of("config")));

        final Map<String, String> hugePipeline = values();
        hugePipeline.put("pipeline.capacity", "2097152");
        assertThrows(IllegalArgumentException.class,
                () -> RuntimeConfigurationSchema.fromValues(hugePipeline, Path.of("config")));

        final Map<String, String> tinyTimeout = values();
        tinyTimeout.put("management.request.timeout.ms", "99");
        assertThrows(IllegalArgumentException.class,
                () -> RuntimeConfigurationSchema.fromValues(tinyTimeout, Path.of("config")));
    }

    private static Map<String, String> values() {
        final Map<String, String> values = new HashMap<>();
        values.put("storage.wal.directory", "wal");
        values.put("storage.snapshot.directory", "snapshot");
        values.put("recovery.mode", "PURE_WAL");
        values.put("protocol.port", "9000");
        return values;
    }
}
