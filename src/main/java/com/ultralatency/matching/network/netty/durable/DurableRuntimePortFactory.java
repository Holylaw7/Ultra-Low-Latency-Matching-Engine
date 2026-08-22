package com.ultralatency.matching.network.netty.durable;

import com.ultralatency.matching.integration.durable.DurableAppendPort;
import com.ultralatency.matching.integration.durable.DurablePublishPort;

/**
 * Runtime composition boundary for the Phase 7 durable adapters.
 *
 * <p>The normal server constructor uses the identity factory, so production wiring still uses
 * the real WAL and pipeline adapters. Tests may wrap those same adapters to establish
 * deterministic post-return barriers without adding a test-only execution path.</p>
 */
@FunctionalInterface
interface DurableRuntimePortFactory {

    /**
     * Wraps the real adapters used by the live composition.
     *
     * @param appendPort real synchronous WAL append adapter
     * @param publishPort real non-blocking pipeline publication adapter
     * @return adapters used by the coordinator
     */
    DurableRuntimePorts create(DurableAppendPort appendPort, DurablePublishPort publishPort);

    /**
     * Returns the production identity composition.
     *
     * @return identity adapter factory
     */
    static DurableRuntimePortFactory production() {
        return DurableRuntimePorts::new;
    }
}
