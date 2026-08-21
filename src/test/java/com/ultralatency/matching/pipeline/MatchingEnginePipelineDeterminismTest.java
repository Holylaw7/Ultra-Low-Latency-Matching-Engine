package com.ultralatency.matching.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ultralatency.matching.engine.EngineCommand;
import com.ultralatency.matching.engine.EngineResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class MatchingEnginePipelineDeterminismTest {

    @Test
    void pipelineMatchesDirectEngineForAtLeast1024Commands() throws InterruptedException {
        final List<EngineCommand> commands = PipelineCommandFixture.commandStream(128);
        final List<EngineResult> directResults = new ArrayList<>(commands.size());
        final com.ultralatency.matching.engine.MatchingEngine directEngine =
                new com.ultralatency.matching.engine.MatchingEngine();
        for (final EngineCommand command : commands) {
            directResults.add(directEngine.process(command));
        }

        final List<EngineResult> pipelineResults = new ArrayList<>(commands.size());
        final CountDownLatch completed = new CountDownLatch(commands.size());
        final MatchingEnginePipeline pipeline = new MatchingEnginePipeline(
                new PipelineConfiguration(2048, PipelineWaitMode.BLOCKING), result -> {
                    pipelineResults.add(result);
                    completed.countDown();
                });

        pipeline.start();
        for (final EngineCommand command : commands) {
            assertEquals(PipelinePublishOutcome.ACCEPTED, pipeline.tryPublish(command));
        }
        assertTrue(completed.await(10, TimeUnit.SECONDS));
        assertEquals(PipelineState.STOPPED, pipeline.shutdown(Duration.ofSeconds(5)));

        assertEquals(directResults, pipelineResults);
        assertEquals(commands.size(), pipelineResults.size());
        for (int index = 0; index < pipelineResults.size(); index++) {
            assertEquals(commands.get(index).sequence(), pipelineResults.get(index).commandSequence());
        }
    }
}
