package com.ultralatency.matching.qualification.ga.soak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests qualification-local identity and raw paced runtime evidence contracts. */
class GaEvidenceIdentityTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void onlyWindowVariesIsDeterministic() throws Exception {
        final Map<String, String> first = Map.of(
                "invocation.schema", GaQuickInvocation.VERSION,
                "protocolV2.window", "4",
                "controller.gitSha", "a".repeat(40),
                "candidate.applicationJarSha256", "b".repeat(64));
        final Map<String, String> second = Map.of(
                "invocation.schema", GaQuickInvocation.VERSION,
                "protocolV2.window", "8",
                "controller.gitSha", "a".repeat(40),
                "candidate.applicationJarSha256", "b".repeat(64));
        final Path firstPath = temporaryDirectory.resolve("first.properties");
        final Path secondPath = temporaryDirectory.resolve("second.properties");
        GaQuickInvocation.write(firstPath, first);
        GaQuickInvocation.write(secondPath, second);
        assertTrue(GaQuickInvocation.onlyWindowVaries(List.of(
                GaQuickInvocation.read(firstPath), GaQuickInvocation.read(secondPath))));
        assertNotEquals(GaQuickInvocation.identity(first), GaQuickInvocation.identity(second));

        final Map<String, String> changed = Map.of(
                "invocation.schema", GaQuickInvocation.VERSION,
                "protocolV2.window", "8",
                "controller.gitSha", "c".repeat(40),
                "candidate.applicationJarSha256", "b".repeat(64));
        assertTrue(!GaQuickInvocation.onlyWindowVaries(List.of(first, changed)));
    }

    @Test
    void pacedRuntimeEvidenceRetainsRawReleaseChronology() throws Exception {
        final Map<String, String> invocation = Map.of(
                "invocation.schema", GaQuickInvocation.VERSION,
                "protocolV2.window", "8",
                "controller.gitSha", "a".repeat(40));
        final GaPacedRuntimeEvidence.CapacityRelease release =
                new GaPacedRuntimeEvidence.CapacityRelease(1L, 1L, 100L, 120L, 125L, 130L);
        final GaPacedRuntimeEvidence evidence = new GaPacedRuntimeEvidence(
                8, 2, 2, 1, 3L, List.of(50L, 60L, 70L), 1L, List.of(release), 1_000L, 2_000L,
                "0".repeat(64), invocation);
        final Path raw = temporaryDirectory.resolve("capacity.csv");
        Files.writeString(raw, evidence.capacityCsv());
        assertTrue(Files.readString(raw).contains("1,1,100,120,125,130,5"));
        assertTrue(evidence.readerWakeCsv().contains("3,70"));
        assertEquals(5L, Long.parseLong(evidence.manifestFields()
                .get("evidence.capacity.releaseDelayP50Nanos")));
        assertEquals(1_000L, evidence.measurementDurationNanos());
        assertEquals(GaQuickInvocation.identity(invocation), evidence.invocationIdentitySha256());
    }

    @Test
    void pacedWindowParticipatesInConfigurationIdentity() {
        final GaSoakMatrix matrix = GaSoakMatrix.quick();
        assertNotEquals(matrix.configurationIdentitySha256(4),
                matrix.configurationIdentitySha256(8));
    }

    @Test
    void derivedConfigurationIdentityIsTheOnlyAllowedWindowVariation() {
        final GaSoakMatrix matrix = GaSoakMatrix.quick();
        final Map<String, String> first = invocationWithWindow(matrix, 4);
        final Map<String, String> second = invocationWithWindow(matrix, 8);
        assertTrue(GaQuickInvocation.onlyWindowVaries(List.of(first, second)));

        final Map<String, String> changed = new java.util.HashMap<>(second);
        changed.put("runtime.javaVmName", "different-vm");
        assertTrue(!GaQuickInvocation.onlyWindowVaries(List.of(first, changed)));
    }

    @Test
    void invalidCapacityChronologyIsRejectedBeforePublication() {
        assertThrows(IllegalArgumentException.class,
                () -> new GaPacedRuntimeEvidence.CapacityRelease(
                        1L, 1L, 100L, 90L, 95L, 100L));
    }

    private static Map<String, String> invocationWithWindow(
            final GaSoakMatrix matrix, final int window) {
        return Map.of(
                "invocation.schema", GaQuickInvocation.VERSION,
                "protocolV2.window", Integer.toString(window),
                "qualification.configurationIdentitySha256",
                matrix.configurationIdentitySha256(window),
                "controller.gitSha", "a".repeat(40),
                "candidate.applicationJarSha256", "b".repeat(64));
    }
}
