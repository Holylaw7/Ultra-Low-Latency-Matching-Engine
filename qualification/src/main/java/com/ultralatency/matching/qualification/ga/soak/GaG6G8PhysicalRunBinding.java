package com.ultralatency.matching.qualification.ga.soak;

import com.ultralatency.matching.qualification.QualificationArtifactHasher;
import com.ultralatency.matching.qualification.QualificationEvidencePublication;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
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
        return fields;
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
