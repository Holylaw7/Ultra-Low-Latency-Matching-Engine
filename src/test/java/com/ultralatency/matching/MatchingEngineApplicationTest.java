package com.ultralatency.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MatchingEngineApplicationTest {

    @Test
    void exposesStableApplicationName() {
        assertEquals(
                "Ultra-Low-Latency Matching Engine",
                MatchingEngineApplication.applicationName());
    }

    @Test
    void exposesStableApplicationVersion() {
        assertEquals("0.1.0-SNAPSHOT", MatchingEngineApplication.applicationVersion());
    }
}
