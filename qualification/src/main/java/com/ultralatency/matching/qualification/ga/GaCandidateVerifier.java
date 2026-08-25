package com.ultralatency.matching.qualification.ga;

import com.ultralatency.matching.qualification.QualificationArtifactHasher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/** Verifies the immutable Git candidate and packaged application artifact. */
public final class GaCandidateVerifier {

    private static final Pattern GIT_SHA1 = Pattern.compile("[0-9a-f]{40}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    private GaCandidateVerifier() {
    }

    /** Expected immutable candidate identity. */
    public record Expected(
            String tag,
            String tagObjectSha,
            String productionSha,
            String productionTreeSha256,
            Path applicationJar) {
        /** Validates the expected identity at construction time. */
        public Expected {
            if (tag == null || tag.isBlank() || tag.indexOf('\n') >= 0
                    || tag.indexOf('\r') >= 0) {
                throw new IllegalArgumentException("tag must be a non-empty single line");
            }
            requireGitSha1(tagObjectSha, "tagObjectSha");
            requireGitSha1(productionSha, "productionSha");
            requireSha256(productionTreeSha256, "productionTreeSha256");
            Objects.requireNonNull(applicationJar, "applicationJar");
        }
    }

    /** Verified candidate identity and derived artifact digests. */
    public record Verified(
            String tag,
            String tagObjectSha,
            String productionSha,
            String productionTreeSha256,
            String applicationJarSha256) {
    }

    /** Verifies the annotated tag, peeled commit, repository object format, tree and JAR. */
    public static Verified verify(final Path repository, final Expected expected)
            throws IOException {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(expected, "expected");
        final Path repo = repository.toAbsolutePath().normalize();
        if (!Files.isDirectory(repo)) {
            throw new IOException("repository is not a directory: " + repo);
        }
        final String objectFormat = git(repo, "rev-parse", "--show-object-format");
        if (!"sha1".equals(objectFormat)) {
            throw new IOException("unsupported Git object format: " + objectFormat);
        }
        final String ref = "refs/tags/" + expected.tag();
        if (!"tag".equals(git(repo, "cat-file", "-t", ref))) {
            throw new IOException("candidate tag is not an annotated tag: " + expected.tag());
        }
        final String tagObject = git(repo, "rev-parse", ref);
        if (!expected.tagObjectSha().equals(tagObject)) {
            throw new IOException("candidate tag object mismatch");
        }
        final String production = git(repo, "rev-parse", ref + "^{}");
        if (!expected.productionSha().equals(production)) {
            throw new IOException("candidate production SHA mismatch");
        }
        final String tree = digestGitArchive(repo, expected.productionSha());
        if (!expected.productionTreeSha256().equals(tree)) {
            throw new IOException("candidate production tree digest mismatch");
        }
        final String jar = QualificationArtifactHasher.sha256(expected.applicationJar());
        return new Verified(expected.tag(), tagObject, production, tree, jar);
    }

    private static String git(final Path repository, final String... arguments) throws IOException {
        final String[] command = new String[arguments.length + 3];
        command[0] = "git";
        command[1] = "-C";
        command[2] = repository.toString();
        System.arraycopy(arguments, 0, command, 3, arguments.length);
        final Process process;
        try {
            process = new ProcessBuilder(command).start();
            final byte[] output = process.getInputStream().readAllBytes();
            final byte[] error = process.getErrorStream().readAllBytes();
            final int exit = process.waitFor();
            if (exit != 0) {
                throw new IOException("git command failed (" + exit + "): "
                        + new String(error, java.nio.charset.StandardCharsets.UTF_8));
            }
            return new String(output, java.nio.charset.StandardCharsets.UTF_8).trim();
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("git command interrupted", exception);
        }
    }

    private static String digestGitArchive(final Path repository, final String productionSha)
            throws IOException {
        final Process process;
        try {
            process = new ProcessBuilder(
                    "git", "-C", repository.toString(), "archive", "--format=tar", productionSha)
                    .start();
            final MessageDigest digest = messageDigest();
            try (InputStream input = process.getInputStream()) {
                final byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) {
                        digest.update(buffer, 0, read);
                    }
                }
            }
            final byte[] error = process.getErrorStream().readAllBytes();
            final int exit = process.waitFor();
            if (exit != 0) {
                throw new IOException("git archive failed (" + exit + "): "
                        + new String(error, java.nio.charset.StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("git archive interrupted", exception);
        }
    }

    private static MessageDigest messageDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JDK", exception);
        }
    }

    private static void requireGitSha1(final String value, final String field) {
        if (value == null || !GIT_SHA1.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be full lowercase Git SHA-1");
        }
    }

    private static void requireSha256(final String value, final String field) {
        if (value == null || !SHA256.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be lowercase SHA-256");
        }
    }
}
