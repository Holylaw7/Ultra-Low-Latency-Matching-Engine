package com.ultralatency.matching.qualification.ga.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Deterministic, fail-closed classification for Phase 11 G11 findings. */
public final class GaSecurityFindingEvaluator {

    /** Scanner completion state. */
    public enum ScanState {
        COMPLETE,
        ABORTED
    }

    /** A vulnerability finding with explicit runtime scope and severity. */
    public record VulnerabilityFinding(
            String dependency, String scope, String severity, Double cvss) {
        public VulnerabilityFinding {
            dependency = required(dependency, "dependency");
            scope = required(scope, "scope").toUpperCase(Locale.ROOT);
            severity = required(severity, "severity").toUpperCase(Locale.ROOT);
            requireScope(scope);
            if (cvss != null && (cvss.isNaN() || cvss.isInfinite() || cvss < 0.0 || cvss > 10.0)) {
                throw new IllegalArgumentException("cvss must be between 0 and 10");
            }
        }
    }

    /** A license finding with explicit runtime/tool scope. */
    public record LicenseFinding(String dependency, String scope, String spdx) {
        public LicenseFinding {
            dependency = required(dependency, "dependency");
            scope = required(scope, "scope").toUpperCase(Locale.ROOT);
            spdx = required(spdx, "spdx");
            requireScope(scope);
        }
    }

    /** A secret finding whose verification/false-positive authority is explicit. */
    public record SecretFinding(
            String fingerprint, String path, boolean verified, boolean approvedFalsePositive) {
        public SecretFinding {
            fingerprint = required(fingerprint, "fingerprint");
            path = required(path, "path");
            if (verified && approvedFalsePositive) {
                throw new IllegalArgumentException("verified secret cannot be approved false positive");
            }
        }
    }

    /** Immutable deterministic decision. */
    public record SecurityDecision(String outcome, String blocker, List<String> reasons) {
        public SecurityDecision {
            outcome = required(outcome, "outcome");
            blocker = required(blocker, "blocker");
            reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
        }

        /** Returns true only for a complete, blocker-free PASS. */
        public boolean passed() {
            return "PASS".equals(outcome) && "NONE".equals(blocker);
        }
    }

    private GaSecurityFindingEvaluator() {
    }

    /** Evaluates scanner availability before interpreting any findings. */
    public static SecurityDecision evaluateScan(final ScanState state) {
        Objects.requireNonNull(state, "state");
        if (state == ScanState.ABORTED) {
            return decision("ABORTED", "B3", List.of("security scanner was unavailable or unidentifiable"));
        }
        return decision("PASS", "NONE", List.of());
    }

    /** Evaluates vulnerabilities; test/tool findings are retained but non-blocking. */
    public static SecurityDecision evaluateVulnerabilities(
            final ScanState state, final Collection<VulnerabilityFinding> findings) {
        final SecurityDecision availability = evaluateScan(state);
        if (!availability.passed()) {
            return availability;
        }
        Objects.requireNonNull(findings, "findings");
        final List<String> blockers = new ArrayList<>();
        for (VulnerabilityFinding finding : findings) {
            if (isRuntime(finding.scope()) && isHighOrCritical(finding)) {
                blockers.add("runtime vulnerability: " + finding.dependency());
            }
        }
        return blockers.isEmpty()
                ? decision("PASS", "NONE", List.of())
                : decision("FAIL", "B1", sorted(blockers));
    }

    /** Evaluates runtime licenses against the approved SPDX set. */
    public static SecurityDecision evaluateLicenses(
            final ScanState state, final Collection<LicenseFinding> findings,
            final Set<String> acceptedRuntimeSpdx) {
        final SecurityDecision availability = evaluateScan(state);
        if (!availability.passed()) {
            return availability;
        }
        Objects.requireNonNull(findings, "findings");
        final Set<String> accepted = Set.copyOf(Objects.requireNonNull(acceptedRuntimeSpdx,
                "acceptedRuntimeSpdx"));
        final List<String> blockers = new ArrayList<>();
        for (LicenseFinding finding : findings) {
            if (isRuntime(finding.scope()) && !accepted.contains(finding.spdx())) {
                blockers.add("runtime license: " + finding.dependency() + " -> " + finding.spdx());
            }
        }
        return blockers.isEmpty()
                ? decision("PASS", "NONE", List.of())
                : decision("FAIL", "B1", sorted(blockers));
    }

    /** Evaluates full-history secret findings; a verified secret is a B0 blocker. */
    public static SecurityDecision evaluateSecrets(
            final ScanState state, final Collection<SecretFinding> findings) {
        final SecurityDecision availability = evaluateScan(state);
        if (!availability.passed()) {
            return availability;
        }
        Objects.requireNonNull(findings, "findings");
        final List<String> blockers = new ArrayList<>();
        for (SecretFinding finding : findings) {
            if (finding.verified()) {
                blockers.add("verified secret: " + finding.fingerprint() + " @ " + finding.path());
            } else if (!finding.approvedFalsePositive()) {
                blockers.add("unresolved secret finding: " + finding.fingerprint() + " @ "
                        + finding.path());
            }
        }
        return blockers.isEmpty()
                ? decision("PASS", "NONE", List.of())
                : decision("FAIL", "B0", sorted(blockers));
    }

    private static boolean isRuntime(final String scope) {
        return "RUNTIME".equals(scope);
    }

    private static void requireScope(final String scope) {
        if (!Set.of("RUNTIME", "TEST", "TOOL").contains(scope)) {
            throw new IllegalArgumentException("unsupported finding scope: " + scope);
        }
    }

    private static boolean isHighOrCritical(final VulnerabilityFinding finding) {
        return finding.cvss() != null && finding.cvss() >= 7.0
                || "HIGH".equals(finding.severity()) || "CRITICAL".equals(finding.severity());
    }

    private static SecurityDecision decision(
            final String outcome, final String blocker, final List<String> reasons) {
        return new SecurityDecision(outcome, blocker, reasons);
    }

    private static List<String> sorted(final Collection<String> reasons) {
        return reasons.stream().sorted(Comparator.naturalOrder()).toList();
    }

    private static String required(final String value, final String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
