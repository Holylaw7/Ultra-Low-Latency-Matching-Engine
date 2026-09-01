package com.ultralatency.matching.qualification.ga.capacity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Evaluates G5 support-envelope observations without claiming maximum capacity. */
public final class GaCapacityEvaluator {

    private GaCapacityEvaluator() {
    }

    /** One explicit measured G5 criterion. */
    public record Criterion(String id, String actual, String operator, String required,
            boolean passed) {
        public Criterion {
            if (id == null || id.isBlank() || actual == null || actual.isBlank()
                    || operator == null || operator.isBlank()
                    || required == null || required.isBlank()) {
                throw new IllegalArgumentException("criterion fields must not be blank");
            }
        }
    }

    /** Result of one scale or all-scale support-envelope evaluation. */
    public record Evaluation(boolean passed, boolean formalEligible, String outcome,
            String failureCode, List<Criterion> criteria, String claim) {
        public Evaluation {
            if (!"PASS".equals(outcome) && !"FAIL".equals(outcome)) {
                throw new IllegalArgumentException("G5 evaluation outcome must be PASS or FAIL");
            }
            if (failureCode == null || failureCode.isBlank() || claim == null || claim.isBlank()) {
                throw new IllegalArgumentException("evaluation metadata must not be blank");
            }
            criteria = List.copyOf(Objects.requireNonNull(criteria, "criteria"));
            if (criteria.isEmpty()) {
                throw new IllegalArgumentException("G5 evaluation needs criteria");
            }
        }
    }

    /** Evaluates one scale using the frozen recovery and integrity semantics. */
    public static Evaluation evaluateScale(
            final GaCapacityObservation observation,
            final int minimumRecoveredActiveOrders) {
        Objects.requireNonNull(observation, "observation");
        if (minimumRecoveredActiveOrders < 0) {
            throw new IllegalArgumentException("minimumRecoveredActiveOrders must be non-negative");
        }
        final List<Criterion> criteria = new ArrayList<>();
        criteria.add(exact("publicPath.complete", observation.publicPathCompleted()
                && observation.completeResponsePopulation(), "true"));
        criteria.add(greaterOrEqual("acceptedCommands", observation.acceptedCommands(),
                observation.commandCount()));
        criteria.add(greaterOrEqual("recoveredActiveOrders", observation.recoveredActiveOrders(),
                minimumRecoveredActiveOrders));
        criteria.add(exact("recovery.converged", observation.exactRecoveryConvergence(), "true"));
        criteria.add(exact("resource.outOfMemory", !observation.outOfMemory(), "true"));
        criteria.add(exact("recovery.sequenceGap", !observation.sequenceGap(), "true"));
        criteria.add(exact("recovery.invalidTrade", !observation.invalidTrade(), "true"));
        criteria.add(exact("recovery.timeout", !observation.timeout(), "true"));
        criteria.add(exact("configuration.bound", observation.configurationBound(), "true"));
        criteria.add(exact("candidate.bound", observation.candidateBound(), "true"));
        criteria.add(exact("controller.bound", observation.controllerBound(), "true"));
        final boolean passed = criteria.stream().allMatch(Criterion::passed);
        return new Evaluation(passed, passed, passed ? "PASS" : "FAIL",
                passed ? "NONE" : "B2", criteria, "TESTED_SUPPORT_ENVELOPE");
    }

    /** Evaluates the exact approved scale set; no best-scale filtering is permitted. */
    public static Evaluation evaluateCampaign(
            final GaCapacityMatrix matrix,
            final List<GaCapacityObservation> observations) {
        Objects.requireNonNull(matrix, "matrix");
        Objects.requireNonNull(observations, "observations");
        final List<Criterion> criteria = new ArrayList<>();
        final boolean countMatches = observations.size() == matrix.commandScales().size();
        criteria.add(exact("campaign.scaleCount", countMatches, "true"));
        if (countMatches) {
            for (int index = 0; index < observations.size(); index++) {
                final GaCapacityObservation observation = observations.get(index);
                final int expectedScale = matrix.commandScales().get(index);
                final Evaluation scale = evaluateScale(observation,
                        matrix.minimumRecoveredActiveOrders(expectedScale));
                criteria.add(exact("scale." + (index + 1) + ".identity",
                        observation.commandCount() == expectedScale, "true"));
                criteria.add(exact("scale." + (index + 1) + ".result",
                        scale.passed(), "true"));
            }
        }
        final boolean passed = criteria.stream().allMatch(Criterion::passed);
        return new Evaluation(passed, passed, passed ? "PASS" : "FAIL",
                passed ? "NONE" : "B2", criteria, "TESTED_SUPPORT_ENVELOPE");
    }

    /** Evaluates a Quick scale as readiness evidence, never as formal G5 evidence. */
    public static Evaluation evaluateQuick(final GaCapacityObservation observation) {
        Objects.requireNonNull(observation, "observation");
        final List<Criterion> criteria = new ArrayList<>();
        criteria.add(exact("quick.publicPath.complete", observation.publicPathCompleted()
                && observation.completeResponsePopulation(), "true"));
        criteria.add(exact("quick.configuration.bound", observation.configurationBound(), "true"));
        criteria.add(exact("quick.candidate.bound", observation.candidateBound(), "true"));
        criteria.add(exact("quick.controller.bound", observation.controllerBound(), "true"));
        criteria.add(exact("quick.recovery.converged", observation.exactRecoveryConvergence(), "true"));
        criteria.add(exact("quick.no.resource.failure", !observation.outOfMemory(), "true"));
        criteria.add(exact("quick.no.sequence.gap", !observation.sequenceGap(), "true"));
        criteria.add(exact("quick.no.invalid.trade", !observation.invalidTrade(), "true"));
        criteria.add(exact("quick.no.timeout", !observation.timeout(), "true"));
        final boolean passed = criteria.stream().allMatch(Criterion::passed);
        return new Evaluation(passed, false, passed ? "PASS" : "FAIL",
                passed ? "NONE" : "B2", criteria, "QUICK_READINESS_ONLY");
    }

    private static Criterion exact(final String id, final boolean actual, final String required) {
        return new Criterion(id, Boolean.toString(actual), "EQ", required,
                actual == Boolean.parseBoolean(required));
    }

    private static Criterion greaterOrEqual(final String id, final long actual, final long required) {
        return new Criterion(id, Long.toString(actual), "GE", Long.toString(required), actual >= required);
    }
}
