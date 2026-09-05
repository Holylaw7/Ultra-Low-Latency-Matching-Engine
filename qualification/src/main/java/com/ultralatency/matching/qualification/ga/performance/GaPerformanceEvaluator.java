package com.ultralatency.matching.qualification.ga.performance;

import com.ultralatency.matching.qualification.QualificationPercentiles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Deterministic G4 threshold and campaign evaluator with no filtering or replacement runs. */
public final class GaPerformanceEvaluator {

    /** Fixed accepted-throughput SLO. */
    public static final double MIN_THROUGHPUT_COMMANDS_PER_SECOND = 500.0;
    /** Fixed response P50 SLO in nanoseconds. */
    public static final long MAX_P50_NANOS = 2_500_000L;
    /** Fixed response P99 SLO in nanoseconds. */
    public static final long MAX_P99_NANOS = 5_000_000L;
    /** Fixed response P99.9 SLO in nanoseconds. */
    public static final long MAX_P999_NANOS = 10_000_000L;
    /** Fixed startup/shutdown P99 SLO in nanoseconds. */
    public static final long MAX_LIFECYCLE_P99_NANOS = 1_250_000_000L;
    /** Maximum paired management regression. */
    public static final double MAX_MANAGEMENT_REGRESSION = 0.10;

    private GaPerformanceEvaluator() {
    }

    /** One explicit measured G4 criterion. */
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

    /** Result of evaluating one run or an all-run campaign. */
    public record Evaluation(
            boolean passed,
            boolean formalEligible,
            String outcome,
            String failureCode,
            List<Criterion> criteria) {
        public Evaluation {
            if (!"PASS".equals(outcome) && !"FAIL".equals(outcome)) {
                throw new IllegalArgumentException("G4 evaluation outcome must be PASS or FAIL");
            }
            if (failureCode == null || failureCode.isBlank()) {
                throw new IllegalArgumentException("failureCode must not be blank");
            }
            criteria = List.copyOf(Objects.requireNonNull(criteria, "criteria"));
            if (criteria.isEmpty()) {
                throw new IllegalArgumentException("G4 evaluation needs criteria");
            }
        }
    }

    /** Evaluates one formal-style run without silently filtering any samples. */
    public static Evaluation evaluateRun(final GaPerformanceObservation observation) {
        Objects.requireNonNull(observation, "observation");
        final QualificationPercentiles.Summary latency = observation.latency();
        final List<Criterion> criteria = new ArrayList<>();
        criteria.add(exact("publicPath.complete", observation.publicPathCompleted()
                && observation.completeResponsePopulation(), "true"));
        criteria.add(exact("measurement.boundary.complete",
                observation.measurement().boundaryComplete(), "true"));
        criteria.add(greaterOrEqual("throughput.commandsPerSecond",
                throughput(observation), MIN_THROUGHPUT_COMMANDS_PER_SECOND));
        criteria.add(lessOrEqual("latency.p50Nanos", latency.p50Nanos(), MAX_P50_NANOS));
        criteria.add(lessOrEqual("latency.p99Nanos", latency.p99Nanos(), MAX_P99_NANOS));
        criteria.add(lessOrEqual("latency.p999Nanos", latency.p999Nanos(), MAX_P999_NANOS));
        criteria.add(zero("errors", observation.errors()));
        criteria.add(zero("timeouts", observation.timeouts()));
        criteria.add(zero("mismatches", observation.mismatches()));
        criteria.add(exact("candidate.ready", observation.candidateReady(), "true"));
        criteria.add(exact("candidate.failureCode", "NONE".equals(
                observation.candidateFailureCode()), "true"));
        criteria.add(zero("candidate.terminalFailures", observation.terminalFailures()));
        criteria.add(exact("process.exit", observation.processExitCode() == 0, "true"));
        criteria.add(exact("evidence.complete", observation.mandatoryEvidenceComplete(), "true"));
        criteria.add(exact("evidence.candidateHealthComplete",
                observation.candidateHealthEvidenceComplete(), "true"));
        criteria.add(exact("configuration.bound", observation.configurationBound(), "true"));
        criteria.add(exact("comparability.bound", observation.comparabilityBound(), "true"));
        criteria.add(exact("candidate.bound", observation.candidateBound(), "true"));
        criteria.add(exact("controller.bound", observation.controllerBound(), "true"));
        final boolean passed = criteria.stream().allMatch(Criterion::passed);
        return new Evaluation(passed, passed, passed ? "PASS" : "FAIL",
                passed ? "NONE" : failureCode(observation, latency), criteria);
    }

    /** Evaluates only readiness properties for a Quick smoke; it cannot claim formal G4. */
    public static Evaluation evaluateQuick(final GaPerformanceObservation observation) {
        Objects.requireNonNull(observation, "observation");
        final List<Criterion> criteria = new ArrayList<>();
        criteria.add(exact("quick.publicPath.complete", observation.publicPathCompleted()
                && observation.completeResponsePopulation(), "true"));
        criteria.add(exact("quick.measurement.boundary.complete",
                observation.measurement().boundaryComplete(), "true"));
        criteria.add(exact("quick.configuration.bound", observation.configurationBound(), "true"));
        criteria.add(exact("quick.comparability.observed", observation.comparabilityBound(), "true"));
        criteria.add(exact("quick.candidate.bound", observation.candidateBound(), "true"));
        criteria.add(exact("quick.controller.bound", observation.controllerBound(), "true"));
        criteria.add(zero("quick.errors", observation.errors()));
        criteria.add(zero("quick.timeouts", observation.timeouts()));
        criteria.add(zero("quick.mismatches", observation.mismatches()));
        final boolean passed = criteria.stream().allMatch(Criterion::passed);
        return new Evaluation(passed, false, passed ? "PASS" : "FAIL",
                passed ? "NONE" : failureCode(observation, observation.latency()), criteria);
    }

    /** Evaluates exactly the three formal runs as an all-run conjunction. */
    public static Evaluation evaluateCampaign(
            final GaPerformanceMatrix matrix,
            final List<GaPerformanceObservation> observations) {
        Objects.requireNonNull(matrix, "matrix");
        Objects.requireNonNull(observations, "observations");
        if (observations.size() != matrix.runCount()) {
            return failedCampaign("campaign.runCount", observations.size(), matrix.runCount());
        }
        final List<Criterion> criteria = new ArrayList<>();
        for (int index = 0; index < observations.size(); index++) {
            final Evaluation run = evaluateRun(observations.get(index));
            criteria.add(exact("run." + (index + 1) + ".result", run.passed(), "true"));
        }
        final long[] startup = observations.stream().flatMapToLong(item ->
                Arrays.stream(item.startupLatencyNanos())).toArray();
        final long[] shutdown = observations.stream().flatMapToLong(item ->
                Arrays.stream(item.shutdownLatencyNanos())).toArray();
        final QualificationPercentiles.Summary startupSummary =
                QualificationPercentiles.summarize(startup);
        final QualificationPercentiles.Summary shutdownSummary =
                QualificationPercentiles.summarize(shutdown);
        criteria.add(greaterOrEqual("lifecycle.sampleCount", startup.length,
                matrix.lifecycleSamples()));
        criteria.add(greaterOrEqual("lifecycle.shutdownSampleCount", shutdown.length,
                matrix.lifecycleSamples()));
        criteria.add(lessOrEqual("lifecycle.startupP99Nanos", startupSummary.p99Nanos(),
                MAX_LIFECYCLE_P99_NANOS));
        criteria.add(lessOrEqual("lifecycle.shutdownP99Nanos", shutdownSummary.p99Nanos(),
                MAX_LIFECYCLE_P99_NANOS));
        criteria.add(managementCriterion(observations));
        final boolean passed = criteria.stream().allMatch(Criterion::passed);
        String failureCode = "NONE";
        if (!passed) {
            failureCode = observations.stream().map(GaPerformanceEvaluator::evaluateRun)
                    .map(Evaluation::failureCode).filter(code -> !"NONE".equals(code))
                    .findFirst().orElse("B1");
            if ("NONE".equals(failureCode)) {
                failureCode = "B1";
            }
        }
        return new Evaluation(passed, passed, passed ? "PASS" : "FAIL",
                failureCode, criteria);
    }

    /** Returns the measured accepted throughput without rounding away failures. */
    public static double throughput(final GaPerformanceObservation observation) {
        return observation.elapsedNanos() == 0 ? 0.0
                : observation.acceptedCommands() * 1_000_000_000.0
                / observation.elapsedNanos();
    }

    private static Criterion managementCriterion(final List<GaPerformanceObservation> observations) {
        final boolean passed = observations.stream().allMatch(item ->
                item.statusThroughputCommandsPerSecond()
                        >= item.idleThroughputCommandsPerSecond() * (1.0 - MAX_MANAGEMENT_REGRESSION)
                && item.statusP99Nanos() <= item.idleStatusP99Nanos()
                        * (1.0 + MAX_MANAGEMENT_REGRESSION));
        return new Criterion(
                "management.regression",
                Boolean.toString(passed),
                "EQ",
                "true",
                passed);
    }

    private static Criterion exact(final String id, final boolean actual, final String required) {
        return new Criterion(id, Boolean.toString(actual), "EQ", required, actual == Boolean.parseBoolean(required));
    }

    private static Criterion zero(final String id, final long actual) {
        return new Criterion(id, Long.toString(actual), "ZERO", "0", actual == 0);
    }

    private static Criterion lessOrEqual(final String id, final long actual, final long required) {
        return new Criterion(id, Long.toString(actual), "LE", Long.toString(required), actual <= required);
    }

    private static Criterion greaterOrEqual(final String id, final double actual, final double required) {
        return new Criterion(id, Double.toString(actual), "GE", Double.toString(required), actual >= required);
    }

    private static Criterion greaterOrEqual(final String id, final long actual, final long required) {
        return new Criterion(id, Long.toString(actual), "GE", Long.toString(required), actual >= required);
    }

    static String failureCode(
            final GaPerformanceObservation observation,
            final QualificationPercentiles.Summary latency) {
        if (!observation.candidateBound() || !observation.controllerBound()) {
            return "B0";
        }
        if (!observation.configurationBound() || !observation.comparabilityBound()) {
            return "B3";
        }
        if (!observation.mandatoryEvidenceComplete()) {
            return "B0";
        }
        if (!observation.candidateHealthEvidenceComplete()) {
            return "B2";
        }
        if (observation.errors() > 0 || observation.timeouts() > 0
                || observation.mismatches() > 0) {
            return "B1";
        }
        if (!observation.candidateReady() || !"NONE".equals(observation.candidateFailureCode())
                || observation.terminalFailures() != 0L || observation.processExitCode() != 0) {
            return "B1";
        }
        if (!observation.publicPathCompleted()
                || !observation.measurement().complete()
                || !observation.measurement().boundaryComplete()) {
            return "B2";
        }
        if (throughput(observation) < MIN_THROUGHPUT_COMMANDS_PER_SECOND
                || latency.p50Nanos() > MAX_P50_NANOS
                || latency.p99Nanos() > MAX_P99_NANOS
                || latency.p999Nanos() > MAX_P999_NANOS) {
            return "B1";
        }
        return "B2";
    }

    private static Evaluation failedCampaign(final String id, final long actual, final long required) {
        return new Evaluation(false, false, "FAIL", "B2",
                List.of(new Criterion(id, Long.toString(actual), "EQ", Long.toString(required), false)));
    }
}
