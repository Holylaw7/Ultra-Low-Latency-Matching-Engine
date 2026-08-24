package com.ultralatency.matching.qualification;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;

/** Evaluates and atomically publishes exactly two assembled-runtime Full manifests. */
public final class ReleaseCandidateAssembledCampaignEvaluator {

    /** Publishes one immutable campaign summary after strict two-run validation. */
    public ReleaseCandidateAssembledCampaignResult evaluate(
            final Path firstManifest,
            final Path secondManifest,
            final Path outputDirectory) throws IOException {
        Objects.requireNonNull(firstManifest, "firstManifest");
        Objects.requireNonNull(secondManifest, "secondManifest");
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        final QualificationManifestV2 first = QualificationManifestV2.read(firstManifest);
        final QualificationManifestV2 second = QualificationManifestV2.read(secondManifest);
        final List<QualificationManifestV2> manifests = List.of(first, second);
        validate(manifests);
        Files.createDirectories(outputDirectory);
        final Path directory = Files.createDirectory(
                outputDirectory.resolve("rc-assembled-campaign-" + java.util.UUID.randomUUID()));
        final Path summary = directory.resolve("qualification-campaign-summary-v1.txt");
        final String text = summaryText(firstManifest, secondManifest, first, second);
        publish(summary, text);
        final Path hashes = directory.resolve("artifact-hashes-v1.txt");
        QualificationManifestV2Store.publishArtifactHashes(
                hashes,
                java.util.Map.of(
                        "run-a-manifest-v2.txt", firstManifest,
                        "run-b-manifest-v2.txt", secondManifest,
                        "qualification-campaign-summary-v1.txt", summary));
        return new ReleaseCandidateAssembledCampaignResult(
                directory, summary, hashes, QualificationArtifactHasher.sha256(summary), true);
    }

    private static void validate(final List<QualificationManifestV2> manifests) {
        final QualificationManifestV2 first = manifests.get(0);
        final QualificationManifestV2 second = manifests.get(1);
        if (!"PASS".equals(first.value("result.status"))
                || !"PASS".equals(second.value("result.status"))) {
            throw new IllegalArgumentException("both assembled Full runs must have PASS manifests");
        }
        requireEqual(first, second, "identity.configurationIdentitySha256");
        requireEqual(first, second, "identity.comparabilityIdentitySha256");
        for (final QualificationManifestV2 manifest : manifests) {
            requireAtLeast(manifest, "result.acceptedCommands",
                    QualificationFullConfiguration.FULL_MINIMUM_COMMANDS);
            requireAtLeast(manifest, "result.elapsedMillis",
                    QualificationFullConfiguration.FULL_MINIMUM_DURATION.toMillis());
            requireAtLeast(manifest, "result.naturalPostGcSampleCount",
                    QualificationFullConfiguration.FULL_MINIMUM_POST_GC_SAMPLES);
            if (!Boolean.parseBoolean(manifest.value("result.heapGuardPassed"))) {
                throw new IllegalArgumentException("each Full run must pass chronological heap guard");
            }
            if (!Boolean.parseBoolean(manifest.value("result.listenerRebound"))
                    || !Boolean.parseBoolean(manifest.value("result.recoveryLeaseReacquired"))
                    || !Boolean.parseBoolean(manifest.value("result.inventoryStable"))) {
                throw new IllegalArgumentException("each Full run must pass lifecycle gates");
            }
        }
    }

    private static void requireEqual(
            final QualificationManifestV2 first,
            final QualificationManifestV2 second,
            final String key) {
        if (!Objects.equals(first.value(key), second.value(key))) {
            throw new IllegalArgumentException(key + " differs between Full runs");
        }
    }

    private static void requireAtLeast(
            final QualificationManifestV2 manifest,
            final String key,
            final long minimum) {
        final String value = manifest.value(key);
        try {
            if (value == null || Long.parseLong(value) < minimum) {
                throw new IllegalArgumentException(key + " is below approved minimum");
            }
        } catch (final NumberFormatException exception) {
            throw new IllegalArgumentException(key + " is not numeric", exception);
        }
    }

    private static String summaryText(
            final Path firstPath,
            final Path secondPath,
            final QualificationManifestV2 first,
            final QualificationManifestV2 second) throws IOException {
        return "schemaVersion=qualification-campaign-summary-v1\n"
                + "evaluatorVersion=rc-assembled-runtime-v1\n"
                + "requiredRunCount=2\n"
                + "requiredCumulativeNaturalPostGcSamples="
                + QualificationFullConfiguration.CAMPAIGN_MINIMUM_POST_GC_SAMPLES + "\n"
                + "runA.manifestSha256=" + QualificationArtifactHasher.sha256(firstPath) + "\n"
                + "runB.manifestSha256=" + QualificationArtifactHasher.sha256(secondPath) + "\n"
                + "runA.configurationIdentitySha256="
                + first.value("identity.configurationIdentitySha256") + "\n"
                + "runB.configurationIdentitySha256="
                + second.value("identity.configurationIdentitySha256") + "\n"
                + "configurationIdentityEqual=true\n"
                + "comparabilityIdentityEqual=true\n"
                + "qualifyingRunCount=2\n"
                + "cumulativeNaturalPostGcSamples="
                + (Long.parseLong(first.value("result.naturalPostGcSampleCount"))
                        + Long.parseLong(second.value("result.naturalPostGcSampleCount"))) + "\n"
                + "campaign.result=true\n"
                + "claims.productionRtoOrAvailability=NOT_CLAIMED\n"
                + "claims.hardwarePowerLossGuarantee=NOT_CLAIMED\n"
                + "claims.exactlyOnce=NOT_CLAIMED\n";
    }

    private static void publish(final Path target, final String text) throws IOException {
        final Path absolute = target.toAbsolutePath().normalize();
        if (Files.exists(absolute, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("campaign summary already exists");
        }
        final Path parent = Objects.requireNonNull(absolute.getParent(), "summary parent");
        Files.createDirectories(parent);
        final Path temporary = Files.createTempFile(parent, absolute.getFileName() + ".", ".tmp");
        boolean moved = false;
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE);
                moved = true;
            } catch (final AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic campaign publication is required", exception);
            }
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }
}

