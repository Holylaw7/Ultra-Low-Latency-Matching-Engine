package com.ultralatency.matching.qualification.ga.soak;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Fail-closed G6 duration, count, correctness and latency-drift evaluator. */
public final class GaSoakEvaluator {

    /** Maximum permitted final-window P99 regression percentage. */
    public static final int MAX_P99_REGRESSION_PERCENT = 20;

    private GaSoakEvaluator() {
    }

    /** One explicit G6 predicate. */
    public record Criterion(
            String id,
            String actual,
            String operator,
            String required,
            boolean passed) {
        public Criterion {
            requireText(id, "id");
            requireText(actual, "actual");
            requireText(operator, "operator");
            requireText(required, "required");
        }
    }

    /** Immutable outcome of a G6 evaluation. */
    public record Evaluation(
            boolean passed,
            boolean formalEligible,
            String outcome,
            String failureCode,
            List<Criterion> criteria) {
        public Evaluation {
            if (!List.of("PASS", "FAIL", "ABORTED").contains(outcome)) {
                throw new IllegalArgumentException("unsupported G6 outcome");
            }
            requireText(failureCode, "failureCode");
            criteria = List.copyOf(Objects.requireNonNull(criteria, "criteria"));
            if (criteria.isEmpty()) {
                throw new IllegalArgumentException("G6 evaluation needs criteria");
            }
            if (passed && (!"PASS".equals(outcome) || !"NONE".equals(failureCode))) {
                throw new IllegalArgumentException("passing G6 evaluation has invalid outcome");
            }
        }
    }

    /** Evaluates the non-formal Quick readiness contract. */
    public static Evaluation evaluateQuick(
            final GaSoakMatrix matrix,
            final GaSoakObservation observation) {
        Objects.requireNonNull(matrix, "matrix");
        Objects.requireNonNull(observation, "observation");
        if (!matrix.isQuick()) {
            throw new IllegalArgumentException("G6 Quick evaluation requires QUICK matrix");
        }
        final List<Criterion> criteria = new ArrayList<>();
        addCommon(criteria, observation);
        criteria.add(exact("quick.configuration", matrix.isApprovedQuick(), "true"));
        criteria.add(exact("quick.stage", observation.stage() == GaSoakMatrix.Stage.QUICK, "true"));
        criteria.add(exact("quick.duration", observation.durationSatisfied(matrix), "true"));
        criteria.add(new Criterion("quick.offeredRate",
                Integer.toString(matrix.offeredRatePerSecond()), "EQ",
                Integer.toString(GaSoakMatrix.APPROVED_OFFERED_RATE_PER_SECOND),
                matrix.offeredRatePerSecond() == GaSoakMatrix.APPROVED_OFFERED_RATE_PER_SECOND));
        final long nominalOffers = nominalOfferOpportunities(matrix);
        criteria.add(new Criterion("quick.nominalOfferOpportunities",
                Long.toString(observation.nominalOfferOpportunities()), "EQ",
                Long.toString(nominalOffers),
                observation.nominalOfferOpportunities() == nominalOffers));
        criteria.add(new Criterion("quick.actualOfferedCommands",
                Long.toString(observation.actualOfferedCommands()), "EQ",
                Long.toString(nominalOffers),
                observation.actualOfferedCommands() == nominalOffers));
        criteria.add(new Criterion("quick.missedOfferOpportunities",
                Long.toString(observation.missedOfferOpportunities()), "ZERO", "0",
                observation.missedOfferOpportunities() == 0L));
        criteria.add(exact("quick.offeredSchedule",
                observation.offeredScheduleSatisfied(matrix), "true"));
        criteria.add(greaterOrEqual("quick.acceptedFloor", observation.acceptedCommands(),
                matrix.acceptedFloor()));
        criteria.add(exact("quick.gracefulShutdown", observation.gracefulShutdown(), "true"));
        final boolean passed = criteria.stream().allMatch(Criterion::passed);
        return result(passed, false, criteria);
    }

    /** Evaluates one future formal Stage A or Stage B observation. */
    public static Evaluation evaluate(
            final GaSoakMatrix matrix,
            final GaSoakObservation observation) {
        Objects.requireNonNull(matrix, "matrix");
        Objects.requireNonNull(observation, "observation");
        if (!matrix.isStageA() && !matrix.isStageB()) {
            throw new IllegalArgumentException("formal G6 evaluation requires Stage A or B");
        }
        final List<Criterion> criteria = new ArrayList<>();
        addCommon(criteria, observation);
        criteria.add(exact("configuration.approved", matrix.isApprovedFormal(), "true"));
        criteria.add(exact("stage.identity", observation.stage() == matrix.stage(), "true"));
        criteria.add(exact("duration", observation.durationSatisfied(matrix), "true"));
        criteria.add(greaterOrEqual("accepted.floor", observation.acceptedCommands(),
                matrix.acceptedFloor()));
        criteria.add(exact("responses.complete", observation.completeResponses(), "true"));
        criteria.add(exact("correctness", observation.correctnessPassed(), "true"));
        criteria.add(exact("replay", observation.replayPassed(), "true"));
        criteria.add(exact("transcript", observation.transcriptPassed(), "true"));
        criteria.add(exact("publicProbe", observation.probePassed(), "true"));
        criteria.add(exact("shutdown.graceful", observation.gracefulShutdown(), "true"));
        criteria.add(exact("evidence.complete", observation.terminalEvidenceComplete(), "true"));
        criteria.add(exact("latency.windows.complete", observation.completeLatencyWindows(), "true"));
        criteria.add(exact("latency.windows.owned", observation.latencyWindowsOwnedByAcceptedOrdinals(),
                "true"));
        if (observation.completeLatencyWindows()
                && observation.latencyWindowsOwnedByAcceptedOrdinals()) {
            final long first = observation.firstLatencyWindow().p99Nanos();
            final long last = observation.finalLatencyWindow().p99Nanos();
            criteria.add(p99DriftCriterion(first, last));
        } else {
            criteria.add(exact("latency.p99Drift", false, "true"));
        }
        final boolean passed = criteria.stream().allMatch(Criterion::passed);
        return result(passed, passed, criteria);
    }

    /** Alias retaining an explicit name for callers that evaluate formal G6. */
    public static Evaluation evaluateFormal(
            final GaSoakMatrix matrix,
            final GaSoakObservation observation) {
        return evaluate(matrix, observation);
    }

    /** Evaluates an explicit duration/count conjunction without executing a run. */
    public static boolean durationAndCountPasses(
            final long elapsedNanos,
            final long requiredDurationNanos,
            final long acceptedCommands,
            final long acceptedFloor) {
        if (elapsedNanos < 0 || requiredDurationNanos < 0
                || acceptedCommands < 0 || acceptedFloor < 0) {
            throw new IllegalArgumentException("duration/count values must be non-negative");
        }
        return elapsedNanos >= requiredDurationNanos && acceptedCommands >= acceptedFloor;
    }

    private static long nominalOfferOpportunities(final GaSoakMatrix matrix) {
        try {
            return Math.multiplyExact(matrix.duration().getSeconds(),
                    (long) matrix.offeredRatePerSecond());
        } catch (final ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    /** Returns the exact integer-safe frozen P99 drift predicate. */
    public static boolean p99DriftPasses(final long firstP99Nanos, final long finalP99Nanos) {
        if (firstP99Nanos < 0 || finalP99Nanos < 0) {
            throw new IllegalArgumentException("P99 values must be non-negative");
        }
        if (firstP99Nanos == 0) {
            return finalP99Nanos == 0;
        }
        return BigInteger.valueOf(finalP99Nanos).multiply(BigInteger.valueOf(100L))
                .compareTo(BigInteger.valueOf(firstP99Nanos).multiply(BigInteger.valueOf(120L))) <= 0;
    }

    /** Evaluates one pair of first/final P99 windows. */
    public static Criterion p99DriftCriterion(
            final long firstP99Nanos,
            final long finalP99Nanos) {
        final boolean passed = p99DriftPasses(firstP99Nanos, finalP99Nanos);
        return new Criterion("latency.p99Drift", firstP99Nanos + "->" + finalP99Nanos,
                "LE", "20", passed);
    }

    private static void addCommon(
            final List<Criterion> criteria,
            final GaSoakObservation observation) {
        criteria.add(exact("publicPath.complete", observation.publicPathCompleted(), "true"));
        criteria.add(zero("errors", observation.errors()));
        criteria.add(zero("timeouts", observation.timeouts()));
        criteria.add(zero("mismatches", observation.mismatches()));
        criteria.add(exact("configuration.bound", observation.configurationBound(), "true"));
        criteria.add(exact("candidate.bound", observation.candidateBound(), "true"));
        criteria.add(exact("controller.bound", observation.controllerBound(), "true"));
    }

    private static Evaluation result(
            final boolean passed,
            final boolean formalEligible,
            final List<Criterion> criteria) {
        return new Evaluation(passed, formalEligible, passed ? "PASS" : "FAIL",
                passed ? "NONE" : failureCode(criteria), criteria);
    }

    private static String failureCode(final List<Criterion> criteria) {
        return criteria.stream().anyMatch(item -> !item.passed()
                && (item.id().endsWith(".bound") || item.id().endsWith(".stage")
                || item.id().equals("stage.identity")))
                ? "B0" : qualificationContractFailure(criteria) || configurationFailure(criteria)
                ? "B2" : "B1";
    }

    private static boolean qualificationContractFailure(final List<Criterion> criteria) {
        final boolean complete = criteria.stream()
                .filter(item -> item.id().equals("latency.windows.complete"))
                .allMatch(Criterion::passed);
        return complete && criteria.stream().anyMatch(item -> !item.passed()
                && item.id().equals("latency.windows.owned"));
    }

    private static boolean configurationFailure(final List<Criterion> criteria) {
        return criteria.stream().anyMatch(item -> !item.passed()
                && (item.id().endsWith(".configuration")
                || item.id().equals("configuration.approved")
                || item.id().equals("quick.offeredRate")));
    }

    private static Criterion exact(final String id, final boolean actual, final String required) {
        return new Criterion(id, Boolean.toString(actual), "EQ", required,
                actual == Boolean.parseBoolean(required));
    }

    private static Criterion zero(final String id, final long actual) {
        return new Criterion(id, Long.toString(actual), "ZERO", "0", actual == 0L);
    }

    private static Criterion greaterOrEqual(final String id, final long actual, final long required) {
        return new Criterion(id, Long.toString(actual), "GE", Long.toString(required),
                actual >= required);
    }

    private static void requireText(final String value, final String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
