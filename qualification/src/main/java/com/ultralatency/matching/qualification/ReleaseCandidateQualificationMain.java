package com.ultralatency.matching.qualification;

import com.ultralatency.matching.MatchingEngineApplication;
import com.ultralatency.matching.app.ReleaseCandidateRuntime;
import com.ultralatency.matching.app.RuntimeConfiguration;
import com.ultralatency.matching.app.RuntimeConfigurationLoader;
import com.ultralatency.matching.app.RuntimeExitCode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Qualification-only entrypoint for the packaged Phase 10 runtime boundary. */
public final class ReleaseCandidateQualificationMain {

    private ReleaseCandidateQualificationMain() {
    }

    /** Runs the bounded child-process control protocol used by the qualification harness. */
    public static void main(final String[] arguments) {
        if (arguments != null && arguments.length == 3 && "child".equals(arguments[0])
                && "--config".equals(arguments[1])) {
            runChild(Path.of(arguments[2]), null, false);
            return;
        }
        if (arguments != null && arguments.length == 5 && "child".equals(arguments[0])
                && "--config".equals(arguments[1]) && "--evidence".equals(arguments[3])) {
            runChild(Path.of(arguments[2]), Path.of(arguments[4]), false);
            return;
        }
        if (arguments != null && arguments.length == 6 && "child".equals(arguments[0])
                && "--config".equals(arguments[1]) && "--evidence".equals(arguments[3])
                && "--allocation-sampling".equals(arguments[5])) {
            runChild(Path.of(arguments[2]), Path.of(arguments[4]), true);
            return;
        }
        if (arguments != null && arguments.length == 3 && "lifecycle".equals(arguments[0])
                && "--output".equals(arguments[1])) {
            runLifecycle(Path.of(arguments[2]));
            return;
        }
        if (arguments != null && arguments.length == 9 && "full".equals(arguments[0])
                && "--artifact".equals(arguments[1]) && "--output".equals(arguments[3])
                && "--git-sha".equals(arguments[5]) && "--baseline-tag".equals(arguments[7])) {
            runFull(Path.of(arguments[2]), Path.of(arguments[4]), arguments[6], arguments[8]);
            return;
        }
        if (arguments != null && arguments.length == 9 && "characterize".equals(arguments[0])
                && "--artifact".equals(arguments[1]) && "--output".equals(arguments[3])
                && "--git-sha".equals(arguments[5]) && "--baseline-tag".equals(arguments[7])) {
            runCharacterization(
                    Path.of(arguments[2]), Path.of(arguments[4]), arguments[6], arguments[8]);
            return;
        }
        if (arguments != null && arguments.length == 7 && "campaign".equals(arguments[0])
                && "--manifest".equals(arguments[1]) && "--manifest".equals(arguments[3])
                && "--output".equals(arguments[5])) {
            runCampaign(Path.of(arguments[2]), Path.of(arguments[4]), Path.of(arguments[6]));
            return;
        }
        {
            System.err.println("usage: child --config <path>");
            System.err.println("       lifecycle --output <directory>");
            System.err.println("       full --artifact <jar> --output <dir>"
                    + " --git-sha <sha> --baseline-tag <tag>");
            System.err.println("       characterize --artifact <jar> --output <dir>"
                    + " --git-sha <sha> --baseline-tag <tag>");
            System.err.println("       campaign --manifest <a> --manifest <b>"
                    + " --output <dir>");
            System.exit(64);
            return;
        }
    }

    private static void runChild(
            final Path configurationPath,
            final Path evidenceDirectory,
            final boolean allocationSampling) {
        QualificationJfrRecording jfr = null;
        QualificationResourceSampler sampler = null;
        final ReleaseCandidateRuntime runtime;
        try {
            if (evidenceDirectory != null) {
                Files.createDirectories(evidenceDirectory);
                jfr = allocationSampling
                        ? QualificationJfrRecording.startCharacterization(
                                evidenceDirectory.resolve("qualification.jfr"))
                        : QualificationJfrRecording.start(
                                evidenceDirectory.resolve("qualification.jfr"));
                sampler = new QualificationResourceSampler(
                        java.time.Duration.ofSeconds(5),
                        QualificationFullConfiguration.FULL_MINIMUM_POST_GC_SAMPLES);
            }
            final RuntimeConfiguration configuration = RuntimeConfigurationLoader.load(
                    configurationPath);
            runtime = MatchingEngineApplication.createRuntime(configuration);
            runtime.start();
            runtime.publishReady();
        } catch (final Throwable failure) {
            closeEvidence(evidenceDirectory, sampler, jfr, failure);
            System.err.println("CHILD_FAILURE " + failure.getClass().getName()
                    + " " + String.valueOf(failure.getMessage()));
            System.exit(1);
            return;
        }

        final PrintWriter output = new PrintWriter(
                System.out, true, StandardCharsets.UTF_8);
        output.println("READY " + runtime.configuration().protocolPort()
                + " " + runtime.configuration().managementPort());
        try (BufferedReader input = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String command;
            while ((command = input.readLine()) != null) {
                if ("SHUTDOWN".equals(command)) {
                    runtime.shutdown();
                    closeEvidence(evidenceDirectory, sampler, jfr, null);
                    output.println("STOPPED " + runtime.status().state() + " "
                            + RuntimeExitCode.forFailure(runtime.status().failureCode()).code());
                    return;
                }
                output.println("IGNORED " + command);
            }
            runtime.shutdown();
            closeEvidence(evidenceDirectory, sampler, jfr, null);
        } catch (final IOException | RuntimeException failure) {
            try {
                runtime.shutdown();
            } catch (final RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            closeEvidence(evidenceDirectory, sampler, jfr, failure);
            System.err.println("CHILD_FAILURE " + failure.getClass().getName()
                    + " " + String.valueOf(failure.getMessage()));
            System.exit(RuntimeExitCode.forFailure(runtime.status().failureCode()).code());
        }
    }

    private static void closeEvidence(
            final Path evidenceDirectory,
            final QualificationResourceSampler sampler,
            final QualificationJfrRecording jfr,
            final Throwable primaryFailure) {
        try {
            if (sampler != null) {
                sampler.close();
                QualificationResourceEvidenceWriter.write(
                        evidenceDirectory.resolve("resource-evidence.csv"), sampler.evidence());
            }
            if (jfr != null) {
                jfr.close();
            }
        } catch (final IOException evidenceFailure) {
            if (primaryFailure != null) {
                primaryFailure.addSuppressed(evidenceFailure);
            } else {
                System.err.println("CHILD_EVIDENCE_FAILURE " + evidenceFailure.getMessage());
            }
        }
    }

    private static void runLifecycle(final Path outputDirectory) {
        try {
            final Path artifact = packagedArtifact();
            final ReleaseCandidateLifecycleResult result =
                    new ReleaseCandidateLifecycleRunner().run(
                            ReleaseCandidateLifecycleConfiguration.full(
                                    artifact, outputDirectory));
            System.out.println("LIFECYCLE " + (result.success() ? "PASS" : "FAIL")
                    + " " + result.cycles().size() + " " + result.summarySha256());
            if (!result.success()) {
                System.exit(1);
            }
        } catch (final Exception failure) {
            System.err.println("LIFECYCLE_FAILURE " + failure.getClass().getName()
                    + " " + String.valueOf(failure.getMessage()));
            System.exit(1);
        }
    }

    private static void runFull(
            final Path artifact,
            final Path outputDirectory,
            final String gitSha,
            final String baselineTag) {
        try {
            final ReleaseCandidateAssembledFullRun run =
                    new ReleaseCandidateAssembledFullRunner().run(
                            artifact, outputDirectory, gitSha, baselineTag);
            System.out.println("FULL " + (run.fullCriteriaPassed() ? "PASS" : "FAIL")
                    + " " + run.manifestPath() + " " + run.manifestSha256());
            if (!run.fullCriteriaPassed()) {
                System.exit(1);
            }
        } catch (final Exception failure) {
            System.err.println("FULL_FAILURE " + failure.getClass().getName()
                    + " " + String.valueOf(failure.getMessage()));
            System.exit(1);
        }
    }

    private static void runCharacterization(
            final Path artifact,
            final Path outputDirectory,
            final String gitSha,
            final String baselineTag) {
        try {
            final ReleaseCandidateCharacterizationResult result =
                    new ReleaseCandidateCharacterizationRunner().run(
                            ReleaseCandidateCharacterizationConfiguration.full(
                                    artifact, outputDirectory, gitSha, baselineTag));
            System.out.println("CHARACTERIZATION " + (result.success() ? "PASS" : "FAIL")
                    + " " + result.summaryPath() + " " + result.summarySha256());
            if (!result.success()) {
                System.exit(1);
            }
        } catch (final Exception failure) {
            System.err.println("CHARACTERIZATION_FAILURE " + failure.getClass().getName()
                    + " " + String.valueOf(failure.getMessage()));
            System.exit(1);
        }
    }

    private static void runCampaign(
            final Path firstManifest,
            final Path secondManifest,
            final Path outputDirectory) {
        try {
            final ReleaseCandidateAssembledCampaignResult result =
                    new ReleaseCandidateAssembledCampaignEvaluator().evaluate(
                            firstManifest, secondManifest, outputDirectory);
            System.out.println("CAMPAIGN PASS " + result.summaryPath()
                    + " " + result.summarySha256());
        } catch (final Exception failure) {
            System.err.println("CAMPAIGN_FAILURE " + failure.getClass().getName()
                    + " " + String.valueOf(failure.getMessage()));
            System.exit(1);
        }
    }

    private static Path packagedArtifact() throws URISyntaxException {
        final Path artifact = Path.of(ReleaseCandidateQualificationMain.class
                .getProtectionDomain().getCodeSource().getLocation().toURI())
                .toAbsolutePath().normalize();
        if (!Files.isRegularFile(artifact) || !artifact.getFileName().toString().endsWith(".jar")) {
            throw new IllegalStateException("lifecycle must run from the packaged qualification jar");
        }
        return artifact;
    }
}
