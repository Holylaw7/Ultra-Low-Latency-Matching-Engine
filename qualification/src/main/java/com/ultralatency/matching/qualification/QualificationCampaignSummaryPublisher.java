package com.ultralatency.matching.qualification;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Builds and atomically publishes a campaign summary from immutable member artifacts. */
public final class QualificationCampaignSummaryPublisher {

    /** Immutable pair of persisted member artifact paths. */
    public record ManifestReference(Path manifestPath, Path artifactHashesPath) {
        public ManifestReference {
            Objects.requireNonNull(manifestPath, "manifestPath");
            Objects.requireNonNull(artifactHashesPath, "artifactHashesPath");
        }
    }

    private QualificationCampaignSummaryPublisher() {
    }

    /** Reads, validates, hashes and publishes a campaign summary exactly once. */
    public static QualificationCampaignSummary publish(
            final Path target,
            final String campaignId,
            final String evaluatorVersion,
            final List<ManifestReference> references,
            final int requiredRunCount,
            final int requiredCumulativeSamples,
            final boolean evaluatorResult) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(campaignId, "campaignId");
        Objects.requireNonNull(evaluatorVersion, "evaluatorVersion");
        Objects.requireNonNull(references, "references");
        if (references.isEmpty()) {
            throw new IllegalArgumentException("campaign must contain at least one run");
        }
        final Path summaryPath = target.toAbsolutePath().normalize();
        final Path root = Objects.requireNonNull(summaryPath.getParent(), "summary parent");
        final List<LoadedReference> loaded = new ArrayList<>();
        for (final ManifestReference reference : references) {
            final Path manifestPath = regularFile(reference.manifestPath(), "manifest");
            final Path artifactHashesPath = regularFile(
                    reference.artifactHashesPath(), "artifact hash sidecar");
            final QualificationManifestV2 manifest = QualificationManifestV2Store.read(manifestPath);
            QualificationManifestV2Store.readArtifactHashes(artifactHashesPath);
            loaded.add(new LoadedReference(
                    manifest,
                    relative(root, manifestPath),
                    relative(root, artifactHashesPath),
                    QualificationArtifactHasher.sha256(artifactHashesPath)));
        }
        loaded.sort(Comparator.comparing(item -> item.manifest.value("source.runId")));
        for (int index = 1; index < loaded.size(); index++) {
            final String previous = loaded.get(index - 1).manifest.value("source.runId");
            final String current = loaded.get(index).manifest.value("source.runId");
            if (previous.equals(current)) {
                throw new IllegalArgumentException("campaign contains duplicate runId: " + current);
            }
        }
        final QualificationCampaignSummary base = QualificationCampaignSummary.fromManifests(
                campaignId,
                evaluatorVersion,
                loaded.stream().map(LoadedReference::manifest).toList(),
                requiredRunCount,
                requiredCumulativeSamples,
                evaluatorResult);
        final Map<String, String> fields = new TreeMap<>(base.fields());
        for (int index = 0; index < loaded.size(); index++) {
            final LoadedReference item = loaded.get(index);
            final String prefix = "run." + String.format("%04d", index + 1);
            fields.put(prefix + ".manifestRelativePath", item.manifestRelativePath);
            fields.put(prefix + ".artifactHashesRelativePath", item.artifactHashesRelativePath);
            fields.put(prefix + ".artifactHashesSha256", item.artifactHashesSha256);
        }
        return QualificationCampaignSummaryStore.publish(
                summaryPath,
                QualificationCampaignSummary.of(fields));
    }

    private static Path regularFile(final Path path, final String label) throws IOException {
        final Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(label + " must be a regular file: " + normalized);
        }
        return normalized;
    }

    private static String relative(final Path root, final Path path) {
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("campaign references must remain under summary root");
        }
        final String value = root.relativize(path).toString().replace('\\', '/');
        if (value.isBlank()) {
            throw new IllegalArgumentException("campaign reference cannot be empty");
        }
        QualificationV2CanonicalCodec.rejectPathValue(value);
        return value;
    }

    private record LoadedReference(
            QualificationManifestV2 manifest,
            String manifestRelativePath,
            String artifactHashesRelativePath,
            String artifactHashesSha256) {
    }
}
