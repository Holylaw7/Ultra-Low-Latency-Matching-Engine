package com.ultralatency.matching.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RuntimeExitCodeTest {

    @Test
    void mapsFailureCategoriesToStableProcessCodes() {
        assertEquals(0, RuntimeExitCode.forFailure(RuntimeFailureCode.NONE).code());
        assertEquals(2, RuntimeExitCode.forFailure(RuntimeFailureCode.CONFIG).code());
        assertEquals(3, RuntimeExitCode.forFailure(RuntimeFailureCode.RECOVERY).code());
        assertEquals(4, RuntimeExitCode.forFailure(RuntimeFailureCode.PROTOCOL_BIND).code());
        assertEquals(5, RuntimeExitCode.forFailure(RuntimeFailureCode.RUNTIME).code());
        assertEquals(6, RuntimeExitCode.forFailure(RuntimeFailureCode.SHUTDOWN_TIMEOUT).code());
    }
}
