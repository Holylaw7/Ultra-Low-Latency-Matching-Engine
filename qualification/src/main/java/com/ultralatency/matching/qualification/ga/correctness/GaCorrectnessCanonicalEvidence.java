package com.ultralatency.matching.qualification.ga.correctness;

import com.ultralatency.matching.qualification.QualificationArtifactHasher;
import com.ultralatency.matching.qualification.QualificationIdentity;
import com.ultralatency.matching.qualification.QualificationEvidencePublication;
import com.ultralatency.matching.qualification.QualificationProfile;
import com.ultralatency.matching.qualification.QualificationWorkloadV1;
import com.ultralatency.matching.qualification.ga.GaCandidateVerifier;
import com.ultralatency.matching.qualification.ga.GaEvidenceCodec;
import com.ultralatency.matching.qualification.ga.GaEvidenceStore;
import com.ultralatency.matching.qualification.ga.GaGateEvaluator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Publishes the canonical evidence views for one physical G1/G2 execution.
 *
 * <p>A physical case is executed once.  Its immutable raw artifacts are
 * referenced by two independent canonical run manifests, one for each gate.
 * The separate binding document proves that the views came from the same
 * physical execution without adding fields to the frozen run schema.</p>
 */
public final class GaCorrectnessCanonicalEvidence {

    /** Canonical gate-version tokens approved for the two correctness views. */
    public static final String G1_VERSION = "g1-v1";
    /** Canonical gate-version token for deterministic recovery. */
    public static final String G2_VERSION = "g2-v1";
    /** Binding payload schema, intentionally separate from the global GA schemas. */
    public static final String BINDING_SCHEMA = "ga-g1-g2-physical-run-binding-v1";
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private GaCorrectnessCanonicalEvidence() {
    }

    /** The two canonical views and their physical-execution binding. */
    public record ViewPair(
            String physicalExecutionId,
            String caseId,
            boolean casePassed,
            int recoveryObservationCount,
            Path g1ManifestPath,
            String g1ManifestSha256,
            Path g2ManifestPath,
            String g2ManifestSha256,
            Path bindingPath,
            String bindingSha256) {

        public ViewPair {
            requireUuid(physicalExecutionId, "physicalExecutionId");
            requireText(caseId, "caseId");
            if (recoveryObservationCount < 0) {
                throw new IllegalArgumentException("recoveryObservationCount must not be negative");
            }
            requireDigest(g1ManifestSha256, "g1ManifestSha256");
            requireDigest(g2ManifestSha256, "g2ManifestSha256");
            requireDigest(bindingSha256, "bindingSha256");
            Objects.requireNonNull(g1ManifestPath, "g1ManifestPath");
            Objects.requireNonNull(g2ManifestPath, "g2ManifestPath");
            Objects.requireNonNull(bindingPath, "bindingPath");
        }
    }

    /** The two immutable gate-result documents emitted after all physical cases. */
    public record GatePair(
            Path g1ResultPath,
            String g1ResultSha256,
            Path g2ResultPath,
            String g2ResultSha256) {

        public GatePair {
            Objects.requireNonNull(g1ResultPath, "g1ResultPath");
            Objects.requireNonNull(g2ResultPath, "g2ResultPath");
            requireDigest(g1ResultSha256, "g1ResultSha256");
            requireDigest(g2ResultSha256, "g2ResultSha256");
        }
    }

    /** Publishes both gate views and the physical-run binding for one case. */
    public static ViewPair publishCaseViews(
            final Path caseDirectory,
            final GaCorrectnessMatrix matrix,
            final GaCorrectnessCaseResult result,
            final GaCorrectnessCanonicalContext context,
            final String physicalExecutionId,
            final Instant started,
            final Instant completed,
            final long elapsedNanos) throws IOException {
        return publishCaseViews(caseDirectory, matrix, result, context, physicalExecutionId,
                started, completed, elapsedNanos,
                GaCorrectnessRuntimeProvenance.capture(caseDirectory));
    }

    /** Publishes views using provenance captured at the physical execution boundary. */
    static ViewPair publishCaseViews(
            final Path caseDirectory,
            final GaCorrectnessMatrix matrix,
            final GaCorrectnessCaseResult result,
            final GaCorrectnessCanonicalContext context,
            final String physicalExecutionId,
            final Instant started,
            final Instant completed,
            final long elapsedNanos,
            final Map<String, String> runtime) throws IOException {
        Objects.requireNonNull(caseDirectory, "caseDirectory");
        Objects.requireNonNull(matrix, "matrix");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(runtime, "runtime");
        requireUuid(physicalExecutionId, "physicalExecutionId");
        Objects.requireNonNull(started, "started");
        Objects.requireNonNull(completed, "completed");
        if (completed.isBefore(started) || elapsedNanos < 0) {
            throw new IllegalArgumentException("physical execution time is invalid");
        }
        final Path root = caseDirectory.toAbsolutePath().normalize();
        if (!root.getFileName().toString().equals(result.matrixCase().id())
                || !root.equals(result.artifactDirectory().toAbsolutePath().normalize())) {
            throw new IOException("canonical case directory does not match matrix case");
        }
        Files.createDirectories(root);
        final Path inventoryPath = root.resolve("SHA256SUMS");
        final GaCorrectnessArtifactInventory.Published inventory =
                GaCorrectnessArtifactInventory.publish(root, inventoryPath);
        final String comparabilityIdentity = QualificationIdentity.digest(runtime);
        final long elapsedMillis = elapsedNanos / 1_000_000L;
        final String g1RunId = UUID.randomUUID().toString();
        final String g2RunId = UUID.randomUUID().toString();
        final Map<String, String> g1 = manifestFields(
                "G1", G1_VERSION, g1RunId, matrix, result, context, runtime,
                comparabilityIdentity, started, completed, elapsedMillis, inventory);
        final Map<String, String> g2 = manifestFields(
                "G2", G2_VERSION, g2RunId, matrix, result, context, runtime,
                comparabilityIdentity, started, completed, elapsedMillis, inventory);
        final Path g1Path = root.resolve("ga-g1-run-manifest-v1.txt");
        final Path g2Path = root.resolve("ga-g2-run-manifest-v1.txt");
        final String g1Digest = GaEvidenceStore.publish(g1Path, GaEvidenceCodec.Schema.RUN, g1);
        final String g2Digest = GaEvidenceStore.publish(g2Path, GaEvidenceCodec.Schema.RUN, g2);
        GaCorrectnessArtifactInventory.publishAdjacentSidecar(g1Path);
        GaCorrectnessArtifactInventory.publishAdjacentSidecar(g2Path);
        final Path binding = root.resolve("ga-g1-g2-physical-run-binding-v1.txt");
        final Map<String, String> bindingFields = bindingFields(
                physicalExecutionId, result, started, completed, elapsedMillis,
                g1Path.getFileName().toString(), g1Digest,
                g2Path.getFileName().toString(), g2Digest, inventory.sha256());
        final String bindingText = canonicalBinding(bindingFields);
        QualificationEvidencePublication.text(binding, bindingText);
        GaCorrectnessArtifactInventory.publishAdjacentSidecar(binding);
        return new ViewPair(
                physicalExecutionId,
                result.matrixCase().id(),
                result.passed(),
                recoveryObservationCount(result),
                g1Path,
                g1Digest,
                g2Path,
                g2Digest,
                binding,
                sha256(bindingText.getBytes(StandardCharsets.US_ASCII)));
    }

    /** Publishes one canonical result for each gate after every case is complete. */
    public static GatePair publishGateResults(
            final Path campaignRoot,
            final GaCorrectnessMatrix matrix,
            final List<ViewPair> views,
            final GaCorrectnessCanonicalContext context,
            final Instant started,
            final Instant completed,
            final boolean matrixPassed) throws IOException {
        Objects.requireNonNull(campaignRoot, "campaignRoot");
        Objects.requireNonNull(matrix, "matrix");
        Objects.requireNonNull(views, "views");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(started, "started");
        Objects.requireNonNull(completed, "completed");
        if (!"ga-g1-g2-test-v1".equals(matrix.version()) && !context.isApprovedCandidate()) {
            throw new IOException("approved matrix requires the frozen candidate context");
        }
        if (completed.isBefore(started) || views.size() > matrix.cases().size()) {
            throw new IllegalArgumentException("invalid canonical gate-result bounds");
        }
        final Path root = campaignRoot.toAbsolutePath().normalize();
        if (views.isEmpty()) {
            throw new IOException("canonical gate result requires at least one run manifest");
        }
        validateViews(root, matrix, views, context);
        final Map<String, String> g1 = gateFields(
                "G1", G1_VERSION, root, matrix, views, context, started, completed, matrixPassed);
        final Map<String, String> g2 = gateFields(
                "G2", G2_VERSION, root, matrix, views, context, started, completed, matrixPassed);
        final Path g1Path = root.resolve("ga-g1-gate-result-v1.txt");
        final Path g2Path = root.resolve("ga-g2-gate-result-v1.txt");
        final String g1Digest = GaEvidenceStore.publish(g1Path, GaEvidenceCodec.Schema.GATE, g1);
        final String g2Digest = GaEvidenceStore.publish(g2Path, GaEvidenceCodec.Schema.GATE, g2);
        GaCorrectnessArtifactInventory.publishAdjacentSidecar(g1Path);
        GaCorrectnessArtifactInventory.publishAdjacentSidecar(g2Path);
        verifyAdjacentSidecar(g1Path, g1Digest);
        verifyAdjacentSidecar(g2Path, g2Digest);
        final boolean g1Passed = GaGateEvaluator.evaluateGate(g1).passed();
        final boolean g2Passed = GaGateEvaluator.evaluateGate(g2).passed();
        final boolean expected = matrixPassed && views.size() == matrix.cases().size()
                && views.stream().allMatch(ViewPair::casePassed);
        if (g1Passed != expected || g2Passed != expected) {
            throw new IOException("canonical gate result semantic evaluation mismatch");
        }
        return new GatePair(g1Path, g1Digest, g2Path, g2Digest);
    }

    private static void validateViews(
            final Path campaignRoot,
            final GaCorrectnessMatrix matrix,
            final List<ViewPair> views,
            final GaCorrectnessCanonicalContext context) throws IOException {
        final List<GaCorrectnessCase> expectedCases = matrix.cases();
        final Set<String> physicalIds = new HashSet<>();
        final Set<String> caseIds = new HashSet<>();
        for (int index = 0; index < views.size(); index++) {
            final ViewPair view = Objects.requireNonNull(views.get(index), "canonical view");
            final GaCorrectnessCase expected = expectedCases.get(index);
            if (!expected.id().equals(view.caseId())) {
                throw new IOException("canonical views are not in matrix order");
            }
            if (!physicalIds.add(view.physicalExecutionId())
                    || !caseIds.add(view.caseId())) {
                throw new IOException("canonical views contain duplicate identities");
            }
            validateViewPair(campaignRoot, matrix, expected, view, context);
        }
    }

    private static void validateViewPair(
            final Path campaignRoot,
            final GaCorrectnessMatrix matrix,
            final GaCorrectnessCase expected,
            final ViewPair view,
            final GaCorrectnessCanonicalContext context) throws IOException {
        final Path caseRoot = campaignRoot.resolve(expected.id()).toAbsolutePath().normalize();
        if (!caseRoot.startsWith(campaignRoot) || !Files.isDirectory(caseRoot)) {
            throw new IOException("canonical case root is invalid");
        }
        requireCanonicalFile(view.g1ManifestPath(), caseRoot, "ga-g1-run-manifest-v1.txt");
        requireCanonicalFile(view.g2ManifestPath(), caseRoot, "ga-g2-run-manifest-v1.txt");
        requireCanonicalFile(view.bindingPath(), caseRoot,
                "ga-g1-g2-physical-run-binding-v1.txt");
        final String g1Digest = QualificationArtifactHasher.sha256(view.g1ManifestPath());
        final String g2Digest = QualificationArtifactHasher.sha256(view.g2ManifestPath());
        final String bindingDigest = QualificationArtifactHasher.sha256(view.bindingPath());
        if (!view.g1ManifestSha256().equals(g1Digest)
                || !view.g2ManifestSha256().equals(g2Digest)
                || !view.bindingSha256().equals(bindingDigest)) {
            throw new IOException("canonical view digest mismatch");
        }
        final Map<String, String> g1 = GaEvidenceStore.read(
                view.g1ManifestPath(), GaEvidenceCodec.Schema.RUN);
        final Map<String, String> g2 = GaEvidenceStore.read(
                view.g2ManifestPath(), GaEvidenceCodec.Schema.RUN);
        verifyRunManifestArtifacts(view.g1ManifestPath());
        verifyRunManifestArtifacts(view.g2ManifestPath());
        if (!"G1".equals(g1.get("gate.id")) || !G1_VERSION.equals(g1.get("gate.version"))
                || !"G2".equals(g2.get("gate.id")) || !G2_VERSION.equals(g2.get("gate.version"))) {
            throw new IOException("canonical view gate attribution mismatch");
        }
        if (g1.get("run.id").equals(g2.get("run.id"))) {
            throw new IOException("paired canonical views must have distinct run IDs");
        }
        if (!expected.profile().name().equals(g1.get("run.profile"))
                || !expected.profile().name().equals(g2.get("run.profile"))
                || !Long.toString(expected.seed()).equals(g1.get("run.seed"))
                || !Long.toString(expected.seed()).equals(g2.get("run.seed"))
                || !Integer.toString(matrix.commandCount()).equals(g1.get("run.commandCount"))
                || !Integer.toString(matrix.commandCount()).equals(g2.get("run.commandCount"))) {
            throw new IOException("canonical view matrix attribution mismatch");
        }
        if (!workloadVersion(expected.profile()).equals(g1.get("workload.version"))
                || !workloadVersion(expected.profile()).equals(g2.get("workload.version"))) {
            throw new IOException("canonical view workload attribution mismatch");
        }
        final Set<String> gateSpecific = Set.of(
                "configuration.identitySha256", "gate.id", "gate.version", "run.id");
        final Set<String> keys = new HashSet<>(g1.keySet());
        keys.addAll(g2.keySet());
        for (String key : keys) {
            if (!gateSpecific.contains(key) && !Objects.equals(g1.get(key), g2.get(key))) {
                throw new IOException("paired canonical views disagree on " + key);
            }
        }
        final String expectedG1Config = configurationIdentity(matrix, "G1", G1_VERSION, context);
        final String expectedG2Config = configurationIdentity(matrix, "G2", G2_VERSION, context);
        if (!expectedG1Config.equals(g1.get("configuration.identitySha256"))
                || !expectedG2Config.equals(g2.get("configuration.identitySha256"))) {
            throw new IOException("canonical configuration identity mismatch");
        }
        final Map<String, String> runtime = new TreeMap<>();
        g1.forEach((key, value) -> {
            if (key.startsWith("runtime.")) {
                runtime.put(key, value);
            }
        });
        if (!QualificationIdentity.digest(runtime).equals(g1.get("comparability.identitySha256"))) {
            throw new IOException("canonical comparability identity mismatch");
        }
        final Map<String, String> binding = readBinding(view.bindingPath());
        if (!view.physicalExecutionId().equals(binding.get("physicalExecution.id"))
                || !expected.id().equals(binding.get("matrixCase.id"))
                || !expected.profile().name().equals(binding.get("profile"))
                || !Long.toString(expected.seed()).equals(binding.get("seed"))
                || !Integer.toString(expected.repetition()).equals(binding.get("repetition"))
                || !view.g1ManifestPath().getFileName().toString().equals(
                binding.get("g1.runManifestPath"))
                || !view.g2ManifestPath().getFileName().toString().equals(
                binding.get("g2.runManifestPath"))
                || !view.g1ManifestSha256().equals(binding.get("g1.runManifestSha256"))
                || !view.g2ManifestSha256().equals(binding.get("g2.runManifestSha256"))
                || !g1.get("artifact.inventory.sha256").equals(
                binding.get("rawEvidenceRootSha256"))) {
            throw new IOException("physical-run binding does not match paired views");
        }
        if (!g1.get("evidence.startedAtUtc").equals(binding.get("startedAtUtc"))
                || !g1.get("evidence.completedAtUtc").equals(binding.get("completedAtUtc"))
                || !g1.get("evidence.elapsedMillis").equals(binding.get("elapsedMillis"))) {
            throw new IOException("physical-run timing does not match paired views");
        }
        final boolean manifestPassed = "PASS".equals(g1.get("evidence.outcome"));
        if (view.casePassed() != manifestPassed || manifestPassed !=
                "PASS".equals(g2.get("evidence.outcome"))) {
            throw new IOException("canonical view outcome mismatch");
        }
    }

    private static void requireCanonicalFile(
            final Path path, final Path caseRoot, final String expectedName) throws IOException {
        final Path normalized = Objects.requireNonNull(path, "canonical path")
                .toAbsolutePath().normalize();
        if (!normalized.getFileName().toString().equals(expectedName)
                || !caseRoot.equals(normalized.getParent())
                || Files.isSymbolicLink(normalized)
                || !Files.isRegularFile(normalized, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("canonical evidence file is not immutable: " + expectedName);
        }
    }

    /** Reads and validates the separate physical binding payload. */
    public static Map<String, String> readBinding(final Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        final byte[] bytes = Files.readAllBytes(path);
        final String text = new String(bytes, StandardCharsets.US_ASCII);
        if (!java.util.Arrays.equals(bytes, text.getBytes(StandardCharsets.US_ASCII))
                || !text.endsWith("\n") || text.indexOf('\r') >= 0
                || !canonicalBindingFields(text).equals(text)) {
            throw new IOException("invalid physical-run binding encoding");
        }
        final Map<String, String> fields = parseBinding(text);
        if (!BINDING_SCHEMA.equals(fields.get("schema.version"))) {
            throw new IOException("unexpected physical-run binding schema");
        }
        final java.util.Set<String> expectedKeys = java.util.Set.of(
                "completedAtUtc", "elapsedMillis", "g1.runManifestPath",
                "g1.runManifestSha256", "g2.runManifestPath", "g2.runManifestSha256",
                "matrixCase.id", "physicalExecution.id", "profile",
                "rawEvidenceRootSha256", "repetition", "schema.version", "seed",
                "startedAtUtc");
        if (!fields.keySet().equals(expectedKeys)) {
            throw new IOException("physical-run binding field set mismatch");
        }
        requireUuid(fields.get("physicalExecution.id"), "physicalExecution.id");
        requireDigest(fields.get("g1.runManifestSha256"), "g1.runManifestSha256");
        requireDigest(fields.get("g2.runManifestSha256"), "g2.runManifestSha256");
        requireDigest(fields.get("rawEvidenceRootSha256"), "rawEvidenceRootSha256");
        requireRelativeBindingPath(fields.get("g1.runManifestPath"));
        requireRelativeBindingPath(fields.get("g2.runManifestPath"));
        requireNonNegativeLong(fields.get("elapsedMillis"), "elapsedMillis");
        requireNonNegativeLong(fields.get("seed"), "seed");
        requirePositiveInteger(fields.get("repetition"), "repetition");
        final Instant started = canonicalInstant(fields.get("startedAtUtc"));
        final Instant completed = canonicalInstant(fields.get("completedAtUtc"));
        if (completed.isBefore(started)) {
            throw new IOException("physical-run binding completion precedes start");
        }
        if (!fields.get("profile").matches(
                "LIFECYCLE_MIX|CROSSING_MULTI_MATCH|RESTING_DEPTH|MEMORY_STEADY_STATE_V1")) {
            throw new IOException("invalid physical-run profile");
        }
        return Map.copyOf(fields);
    }

    private static void requireNonNegativeLong(final String value, final String field)
            throws IOException {
        try {
            if (value == null || !value.matches("0|[1-9][0-9]*")
                    || Long.parseLong(value) < 0) {
                throw new IllegalArgumentException("invalid non-negative integer");
            }
        } catch (final RuntimeException exception) {
            throw new IOException(field + " is not a non-negative integer", exception);
        }
    }

    private static void requirePositiveInteger(final String value, final String field)
            throws IOException {
        requireNonNegativeLong(value, field);
        if ("0".equals(value)) {
            throw new IOException(field + " must be positive");
        }
    }

    /** Verifies every artifact referenced by one canonical run manifest. */
    public static void verifyRunManifestArtifacts(final Path manifestPath) throws IOException {
        Objects.requireNonNull(manifestPath, "manifestPath");
        final Path manifest = manifestPath.toAbsolutePath().normalize();
        final Map<String, String> fields = GaEvidenceStore.read(
                manifest, GaEvidenceCodec.Schema.RUN);
        final Path root = manifest.getParent();
        if (root == null) {
            throw new IOException("canonical manifest has no parent");
        }
        final Path inventory = resolveContained(root, fields.get("artifact.inventory.path"));
        if (!Files.isRegularFile(inventory, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(inventory)
                || Files.size(inventory) != Long.parseLong(fields.get("artifact.inventory.size"))
                || !fields.get("artifact.inventory.sha256").equals(
                QualificationArtifactHasher.sha256(inventory))) {
            throw new IOException("canonical artifact inventory mismatch");
        }
        verifyAdjacentSidecar(inventory, QualificationArtifactHasher.sha256(inventory));
        verifyAdjacentSidecar(manifest, QualificationArtifactHasher.sha256(manifest));
        final Map<String, ArtifactReference> expected = new TreeMap<>();
        int index = 1;
        while (fields.containsKey(String.format("artifact.%04d.path", index))) {
            final String prefix = String.format("artifact.%04d", index++);
            final String path = fields.get(prefix + ".path");
            final Path artifact = resolveContained(root, path);
            final long size = Long.parseLong(fields.get(prefix + ".size"));
            final String digest = fields.get(prefix + ".sha256");
            if (!Files.isRegularFile(artifact, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(artifact) || Files.size(artifact) != size
                    || !digest.equals(QualificationArtifactHasher.sha256(artifact))) {
                throw new IOException("canonical artifact mismatch: " + path);
            }
            verifyAdjacentSidecar(artifact, digest);
            if (expected.put(path, new ArtifactReference(size, digest)) != null) {
                throw new IOException("duplicate canonical artifact path: " + path);
            }
        }
        if (expected.isEmpty()) {
            throw new IOException("canonical manifest has no artifacts");
        }
        final Map<String, ArtifactReference> actual = readInventory(inventory);
        if (!expected.equals(actual)) {
            throw new IOException("canonical inventory does not match manifest artifacts");
        }
        final Path binding = root.resolve("ga-g1-g2-physical-run-binding-v1.txt");
        if (Files.isRegularFile(binding, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(binding)) {
            verifyAdjacentSidecar(binding, QualificationArtifactHasher.sha256(binding));
        } else {
            throw new IOException("canonical physical-run binding is missing");
        }
        rejectUnlistedFiles(root, inventory, expected.keySet());
    }

    private static void verifyAdjacentSidecar(
            final Path artifact, final String expectedDigest) throws IOException {
        final Path normalized = artifact.toAbsolutePath().normalize();
        final Path parent = normalized.getParent();
        if (parent == null || !Files.isRegularFile(normalized, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(normalized)) {
            throw new IOException("canonical payload is not a regular file");
        }
        final Path sidecar = parent.resolve(normalized.getFileName() + ".sha256");
        if (!Files.isRegularFile(sidecar, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(sidecar)) {
            throw new IOException("canonical payload sidecar is missing: "
                    + sidecar.getFileName());
        }
        final Map<String, String> values = GaEvidenceStore.readArtifactSidecar(sidecar);
        final String name = normalized.getFileName().toString();
        if (values.size() != 1 || !expectedDigest.equals(values.get(name))) {
            throw new IOException("canonical payload sidecar mismatch: " + name);
        }
    }

    private static void rejectUnlistedFiles(
            final Path caseRoot,
            final Path inventory,
            final Set<String> inventoryEntries) throws IOException {
        final Set<String> allowed = new HashSet<>(inventoryEntries);
        allowed.add(relative(caseRoot, inventory));
        allowed.add(relative(caseRoot, inventory.resolveSibling(inventory.getFileName() + ".sha256")));
        allowed.add("ga-g1-run-manifest-v1.txt");
        allowed.add("ga-g2-run-manifest-v1.txt");
        allowed.add("ga-g1-g2-physical-run-binding-v1.txt");
        allowed.add("ga-g1-run-manifest-v1.txt.sha256");
        allowed.add("ga-g2-run-manifest-v1.txt.sha256");
        allowed.add("ga-g1-g2-physical-run-binding-v1.txt.sha256");
        for (String entry : inventoryEntries) {
            final Path artifact = resolveContained(caseRoot, entry);
            allowed.add(relative(caseRoot,
                    artifact.resolveSibling(artifact.getFileName() + ".sha256")));
        }
        try (var stream = Files.walk(caseRoot)) {
            for (Path path : stream.toList()) {
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("canonical case contains a symbolic link");
                }
                if (Files.isDirectory(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                if (!Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("canonical case contains a non-regular entry");
                }
                final String relative = relative(caseRoot, path);
                if (!allowed.contains(relative)) {
                    throw new IOException("canonical case has unlisted artifact: " + relative);
                }
            }
        }
    }

    private static Map<String, String> manifestFields(
            final String gate,
            final String gateVersion,
            final String runId,
            final GaCorrectnessMatrix matrix,
            final GaCorrectnessCaseResult result,
            final GaCorrectnessCanonicalContext context,
            final Map<String, String> runtime,
            final String comparabilityIdentity,
            final Instant started,
            final Instant completed,
            final long elapsedMillis,
            final GaCorrectnessArtifactInventory.Published inventory) {
        final GaCorrectnessCase matrixCase = result.matrixCase();
        final GaCandidateVerifier.Verified candidate = context.candidate();
        final String failure = result.failures().isEmpty()
                ? "none" : String.join(";", result.failures());
        final Map<String, String> fields = new TreeMap<>();
        fields.put("candidate.applicationJarSha256", candidate.applicationJarSha256());
        fields.put("candidate.productionSha", candidate.productionSha());
        fields.put("candidate.productionTreeSha256", candidate.productionTreeSha256());
        fields.put("candidate.tag", candidate.tag());
        fields.put("candidate.tagObjectSha", candidate.tagObjectSha());
        fields.put("comparability.identitySha256", comparabilityIdentity);
        fields.put("configuration.identitySha256",
                configurationIdentity(matrix, gate, gateVersion, context));
        fields.put("controller.gitSha", context.controllerGitSha());
        fields.put("evidence.completedAtUtc", completed.toString());
        fields.put("evidence.elapsedMillis", Long.toString(elapsedMillis));
        fields.put("evidence.failureCode", result.passed() ? "NONE" : "B1");
        fields.put("evidence.failureDigestSha256", sha256(failure.getBytes(StandardCharsets.UTF_8)));
        fields.put("evidence.outcome", result.passed() ? "PASS" : "FAIL");
        fields.put("evidence.startedAtUtc", started.toString());
        fields.put("gate.id", gate);
        fields.put("gate.version", gateVersion);
        fields.put("run.commandCount", Integer.toString(matrix.commandCount()));
        fields.put("run.id", runId);
        fields.put("run.profile", matrixCase.profile().name());
        fields.put("run.seed", Long.toString(matrixCase.seed()));
        runtime.forEach(fields::put);
        fields.put("schema.version", GaEvidenceCodec.Schema.RUN.version());
        fields.put("workload.version", workloadVersion(matrixCase.profile()));
        fields.put("artifact.inventory.path", inventory.path().getFileName().toString());
        fields.put("artifact.inventory.sha256", inventory.sha256());
        fields.put("artifact.inventory.size", Long.toString(inventory.size()));
        int index = 1;
        for (GaCorrectnessArtifactInventory.Artifact artifact : inventory.artifacts()) {
            final String prefix = String.format("artifact.%04d", index++);
            fields.put(prefix + ".path", artifact.path());
            fields.put(prefix + ".sha256", artifact.sha256());
            fields.put(prefix + ".size", Long.toString(artifact.size()));
        }
        return fields;
    }

    private static Map<String, String> gateFields(
            final String gate,
            final String gateVersion,
            final Path root,
            final GaCorrectnessMatrix matrix,
            final List<ViewPair> views,
            final GaCorrectnessCanonicalContext context,
            final Instant started,
            final Instant completed,
            final boolean matrixPassed) throws IOException {
        final List<Map<String, String>> manifests = new ArrayList<>();
        for (ViewPair view : views) {
            final Path path = "G1".equals(gate) ? view.g1ManifestPath() : view.g2ManifestPath();
            final Map<String, String> manifest = GaEvidenceStore.read(path, GaEvidenceCodec.Schema.RUN);
            if (!gate.equals(manifest.get("gate.id"))
                    || !gateVersion.equals(manifest.get("gate.version"))) {
                throw new IOException("gate manifest attribution mismatch");
            }
            verifyRunManifestArtifacts(path);
            final String expectedDigest = "G1".equals(gate)
                    ? view.g1ManifestSha256() : view.g2ManifestSha256();
            if (!expectedDigest.equals(QualificationArtifactHasher.sha256(path))) {
                throw new IOException("gate manifest digest mismatch");
            }
            manifests.add(manifest);
        }
        final Map<String, String> first = manifests.get(0);
        final GaCandidateVerifier.Verified candidate = context.candidate();
        for (Map<String, String> manifest : manifests) {
            if (!first.get("candidate.applicationJarSha256").equals(
                    manifest.get("candidate.applicationJarSha256"))
                    || !first.get("candidate.productionSha").equals(
                    manifest.get("candidate.productionSha"))
                    || !first.get("candidate.productionTreeSha256").equals(
                    manifest.get("candidate.productionTreeSha256"))
                    || !first.get("candidate.tag").equals(manifest.get("candidate.tag"))
                    || !first.get("candidate.tagObjectSha").equals(
                    manifest.get("candidate.tagObjectSha"))
                    || !first.get("controller.gitSha").equals(manifest.get("controller.gitSha"))
                    || !first.get("comparability.identitySha256").equals(
                    manifest.get("comparability.identitySha256"))
                    || !first.get("configuration.identitySha256").equals(
                    manifest.get("configuration.identitySha256"))) {
                throw new IOException("canonical manifest identity mismatch");
            }
        }
        if (!candidate.applicationJarSha256().equals(first.get("candidate.applicationJarSha256"))
                || !candidate.productionSha().equals(first.get("candidate.productionSha"))
                || !candidate.productionTreeSha256().equals(
                first.get("candidate.productionTreeSha256"))
                || !candidate.tag().equals(first.get("candidate.tag"))
                || !candidate.tagObjectSha().equals(first.get("candidate.tagObjectSha"))
                || !context.controllerGitSha().equals(first.get("controller.gitSha"))) {
            throw new IOException("canonical gate candidate identity mismatch");
        }
        final boolean casesPass = matrixPassed && views.size() == matrix.cases().size()
                && views.stream().allMatch(ViewPair::casePassed);
        final Map<String, String> fields = new TreeMap<>();
        fields.put("blocker.classification", casesPass ? "NONE" : "B1");
        fields.put("candidate.applicationJarSha256", candidate.applicationJarSha256());
        fields.put("candidate.productionSha", candidate.productionSha());
        fields.put("candidate.productionTreeSha256", candidate.productionTreeSha256());
        fields.put("candidate.tag", candidate.tag());
        fields.put("candidate.tagObjectSha", candidate.tagObjectSha());
        fields.put("comparability.identitySha256", first.get("comparability.identitySha256"));
        fields.put("configuration.identitySha256", first.get("configuration.identitySha256"));
        fields.put("controller.gitSha", context.controllerGitSha());
        final List<Criterion> criteria = criteria(gate, matrix, views, casesPass);
        fields.put("criterion.count", Integer.toString(criteria.size()));
        for (int index = 0; index < criteria.size(); index++) {
            final Criterion criterion = criteria.get(index);
            final String prefix = String.format("criterion.%04d", index + 1);
            fields.put(prefix + ".id", criterion.id());
            fields.put(prefix + ".actual", criterion.actual());
            fields.put(prefix + ".operator", criterion.operator());
            fields.put(prefix + ".required", criterion.required());
            fields.put(prefix + ".result", criterion.passed() ? "PASS" : "FAIL");
        }
        fields.put("evidence.completedAtUtc", completed.toString());
        fields.put("evidence.outcome", casesPass ? "PASS" : "FAIL");
        fields.put("evidence.startedAtUtc", started.toString());
        fields.put("gate.id", gate);
        fields.put("gate.version", gateVersion);
        final List<String> limitations = List.of(
                "Qualification is limited to the approved profiles, seeds and command count.",
                "Recovery evidence does not claim arbitrary in-flight crash safety.",
                "The result does not claim hardware power-loss durability or exactly-once processing.");
        fields.put("limitation.count", Integer.toString(limitations.size()));
        for (int index = 0; index < limitations.size(); index++) {
            final String prefix = String.format("limitation.%04d", index + 1);
            fields.put(prefix + ".code", "L" + (index + 1));
            fields.put(prefix + ".statementDigestSha256",
                    sha256(limitations.get(index).getBytes(StandardCharsets.UTF_8)));
        }
        fields.put("manifest.count", Integer.toString(manifests.size()));
        for (int index = 0; index < views.size(); index++) {
            final ViewPair view = views.get(index);
            final Path path = "G1".equals(gate) ? view.g1ManifestPath() : view.g2ManifestPath();
            final String prefix = String.format("manifest.%04d", index + 1);
            fields.put(prefix + ".path", relative(root, path));
            fields.put(prefix + ".sha256", "G1".equals(gate)
                    ? view.g1ManifestSha256() : view.g2ManifestSha256());
        }
        fields.put("schema.version", GaEvidenceCodec.Schema.GATE.version());
        return fields;
    }

    private static List<Criterion> criteria(
            final String gate,
            final GaCorrectnessMatrix matrix,
            final List<ViewPair> views,
            final boolean casesPass) {
        final int expectedCases = matrix.cases().size();
        final int observedCases = views.size();
        final int passedCases = (int) views.stream().filter(ViewPair::casePassed).count();
        final List<Criterion> result = new ArrayList<>();
        result.add(new Criterion("physical-case-count", Integer.toString(observedCases),
                "EXACT", Integer.toString(expectedCases), observedCases == expectedCases));
        result.add(new Criterion("case-results", Integer.toString(passedCases),
                "EXACT", Integer.toString(expectedCases), passedCases == expectedCases));
        if ("G1".equals(gate)) {
            result.add(new Criterion("ordered-results", casesPass ? "0" : "1",
                    "ZERO", "0", casesPass));
            result.add(new Criterion("command-sequence-gaps", casesPass ? "0" : "1",
                    "ZERO", "0", casesPass));
            result.add(new Criterion("public-probe-divergence", casesPass ? "0" : "1",
                    "ZERO", "0", casesPass));
        } else {
            final int expectedRecovery = matrix.recoveryObservationCount();
            final int observedRecovery = views.stream()
                    .mapToInt(ViewPair::recoveryObservationCount).sum();
            result.add(new Criterion("recovery-observations", Integer.toString(observedRecovery),
                    "EXACT", Integer.toString(expectedRecovery),
                    observedRecovery == expectedRecovery));
            result.add(new Criterion("checkpoint-convergence", casesPass ? "0" : "1",
                    "ZERO", "0", casesPass));
            result.add(new Criterion("transcript-convergence", casesPass ? "0" : "1",
                    "ZERO", "0", casesPass));
        }
        return List.copyOf(result);
    }

    private record Criterion(
            String id, String actual, String operator, String required, boolean passed) {
    }

    private static Map<String, String> bindingFields(
            final String physicalId,
            final GaCorrectnessCaseResult result,
            final Instant started,
            final Instant completed,
            final long elapsedMillis,
            final String g1Path,
            final String g1Digest,
            final String g2Path,
            final String g2Digest,
            final String rawRootDigest) {
        final Map<String, String> values = new TreeMap<>();
        values.put("completedAtUtc", completed.toString());
        values.put("elapsedMillis", Long.toString(elapsedMillis));
        values.put("g1.runManifestPath", g1Path);
        values.put("g1.runManifestSha256", g1Digest);
        values.put("g2.runManifestPath", g2Path);
        values.put("g2.runManifestSha256", g2Digest);
        values.put("matrixCase.id", result.matrixCase().id());
        values.put("physicalExecution.id", physicalId);
        values.put("profile", result.matrixCase().profile().name());
        values.put("rawEvidenceRootSha256", rawRootDigest);
        values.put("repetition", Integer.toString(result.matrixCase().repetition()));
        values.put("schema.version", BINDING_SCHEMA);
        values.put("seed", Long.toString(result.matrixCase().seed()));
        values.put("startedAtUtc", started.toString());
        return values;
    }

    private static String canonicalBinding(final Map<String, String> values) {
        final StringBuilder output = new StringBuilder();
        values.forEach((key, value) -> {
            requireText(key, "binding key");
            requireText(value, key);
            if (key.contains("\n") || key.contains("\r")
                    || value.contains("\n") || value.contains("\r") || value.contains("=")) {
                throw new IllegalArgumentException("binding contains an unsafe value");
            }
            output.append(key).append('=').append(value).append('\n');
        });
        return output.toString();
    }

    private static String canonicalBindingFields(final String text) throws IOException {
        final Map<String, String> values = parseBinding(text);
        return canonicalBinding(new TreeMap<>(values));
    }

    private static Map<String, String> parseBinding(final String text) throws IOException {
        final Map<String, String> values = new LinkedHashMap<>();
        final String[] lines = text.split("\n", -1);
        if (lines.length == 0 || !lines[lines.length - 1].isEmpty()) {
            throw new IOException("binding is not LF terminated");
        }
        String previous = null;
        for (int index = 0; index < lines.length - 1; index++) {
            final String line = lines[index];
            final int separator = line.indexOf('=');
            if (separator <= 0 || separator != line.lastIndexOf('=')) {
                throw new IOException("malformed binding line");
            }
            final String key = line.substring(0, separator);
            final String value = line.substring(separator + 1);
            if (previous != null && previous.compareTo(key) >= 0 || values.put(key, value) != null) {
                throw new IOException("binding keys are not unique/sorted");
            }
            previous = key;
        }
        return values;
    }

    private static String configurationIdentity(
            final GaCorrectnessMatrix matrix,
            final String gate,
            final String gateVersion,
            final GaCorrectnessCanonicalContext context) {
        final Map<String, String> values = new TreeMap<>();
        values.put("candidate.tag", context.candidate().tag());
        values.put("gate.id", gate);
        values.put("gate.version", gateVersion);
        values.put("matrix.commandCount", Integer.toString(matrix.commandCount()));
        values.put("matrix.snapshotPrefixes", matrix.snapshotPrefixes().toString());
        values.put("matrix.version", matrix.version());
        values.put("matrix.walSegmentSizeBytes", Integer.toString(matrix.walSegmentSizeBytes()));
        values.put("matrix.profiles", matrix.profiles().stream()
                .map(Enum::name).sorted().toList().toString());
        values.put("matrix.repetitions", Integer.toString(matrix.repetitions()));
        values.put("matrix.seeds", matrix.seeds().stream().sorted().toList().toString());
        values.put("workload.versions", matrix.profiles().stream()
                .map(GaCorrectnessCanonicalEvidence::workloadVersion)
                .distinct().sorted().collect(java.util.stream.Collectors.joining(",")));
        return QualificationIdentity.digest(values);
    }

    private static int recoveryObservationCount(final GaCorrectnessCaseResult result) {
        return (int) result.observations().stream()
                .filter(observation -> !observation.live()).count();
    }

    private static String workloadVersion(final QualificationProfile profile) {
        return profile == QualificationProfile.MEMORY_STEADY_STATE_V1
                ? QualificationWorkloadV1.MEMORY_STEADY_STATE_VERSION
                : QualificationWorkloadV1.VERSION;
    }

    private static String relative(final Path root, final Path path) throws IOException {
        final Path normalizedRoot = root.toAbsolutePath().normalize();
        final Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(normalizedRoot) || normalized.equals(normalizedRoot)) {
            throw new IOException("canonical evidence path escaped campaign root");
        }
        final String value = normalizedRoot.relativize(normalized).toString().replace('\\', '/');
        if (value.isBlank() || value.contains("//") || value.contains("../")
                || value.startsWith("../") || value.startsWith("/")) {
            throw new IOException("canonical evidence path is not relative");
        }
        return value;
    }

    private static Path resolveContained(final Path root, final String relative) throws IOException {
        if (relative == null || relative.isBlank() || relative.startsWith("/")
                || relative.startsWith("\\") || relative.contains("\\")) {
            throw new IOException("invalid canonical relative path");
        }
        final Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            throw new IOException("canonical path escaped manifest root");
        }
        return resolved;
    }

    private record ArtifactReference(long size, String sha256) {
    }

    private static Map<String, ArtifactReference> readInventory(final Path path) throws IOException {
        final byte[] bytes = Files.readAllBytes(path);
        final String text = new String(bytes, StandardCharsets.US_ASCII);
        if (!java.util.Arrays.equals(bytes, text.getBytes(StandardCharsets.US_ASCII))
                || text.indexOf('\r') >= 0 || !text.endsWith("\n")) {
            throw new IOException("canonical inventory is not ASCII/LF");
        }
        final Map<String, ArtifactReference> entries = new TreeMap<>();
        final String[] lines = text.split("\n", -1);
        String previous = null;
        for (int index = 0; index < lines.length - 1; index++) {
            final String line = lines[index];
            if (line.length() < 68 || line.charAt(64) != ' ' || line.charAt(65) != ' ') {
                throw new IOException("malformed canonical inventory line");
            }
            final String digest = line.substring(0, 64);
            final String relative = line.substring(66);
            if (!digest.matches("[0-9a-f]{64}") || relative.isBlank()
                    || relative.startsWith("/") || relative.startsWith("\\")
                    || relative.contains("\\") || relative.contains("//")) {
                throw new IOException("invalid canonical inventory entry");
            }
            for (String segment : relative.split("/", -1)) {
                if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                    throw new IOException("invalid canonical inventory path");
                }
            }
            if (previous != null && previous.compareTo(relative) >= 0) {
                throw new IOException("canonical inventory is not sorted");
            }
            final Path artifact = resolveContained(path.getParent(), relative);
            if (!Files.isRegularFile(artifact, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(artifact)) {
                throw new IOException("canonical inventory references non-regular artifact");
            }
            final long size = Files.size(artifact);
            if (entries.put(relative, new ArtifactReference(size, digest)) != null) {
                throw new IOException("duplicate canonical inventory entry");
            }
            previous = relative;
        }
        return Map.copyOf(entries);
    }

    private static String sha256(final byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (final java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JDK", exception);
        }
    }

    private static void requireUuid(final String value, final String field) {
        if (value == null || !UUID_PATTERN.matcher(value).matches()
                || !UUID.fromString(value).toString().equals(value)) {
            throw new IllegalArgumentException(field + " must be a lowercase UUID");
        }
    }

    private static void requireDigest(final String value, final String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be lowercase SHA-256");
        }
    }

    private static String requireText(final String value, final String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static void requireRelativeBindingPath(final String value) throws IOException {
        if (value == null || value.isBlank() || value.startsWith("/")
                || value.startsWith("\\") || value.contains("\\") || value.contains("//")) {
            throw new IOException("invalid physical-run binding path");
        }
        for (String segment : value.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IOException("invalid physical-run binding path");
            }
        }
    }

    private static Instant canonicalInstant(final String value) throws IOException {
        try {
            if (value == null || !value.endsWith("Z")
                    || !Instant.parse(value).toString().equals(value)) {
                throw new IllegalArgumentException("not canonical UTC instant");
            }
            return Instant.parse(value);
        } catch (final RuntimeException exception) {
            throw new IOException("invalid physical-run binding timestamp", exception);
        }
    }
}
