package com.ultralatency.matching.qualification.ga;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Verifies that a qualification controller did not change frozen production inputs. */
public final class GaFrozenBoundaryVerifier {

    private static final Pattern GIT_SHA1 = Pattern.compile("[0-9a-f]{40}");
    private static final String TASK_052_APPROVED_CORE_TEST_EXCEPTION =
            "src/test/java/com/ultralatency/matching/pipeline/MatchingEnginePipelineFailureTest.java";
    private static final String[] FROZEN_PATHS = {
        "src/main", "src/test", "pom.xml", "core/pom.xml"
    };

    private GaFrozenBoundaryVerifier() {
    }

    /** Verifies the immutable production/POM path set between two Git commits. */
    public static void verify(
            final Path repository,
            final String productionSha,
            final String controllerSha) throws IOException {
        Objects.requireNonNull(repository, "repository");
        requireGitSha(productionSha, "productionSha");
        requireGitSha(controllerSha, "controllerSha");
        final Path repo = repository.toAbsolutePath().normalize();
        if (!Files.isDirectory(repo)) {
            throw new IOException("repository is not a directory: " + repo);
        }
        final String resolvedProduction = git(repo, "rev-parse", productionSha + "^{commit}");
        if (!productionSha.equals(resolvedProduction)) {
            throw new IOException("production SHA does not identify the expected commit");
        }
        final String resolvedController = git(repo, "rev-parse", controllerSha + "^{commit}");
        if (!controllerSha.equals(resolvedController)) {
            throw new IOException("controller SHA does not identify the expected commit");
        }
        final List<String> changed = changedFrozenPaths(repo, productionSha, controllerSha);
        if (!changed.isEmpty()) {
            throw new IOException("frozen production boundary changed: " + changed);
        }
    }

    private static List<String> changedFrozenPaths(
            final Path repository,
            final String productionSha,
            final String controllerSha) throws IOException {
        final Set<String> changed = new TreeSet<>();
        addLines(changed, gitWithFrozenPaths(repository, "diff", "--name-only",
                "--diff-filter=ACDMRTUXB", productionSha, controllerSha));
        addLines(changed, gitWithFrozenPaths(repository, "diff", "--name-only",
                "--diff-filter=ACDMRTUXB"));
        addLines(changed, gitWithFrozenPaths(repository, "diff", "--cached", "--name-only",
                "--diff-filter=ACDMRTUXB"));
        addLines(changed, gitWithFrozenPaths(repository, "ls-files", "--others",
                "--exclude-standard"));
        return List.copyOf(changed);
    }

    private static void addLines(final Set<String> target, final String output) {
        for (String line : output.split("\\R")) {
            if (!line.isBlank()) {
                final String normalized = line.replace('\\', '/');
                if (!TASK_052_APPROVED_CORE_TEST_EXCEPTION.equals(normalized)) {
                    target.add(normalized);
                }
            }
        }
    }

    private static String gitWithFrozenPaths(
            final Path repository,
            final String... prefix) throws IOException {
        final List<String> arguments = new ArrayList<>(Arrays.asList(prefix));
        arguments.add("--");
        arguments.addAll(Arrays.asList(FROZEN_PATHS));
        return git(repository, arguments.toArray(String[]::new));
    }

    private static String git(final Path repository, final String... arguments)
            throws IOException {
        final String[] command = new String[arguments.length + 3];
        command[0] = "git";
        command[1] = "-C";
        command[2] = repository.toString();
        System.arraycopy(arguments, 0, command, 3, arguments.length);
        try {
            final Process process = new ProcessBuilder(command).start();
            final byte[] output = process.getInputStream().readAllBytes();
            final byte[] error = process.getErrorStream().readAllBytes();
            final int exit = process.waitFor();
            if (exit != 0) {
                throw new IOException("git command failed (" + exit + "): "
                        + new String(error, StandardCharsets.UTF_8));
            }
            return new String(output, StandardCharsets.UTF_8).trim();
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("git command interrupted", exception);
        }
    }

    private static void requireGitSha(final String value, final String field) {
        if (value == null || !GIT_SHA1.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be full lowercase Git SHA-1");
        }
    }
}
