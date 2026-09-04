package com.ultralatency.matching.qualification.ga.correctness;

import com.ultralatency.matching.qualification.ga.GaCandidateVerifier;
import com.ultralatency.matching.qualification.ga.GaFrozenBoundaryVerifier;
import com.ultralatency.matching.qualification.QualificationArtifactHasher;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable identity context captured by the correctness controller before a
 * physical matrix execution starts.
 *
 * <p>The real context verifies the frozen annotated candidate.  Tests may use
 * the explicit test context, but that context is never selected for the
 * approved matrix.</p>
 */
public record GaCorrectnessCanonicalContext(
        Path repository,
        String controllerGitSha,
        GaCandidateVerifier.Verified candidate) {

    private static final Pattern GIT_SHA1 = Pattern.compile("[0-9a-f]{40}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final String DEFAULT_TAG = "v0.9.0-rc.2";
    private static final String DEFAULT_TAG_OBJECT =
            "9e2a67ada0e3b6220b730131d0bae79dc03073ed";
    private static final String DEFAULT_PRODUCTION =
            "740e8a3dea0a759c707c597778c26c41e9bb3e47";
    private static final String DEFAULT_TREE =
            "ef1d9f4cb64a9d6e331fb326ebe8f3b0abb29a53bf6045a5d4999a53e73b4bbc";
    private static final String DEFAULT_APPLICATION_JAR =
            "0b77d37985b9124ac4fd1b90d669db550efd0cf00c23af65fdc29b35071703c4";
    /** Protocol selected by the frozen RC2 formal public-path campaign. */
    public static final String APPROVED_PROTOCOL_VERSION = "v2";
    /** Bounded Protocol v2 window selected by the frozen RC2 candidate. */
    public static final int APPROVED_PROTOCOL_V2_WINDOW = 8;
    /** Durability mode selected by the frozen RC2 formal campaign. */
    public static final String APPROVED_WAL_MODE = "SYNC_EACH_APPEND";

    /** Validates immutable identity values. */
    public GaCorrectnessCanonicalContext {
        repository = Objects.requireNonNull(repository, "repository")
                .toAbsolutePath().normalize();
        if (!GIT_SHA1.matcher(Objects.requireNonNull(controllerGitSha,
                "controllerGitSha")).matches()) {
            throw new IllegalArgumentException("controllerGitSha must be full Git SHA-1");
        }
        Objects.requireNonNull(candidate, "candidate");
        requireGit(candidate.tagObjectSha(), "candidate.tagObjectSha");
        requireGit(candidate.productionSha(), "candidate.productionSha");
        requireSha256(candidate.productionTreeSha256(), "candidate.productionTreeSha256");
        requireSha256(candidate.applicationJarSha256(), "candidate.applicationJarSha256");
    }

    /** Builds a verified context from the frozen candidate and current controller checkout. */
    public static GaCorrectnessCanonicalContext fromSystem() throws IOException {
        final Path repository = Path.of(System.getProperty(
                "qualification.repository", System.getProperty("user.dir")))
                .toAbsolutePath().normalize();
        final String tag = frozenProperty("qualification.baseline", DEFAULT_TAG);
        frozenProperty("qualification.candidate.tagObjectSha", DEFAULT_TAG_OBJECT);
        frozenProperty("qualification.candidate.productionSha", DEFAULT_PRODUCTION);
        frozenProperty("qualification.candidate.productionTreeSha256", DEFAULT_TREE);
        frozenProperty("qualification.candidate.applicationJarSha256", DEFAULT_APPLICATION_JAR);
        final String configuredJar = System.getProperty("qualification.candidate.applicationJar");
        if (configuredJar != null && !configuredJar.equals("core/target/matching-engine-rc.jar")) {
            throw new IOException("qualification candidate application JAR path is frozen");
        }
        final Path jar = repository.resolve("core/target/matching-engine-rc.jar")
                .toAbsolutePath().normalize();
        final GaCandidateVerifier.Expected expected = new GaCandidateVerifier.Expected(
                tag,
                DEFAULT_TAG_OBJECT,
                DEFAULT_PRODUCTION,
                DEFAULT_TREE,
                jar);
        final GaCandidateVerifier.Verified verified = GaCandidateVerifier.verify(repository, expected);
        final String controller = gitHead(repository);
        final String configuredController = System.getProperty("qualification.git.sha");
        if (configuredController != null && !configuredController.equals(controller)) {
            throw new IOException("qualification controller SHA must match repository HEAD");
        }
        GaFrozenBoundaryVerifier.verify(repository, verified.productionSha(), controller);
        return new GaCorrectnessCanonicalContext(
                repository,
                controller,
                verified);
    }

    private static String frozenProperty(final String name, final String expected)
            throws IOException {
        final String configured = System.getProperty(name);
        if (configured != null && !configured.equals(expected)) {
            throw new IOException(name + " is frozen for the approved candidate");
        }
        return expected;
    }

    /** Returns a deterministic, explicitly test-only context. */
    static GaCorrectnessCanonicalContext test(final Path repository) {
        final String digest = "0".repeat(64);
        final GaCandidateVerifier.Verified verified = new GaCandidateVerifier.Verified(
                "v0.9.0-rc.1",
                "0".repeat(40),
                "1".repeat(40),
                digest,
                digest);
        return new GaCorrectnessCanonicalContext(
                Objects.requireNonNull(repository, "repository"),
                "2".repeat(40),
                verified);
    }

    /** Returns whether the context identifies the Human-approved RC candidate. */
    public boolean isApprovedCandidate() {
        return DEFAULT_TAG.equals(candidate.tag())
                && DEFAULT_TAG_OBJECT.equals(candidate.tagObjectSha())
                && DEFAULT_PRODUCTION.equals(candidate.productionSha())
                && DEFAULT_TREE.equals(candidate.productionTreeSha256())
                && DEFAULT_APPLICATION_JAR.equals(candidate.applicationJarSha256());
    }

    /** Returns the protocol identity bound to RC2 formal qualification. */
    public String protocolVersion() {
        return APPROVED_PROTOCOL_VERSION;
    }

    /** Returns the bounded v2 window bound to RC2 formal qualification. */
    public int protocolV2Window() {
        return APPROVED_PROTOCOL_V2_WINDOW;
    }

    /** Returns the WAL durability identity bound to RC2 formal qualification. */
    public String walMode() {
        return APPROVED_WAL_MODE;
    }

    /**
     * Returns the SHA-256 of the qualification artifact that loaded this context.
     *
     * <p>Formal executions are required to run from the packaged qualification JAR.  Unit tests
     * execute from an exploded classes directory, in which case the value is omitted unless an
     * explicit, validated property supplies the packaged artifact identity.  When both a
     * packaged location and a property are present they must agree.</p>
     */
    public String qualificationJarSha256() throws IOException {
        final String configured = System.getProperty("qualification.jarSha256");
        if (configured != null) {
            requireSha256(configured, "qualification.jarSha256");
        }
        final Path artifact = codeSourceJar();
        if (artifact == null) {
            return configured;
        }
        final String actual = QualificationArtifactHasher.sha256(artifact);
        if (configured != null && !configured.equals(actual)) {
            throw new IOException("qualification JAR SHA-256 does not match configured identity");
        }
        return actual;
    }

    /**
     * Returns the packaged qualification JAR which supplied this context.  Formal child
     * processes use this path as the second, wrapper-only class-path entry after the candidate
     * JAR.  Exploded classes are rejected instead of silently constructing an in-process path.
     */
    public Path qualificationJarPath() throws IOException {
        final Path artifact = codeSourceJar();
        if (artifact == null) {
            final String configured = System.getProperty("qualification.jar");
            if (configured == null || configured.isBlank()) {
                throw new IOException("formal qualification requires a packaged qualification JAR");
            }
            final Path configuredPath = Path.of(configured).toAbsolutePath().normalize();
            if (!java.nio.file.Files.isRegularFile(configuredPath)
                    || !configuredPath.getFileName().toString().endsWith(".jar")) {
                throw new IOException("configured qualification.jar is not a packaged JAR");
            }
            verifyQualificationJarDigest(configuredPath);
            return configuredPath;
        }
        verifyQualificationJarDigest(artifact);
        return artifact;
    }

    private void verifyQualificationJarDigest(final Path artifact) throws IOException {
        final String configured = System.getProperty("qualification.jarSha256");
        if (configured != null) {
            requireSha256(configured, "qualification.jarSha256");
        }
        final String actual = QualificationArtifactHasher.sha256(artifact);
        if (configured != null && !configured.equals(actual)) {
            throw new IOException("qualification JAR SHA-256 does not match configured identity");
        }
    }

    private static Path codeSourceJar() throws IOException {
        final java.net.URL location = GaCorrectnessCanonicalContext.class
                .getProtectionDomain().getCodeSource() == null
                ? null
                : GaCorrectnessCanonicalContext.class.getProtectionDomain().getCodeSource()
                        .getLocation();
        if (location == null) {
            return null;
        }
        final Path artifact;
        try {
            artifact = Path.of(location.toURI()).toAbsolutePath().normalize();
        } catch (final java.net.URISyntaxException exception) {
            throw new IOException("cannot resolve qualification code source", exception);
        }
        return java.nio.file.Files.isRegularFile(artifact)
                && artifact.getFileName().toString().endsWith(".jar") ? artifact : null;
    }

    private static String gitHead(final Path repository) throws IOException {
        final Process process = new ProcessBuilder(
                "git", "-C", repository.toString(), "rev-parse", "HEAD").start();
        final byte[] output;
        final byte[] error = process.getErrorStream().readAllBytes();
        try {
            output = process.getInputStream().readAllBytes();
            final int exit = process.waitFor();
            if (exit != 0) {
                throw new IOException("git rev-parse HEAD failed: "
                        + new String(error, StandardCharsets.UTF_8));
            }
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("git rev-parse HEAD interrupted", exception);
        }
        final String sha = new String(output, StandardCharsets.US_ASCII).trim();
        if (!GIT_SHA1.matcher(sha).matches()) {
            throw new IOException("git HEAD is not a full SHA-1 object ID");
        }
        return sha;
    }

    private static void requireGit(final String value, final String field) {
        if (value == null || !GIT_SHA1.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be full Git SHA-1");
        }
    }

    private static void requireSha256(final String value, final String field) {
        if (value == null || !SHA256.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be lowercase SHA-256");
        }
    }
}
