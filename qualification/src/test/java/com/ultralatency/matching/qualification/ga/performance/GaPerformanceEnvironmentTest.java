package com.ultralatency.matching.qualification.ga.performance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests deterministic environment identity capture and comparison. */
class GaPerformanceEnvironmentTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void capturedEnvironmentContainsVmIdentity() throws Exception {
        final Map<String, String> captured = GaPerformanceEnvironment.capture(temporaryDirectory);
        assertTrue(captured.containsKey("java.vm.name"));
        assertTrue(captured.containsKey("java.vm.version"));
        assertTrue(captured.containsKey("heap.max.bytes"));
    }

    @Test
    void changedReferenceFieldIsReported() {
        final Map<String, String> changed = new HashMap<>(GaPerformanceEnvironment.reference());
        changed.put("os.name", "different");
        assertFalse(GaPerformanceEnvironment.matchesReference(changed));
        assertTrue(GaPerformanceEnvironment.mismatches(changed).containsKey("os.name"));
    }
}
