package com.ultralatency.matching.qualification.ga.soak;

import com.ultralatency.matching.qualification.QualificationEvidencePublication;
import com.ultralatency.matching.qualification.ga.GaEvidenceCodec;
import com.ultralatency.matching.qualification.ga.GaEvidenceStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Generates and verifies a run-derived index for a paced Quick matrix. */
public final class GaSoakMatrixSummary {

    /** Version of the qualification-local matrix index. */
    public static final String VERSION = "qualification-paced-matrix-summary-v1";
    private static final String[] HEADER = {
        "runOrdinal", "physicalExecutionId", "g6RunId", "g8RunId", "protocolV2Window",
        "controllerGitSha", "candidateJarSha256", "qualificationJarSha256",
        "invocationIdentitySha256", "configurationIdentitySha256", "measurementStartNanos",
        "measurementEndNanos", "measurementDurationNanos", "g6Outcome", "g8Outcome",
        "nominalOfferOpportunities", "actualOfferedCommands", "missedSchedulerLate",
        "missedWindowFull", "acceptedCommands", "maxInFlight", "maxPendingWire",
        "maxCompletedUndrained", "readerWakeCount", "capacityReleaseCount",
        "releaseDelayP50Nanos", "releaseDelayP90Nanos", "releaseDelayP99Nanos",
        "releaseDelayMaxNanos", "rawEvidenceRoot", "inventorySha256"
    };

    private GaSoakMatrixSummary() {
    }

    /** Publishes a deterministic summary derived from immutable run artifacts. */
    public static Path publish(final Path target, final List<Path> runRoots) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(runRoots, "runRoots");
        if (runRoots.isEmpty()) {
            throw new IllegalArgumentException("matrix summary requires at least one run");
        }
        final List<String> rows = new ArrayList<>();
        rows.add(String.join(",", HEADER));
        int ordinal = 1;
        final List<Path> normalizedRoots = new ArrayList<>();
        for (Path supplied : runRoots) {
            final Path root = normalizeRoot(supplied);
            normalizedRoots.add(root);
            final GaPacedEvidenceVerifier.Report reconstructed =
                    GaPacedEvidenceVerifier.verify(root);
            final Map<String, String> g6 = readManifest(root.resolve("g6-run-manifest-v1.txt"));
            final Map<String, String> g8 = readManifest(root.resolve("g8-run-manifest-v1.txt"));
            final Map<String, String> invocation = GaQuickInvocation.read(
                    root.resolve("invocation-v1.properties"));
            final String invocationIdentity = GaQuickInvocation.identity(invocation);
            requireEqual(g6, g8, "physicalExecution.id");
            requireEqual(g6, g8, "controller.gitSha");
            requireEqual(g6, g8, "candidate.applicationJarSha256");
            requireEqual(g6, g8, "qualification.jarSha256");
            requireEqual(g6, g8, "invocation.identitySha256");
            requireEqual(g6, g8, "configuration.identitySha256");
            if (!invocationIdentity.equals(g6.get("invocation.identitySha256"))) {
                throw new IOException("invocation identity does not match run manifest");
            }
            final String accepted = readKeyValue(root.resolve("terminal-evidence-v1.txt"),
                    "acceptedCommands");
            final String rawRoot = root.toString().replace('\\', '/');
            rows.add(String.join(",", Integer.toString(ordinal++), g6.get("physicalExecution.id"),
                    g6.get("run.id"), g8.get("run.id"), g6.get("run.protocolV2Window"),
                    g6.get("controller.gitSha"), g6.get("candidate.applicationJarSha256"),
                    g6.get("qualification.jarSha256"), g6.get("invocation.identitySha256"),
                    g6.get("configuration.identitySha256"), g6.get("evidence.measurementStartNanos"),
                    g6.get("evidence.measurementEndNanos"), g6.get("evidence.measurementDurationNanos"),
                    g6.get("evidence.outcome"), g8.get("evidence.outcome"),
                    Long.toString(reconstructed.nominalOfferOpportunities()),
                    Long.toString(reconstructed.actualOfferedCommands()),
                    Long.toString(reconstructed.missedSchedulerLate()),
                    Long.toString(reconstructed.missedWindowFull()), accepted,
                    g6.get("evidence.capacity.maxInFlight"),
                    g6.get("evidence.capacity.maxPendingWire"),
                    g6.get("evidence.capacity.maxCompletedUndrained"),
                    g6.get("evidence.capacity.readerWakeCount"),
                    g6.get("evidence.capacity.releaseCount"),
                    g6.get("evidence.capacity.releaseDelayP50Nanos"),
                    g6.get("evidence.capacity.releaseDelayP90Nanos"),
                    g6.get("evidence.capacity.releaseDelayP99Nanos"),
                    g6.get("evidence.capacity.releaseDelayMaxNanos"), rawRoot,
                     g6.get("artifact.inventory.sha256")));
        }
        if (normalizedRoots.size() < 2
                || !GaPacedEvidenceVerifier.onlyWindowVaries(normalizedRoots)) {
            throw new IOException("matrix is not a fixed only-N-varied lineage");
        }
        final Map<String, String> firstMatrixManifest = readManifest(
                normalizedRoots.get(0).resolve("g6-run-manifest-v1.txt"));
        for (Path root : normalizedRoots) {
            final Map<String, String> manifest = readManifest(
                    root.resolve("g6-run-manifest-v1.txt"));
            requireEqual(firstMatrixManifest, manifest, "controller.gitSha");
            requireEqual(firstMatrixManifest, manifest, "candidate.applicationJarSha256");
            requireEqual(firstMatrixManifest, manifest, "qualification.jarSha256");
            requireEqual(firstMatrixManifest, manifest, "comparability.identitySha256");
        }
        final Path absolute = target.toAbsolutePath().normalize();
        QualificationEvidencePublication.text(absolute, String.join("\n", rows) + "\n");
        GaEvidenceStore.publishArtifactSidecar(
                absolute.resolveSibling(absolute.getFileName() + ".sha256"),
                Map.of(absolute.getFileName().toString(), absolute));
        final Path metadata = absolute.resolveSibling("matrix-summary-v1.properties");
        final Map<String, String> metadataFields = new java.util.TreeMap<>();
        final Map<String, String> firstManifest = firstMatrixManifest;
        metadataFields.put("matrix.schema", VERSION);
        metadataFields.put("matrix.runCount", Integer.toString(normalizedRoots.size()));
        metadataFields.put("matrix.onlyNVaried", "true");
        metadataFields.put("matrix.controllerGitSha", firstManifest.get("controller.gitSha"));
        metadataFields.put("matrix.candidateJarSha256",
                firstManifest.get("candidate.applicationJarSha256"));
        metadataFields.put("matrix.qualificationJarSha256",
                firstManifest.get("qualification.jarSha256"));
        metadataFields.put("matrix.comparabilityIdentitySha256",
                firstManifest.get("comparability.identitySha256"));
        QualificationEvidencePublication.text(metadata, canonicalProperties(metadataFields));
        GaEvidenceStore.publishArtifactSidecar(
                metadata.resolveSibling(metadata.getFileName() + ".sha256"),
                Map.of(metadata.getFileName().toString(), metadata));
        return absolute;
    }

    /** Returns sorted canonical key/value text for the matrix metadata sidecar. */
    private static String canonicalProperties(final Map<String, String> fields) {
        final StringBuilder text = new StringBuilder();
        new java.util.TreeMap<>(fields).forEach((key, value) ->
                text.append(key).append('=').append(value).append('\n'));
        return text.toString();
    }

    /** Returns whether all supplied runs have identical material invocation fields except N. */
    public static boolean onlyWindowVaries(final List<Path> runRoots) throws IOException {
        Objects.requireNonNull(runRoots, "runRoots");
        final List<Map<String, String>> invocations = new ArrayList<>();
        for (Path supplied : runRoots) {
            invocations.add(GaQuickInvocation.read(
                    normalizeRoot(supplied).resolve("invocation-v1.properties")));
        }
        return GaQuickInvocation.onlyWindowVaries(invocations);
    }

    private static Path normalizeRoot(final Path supplied) throws IOException {
        final Path path = Objects.requireNonNull(supplied, "run root")
                .toAbsolutePath().normalize();
        if (Files.isRegularFile(path.resolve("g6-run-manifest-v1.txt"))) {
            return path;
        }
        final List<Path> children;
        try (var stream = Files.list(path)) {
            children = stream.filter(Files::isDirectory)
                    .filter(item -> item.getFileName().toString().startsWith("g6-g8-quick-"))
                    .toList();
        }
        if (children.size() != 1) {
            throw new IOException("run root must identify exactly one physical Quick: " + path);
        }
        return children.get(0);
    }

    private static Map<String, String> readManifest(final Path path) throws IOException {
        return GaEvidenceStore.read(path, GaEvidenceCodec.Schema.RUN);
    }

    private static void requireEqual(
            final Map<String, String> first,
            final Map<String, String> second,
            final String key) throws IOException {
        if (!Objects.equals(first.get(key), second.get(key))) {
            throw new IOException("G6/G8 identity mismatch for " + key);
        }
    }

    private static String readKeyValue(final Path path, final String key) throws IOException {
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (line.startsWith(key + "=")) {
                final String value = line.substring(key.length() + 1);
                if (value.isBlank()) {
                    throw new IOException("blank terminal value for " + key);
                }
                return value;
            }
        }
        throw new IOException("missing terminal value for " + key);
    }

}
