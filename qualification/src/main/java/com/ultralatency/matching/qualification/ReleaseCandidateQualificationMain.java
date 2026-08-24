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
            runChild(Path.of(arguments[2]));
            return;
        }
        if (arguments != null && arguments.length == 3 && "lifecycle".equals(arguments[0])
                && "--output".equals(arguments[1])) {
            runLifecycle(Path.of(arguments[2]));
            return;
        }
        {
            System.err.println("usage: child --config <path>");
            System.err.println("       lifecycle --output <directory>");
            System.exit(64);
            return;
        }
    }

    private static void runChild(final Path configurationPath) {
        final ReleaseCandidateRuntime runtime;
        try {
            final RuntimeConfiguration configuration = RuntimeConfigurationLoader.load(
                    configurationPath);
            runtime = MatchingEngineApplication.createRuntime(configuration);
            runtime.start();
            runtime.publishReady();
        } catch (final Throwable failure) {
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
                    output.println("STOPPED " + runtime.status().state() + " "
                            + RuntimeExitCode.forFailure(runtime.status().failureCode()).code());
                    return;
                }
                output.println("IGNORED " + command);
            }
            runtime.shutdown();
        } catch (final IOException | RuntimeException failure) {
            try {
                runtime.shutdown();
            } catch (final RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            System.err.println("CHILD_FAILURE " + failure.getClass().getName()
                    + " " + String.valueOf(failure.getMessage()));
            System.exit(RuntimeExitCode.forFailure(runtime.status().failureCode()).code());
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
