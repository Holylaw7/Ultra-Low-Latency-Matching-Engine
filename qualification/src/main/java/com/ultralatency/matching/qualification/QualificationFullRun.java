package com.ultralatency.matching.qualification;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/** Immutable outcome and raw-artifact locations for one full qualification campaign. */
public record QualificationFullRun(
        QualificationRun qualificationRun,
        QualificationResourceEvidence resourceEvidence,
        Duration elapsed,
        boolean listenerRebound,
        boolean recoveryLeaseReacquired,
        boolean inventoryStable,
        QualificationStorageInventory storageInventory,
        boolean fullCriteriaPassed,
        Path artifactDirectory,
        Path artifactHashesPath,
        String jfrDigestHex,
        String manifestDigestHex,
        String resourceEvidenceDigestHex,
        String artifactHashesDigestHex) {

    /** Validates one campaign outcome. */
    public QualificationFullRun {
        Objects.requireNonNull(qualificationRun, "qualificationRun");
        Objects.requireNonNull(resourceEvidence, "resourceEvidence");
        Objects.requireNonNull(elapsed, "elapsed");
        if (elapsed.isNegative()) {
            throw new IllegalArgumentException("elapsed must not be negative");
        }
        Objects.requireNonNull(artifactDirectory, "artifactDirectory");
        Objects.requireNonNull(storageInventory, "storageInventory");
        Objects.requireNonNull(artifactHashesPath, "artifactHashesPath");
        requireDigest(jfrDigestHex, "jfrDigestHex");
        requireDigest(manifestDigestHex, "manifestDigestHex");
        requireDigest(resourceEvidenceDigestHex, "resourceEvidenceDigestHex");
        requireDigest(artifactHashesDigestHex, "artifactHashesDigestHex");
    }

    private static void requireDigest(final String value, final String name) {
        Objects.requireNonNull(value, name);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must be lowercase SHA-256");
        }
    }
}
