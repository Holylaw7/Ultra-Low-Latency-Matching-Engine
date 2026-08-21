package com.ultralatency.matching.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PipelineConfigurationTest {

    @Test
    void defaultsUseBlockingModeAndDocumentedCapacity() {
        final PipelineConfiguration configuration = PipelineConfiguration.defaults();

        assertEquals(PipelineConfiguration.DEFAULT_CAPACITY, configuration.capacity());
        assertEquals(PipelineWaitMode.BLOCKING, configuration.waitMode());
    }

    @Test
    void acceptsMinimumAndPowerOfTwoCapacities() {
        assertEquals(2, new PipelineConfiguration(2, PipelineWaitMode.BLOCKING).capacity());
        assertEquals(1024, new PipelineConfiguration(1024, PipelineWaitMode.YIELDING).capacity());
        assertEquals(1 << 20, new PipelineConfiguration(1 << 20, PipelineWaitMode.BUSY_SPIN).capacity());
    }

    @Test
    void rejectsCapacityBelowMinimum() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PipelineConfiguration(1, PipelineWaitMode.BLOCKING));
    }

    @Test
    void rejectsNonPowerOfTwoCapacity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PipelineConfiguration(3, PipelineWaitMode.BLOCKING));
    }

    @Test
    void rejectsNullWaitMode() {
        assertThrows(
                NullPointerException.class,
                () -> new PipelineConfiguration(2, null));
    }

    @Test
    void remainsAValueObject() {
        assertEquals(
                new PipelineConfiguration(1024, PipelineWaitMode.BLOCKING),
                new PipelineConfiguration(1024, PipelineWaitMode.BLOCKING));
    }
}
