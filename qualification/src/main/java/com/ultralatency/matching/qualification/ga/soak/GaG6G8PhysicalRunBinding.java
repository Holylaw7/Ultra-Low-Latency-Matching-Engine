package com.ultralatency.matching.qualification.ga.soak;

import com.ultralatency.matching.qualification.QualificationArtifactHasher;
import com.ultralatency.matching.qualification.QualificationEvidencePublication;
import com.ultralatency.matching.qualification.ga.GaEvidenceCodec;
import com.ultralatency.matching.qualification.ga.GaEvidenceStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Qualification-only binding between one physical soak execution and its two gate views.
 *
 * <p>The global GA schemas intentionally remain unchanged: this small strict payload proves
 * that the distinct G6 and G8 manifests refer to one physical execution and one inventory.</p>
 */
public final class GaG6G8PhysicalRunBinding {

    /** Task-specific binding schema, not a replacement global GA schema. */
    public static final String SCHEMA = "ga-g6-g8-physical-run-binding-v1";
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern GIT_SHA_PATTERN = Pattern.compile("[0-9a-f]{40}");
    private static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    private GaG6G8PhysicalRunBinding() {
    }

    /** Immutable binding fields captured at the physical execution boundary. */
    public record Fields(
            String physicalExecutionId,
            GaSoakMatrix.Stage stage,
            String g6RunId,
            String g6ManifestPath,
            String g6ManifestSha256,
            String g8RunId,
            String g8ManifestPath,
            String g8ManifestSha256,
            String controllerGitSha,
            String candidateTag,
            String candidateTagObjectSha,
            String candidateProductionSha,
            String candidateApplicationJarSha256,
            String candidateProductionTreeSha256,
            String configurationIdentitySha256,
            String inventorySha256) {

        public Fields {
            requireUuid(physicalExecutionId, "physicalExecutionId");
            Objects.requireNonNull(stage, "stage");
            requireUuid(g6RunId, "g6RunId");
            requireUuid(g8RunId, "g8RunId");
            if (g6RunId.equals(g8RunId)) {
                throw new IllegalArgumentException("G6 and G8 run IDs must differ");
            }
            requireRelative(g6ManifestPath, "g6ManifestPath");
            requireRelative(g8ManifestPath, "g8ManifestPath");
            requireSha256(g6ManifestSha256, "g6ManifestSha256");
            requireSha256(g8ManifestSha256, "g8ManifestSha256");
            requireGit(controllerGitSha, "controllerGitSha");
            requireText(candidateTag, "candidateTag");
            requireGit(candidateTagObjectSha, "candidateTagObjectSha");
            requireGit(candidateProductionSha, "candidateProductionSha");
            requireSha256(candidateApplicationJarSha256, "candidateApplicationJarSha256");
            requireSha256(candidateProductionTreeSha256, "candidateProductionTreeSha256");
            requireSha256(configurationIdentitySha256, "configurationIdentitySha256");
            requireSha256(inventorySha256, "inventorySha256");
        }
    }

    /** Result of immutable binding publication. */
    public record Published(Path path, String sha256, Fields fields) {
        public Published {
            Objects.requireNonNull(path, "path");
            requireSha256(sha256, "sha256");
            Objects.requireNonNull(fields, "fields");
        }
    }

    /** Publishes one canonical binding and its adjacent SHA-256 sidecar exactly once. */
    public static Published publish(final Path target, final Fields fields) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(fields, "fields");
        final String text = canonical(fields);
        QualificationEvidencePublication.text(target, text);
        final Path sidecar = target.resolveSibling(target.getFileName() + ".sha256");
        publishSidecar(sidecar, target);
        return new Published(target.toAbsolutePath().normalize(), digest(text), fields);
    }

    /** Reads, validates and returns one immutable binding document. */
    public static Fields read(final Path target) throws IOException {
        Objects.requireNonNull(target, "target");
        final byte[] bytes = Files.readAllBytes(target);
        final String text = new String(bytes, StandardCharsets.US_ASCII);
        if (!java.util.Arrays.equals(bytes, text.getBytes(StandardCharsets.US_ASCII))) {
            throw new IOException("binding is not canonical ASCII");
        }
        final Map<String, String> values = parse(text);
        final Fields fields = fields(values);
        if (!canonical(fields).equals(text)) {
            throw new IOException("binding is not sorted/canonical");
        }
        return fields;
    }

    /** Verifies the binding and its adjacent sidecar without changing either artifact. */
    public static Fields verify(final Path target) throws IOException {
        final Fields fields = read(target);
        final Path sidecar = target.resolveSibling(target.getFileName() + ".sha256");
        if (!Files.isRegularFile(sidecar)) {
            throw new IOException("binding sidecar is missing");
        }
        final String expected = digest(Files.readString(target, StandardCharsets.US_ASCII));
        final String sidecarText = Files.readString(sidecar, StandardCharsets.US_ASCII);
        final String expectedLine = expected + "  " + target.getFileName() + "\n";
        if (!expectedLine.equals(sidecarText)) {
            throw new IOException("binding sidecar digest mismatch");
        }
        final Path root = target.toAbsolutePath().normalize().getParent();
        final Path g6Manifest = resolveOwned(root, fields.g6ManifestPath(), "G6 manifest");
        final Path g8Manifest = resolveOwned(root, fields.g8ManifestPath(), "G8 manifest");
        if (g6Manifest.equals(g8Manifest)) {
            throw new IOException("G6 and G8 manifest paths must differ");
        }
        final Map<String, String> g6 = verifyManifest(root, g6Manifest, fields.g6ManifestSha256(),
                "G6", fields);
        final Map<String, String> g8 = verifyManifest(root, g8Manifest, fields.g8ManifestSha256(),
                "G8", fields);
        verifySharedManifestFields(g6, g8);
        final String inventoryPath = required(g6, "artifact.inventory.path");
        if (!inventoryPath.equals(required(g8, "artifact.inventory.path"))
                || !fields.inventorySha256().equals(required(g6, "artifact.inventory.sha256"))
                || !fields.inventorySha256().equals(required(g8, "artifact.inventory.sha256"))) {
            throw new IOException("G6/G8 inventory binding mismatch");
        }
        final Path inventory = resolveOwned(root, inventoryPath, "inventory");
        final String inventoryDigest = QualificationArtifactHasher.sha256(inventory);
        if (!fields.inventorySha256().equals(inventoryDigest)) {
            throw new IOException("inventory digest does not match binding");
        }
        verifyArtifactSidecar(inventory, inventoryDigest);
        final Map<String, String> inventoryEntries = readInventory(inventory);
        verifyManifestArtifacts(root, g6, inventoryEntries);
        verifyManifestArtifacts(root, g8, inventoryEntries);
        verifyPhysicalRunIdentity(root, g6, g8, fields);
        return fields;
    }

    private static Map<String, String> verifyManifest(
            final Path root,
            final Path manifest,
            final String expectedDigest,
            final String gate,
            final Fields binding) throws IOException {
        if (!expectedDigest.equals(QualificationArtifactHasher.sha256(manifest))) {
            throw new IOException(gate + " manifest digest does not match binding");
        }
        verifyArtifactSidecar(manifest, expectedDigest);
        final Map<String, String> values = GaEvidenceStore.read(manifest, GaEvidenceCodec.Schema.RUN);
        if (!gate.equals(values.get("gate.id"))
                || !bindingController(values, binding)
                || !bindingCandidate(values, binding)
                || !binding.configurationIdentitySha256().equals(
                values.get("configuration.identitySha256"))
                || !GaSoakMatrix.APPROVED_PROFILE.equals(values.get("run.profile"))
                || !Long.toString(GaSoakMatrix.APPROVED_SEED).equals(values.get("run.seed"))) {
            throw new IOException(gate + " manifest identity mismatch");
        }
        if (!stageVersionMatches(binding.stage(), gate, values.get("gate.version"))) {
            throw new IOException(gate + " manifest stage/version mismatch");
        }
        if (!bindingRunId(values, gate, binding)) {
            throw new IOException(gate + " manifest run identity mismatch");
        }
        return values;
    }

    private static void verifySharedManifestFields(
            final Map<String, String> g6,
            final Map<String, String> g8) throws IOException {
        for (String key : List.of(
                "candidate.applicationJarSha256", "candidate.productionSha",
                "candidate.productionTreeSha256", "candidate.tag", "candidate.tagObjectSha",
                "comparability.identitySha256", "configuration.identitySha256",
                "evidence.startedAtUtc", "evidence.completedAtUtc", "artifact.inventory.path",
                "artifact.inventory.sha256", "artifact.inventory.size", "run.commandCount")) {
            if (!Objects.equals(g6.get(key), g8.get(key))) {
                throw new IOException("G6/G8 manifest shared field mismatch: " + key);
            }
        }
        parseNonNegative(required(g6, "run.commandCount"), "G6 command count");
        parseNonNegative(required(g8, "run.commandCount"), "G8 command count");
    }

    private static void verifyManifestArtifacts(
            final Path root,
            final Map<String, String> manifest,
            final Map<String, String> inventory) throws IOException {
        final Map<String, String> declared = new TreeMap<>();
        int index = 1;
        while (true) {
            final String prefix = String.format("artifact.%04d", index);
            final String path = manifest.get(prefix + ".path");
            if (path == null) {
                break;
            }
            final String digest = manifest.get(prefix + ".sha256");
            final String size = manifest.get(prefix + ".size");
            if (digest == null || size == null || declared.put(path, digest) != null) {
                throw new IOException("manifest artifact declaration is incomplete");
            }
            final Path artifact = resolveOwned(root, path, "manifest artifact");
            verifyArtifactSidecar(artifact, digest);
            if (!digest.equals(QualificationArtifactHasher.sha256(artifact))
                    || !Long.toString(Files.size(artifact)).equals(size)) {
                throw new IOException("manifest artifact digest/size mismatch: " + path);
            }
            index++;
        }
        if (!declared.equals(inventory)) {
            throw new IOException("manifest artifact inventory mismatch");
        }
    }

    private static void verifyPhysicalRunIdentity(
            final Path root,
            final Map<String, String> g6,
            final Map<String, String> g8,
            final Fields binding) throws IOException {
        final String g6Samples = requiredArtifact(g6, "resource-samples-v1.csv");
        final String g8Samples = requiredArtifact(g8, "resource-samples-v1.csv");
        if (!g6Samples.equals(g8Samples)) {
            throw new IOException("G6/G8 resource sample binding mismatch");
        }
        final Path samples = resolveOwned(root, g6Samples, "resource samples");
        final String text = Files.readString(samples, StandardCharsets.US_ASCII);
        if (!text.startsWith("physicalExecutionId,stage,sequence,monotonicNanos,")
                || !text.endsWith("\n") || text.indexOf('\r') >= 0) {
            throw new IOException("resource samples are not canonical physical evidence");
        }
        final String[] lines = text.split("\n", -1);
        long previousSequence = -1L;
        long previousTimestamp = -1L;
        boolean observed = false;
        for (int index = 1; index < lines.length - 1; index++) {
            final String[] fields = lines[index].split(",", -1);
            if (fields.length < 4 || !binding.physicalExecutionId().equals(fields[0])
                    || !binding.stage().name().equals(fields[1])) {
                throw new IOException("resource sample physical/stage identity mismatch");
            }
            final long sequence = parseNonNegative(fields[2], "resource sample sequence");
            final long timestamp = parseNonNegative(fields[3], "resource sample timestamp");
            if (sequence <= previousSequence || timestamp < previousTimestamp) {
                throw new IOException("resource sample ordering mismatch");
            }
            previousSequence = sequence;
            previousTimestamp = timestamp;
            observed = true;
        }
        if (!observed) {
            throw new IOException("resource samples are empty");
        }
    }

    private static String requiredArtifact(
            final Map<String, String> manifest,
            final String basename) throws IOException {
        String found = null;
        int index = 1;
        while (true) {
            final String path = manifest.get(String.format("artifact.%04d.path", index));
            if (path == null) {
                break;
            }
            if (path.equals(basename) || path.endsWith("/" + basename)) {
                if (found != null) {
                    throw new IOException("duplicate resource artifact");
                }
                found = path;
            }
            index++;
        }
        if (found == null) {
            throw new IOException("required resource artifact is missing: " + basename);
        }
        return found;
    }

    private static long parseNonNegative(final String value, final String field)
            throws IOException {
        try {
            final long parsed = Long.parseLong(value);
            if (parsed < 0L) {
                throw new IOException(field + " must be non-negative");
            }
            return parsed;
        } catch (final NumberFormatException exception) {
            throw new IOException(field + " is not an integer", exception);
        }
    }

    private static Map<String, String> readInventory(final Path inventory) throws IOException {
        final String text = Files.readString(inventory, StandardCharsets.US_ASCII);
        if (!text.endsWith("\n") || text.indexOf('\r') >= 0) {
            throw new IOException("inventory is not canonical LF text");
        }
        final TreeMap<String, String> result = new TreeMap<>();
        final String[] lines = text.split("\n", -1);
        for (int index = 0; index < lines.length - 1; index++) {
            final String line = lines[index];
            if (line.length() < 67 || line.charAt(64) != ' ' || line.charAt(65) != ' ') {
                throw new IOException("malformed inventory line");
            }
            final String digest = line.substring(0, 64);
            final String path = line.substring(66);
            if (!SHA256_PATTERN.matcher(digest).matches() || !relativePath(path)
                    || result.put(path, digest) != null) {
                throw new IOException("invalid inventory entry");
            }
        }
        final StringBuilder canonical = new StringBuilder();
        result.forEach((path, digest) -> canonical.append(digest).append("  ")
                .append(path).append('\n'));
        if (!canonical.toString().equals(text) || result.isEmpty()) {
            throw new IOException("inventory is not sorted/canonical");
        }
        return Map.copyOf(result);
    }

    private static void verifyArtifactSidecar(
            final Path artifact,
            final String expectedDigest) throws IOException {
        final Path sidecar = artifact.resolveSibling(artifact.getFileName() + ".sha256");
        final Map<String, String> values = GaEvidenceStore.readArtifactSidecar(sidecar);
        if (values.size() != 1 || !expectedDigest.equals(values.get(artifact.getFileName().toString()))) {
            throw new IOException("artifact sidecar digest mismatch: " + artifact);
        }
    }

    private static Path resolveOwned(
            final Path root,
            final String relative,
            final String description) throws IOException {
        if (!relativePath(relative)) {
            throw new IOException(description + " path is not relative POSIX text");
        }
        final Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root) || !Files.isRegularFile(resolved, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(description + " is missing or outside binding root");
        }
        return resolved;
    }

    private static boolean relativePath(final String value) {
        if (value == null || value.isBlank() || value.contains("\\") || value.startsWith("/")
                || value.contains("//")) {
            return false;
        }
        for (String segment : value.split("/", -1)) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                return false;
            }
        }
        return true;
    }

    private static boolean bindingController(
            final Map<String, String> values,
            final Fields binding) {
        return binding.controllerGitSha().equals(values.get("controller.gitSha"));
    }

    private static boolean bindingCandidate(
            final Map<String, String> values,
            final Fields binding) {
        return binding.candidateTag().equals(values.get("candidate.tag"))
                && binding.candidateTagObjectSha().equals(values.get("candidate.tagObjectSha"))
                && binding.candidateProductionSha().equals(values.get("candidate.productionSha"))
                && binding.candidateApplicationJarSha256().equals(
                values.get("candidate.applicationJarSha256"))
                && binding.candidateProductionTreeSha256().equals(
                values.get("candidate.productionTreeSha256"));
    }

    private static boolean bindingRunId(
            final Map<String, String> values,
            final String gate,
            final Fields binding) {
        return ("G6".equals(gate) ? binding.g6RunId() : binding.g8RunId())
                .equals(values.get("run.id"));
    }

    private static boolean stageVersionMatches(
            final GaSoakMatrix.Stage stage,
            final String gate,
            final String version) {
        if (version == null) {
            return false;
        }
        if (stage == GaSoakMatrix.Stage.QUICK) {
            return ("G6".equals(gate) && GaSoakEvidencePublisher.G6_QUICK_VERSION.equals(version))
                    || ("G8".equals(gate)
                    && GaSoakEvidencePublisher.G8_QUICK_VERSION.equals(version));
        }
        final String token = stage == GaSoakMatrix.Stage.STAGE_A ? "stage-a" : "stage-b";
        return version.contains(token);
    }

    private static String required(final Map<String, String> values, final String key)
            throws IOException {
        final String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IOException("manifest field is missing: " + key);
        }
        return value;
    }

    private static String canonical(final Fields fields) {
        final Map<String, String> values = new TreeMap<>();
        values.put("candidate.applicationJarSha256", fields.candidateApplicationJarSha256());
        values.put("candidate.productionSha", fields.candidateProductionSha());
        values.put("candidate.productionTreeSha256", fields.candidateProductionTreeSha256());
        values.put("candidate.tag", fields.candidateTag());
        values.put("candidate.tagObjectSha", fields.candidateTagObjectSha());
        values.put("configuration.identitySha256", fields.configurationIdentitySha256());
        values.put("controller.gitSha", fields.controllerGitSha());
        values.put("g6.manifestPath", fields.g6ManifestPath());
        values.put("g6.manifestSha256", fields.g6ManifestSha256());
        values.put("g6.runId", fields.g6RunId());
        values.put("g8.manifestPath", fields.g8ManifestPath());
        values.put("g8.manifestSha256", fields.g8ManifestSha256());
        values.put("g8.runId", fields.g8RunId());
        values.put("inventory.sha256", fields.inventorySha256());
        values.put("physicalExecution.id", fields.physicalExecutionId());
        values.put("schema.version", SCHEMA);
        values.put("stage", fields.stage().name());
        final StringBuilder output = new StringBuilder();
        values.forEach((key, value) -> output.append(key).append('=').append(value).append('\n'));
        return output.toString();
    }

    private static Map<String, String> parse(final String text) throws IOException {
        if (text.isEmpty() || !text.endsWith("\n") || text.indexOf('\r') >= 0
                || text.indexOf('=') < 0) {
            throw new IOException("binding is not one LF-terminated document");
        }
        final Map<String, String> values = new LinkedHashMap<>();
        String previous = null;
        final String[] lines = text.split("\n", -1);
        for (int index = 0; index < lines.length - 1; index++) {
            final String line = lines[index];
            final int separator = line.indexOf('=');
            if (separator <= 0 || separator != line.lastIndexOf('=')) {
                throw new IOException("malformed binding line");
            }
            final String key = line.substring(0, separator);
            final String value = line.substring(separator + 1);
            if (previous != null && previous.compareTo(key) >= 0
                    || values.put(key, value) != null) {
                throw new IOException("binding fields are not unique/sorted");
            }
            previous = key;
        }
        if (values.size() != 17) {
            throw new IOException("binding field count is not exact");
        }
        return Map.copyOf(values);
    }

    private static Fields fields(final Map<String, String> values) throws IOException {
        if (!SCHEMA.equals(values.get("schema.version"))) {
            throw new IOException("unexpected binding schema");
        }
        final String stage = values.get("stage");
        final GaSoakMatrix.Stage parsedStage;
        try {
            parsedStage = GaSoakMatrix.Stage.valueOf(require(values, "stage"));
        } catch (final IllegalArgumentException exception) {
            throw new IOException("invalid binding stage", exception);
        }
        try {
            return new Fields(
                    values.get("physicalExecution.id"), parsedStage, values.get("g6.runId"),
                    values.get("g6.manifestPath"), values.get("g6.manifestSha256"),
                    values.get("g8.runId"), values.get("g8.manifestPath"),
                    values.get("g8.manifestSha256"), values.get("controller.gitSha"),
                    values.get("candidate.tag"), values.get("candidate.tagObjectSha"),
                    values.get("candidate.productionSha"),
                    values.get("candidate.applicationJarSha256"),
                    values.get("candidate.productionTreeSha256"),
                    values.get("configuration.identitySha256"), values.get("inventory.sha256"));
        } catch (final IllegalArgumentException exception) {
            throw new IOException("invalid binding fields", exception);
        }
    }

    private static String require(final Map<String, String> values, final String key)
            throws IOException {
        final String value = values.get(key);
        if (value == null) {
            throw new IOException("binding field is missing: " + key);
        }
        return value;
    }

    private static void publishSidecar(final Path sidecar, final Path artifact) throws IOException {
        final String digest = QualificationArtifactHasher.sha256(artifact);
        QualificationEvidencePublication.text(sidecar,
                digest + "  " + artifact.getFileName() + "\n");
    }

    private static String digest(final String text) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            text.getBytes(StandardCharsets.US_ASCII)));
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JDK", exception);
        }
    }

    private static void requireUuid(final String value, final String name) {
        requireText(value, name);
        if (!UUID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a lowercase UUID");
        }
    }

    private static void requireGit(final String value, final String name) {
        requireText(value, name);
        if (!GIT_SHA_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a lowercase Git SHA-1");
        }
    }

    private static void requireSha256(final String value, final String name) {
        requireText(value, name);
        if (!SHA256_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be lowercase SHA-256");
        }
    }

    private static void requireRelative(final String value, final String name) {
        requireText(value, name);
        if (value.startsWith("/") || value.startsWith("\\") || value.contains("\\")
                || value.contains("../") || value.equals("..") || value.contains("\n")
                || value.contains("\r")) {
            throw new IllegalArgumentException(name + " must be a relative POSIX path");
        }
    }

    private static void requireText(final String value, final String name) {
        if (value == null || value.isBlank() || value.contains("\n") || value.contains("\r")
                || value.contains("=")) {
            throw new IllegalArgumentException(name + " must be non-empty safe text");
        }
    }
}
