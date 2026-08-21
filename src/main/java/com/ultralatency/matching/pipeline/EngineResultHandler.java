package com.ultralatency.matching.pipeline;

import com.ultralatency.matching.engine.EngineResult;

/**
 * Project-owned result observation boundary for a future matching pipeline.
 *
 * <p>The eventual pipeline invokes this handler synchronously on its matching consumer thread.
 * This contract does not provide I/O, publication, threading or persistence semantics.</p>
 */
@FunctionalInterface
public interface EngineResultHandler {

    /**
     * Observes one immutable engine result.
     *
     * @param result result produced by the frozen synchronous MatchingEngine
     */
    void onResult(EngineResult result);
}
