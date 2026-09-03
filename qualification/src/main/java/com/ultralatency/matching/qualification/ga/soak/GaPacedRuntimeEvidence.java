package com.ultralatency.matching.qualification.ga.soak;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Qualification-owned raw runtime evidence for one paced Protocol-v2 run. */
public record GaPacedRuntimeEvidence(
        int configuredWindow,
        int maximumObservedInFlight,
        int maximumObservedPendingWire,
        int maximumObservedCompletedUndrained,
        long readerWakeCount,
        List<Long> readerWakeNanos,
        long capacityReleaseCount,
        List<CapacityRelease> capacityReleases,
        long measurementStartNanos,
        long measurementEndNanos,
        String qualificationJarSha256,
        Map<String, String> invocationFields) {

    /** One validated response and its capacity-release chronology. */
    public record CapacityRelease(
            long requestId,
            long commandSequence,
            long offeredNanos,
            long responseCompletedNanos,
            long capacityReleaseNanos,
            long schedulerConsumedNanos) {
        public CapacityRelease {
            if (requestId <= 0L || commandSequence <= 0L || offeredNanos < 0L
                    || responseCompletedNanos < offeredNanos
                    || capacityReleaseNanos < responseCompletedNanos
                    || schedulerConsumedNanos < capacityReleaseNanos) {
                throw new IllegalArgumentException("capacity release chronology is invalid");
            }
        }

        /** Returns response-complete to capacity-release delay. */
        public long releaseDelayNanos() {
            return capacityReleaseNanos - responseCompletedNanos;
        }
    }

    /** Validates one immutable runtime evidence snapshot. */
    public GaPacedRuntimeEvidence {
        if (configuredWindow < 1 || maximumObservedInFlight < 0
                || maximumObservedPendingWire < 0 || maximumObservedCompletedUndrained < 0
                || readerWakeCount < 0 || capacityReleaseCount < 0
                || measurementStartNanos < 0L || measurementEndNanos < measurementStartNanos
                || maximumObservedInFlight > configuredWindow
                || maximumObservedPendingWire > configuredWindow
                || maximumObservedCompletedUndrained > configuredWindow) {
            throw new IllegalArgumentException("paced runtime evidence is outside its bounds");
        }
        Objects.requireNonNull(qualificationJarSha256, "qualificationJarSha256");
        if (!qualificationJarSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("qualificationJarSha256 must be lowercase SHA-256");
        }
        capacityReleases = List.copyOf(Objects.requireNonNull(capacityReleases,
                "capacityReleases"));
        readerWakeNanos = List.copyOf(Objects.requireNonNull(readerWakeNanos,
                "readerWakeNanos"));
        invocationFields = Map.copyOf(GaQuickInvocation.requireForEvidence(invocationFields));
        if (readerWakeCount != readerWakeNanos.size()) {
            throw new IllegalArgumentException("reader wake count does not match raw events");
        }
        long previousWake = -1L;
        for (Long wake : readerWakeNanos) {
            if (wake == null || wake < previousWake) {
                throw new IllegalArgumentException("reader wake chronology is invalid");
            }
            previousWake = wake;
        }
        if (capacityReleaseCount != capacityReleases.size()) {
            throw new IllegalArgumentException("capacity release count does not match raw events");
        }
        for (CapacityRelease release : capacityReleases) {
            Objects.requireNonNull(release, "capacity release");
        }
        final String configured = invocationFields.get("protocolV2.window");
        if (!Integer.toString(configuredWindow).equals(configured)) {
            throw new IllegalArgumentException("invocation window does not match runtime evidence");
        }
    }

    /** Returns the canonical invocation identity. */
    public String invocationIdentitySha256() {
        return GaQuickInvocation.identity(invocationFields);
    }

    /** Returns the exact measurement duration represented by the absolute boundaries. */
    public long measurementDurationNanos() {
        return measurementEndNanos - measurementStartNanos;
    }

    /** Returns raw capacity evidence sufficient for independent recomputation. */
    public String capacityCsv() {
        final StringBuilder text = new StringBuilder(
                "requestId,commandSequence,offeredNanos,responseCompletedNanos,"
                        + "capacityReleaseNanos,schedulerConsumedNanos,releaseDelayNanos\n");
        for (CapacityRelease release : capacityReleases) {
            text.append(release.requestId()).append(',')
                    .append(release.commandSequence()).append(',')
                    .append(release.offeredNanos()).append(',')
                    .append(release.responseCompletedNanos()).append(',')
                    .append(release.capacityReleaseNanos()).append(',')
                    .append(release.schedulerConsumedNanos()).append(',')
                    .append(release.releaseDelayNanos()).append('\n');
        }
        return text.toString();
    }

    /** Returns raw reader wake evidence sufficient to recompute the wake count. */
    public String readerWakeCsv() {
        final StringBuilder text = new StringBuilder(
                "wakeOrdinal,wakeMonotonicNanos\n");
        for (int index = 0; index < readerWakeNanos.size(); index++) {
            text.append(index + 1).append(',').append(readerWakeNanos.get(index)).append('\n');
        }
        return text.toString();
    }

    /** Returns canonical raw measurement-boundary evidence. */
    public String measurementBoundaryText() {
        return "measurement.schema=qualification-measurement-boundary-v1\n"
                + "measurement.startNanos=" + measurementStartNanos + "\n"
                + "measurement.endNanos=" + measurementEndNanos + "\n"
                + "measurement.durationNanos=" + measurementDurationNanos() + "\n";
    }

    /** Returns summary fields copied into the canonical run manifest. */
    public Map<String, String> manifestFields() {
        final List<Long> delays = capacityReleases.stream()
                .map(CapacityRelease::releaseDelayNanos)
                .sorted()
                .toList();
        return Map.ofEntries(
                Map.entry("evidence.capacity.maxInFlight", Integer.toString(maximumObservedInFlight)),
                Map.entry("evidence.capacity.maxPendingWire", Integer.toString(
                        maximumObservedPendingWire)),
                Map.entry("evidence.capacity.maxCompletedUndrained", Integer.toString(
                        maximumObservedCompletedUndrained)),
                Map.entry("evidence.capacity.readerWakeCount", Long.toString(readerWakeCount)),
                Map.entry("evidence.capacity.releaseCount", Long.toString(capacityReleaseCount)),
                Map.entry("evidence.capacity.releaseDelayP50Nanos", Long.toString(percentile(delays,
                        50))),
                Map.entry("evidence.capacity.releaseDelayP90Nanos", Long.toString(percentile(delays,
                        90))),
                Map.entry("evidence.capacity.releaseDelayP99Nanos", Long.toString(percentile(delays,
                        99))),
                Map.entry("evidence.capacity.releaseDelayMaxNanos", Long.toString(delays.isEmpty()
                        ? 0L : delays.get(delays.size() - 1))),
                Map.entry("evidence.measurementStartNanos", Long.toString(measurementStartNanos)),
                Map.entry("evidence.measurementEndNanos", Long.toString(measurementEndNanos)),
                Map.entry("evidence.measurementDurationNanos", Long.toString(measurementDurationNanos())));
    }

    /** Returns an empty legacy fixture snapshot for publisher compatibility tests. */
    static GaPacedRuntimeEvidence legacy(final long startNanos, final long endNanos) {
        final int window = 1;
        return new GaPacedRuntimeEvidence(window, 0, 0, 0, 0L, List.of(), 0L, List.of(), startNanos,
                endNanos, "0".repeat(64), Map.of(
                        "invocation.schema", GaQuickInvocation.VERSION,
                        "protocolV2.window", Integer.toString(window)));
    }

    private static long percentile(final List<Long> sorted, final int percentile) {
        if (sorted.isEmpty()) {
            return 0L;
        }
        final int rank = Math.max(1, (int) Math.ceil(sorted.size() * percentile / 100.0));
        return sorted.get(rank - 1);
    }
}
