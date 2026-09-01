package com.ultralatency.matching.qualification.ga.soak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests strict distinct G6/G8 physical-run binding publication. */
class GaG6G8PhysicalRunBindingTest {

    private static final String GIT = "0123456789abcdef0123456789abcdef01234567";
    private static final String SHA =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @TempDir
    Path temporaryDirectory;

    @Test
    void publishesAndVerifiesDistinctGateIdentities() throws Exception {
        final Path target = temporaryDirectory.resolve("binding.txt");
        final GaG6G8PhysicalRunBinding.Fields fields = fields();

        final GaG6G8PhysicalRunBinding.Published published =
                GaG6G8PhysicalRunBinding.publish(target, fields);
        final GaG6G8PhysicalRunBinding.Fields read =
                GaG6G8PhysicalRunBinding.verify(target);

        assertEquals(fields, read);
        assertEquals(published.sha256(),
                published.sha256());
        assertEquals(fields.g6RunId(), read.g6RunId());
        assertEquals(fields.g8RunId(), read.g8RunId());
        assertEquals(17, Files.readAllLines(target).size());
    }

    @Test
    void rejectsSharedRunIdAndTamperedBindingSidecar() throws Exception {
        final GaG6G8PhysicalRunBinding.Fields valid = fields();
        assertThrows(IllegalArgumentException.class, () ->
                new GaG6G8PhysicalRunBinding.Fields(
                        valid.physicalExecutionId(), valid.stage(), valid.g6RunId(),
                        valid.g6ManifestPath(), valid.g6ManifestSha256(), valid.g6RunId(),
                        valid.g8ManifestPath(), valid.g8ManifestSha256(), valid.controllerGitSha(),
                        valid.candidateTag(), valid.candidateTagObjectSha(),
                        valid.candidateProductionSha(), valid.candidateApplicationJarSha256(),
                        valid.candidateProductionTreeSha256(), valid.configurationIdentitySha256(),
                        valid.inventorySha256()));

        final Path target = temporaryDirectory.resolve("binding.txt");
        GaG6G8PhysicalRunBinding.publish(target, valid);
        Files.writeString(target.resolveSibling("binding.txt.sha256"), SHA + "  binding.txt\n");
        assertThrows(java.io.IOException.class, () -> GaG6G8PhysicalRunBinding.verify(target));
    }

    private static GaG6G8PhysicalRunBinding.Fields fields() {
        return new GaG6G8PhysicalRunBinding.Fields(
                "00000000-0000-4000-8000-000000000001",
                GaSoakMatrix.Stage.QUICK,
                "00000000-0000-4000-8000-000000000002",
                "g6-run-manifest-v1.txt", SHA,
                "00000000-0000-4000-8000-000000000003",
                "g8-run-manifest-v1.txt", SHA,
                GIT, "v0.9.0-rc.1", GIT, GIT, SHA, SHA, SHA, SHA);
    }
}
