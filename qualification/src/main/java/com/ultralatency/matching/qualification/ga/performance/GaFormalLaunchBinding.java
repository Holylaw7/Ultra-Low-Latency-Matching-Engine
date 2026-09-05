package com.ultralatency.matching.qualification.ga.performance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Role-labelled paths for the packaged formal G4 child launch.
 *
 * <p>All three values are {@link Path}s, so a positional call can otherwise compile while
 * assigning the qualification artifact or output directory to the wrong role.  This small
 * qualification-owned value object makes the roles explicit at the launch boundary.</p>
 */
public record GaFormalLaunchBinding(
        Path candidateArtifact,
        Path qualificationArtifact,
        Path outputDirectory) {

    /** Validates the role-labelled launch paths before any child process is started. */
    public GaFormalLaunchBinding {
        candidateArtifact = normalizeJar(candidateArtifact, "candidateArtifact");
        qualificationArtifact = normalizeJar(qualificationArtifact, "qualificationArtifact");
        outputDirectory = normalizeOutput(outputDirectory);
        if (candidateArtifact.equals(qualificationArtifact)) {
            throw new IllegalArgumentException("candidate and qualification artifacts must differ");
        }
    }

    private static Path normalizeJar(final Path value, final String name) {
        final Path normalized = Objects.requireNonNull(value, name).toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)
                || !normalized.getFileName().toString().toLowerCase().endsWith(".jar")) {
            throw new IllegalArgumentException(name + " must be a packaged JAR: " + normalized);
        }
        return normalized;
    }

    private static Path normalizeOutput(final Path value) {
        final Path normalized = Objects.requireNonNull(value, "outputDirectory")
                .toAbsolutePath().normalize();
        if (Files.exists(normalized) && !Files.isDirectory(normalized)) {
            throw new IllegalArgumentException("outputDirectory must be a directory: " + normalized);
        }
        return normalized;
    }

    /** Creates a checked binding and converts path validation failures to launch IO failures. */
    public static GaFormalLaunchBinding checked(
            final Path candidateArtifact,
            final Path qualificationArtifact,
            final Path outputDirectory) throws IOException {
        try {
            return new GaFormalLaunchBinding(candidateArtifact, qualificationArtifact,
                    outputDirectory);
        } catch (final IllegalArgumentException failure) {
            throw new IOException("invalid formal G4 launch binding", failure);
        }
    }
}
