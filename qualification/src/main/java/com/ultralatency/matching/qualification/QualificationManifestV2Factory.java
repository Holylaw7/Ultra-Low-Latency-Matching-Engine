package com.ultralatency.matching.qualification;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Builds a v2 manifest from one completed qualification run without retaining run history. */
public final class QualificationManifestV2Factory {

    /** Immutable relative artifact reference recorded in a v2 manifest. */
    public record ArtifactReference(String relativePath, long size, String sha256) {
        public ArtifactReference {
            Objects.requireNonNull(relativePath, "relativePath");
            QualificationV2CanonicalCodec.rejectPathValue(relativePath);
            if (relativePath.isBlank() || relativePath.contains("\\")) {
                throw new IllegalArgumentException("artifact path must be relative POSIX text");
            }
            if (size < 0 || sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("invalid artifact reference");
            }
        }
    }

    private QualificationManifestV2Factory() {
    }

    /** Creates the immutable v2 manifest field set for a terminal run. */
    public static QualificationManifestV2 create(
            final QualificationFullConfiguration configuration,
            final QualificationManifest legacyManifest,
            final QualificationResult result,
            final QualificationResourceEvidence resources,
            final Duration elapsed,
            final Instant completedAt,
            final boolean listenerRebound,
            final boolean recoveryLeaseReacquired,
            final boolean inventoryStable,
            final QualificationStorageInventory inventory,
            final Map<String, String> runtimeProvenance,
            final Map<String, ArtifactReference> artifacts,
            final String status) {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(legacyManifest, "legacyManifest");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(elapsed, "elapsed");
        Objects.requireNonNull(completedAt, "completedAt");
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(runtimeProvenance, "runtimeProvenance");
        Objects.requireNonNull(artifacts, "artifacts");
        if (elapsed.isNegative()) {
            throw new IllegalArgumentException("elapsed must not be negative");
        }
        if (!status.equals("PASS") && !status.equals("FAIL") && !status.equals("ABORTED")) {
            throw new IllegalArgumentException("status must be PASS/FAIL/ABORTED");
        }
        final String gitSha = legacyManifest.gitSha();
        final String baselineTag = legacyManifest.baselineTag();
        final QualificationIdentity.Pair identities = QualificationIdentity.forRun(
                configuration, runtimeProvenance, gitSha, baselineTag);
        final Map<String, String> values = new LinkedHashMap<>();
        values.put("schemaVersion", QualificationV2CanonicalCodec.MANIFEST_SCHEMA);
        values.put("canonicalizationVersion", QualificationV2CanonicalCodec.CANONICALIZATION_VERSION);
        values.put("source.runId", legacyManifest.runId());
        values.put("source.gitSha", gitSha);
        values.put("source.baselineTag", baselineTag);
        values.put("source.startedAtUtc", legacyManifest.createdAt().toString());
        values.put("source.completedAtUtc", completedAt.toString());
        values.put("identity.configurationIdentitySha256",
                identities.configurationIdentitySha256());
        values.put("identity.comparabilityIdentitySha256",
                identities.comparabilityIdentitySha256());
        values.put("configuration.lane", configuration.lane().name());
        values.put("configuration.profile", configuration.profile().name());
        values.put("configuration.workloadVersion", legacyManifest.workload().version());
        values.put("configuration.seed", Long.toString(configuration.seed()));
        values.put("configuration.commandCount", Integer.toString(configuration.commandCount()));
        values.put("configuration.minimumDuration", configuration.minimumDuration().toString());
        values.put("configuration.commandTimeout", configuration.commandTimeout().toString());
        values.put("configuration.sampleInterval", configuration.sampleInterval().toString());
        values.put("configuration.minimumPostGcSamples",
                Integer.toString(configuration.minimumPostGcSamples()));
        runtimeProvenance.forEach((key, value) -> values.put(key, value));
        values.put("result.status", status);
        values.put("result.elapsedMillis", Long.toString(elapsed.toMillis()));
        values.put("result.acceptedCommands", Long.toString(result.acceptedCommands()));
        values.put("result.responseCount", Long.toString(result.responseCount()));
        values.put("result.tradeCount", Long.toString(result.tradeCount()));
        values.put("result.checkpointDigestHex", result.checkpointDigestHex());
        values.put("result.transcriptDigestHex", result.transcriptDigestHex());
        values.put("result.publicProbeDigestHex", result.publicProbeDigestHex());
        values.put("result.resultDigestHex", result.digestHex());
        values.put("result.heapGuardAssessed", Boolean.toString(resources.heapGuardAssessed()));
        values.put("result.heapGuardPassed", Boolean.toString(resources.heapGuardPassed()));
        values.put("result.naturalPostGcSampleCount",
                Integer.toString(resources.naturalPostGcHeapBytes().size()));
        values.put("result.threadBaselineRestored",
                Boolean.toString(resources.threadBaselineRestored()));
        values.put("result.listenerRebound", Boolean.toString(listenerRebound));
        values.put("result.recoveryLeaseReacquired", Boolean.toString(recoveryLeaseReacquired));
        values.put("result.inventoryStable", Boolean.toString(inventoryStable));
        values.put("result.walCommandDigestHex",
                result.measurements().getOrDefault("walCommandDigestHex", QualificationCanonicalizer.EMPTY_DIGEST));
        values.put("result.checkpointActiveOrderCount",
                result.measurements().getOrDefault("memoryStateActiveOrderCount", "0"));
        values.put("result.walFileCount", Long.toString(inventory.walFileCount()));
        values.put("result.walBytes", Long.toString(inventory.walBytes()));
        values.put("result.snapshotFileCount", Long.toString(inventory.snapshotFileCount()));
        values.put("result.snapshotBytes", Long.toString(inventory.snapshotBytes()));
        values.put("result.temporaryFileCount", Long.toString(inventory.temporaryFileCount()));
        values.put("claims.qualificationOnly", "true");
        values.put("claims.hardwarePowerLossGuarantee", "NOT_CLAIMED");
        values.put("claims.productionRtoOrAvailability", "NOT_CLAIMED");
        values.put("claims.memoryLeakFreedom", "NOT_CLAIMED");
        artifacts.forEach((name, artifact) -> {
            final String prefix = "artifact." + name;
            values.put(prefix + ".relativePath", artifact.relativePath());
            values.put(prefix + ".size", Long.toString(artifact.size()));
            values.put(prefix + ".sha256", artifact.sha256());
        });
        return QualificationManifestV2.of(values);
    }
}
