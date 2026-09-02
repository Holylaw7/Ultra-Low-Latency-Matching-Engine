package com.ultralatency.matching.qualification.ga.observability;

import com.ultralatency.matching.qualification.ga.soak.GaSoakMatrix;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Fail-closed G8 observability/resource evaluator. */
public final class GaObservabilityEvaluator {

    private GaObservabilityEvaluator() {
    }

    /** One explicit G8 predicate. */
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

    /** Immutable G8 outcome, including the explicit PASS/FAIL/ABORTED class. */
    public record Evaluation(
            boolean passed,
            boolean formalEligible,
            String outcome,
            String failureCode,
            List<Criterion> criteria) {
        public Evaluation {
            if (!List.of("PASS", "FAIL", "ABORTED").contains(outcome)
                    || failureCode == null || failureCode.isBlank()) {
                throw new IllegalArgumentException("invalid G8 evaluation");
            }
            criteria = List.copyOf(Objects.requireNonNull(criteria, "criteria"));
            if (criteria.isEmpty()) {
                throw new IllegalArgumentException("G8 evaluation needs criteria");
            }
        }
    }

    /** Evaluates one non-formal Quick readiness observation. */
    public static Evaluation evaluateQuick(
            final GaSoakMatrix matrix,
            final GaObservabilityObservation observation) {
        Objects.requireNonNull(matrix, "matrix");
        Objects.requireNonNull(observation, "observation");
        if (!matrix.isQuick()) {
            throw new IllegalArgumentException("G8 Quick evaluation requires QUICK matrix");
        }
        final List<Criterion> criteria = commonCriteria(observation);
        criteria.add(exact("quick.configuration", matrix.isApprovedQuick(), "true"));
        criteria.add(exact("quick.stage", observation.stage() == GaSoakMatrix.Stage.QUICK, "true"));
        criteria.add(exact("quick.management.complete", observation.managementComplete(), "true"));
        criteria.add(exact("quick.management.counters.monotonic",
                observation.managementCountersNonRegressing(), "true"));
        criteria.add(exact("quick.management.stateTransitions",
                observation.managementStateTransitionsValid(), "true"));
        criteria.add(exact("quick.jfr.readable", observation.jfrEvidence().readable(), "true"));
        criteria.add(exact("quick.gc.parser.ready", observation.gcEvidence().parsed(), "true"));
        criteria.add(exact("quick.client.evidence.complete", observation.clientEvidenceComplete(), "true"));
        criteria.add(exact("quick.terminal.evidence.complete",
                observation.terminalEvidenceComplete(), "true"));
        criteria.add(exact("quick.shutdown.transient.clean",
                observation.transientFilesCleanAfterShutdown(), "true"));
        criteria.add(exact("quick.no.undeclared.transient",
                !observation.undeclaredTransientFiles(), "true"));
        criteria.add(exact("quick.exit", observation.exitCode() == 0, "true"));
        final boolean passed = criteria.stream().allMatch(Criterion::passed);
        final boolean environmentAbort = observation.externallyInterrupted()
                || "B3".equals(observation.jfrEvidence().failureCode())
                || "B3".equals(observation.gcEvidence().failureCode());
        final String outcome = passed ? "PASS" : environmentAbort ? "ABORTED" : "FAIL";
        return new Evaluation(passed, false, outcome,
                failureCode(outcome, environmentAbort, criteria, observation), criteria);
    }

    /** Evaluates the formal G8 resource and observability contract. */
    public static Evaluation evaluateFormal(
            final GaSoakMatrix matrix,
            final GaObservabilityObservation observation) {
        Objects.requireNonNull(matrix, "matrix");
        Objects.requireNonNull(observation, "observation");
        if (!matrix.isStageA() && !matrix.isStageB()) {
            throw new IllegalArgumentException("formal G8 evaluation requires Stage A or B");
        }
        final List<Criterion> criteria = commonCriteria(observation);
        criteria.add(exact("configuration.approved", matrix.isApprovedFormal(), "true"));
        criteria.add(exact("stage.identity", observation.stage() == matrix.stage(), "true"));
        criteria.add(exact("management.complete", observation.managementComplete(), "true"));
        criteria.add(exact("management.counters.monotonic",
                observation.managementCountersNonRegressing(), "true"));
        criteria.add(exact("management.stateTransitions",
                observation.managementStateTransitionsValid(), "true"));
        criteria.add(exact("jfr.complete", observation.jfrEvidence().complete(), "true"));
        criteria.add(exact("gc.evidence.applicable", observation.gcEvidence().applicable(), "true"));
        criteria.add(exact("client.evidence.complete", observation.clientEvidenceComplete(), "true"));
        criteria.add(exact("terminal.evidence.complete", observation.terminalEvidenceComplete(), "true"));
        criteria.add(exact("transient.files.clean", observation.transientFilesCleanAfterShutdown(), "true"));
        criteria.add(exact("no.undeclared.transient", !observation.undeclaredTransientFiles(), "true"));
        criteria.add(exact("exit.zero", observation.exitCode() == 0, "true"));
        criteria.add(resourceCriterion(observation, GaResourceGuards.Metric.THREADS));
        criteria.add(resourceCriterion(observation, GaResourceGuards.Metric.TRANSIENT_FILE_COUNT));
        criteria.add(resourceCriterion(observation, GaResourceGuards.Metric.TRANSIENT_FILE_BYTES));
        final GaNaturalGcGuard.Evaluation gc = GaNaturalGcGuard.evaluate(
                observation.gcEvidence().samples(), observation.physicalExecutionId(),
                observation.stage());
        criteria.add(exact("naturalGc.guard", gc.passed(), "true"));
        final String outcome;
        final String failureCode;
        if (observation.externallyInterrupted()
                || "B3".equals(observation.jfrEvidence().failureCode())
                || "B3".equals(observation.gcEvidence().failureCode())) {
            outcome = "ABORTED";
            failureCode = "B3";
        } else if (gc.outcome().equals("ABORTED")
                || criteria.stream().anyMatch(item -> item.id().startsWith("resource.")
                && !item.passed() && item.actual().equals("ABORTED"))) {
            outcome = "ABORTED";
            failureCode = "B3";
        } else {
            outcome = criteria.stream().allMatch(Criterion::passed) ? "PASS" : "FAIL";
            failureCode = failureCode(outcome, false, criteria, observation);
        }
        return new Evaluation("PASS".equals(outcome), "PASS".equals(outcome), outcome,
                failureCode, criteria);
    }

    /** Alias for formal evaluation. */
    public static Evaluation evaluate(
            final GaSoakMatrix matrix,
            final GaObservabilityObservation observation) {
        return matrix.isQuick() ? evaluateQuick(matrix, observation)
                : evaluateFormal(matrix, observation);
    }

    /** Evaluates one hard resource pair, including its exact zero-baseline semantics. */
    public static Criterion resourcePairCriterion(
            final GaResourceGuards.Metric metric,
            final long firstMedian,
            final long finalMedian) {
        final boolean passed = GaResourceGuards.driftPasses(firstMedian, finalMedian);
        return new Criterion("resource." + metric.name().toLowerCase(java.util.Locale.ROOT),
                Long.toString(finalMedian), "LE", "20", passed);
    }

    private static List<Criterion> commonCriteria(
            final GaObservabilityObservation observation) {
        final List<Criterion> criteria = new ArrayList<>();
        criteria.add(exact("configuration.bound", observation.configurationBound(), "true"));
        criteria.add(exact("candidate.bound", observation.candidateBound(), "true"));
        criteria.add(exact("controller.bound", observation.controllerBound(), "true"));
        return criteria;
    }

    private static String failureCode(
            final String outcome,
            final boolean environmentAbort,
            final List<Criterion> criteria,
            final GaObservabilityObservation observation) {
        if ("PASS".equals(outcome)) {
            return "NONE";
        }
        if (environmentAbort) {
            return "B3";
        }
        if (identityFailure(criteria)) {
            return "B0";
        }
        if (configurationFailure(criteria)) {
            return "B2";
        }
        if ("B2".equals(observation.jfrEvidence().failureCode())
                || "B2".equals(observation.gcEvidence().failureCode())) {
            return "B2";
        }
        if (resourceIdentityFailure(observation) || naturalGcIdentityFailure(observation)) {
            return "B0";
        }
        if (!observation.managementComplete()) {
            return "B2";
        }
        return "B1";
    }

    private static boolean identityFailure(final List<Criterion> criteria) {
        return criteria.stream().anyMatch(item -> !item.passed()
                && (item.id().endsWith("configuration.bound")
                || item.id().endsWith("candidate.bound")
                || item.id().endsWith("controller.bound")
                || item.id().endsWith("stage")
                || item.id().equals("stage.identity")));
    }

    private static boolean configurationFailure(final List<Criterion> criteria) {
        return criteria.stream().anyMatch(item -> !item.passed()
                && (item.id().endsWith(".configuration")
                || item.id().equals("configuration.approved")
                || item.id().equals("quick.offeredRate")));
    }

    private static boolean resourceIdentityFailure(
            final GaObservabilityObservation observation) {
        for (GaResourceGuards.Metric metric : GaResourceGuards.Metric.values()) {
            if ("B0".equals(GaResourceGuards.evaluate(metric, observation.resourceSamples(),
                    observation.physicalExecutionId(), observation.stage()).failureCode())) {
                return true;
            }
        }
        return false;
    }

    private static boolean naturalGcIdentityFailure(
            final GaObservabilityObservation observation) {
        return "B0".equals(GaNaturalGcGuard.evaluate(observation.gcEvidence().samples(),
                observation.physicalExecutionId(), observation.stage()).failureCode());
    }

    private static Criterion resourceCriterion(
            final GaObservabilityObservation observation,
            final GaResourceGuards.Metric metric) {
        final GaResourceGuards.Evaluation evaluation = GaResourceGuards.evaluate(
                metric, observation.resourceSamples(), observation.physicalExecutionId(),
                observation.stage());
        return new Criterion("resource." + metric.name().toLowerCase(java.util.Locale.ROOT),
                evaluation.outcome().equals("ABORTED") ? "ABORTED"
                        : Long.toString(evaluation.finalMedian()),
                "LE_PERCENT", "20", evaluation.passed());
    }

    private static Criterion exact(final String id, final boolean actual, final String required) {
        return new Criterion(id, Boolean.toString(actual), "EQ", required,
                actual == Boolean.parseBoolean(required));
    }

    private static void requireText(final String value, final String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
