package com.ultralatency.matching.qualification.ga.correctness;

import com.ultralatency.matching.qualification.ga.GaCandidateVerifier;
import com.ultralatency.matching.qualification.ga.GaFrozenBoundaryVerifier;
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
    private static final String DEFAULT_TAG = "v0.9.0-rc.1";
    private static final String DEFAULT_TAG_OBJECT =
            "dfd38c08e80aed9035bf1c2d7c8faf8bae99c356";
    private static final String DEFAULT_PRODUCTION =
            "e2828f563ee41316c062385c0244ac1336731359";
    private static final String DEFAULT_TREE =
            "81739474c2ce269a7771885e87805a8cffcec864a46449a8f5347586cfd06651";

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
                && DEFAULT_TREE.equals(candidate.productionTreeSha256());
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
