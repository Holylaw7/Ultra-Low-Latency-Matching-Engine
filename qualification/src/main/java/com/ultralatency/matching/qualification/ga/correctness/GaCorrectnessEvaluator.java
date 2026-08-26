package com.ultralatency.matching.qualification.ga.correctness;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Fail-closed evaluator for the fixed G1/G2 correctness matrix. */
public final class GaCorrectnessEvaluator {

    private GaCorrectnessEvaluator() {
    }

    /** Evaluates all cases without merging evidence across independent runs. */
    public static List<String> evaluate(
            final GaCorrectnessMatrix matrix,
            final List<GaCorrectnessCaseResult> cases) {
        Objects.requireNonNull(matrix, "matrix");
        Objects.requireNonNull(cases, "cases");
        final List<String> failures = new ArrayList<>();
        final Map<String, GaCorrectnessCaseResult> byId = new HashMap<>();
        for (final GaCorrectnessCaseResult result : cases) {
            final String id = result.matrixCase().id();
            if (byId.put(id, result) != null) {
                failures.add("duplicate matrix case: " + id);
            }
        }
        final Set<String> expected = matrix.cases().stream()
                .map(GaCorrectnessCase::id).collect(Collectors.toSet());
        final Set<String> actual = new HashSet<>(byId.keySet());
        expected.stream().filter(id -> !actual.contains(id))
                .forEach(id -> failures.add("missing matrix case: " + id));
        actual.stream().filter(id -> !expected.contains(id))
                .forEach(id -> failures.add("unexpected matrix case: " + id));

        for (final GaCorrectnessCase matrixCase : matrix.cases()) {
            final GaCorrectnessCaseResult result = byId.get(matrixCase.id());
            if (result != null) {
                failures.addAll(caseFailures(matrix, result));
            }
        }
        failures.addAll(repetitionFailures(matrix, byId));
        return List.copyOf(failures);
    }

    /** Returns whether every fixed criterion is satisfied by the supplied case. */
    public static boolean passesCase(
            final GaCorrectnessMatrix matrix,
            final GaCorrectnessCaseResult result) {
        return caseFailures(matrix, result).isEmpty();
    }

    private static List<String> caseFailures(
            final GaCorrectnessMatrix matrix,
            final GaCorrectnessCaseResult result) {
        final List<String> failures = new ArrayList<>();
        final String id = result.matrixCase().id();
        if (!result.passed()) {
            failures.add(id + ": runner reported failure");
        }
        result.failures().forEach(failure -> failures.add(id + ": " + failure));

        final Map<String, GaCorrectnessObservation> observations = new HashMap<>();
        for (final GaCorrectnessObservation observation : result.observations()) {
            final String key = observation.mode() + "@" + observation.snapshotSequence();
            if (observations.put(key, observation) != null) {
                failures.add(id + ": duplicate observation " + key);
            }
        }
        final GaCorrectnessObservation live = observations.get("LIVE@0");
        final GaCorrectnessObservation pure = observations.get("PURE_WAL@0");
        if (live == null) {
            failures.add(id + ": missing LIVE observation");
        }
        if (pure == null) {
            failures.add(id + ": missing PURE_WAL observation");
        }
        final int expectedObservations = matrix.snapshotPrefixes().size() + 2;
        if (observations.size() != expectedObservations) {
            failures.add(id + ": expected " + expectedObservations
                    + " observations, got " + observations.size());
        }
        for (final int prefix : matrix.snapshotPrefixes()) {
            final String key = "SNAPSHOT_THEN_WAL@" + prefix;
            final GaCorrectnessObservation snapshot = observations.get(key);
            if (snapshot == null) {
                failures.add(id + ": missing Snapshot-tail observation at " + prefix);
            } else {
                if (snapshot.acceptedCommands() != matrix.commandCount() - prefix) {
                    failures.add(id + ": Snapshot-tail command count mismatch at " + prefix);
                }
                final String expectedDigest = result.expectedSnapshotTranscriptDigests()
                        .get(prefix);
                if (expectedDigest == null) {
                    failures.add(id + ": missing PURE_WAL suffix digest at " + prefix);
                } else if (!expectedDigest.equals(snapshot.transcriptDigestHex())) {
                    failures.add(id + ": Snapshot-tail transcript mismatch at " + prefix);
                }
                if (pure != null && !sameCheckpointAndProbe(pure, snapshot)) {
                    failures.add(id + ": Snapshot-tail final state/probe mismatch at " + prefix);
                }
            }
        }
        if (live != null && pure != null) {
            if (live.acceptedCommands() != matrix.commandCount()
                    || pure.acceptedCommands() != matrix.commandCount()) {
                failures.add(id + ": live/PURE_WAL command count mismatch");
            }
            if (!sameObservation(live, pure)) {
                failures.add(id + ": live/PURE_WAL observation mismatch");
            }
        }
        return List.copyOf(failures);
    }

    private static boolean sameObservation(
            final GaCorrectnessObservation first,
            final GaCorrectnessObservation second) {
        return first.acceptedCommands() == second.acceptedCommands()
                && first.tradeCount() == second.tradeCount()
                && first.walDigestHex().equals(second.walDigestHex())
                && first.checkpointDigestHex().equals(second.checkpointDigestHex())
                && first.transcriptDigestHex().equals(second.transcriptDigestHex())
                && first.publicProbeDigestHex().equals(second.publicProbeDigestHex());
    }

    private static boolean sameCheckpointAndProbe(
            final GaCorrectnessObservation pure,
            final GaCorrectnessObservation snapshot) {
        return pure.walDigestHex().equals(snapshot.walDigestHex())
                && pure.checkpointDigestHex().equals(snapshot.checkpointDigestHex())
                && pure.publicProbeDigestHex().equals(snapshot.publicProbeDigestHex());
    }

    private static List<String> repetitionFailures(
            final GaCorrectnessMatrix matrix,
            final Map<String, GaCorrectnessCaseResult> byId) {
        final List<String> failures = new ArrayList<>();
        for (final var profile : matrix.profiles()) {
            for (final long seed : matrix.seeds()) {
                GaCorrectnessCaseResult previous = null;
                for (int repetition = 1; repetition <= matrix.repetitions(); repetition++) {
                    final GaCorrectnessCaseResult current = byId.get(
                            new GaCorrectnessCase(profile, seed, repetition).id());
                    if (current == null) {
                        continue;
                    }
                    if (previous != null && !equivalentEvidence(previous, current)) {
                        failures.add(profile + "/" + seed
                                + ": deterministic repetitions differ");
                    }
                    previous = current;
                }
            }
        }
        return List.copyOf(failures);
    }

    private static boolean equivalentEvidence(
            final GaCorrectnessCaseResult first,
            final GaCorrectnessCaseResult second) {
        if (!first.passed() || !second.passed()
                || first.observations().size() != second.observations().size()) {
            return false;
        }
        final Map<String, GaCorrectnessObservation> firstByKey = observations(first);
        final Map<String, GaCorrectnessObservation> secondByKey = observations(second);
        if (!firstByKey.keySet().equals(secondByKey.keySet())) {
            return false;
        }
        for (final String key : firstByKey.keySet()) {
            final GaCorrectnessObservation left = firstByKey.get(key);
            final GaCorrectnessObservation right = secondByKey.get(key);
            if (!sameObservation(left, right)) {
                return false;
            }
        }
        return first.expectedSnapshotTranscriptDigests()
                .equals(second.expectedSnapshotTranscriptDigests());
    }

    private static Map<String, GaCorrectnessObservation> observations(
            final GaCorrectnessCaseResult result) {
        return result.observations().stream().collect(Collectors.toUnmodifiableMap(
                observation -> observation.mode() + "@" + observation.snapshotSequence(),
                observation -> observation));
    }
}
