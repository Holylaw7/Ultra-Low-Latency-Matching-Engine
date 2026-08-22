package com.ultralatency.matching.network.netty.durable;

import com.ultralatency.matching.integration.durable.DurableAppendPort;
import com.ultralatency.matching.integration.durable.DurablePublishPort;
import java.util.Objects;

/**
 * The append and publication adapters used by one durable server composition.
 *
 * @param appendPort synchronous WAL append adapter
 * @param publishPort non-blocking pipeline publication adapter
 */
record DurableRuntimePorts(
        DurableAppendPort appendPort,
        DurablePublishPort publishPort) {

    /**
     * Validates the composition boundary.
     */
    DurableRuntimePorts {
        Objects.requireNonNull(appendPort, "appendPort");
        Objects.requireNonNull(publishPort, "publishPort");
    }
}
