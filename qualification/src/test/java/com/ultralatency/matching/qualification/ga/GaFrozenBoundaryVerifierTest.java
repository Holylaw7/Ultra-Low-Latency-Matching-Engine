package com.ultralatency.matching.qualification.ga;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Verifies the qualification-only frozen production path check. */
class GaFrozenBoundaryVerifierTest {

    @Test
    void rejectsNonCanonicalGitIdentity() {
        final Path repository = Path.of("..").toAbsolutePath().normalize();
        assertThrows(IllegalArgumentException.class, () -> GaFrozenBoundaryVerifier.verify(
                repository, "not-a-git-sha", "0".repeat(40)));
    }

    @Test
    void checksTheCurrentRepositoryWithoutChangingIt() throws IOException {
        final Path repository = Path.of("..").toAbsolutePath().normalize();
        final String head = gitHead(repository);
        assertDoesNotThrow(() -> GaFrozenBoundaryVerifier.verify(repository, head, head));
        assertDoesNotThrow(() -> GaFrozenBoundaryVerifier.verify(repository,
                "e2828f563ee41316c062385c0244ac1336731359", head));
    }

    private static String gitHead(final Path repository) throws IOException {
        try {
            final Process process = new ProcessBuilder(
                    "git", "-C", repository.toString(), "rev-parse", "HEAD").start();
            final String output = new String(process.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.US_ASCII).trim();
            final int exit = process.waitFor();
            if (exit != 0) {
                throw new IOException("git rev-parse failed");
            }
            return output;
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("git rev-parse interrupted", exception);
        }
    }
}
