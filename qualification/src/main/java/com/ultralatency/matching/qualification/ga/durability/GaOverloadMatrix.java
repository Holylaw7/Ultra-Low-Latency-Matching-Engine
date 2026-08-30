package com.ultralatency.matching.qualification.ga.durability;

import java.util.List;
import java.util.Objects;

/** Immutable, bounded G7 overload matrix. */
public record GaOverloadMatrix(
        String version,
        int pipelineCapacity,
        int maxRequestFrameBytes,
        int maxManagementRequestBytes,
        int sessionAttempts,
        int pipelinedRequestCount,
        List<GaOverloadScenario> scenarios) {

    /** Frozen G7 matrix identity. */
    public static final String APPROVED_VERSION = "ga-g7-overload-v1";
    /** The production pipeline capacity used by the approved runtime. */
    public static final int APPROVED_PIPELINE_CAPACITY = 1_024;
    /** Protocol v1 maximum frame bound. */
    public static final int APPROVED_MAX_REQUEST_FRAME_BYTES = 104;
    /** Management request bound from the Phase 10 management contract. */
    public static final int APPROVED_MAX_MANAGEMENT_REQUEST_BYTES =
            com.ultralatency.matching.operations.ManagementProtocol.MAX_REQUEST_BYTES;
    /** Number of independent second-session attempts in the focused matrix. */
    public static final int APPROVED_SESSION_ATTEMPTS = 3;
    /** Number of coalesced/pipelined requests used by the overload probe. */
    public static final int APPROVED_PIPELINED_REQUEST_COUNT = 2;

    /** Creates and validates a bounded matrix. */
    public GaOverloadMatrix {
        Objects.requireNonNull(version, "version");
        if (version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
        if (pipelineCapacity < 2
                || pipelineCapacity > com.ultralatency.matching.app.RuntimeConfiguration
                        .MAX_PIPELINE_CAPACITY
                || Integer.bitCount(pipelineCapacity) != 1) {
            throw new IllegalArgumentException("pipelineCapacity must be a power of two");
        }
        if (maxRequestFrameBytes <= 0
                || maxRequestFrameBytes > com.ultralatency.matching.network.protocol
                        .ProtocolConstants.MAX_FRAME_LENGTH
                || maxManagementRequestBytes <= 0
                || maxManagementRequestBytes > com.ultralatency.matching.operations
                        .ManagementProtocol.MAX_REQUEST_BYTES) {
            throw new IllegalArgumentException("frame bounds must be positive");
        }
        if (sessionAttempts <= 0 || sessionAttempts > 64 || pipelinedRequestCount < 2
                || pipelinedRequestCount > 64) {
            throw new IllegalArgumentException("overload counts are outside bounds");
        }
        scenarios = List.copyOf(Objects.requireNonNull(scenarios, "scenarios"));
        if (scenarios.isEmpty() || scenarios.stream().anyMatch(Objects::isNull)
                || scenarios.stream().distinct().count() != scenarios.size()) {
            throw new IllegalArgumentException("overload scenarios must be unique and non-empty");
        }
    }

    /** Returns the Human-approved G7 matrix. */
    public static GaOverloadMatrix approved() {
        return new GaOverloadMatrix(
                APPROVED_VERSION,
                APPROVED_PIPELINE_CAPACITY,
                APPROVED_MAX_REQUEST_FRAME_BYTES,
                APPROVED_MAX_MANAGEMENT_REQUEST_BYTES,
                APPROVED_SESSION_ATTEMPTS,
                APPROVED_PIPELINED_REQUEST_COUNT,
                List.of(GaOverloadScenario.values()));
    }

    /** Returns a small deterministic matrix for focused tests only. */
    public static GaOverloadMatrix test() {
        return new GaOverloadMatrix(
                "ga-g7-overload-test-v1",
                2,
                APPROVED_MAX_REQUEST_FRAME_BYTES,
                APPROVED_MAX_MANAGEMENT_REQUEST_BYTES,
                APPROVED_SESSION_ATTEMPTS,
                2,
                List.of(GaOverloadScenario.values()));
    }

    /** Returns whether this is the exact approved matrix. */
    public boolean isApproved() {
        return APPROVED_VERSION.equals(version)
                && pipelineCapacity == APPROVED_PIPELINE_CAPACITY
                && maxRequestFrameBytes == APPROVED_MAX_REQUEST_FRAME_BYTES
                && maxManagementRequestBytes == APPROVED_MAX_MANAGEMENT_REQUEST_BYTES
                && sessionAttempts == APPROVED_SESSION_ATTEMPTS
                && pipelinedRequestCount == APPROVED_PIPELINED_REQUEST_COUNT
                && scenarios.equals(List.of(GaOverloadScenario.values()));
    }
}
