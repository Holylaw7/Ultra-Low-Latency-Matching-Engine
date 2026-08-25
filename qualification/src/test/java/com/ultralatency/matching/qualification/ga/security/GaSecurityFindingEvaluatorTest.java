package com.ultralatency.matching.qualification.ga.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Tests fail-closed G11 finding classification. */
class GaSecurityFindingEvaluatorTest {

    @Test
    void runtimeHighAndCriticalFindingsBlockButTestFindingsDoNot() {
        final var testOnly = GaSecurityFindingEvaluator.evaluateVulnerabilities(
                GaSecurityFindingEvaluator.ScanState.COMPLETE,
                List.of(new GaSecurityFindingEvaluator.VulnerabilityFinding(
                        "test-tool", "TEST", "CRITICAL", 9.9)));
        assertTrue(testOnly.passed());
        final var runtime = GaSecurityFindingEvaluator.evaluateVulnerabilities(
                GaSecurityFindingEvaluator.ScanState.COMPLETE,
                List.of(new GaSecurityFindingEvaluator.VulnerabilityFinding(
                        "runtime-lib", "RUNTIME", "MEDIUM", 7.0)));
        assertFalse(runtime.passed());
        assertEquals("B1", runtime.blocker());
    }

    @Test
    void licensesSecretsAndScannerOutageAreFailClosed() {
        final var license = GaSecurityFindingEvaluator.evaluateLicenses(
                GaSecurityFindingEvaluator.ScanState.COMPLETE,
                List.of(new GaSecurityFindingEvaluator.LicenseFinding(
                        "runtime-lib", "RUNTIME", "GPL-3.0-only")),
                Set.of("Apache-2.0", "MIT"));
        assertFalse(license.passed());
        final var secret = GaSecurityFindingEvaluator.evaluateSecrets(
                GaSecurityFindingEvaluator.ScanState.COMPLETE,
                List.of(new GaSecurityFindingEvaluator.SecretFinding(
                        "fingerprint", "history.txt", true, false)));
        assertFalse(secret.passed());
        assertEquals("B0", secret.blocker());
        final var aborted = GaSecurityFindingEvaluator.evaluateScan(
                GaSecurityFindingEvaluator.ScanState.ABORTED);
        assertEquals("ABORTED", aborted.outcome());
        assertFalse(aborted.passed());
    }

    @Test
    void approvedFalsePositiveIsNotASecretPassByOmission() {
        final var result = GaSecurityFindingEvaluator.evaluateSecrets(
                GaSecurityFindingEvaluator.ScanState.COMPLETE,
                List.of(new GaSecurityFindingEvaluator.SecretFinding(
                        "fingerprint", "history.txt", false, true)));
        assertTrue(result.passed());
        final var unresolved = GaSecurityFindingEvaluator.evaluateSecrets(
                GaSecurityFindingEvaluator.ScanState.COMPLETE,
                List.of(new GaSecurityFindingEvaluator.SecretFinding(
                        "fingerprint", "history.txt", false, false)));
        assertFalse(unresolved.passed());
    }
}
