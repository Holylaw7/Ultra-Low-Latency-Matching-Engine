package com.ultralatency.matching.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.ultralatency.matching.domain.Sequence;
import com.ultralatency.matching.engine.CommandOutcome;
import com.ultralatency.matching.engine.EngineResult;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PipelineContractTest {

    @Test
    void exposesOnlyProjectOwnedPipelineEnums() {
        assertEquals(
                List.of(PipelineWaitMode.BLOCKING, PipelineWaitMode.YIELDING, PipelineWaitMode.BUSY_SPIN),
                List.of(PipelineWaitMode.values()));
        assertEquals(
                List.of(PipelinePublishOutcome.ACCEPTED, PipelinePublishOutcome.FULL),
                List.of(PipelinePublishOutcome.values()));
        assertEquals(
                List.of(
                        PipelineState.NEW,
                        PipelineState.RUNNING,
                        PipelineState.DRAINING,
                        PipelineState.STOPPED,
                        PipelineState.FAILED),
                List.of(PipelineState.values()));
    }

    @Test
    void resultHandlerReceivesImmutableEngineResult() {
        final EngineResult result = new EngineResult(
                Sequence.of(1), CommandOutcome.ACCEPTED, List.of());
        final AtomicReference<EngineResult> observed = new AtomicReference<>();
        final EngineResultHandler handler = observed::set;

        handler.onResult(result);

        assertNotNull(observed.get());
        assertEquals(result, observed.get());
        assertEquals(List.of(), observed.get().matches());
    }
}
