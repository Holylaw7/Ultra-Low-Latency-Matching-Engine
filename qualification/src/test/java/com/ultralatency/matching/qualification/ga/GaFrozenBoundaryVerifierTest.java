package com.ultralatency.matching.qualification.ga;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies the qualification-only frozen production path check. */
class GaFrozenBoundaryVerifierTest {

    private static final String APPROVED_CORE_TEST =
            "src/test/java/com/ultralatency/matching/pipeline/MatchingEnginePipelineFailureTest.java";
    private static final String OTHER_CORE_TEST =
            "src/test/java/com/ultralatency/matching/pipeline/OtherPipelineTest.java";
    private static final String PRODUCTION_FILE = "src/main/java/example/Production.java";

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

    @Test
    void allowsOnlyTheExactApprovedCoreTestException(@TempDir final Path temporaryDirectory)
            throws Exception {
        final Path repository = initializeRepository(temporaryDirectory.resolve("approved"));
        final String head = gitHead(repository);
        write(repository, APPROVED_CORE_TEST, "changed\n");

        assertDoesNotThrow(() -> GaFrozenBoundaryVerifier.verify(repository, head, head));
    }

    @Test
    void rejectsOtherCoreProductionAndMultipleChanges(@TempDir final Path temporaryDirectory)
            throws Exception {
        final Path otherCoreRepository = initializeRepository(
                temporaryDirectory.resolve("other-core"));
        final String otherCoreHead = gitHead(otherCoreRepository);
        write(otherCoreRepository, OTHER_CORE_TEST, "changed\n");
        assertThrows(IOException.class,
                () -> GaFrozenBoundaryVerifier.verify(
                        otherCoreRepository, otherCoreHead, otherCoreHead));

        final Path productionRepository = initializeRepository(
                temporaryDirectory.resolve("production"));
        final String productionHead = gitHead(productionRepository);
        write(productionRepository, PRODUCTION_FILE, "changed\n");
        assertThrows(IOException.class,
                () -> GaFrozenBoundaryVerifier.verify(
                        productionRepository, productionHead, productionHead));

        final Path multipleRepository = initializeRepository(
                temporaryDirectory.resolve("multiple"));
        final String multipleHead = gitHead(multipleRepository);
        write(multipleRepository, APPROVED_CORE_TEST, "approved change\n");
        write(multipleRepository, OTHER_CORE_TEST, "unauthorized change\n");
        assertThrows(IOException.class,
                () -> GaFrozenBoundaryVerifier.verify(
                        multipleRepository, multipleHead, multipleHead));
    }

    @Test
    void qualificationOnlyChangesRemainOutsideFrozenBoundary(@TempDir final Path temporaryDirectory)
            throws Exception {
        final Path repository = initializeRepository(temporaryDirectory.resolve("qualification"));
        final String head = gitHead(repository);
        write(repository, "qualification/src/test/java/example/QualificationTest.java",
                "changed\n");

        assertDoesNotThrow(() -> GaFrozenBoundaryVerifier.verify(repository, head, head));
    }

    @Test
    void similarlyNamedPathDoesNotInheritTheException(@TempDir final Path temporaryDirectory)
            throws Exception {
        final Path repository = initializeRepository(temporaryDirectory.resolve("neighbor"));
        final String head = gitHead(repository);
        write(repository,
                "src/test/java/com/ultralatency/matching/pipeline/MatchingEnginePipelineFailureTestHelper.java",
                "changed\n");

        assertThrows(IOException.class,
                () -> GaFrozenBoundaryVerifier.verify(repository, head, head));
    }

    private static Path initializeRepository(final Path repository) throws Exception {
        Files.createDirectories(repository);
        write(repository, APPROVED_CORE_TEST, "baseline\n");
        write(repository, OTHER_CORE_TEST, "baseline\n");
        write(repository, PRODUCTION_FILE, "baseline\n");
        runGit(repository, "init", "--quiet");
        runGit(repository, "config", "user.name", "TASK-052 Test");
        runGit(repository, "config", "user.email", "task-052@example.invalid");
        runGit(repository, "add", ".");
        runGit(repository, "commit", "--quiet", "-m", "baseline");
        return repository;
    }

    private static void write(
            final Path repository, final String relativePath, final String content)
            throws IOException {
        final Path path = repository.resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private static String runGit(final Path repository, final String... arguments)
            throws IOException, InterruptedException {
        final String[] command = new String[arguments.length + 3];
        command[0] = "git";
        command[1] = "-C";
        command[2] = repository.toString();
        System.arraycopy(arguments, 0, command, 3, arguments.length);
        final Process process = new ProcessBuilder(command).start();
        final byte[] output = process.getInputStream().readAllBytes();
        final byte[] error = process.getErrorStream().readAllBytes();
        final int exit = process.waitFor();
        if (exit != 0) {
            throw new IOException("git command failed (" + exit + "): "
                    + new String(error, StandardCharsets.UTF_8));
        }
        return new String(output, StandardCharsets.UTF_8).trim();
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
