package com.ultralatency.matching.qualification;

import com.ultralatency.matching.MatchingEngineApplication;
import com.ultralatency.matching.app.ReleaseCandidateRuntime;
import com.ultralatency.matching.app.RuntimeConfiguration;
import com.ultralatency.matching.app.RuntimeConfigurationLoader;
import com.ultralatency.matching.app.RuntimeExitCode;
import com.ultralatency.matching.qualification.ga.correctness.GaCorrectnessCampaignResult;
import com.ultralatency.matching.qualification.ga.correctness.GaCorrectnessMatrix;
import com.ultralatency.matching.qualification.ga.correctness.GaCorrectnessRunner;
import com.ultralatency.matching.qualification.ga.durability.GaDurabilityCampaignResult;
import com.ultralatency.matching.qualification.ga.durability.GaDurabilityMatrix;
import com.ultralatency.matching.qualification.ga.durability.GaDurabilityRunner;
import com.ultralatency.matching.qualification.ga.durability.GaOverloadCampaignResult;
import com.ultralatency.matching.qualification.ga.durability.GaOverloadMatrix;
import com.ultralatency.matching.qualification.ga.durability.GaOverloadRunner;
import com.ultralatency.matching.qualification.ga.capacity.GaCapacityQuickResult;
import com.ultralatency.matching.qualification.ga.capacity.GaCapacityRunner;
import com.ultralatency.matching.qualification.ga.performance.GaPerformanceQuickResult;
import com.ultralatency.matching.qualification.ga.performance.GaPerformanceRunner;
import com.ultralatency.matching.qualification.ga.soak.GaSoakQuickResult;
import com.ultralatency.matching.qualification.ga.soak.GaSoakRunner;
import com.ultralatency.matching.qualification.ga.soak.GaPacedEvidenceVerifier;
import com.ultralatency.matching.qualification.ga.soak.GaSoakMatrixSummary;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
        if (arguments != null && arguments.length == 3 && "ga-correctness".equals(arguments[0])
                && "--matrix".equals(arguments[1])) {
            runGaCorrectness(matrix(arguments[2]), Path.of("qualification-results"));
            return;
        }
        if (arguments != null && arguments.length == 5 && "ga-correctness".equals(arguments[0])
                && "--matrix".equals(arguments[1]) && "--output".equals(arguments[3])) {
            runGaCorrectness(matrix(arguments[2]), Path.of(arguments[4]));
            return;
        }
        if (arguments != null && arguments.length == 3 && "ga-durability".equals(arguments[0])
                && "--matrix".equals(arguments[1])) {
            runGaDurability(durabilityMatrix(arguments[2]), Path.of("qualification-results"));
            return;
        }
        if (arguments != null && arguments.length == 5 && "ga-durability".equals(arguments[0])
                && "--matrix".equals(arguments[1]) && "--output".equals(arguments[3])) {
            runGaDurability(durabilityMatrix(arguments[2]), Path.of(arguments[4]));
            return;
        }
        if (arguments != null && arguments.length == 3 && "ga-overload".equals(arguments[0])
                && "--matrix".equals(arguments[1])) {
            runGaOverload(overloadMatrix(arguments[2]), Path.of("qualification-results"));
            return;
        }
        if (arguments != null && arguments.length == 5 && "ga-overload".equals(arguments[0])
                && "--matrix".equals(arguments[1]) && "--output".equals(arguments[3])) {
            runGaOverload(overloadMatrix(arguments[2]), Path.of(arguments[4]));
            return;
        }
        if (arguments != null && arguments.length == 3 && "ga-performance".equals(arguments[0])
                && "--lane".equals(arguments[1]) && "quick".equals(arguments[2])) {
            runGaPerformanceQuick(Path.of("qualification-results"));
            return;
        }
        if (arguments != null && arguments.length == 5 && "ga-performance".equals(arguments[0])
                && "--lane".equals(arguments[1]) && "quick".equals(arguments[2])
                && "--output".equals(arguments[3])) {
            runGaPerformanceQuick(Path.of(arguments[4]));
            return;
        }
        if (arguments != null && arguments.length == 3 && "ga-capacity".equals(arguments[0])
                && "--lane".equals(arguments[1]) && "quick".equals(arguments[2])) {
            runGaCapacityQuick(Path.of("qualification-results"));
            return;
        }
        if (arguments != null && arguments.length == 5 && "ga-capacity".equals(arguments[0])
                && "--lane".equals(arguments[1]) && "quick".equals(arguments[2])
                && "--output".equals(arguments[3])) {
            runGaCapacityQuick(Path.of(arguments[4]));
            return;
        }
        if (arguments != null && arguments.length == 3 && "ga-soak".equals(arguments[0])
                && "--lane".equals(arguments[1]) && "quick".equals(arguments[2])) {
            runGaSoakQuick(Path.of("qualification-results"));
            return;
        }
        if (arguments != null && arguments.length == 5 && "ga-soak".equals(arguments[0])
                && "--lane".equals(arguments[1]) && "quick".equals(arguments[2])
                && "--output".equals(arguments[3])) {
            runGaSoakQuick(Path.of(arguments[4]));
            return;
        }
        if (arguments != null && arguments.length >= 4 && "matrix-summary".equals(arguments[0])
                && "--output".equals(arguments[1])) {
            runMatrixSummary(Path.of(arguments[2]),
                    List.of(Arrays.copyOfRange(arguments, 3, arguments.length)));
            return;
        }
        if (arguments != null && arguments.length == 2 && "verify-paced".equals(arguments[0])) {
            runPacedEvidenceVerification(Path.of(arguments[1]));
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
            System.err.println("       ga-correctness --matrix <ga-g1-g2-v1|ga-g1-g2-test-v1>"
                    + " [--output <dir>]");
            System.err.println("       ga-durability --matrix <ga-g3-g7-v1|ga-g3-g7-test-v1>"
                    + " [--output <dir>]");
            System.err.println("       ga-overload --matrix <ga-g7-overload-v1|ga-g7-overload-test-v1>"
                    + " [--output <dir>]");
            System.err.println("       ga-performance --lane quick [--output <dir>]");
            System.err.println("       ga-capacity --lane quick [--output <dir>]");
            System.err.println("       ga-soak --lane quick [--output <dir>]");
            System.err.println("       matrix-summary --output <file> <run-root>...");
            System.err.println("       verify-paced <run-root>");
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

    private static void runGaCorrectness(
            final GaCorrectnessMatrix matrix,
            final Path outputDirectory) {
        try {
            final GaCorrectnessCampaignResult result = new GaCorrectnessRunner().run(
                    matrix, outputDirectory);
            System.out.println("GA_CORRECTNESS " + (result.passed() ? "PASS" : "FAIL")
                    + " " + result.summaryPath() + " " + result.summarySha256());
            if (!result.passed()) {
                System.exit(1);
            }
        } catch (final Exception failure) {
            System.err.println("GA_CORRECTNESS_FAILURE " + failure.getClass().getName()
                    + " " + String.valueOf(failure.getMessage()));
            System.exit(1);
        }
    }

    private static void runGaDurability(
            final GaDurabilityMatrix matrix,
            final Path outputDirectory) {
        try {
            final GaDurabilityCampaignResult result = new GaDurabilityRunner().run(
                    matrix, outputDirectory);
            System.out.println("GA_DURABILITY " + (result.passed() ? "PASS" : "FAIL")
                    + " " + result.summaryPath());
            if (!result.passed()) {
                System.exit(1);
            }
        } catch (final Exception failure) {
            System.err.println("GA_DURABILITY_FAILURE " + failure.getClass().getName()
                    + " " + String.valueOf(failure.getMessage()));
            System.exit(1);
        }
    }

    private static void runGaOverload(
            final GaOverloadMatrix matrix,
            final Path outputDirectory) {
        try {
            final GaOverloadCampaignResult result = new GaOverloadRunner().run(
                    matrix, outputDirectory);
            System.out.println("GA_OVERLOAD " + (result.passed() ? "PASS" : "FAIL")
                    + " " + result.summaryPath());
            if (!result.passed()) {
                System.exit(1);
            }
        } catch (final Exception failure) {
            System.err.println("GA_OVERLOAD_FAILURE " + failure.getClass().getName()
                    + " " + String.valueOf(failure.getMessage()));
            System.exit(1);
        }
    }

    private static void runGaPerformanceQuick(final Path outputDirectory) {
        try {
            final GaPerformanceQuickResult result = new GaPerformanceRunner().runQuick(
                    outputDirectory);
            System.out.println("GA_PERFORMANCE QUICK "
                    + (result.evaluation().passed() ? "PASS" : "FAIL") + " "
                    + result.manifestPath() + " " + result.gateResultPath());
            if (!result.evaluation().passed()) {
                System.exit(1);
            }
        } catch (final Exception failure) {
            System.err.println("GA_PERFORMANCE_QUICK_FAILURE " + failure.getClass().getName()
                    + " " + String.valueOf(failure.getMessage()));
            System.exit(1);
        }
    }

    private static void runGaCapacityQuick(final Path outputDirectory) {
        try {
            final GaCapacityQuickResult result = new GaCapacityRunner().runQuick(outputDirectory);
            System.out.println("GA_CAPACITY QUICK "
                    + (result.evaluation().passed() ? "PASS" : "FAIL") + " "
                    + result.manifestPath() + " " + result.gateResultPath());
            if (!result.evaluation().passed()) {
                System.exit(1);
            }
        } catch (final Exception failure) {
            System.err.println("GA_CAPACITY_QUICK_FAILURE " + failure.getClass().getName()
                    + " " + String.valueOf(failure.getMessage()));
            System.exit(1);
        }
    }

    private static void runGaSoakQuick(final Path outputDirectory) {
        try {
            final int window = Integer.getInteger("qualification.paced.maxInFlight",
                    com.ultralatency.matching.network.protocol.ProtocolConstants
                            .DEFAULT_PIPELINED_MAX_IN_FLIGHT);
            final GaSoakQuickResult result = new GaSoakRunner(null, window).runQuick(outputDirectory);
            System.out.println("GA_SOAK QUICK "
                    + (result.g6Evaluation().passed() && result.g8Evaluation().passed()
                    ? "PASS" : "FAIL") + " " + result.evidenceRoot()
                    + " " + result.g6RunId() + " " + result.g8RunId());
            if (!result.g6Evaluation().passed() || !result.g8Evaluation().passed()) {
                System.exit(1);
            }
        } catch (final Exception failure) {
            System.err.println("GA_SOAK_QUICK_FAILURE " + failure.getClass().getName()
                    + " " + String.valueOf(failure.getMessage()));
            System.exit(1);
        }
    }

    private static void runMatrixSummary(final Path output, final List<String> roots) {
        try {
            final List<Path> runRoots = new ArrayList<>();
            for (String root : roots) {
                runRoots.add(Path.of(root));
            }
            final Path summary = GaSoakMatrixSummary.publish(output, runRoots);
            System.out.println("GA_SOAK_MATRIX_SUMMARY " + summary
                    + " onlyNVaried=" + GaSoakMatrixSummary.onlyWindowVaries(runRoots));
        } catch (final Exception failure) {
            System.err.println("GA_SOAK_MATRIX_SUMMARY_FAILURE " + failure.getClass().getName()
                    + " " + String.valueOf(failure.getMessage()));
            System.exit(1);
        }
    }

    private static void runPacedEvidenceVerification(final Path root) {
        try {
            final GaPacedEvidenceVerifier.Report report = GaPacedEvidenceVerifier.verify(root);
            System.out.println("GA_SOAK_EVIDENCE_VERIFIED " + report.root()
                    + " N=" + report.configuredWindow()
                    + " nominal=" + report.nominalOfferOpportunities()
                    + " offered=" + report.actualOfferedCommands()
                    + " late=" + report.missedSchedulerLate()
                    + " windowFull=" + report.missedWindowFull());
        } catch (final Exception failure) {
            System.err.println("GA_SOAK_EVIDENCE_VERIFY_FAILURE " + failure.getClass().getName()
                    + " " + String.valueOf(failure.getMessage()));
            System.exit(1);
        }
    }

    private static GaCorrectnessMatrix matrix(final String name) {
        return switch (name) {
            case GaCorrectnessMatrix.APPROVED_VERSION -> GaCorrectnessMatrix.approved();
            case "ga-g1-g2-test-v1" -> GaCorrectnessMatrix.test();
            default -> throw new IllegalArgumentException("unsupported G1/G2 matrix: " + name);
        };
    }

    private static GaDurabilityMatrix durabilityMatrix(final String name) {
        return switch (name) {
            case GaDurabilityMatrix.APPROVED_VERSION -> GaDurabilityMatrix.approved();
            case "ga-g3-g7-test-v1" -> GaDurabilityMatrix.test();
            default -> throw new IllegalArgumentException("unsupported G3 matrix: " + name);
        };
    }

    private static GaOverloadMatrix overloadMatrix(final String name) {
        return switch (name) {
            case GaOverloadMatrix.APPROVED_VERSION -> GaOverloadMatrix.approved();
            case "ga-g7-overload-test-v1" -> GaOverloadMatrix.test();
            default -> throw new IllegalArgumentException("unsupported G7 matrix: " + name);
        };
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
