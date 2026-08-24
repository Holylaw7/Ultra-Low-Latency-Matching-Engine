package com.ultralatency.matching.app;

import com.ultralatency.matching.MatchingEngineApplication;
import java.io.PrintStream;
import java.net.BindException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

/** Strict command-line boundary for the release-candidate application. */
public final class RuntimeCommandLine {

    private static final String HELP = "Usage: java -jar matching-engine-rc.jar "
            + "[--config <path>|--config=<path>] [action]\n"
            + "Actions:\n"
            + "  --validate-config        validate without starting storage or listeners\n"
            + "  --print-effective-config print canonical sanitized configuration\n"
            + "  --help                   print this help\n"
            + "  --version                print application version\n";

    private RuntimeCommandLine() {
    }

    /** Executes one parsed command and returns its stable process exit code. */
    public static int execute(
            final String[] arguments,
            final PrintStream output,
            final PrintStream error) {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(error, "error");
        try {
            final ParsedArguments parsed = parse(arguments);
            if (parsed.help()) {
                output.print(HELP);
                return RuntimeExitCode.CLEAN.code();
            }
            if (parsed.version()) {
                output.println(MatchingEngineApplication.applicationName()
                        + " " + MatchingEngineApplication.applicationVersion());
                return RuntimeExitCode.CLEAN.code();
            }
            final RuntimeConfiguration configuration = RuntimeConfigurationLoader.load(
                    parsed.configurationFile());
            return switch (parsed.action()) {
                case VALIDATE -> {
                    output.println("Configuration valid");
                    yield RuntimeExitCode.CLEAN.code();
                }
                case PRINT_EFFECTIVE -> {
                    output.print(configuration.canonicalText());
                    yield RuntimeExitCode.CLEAN.code();
                }
                case RUN -> run(configuration, error);
            };
        } catch (final IllegalArgumentException exception) {
            error.println("Configuration rejected: " + safeMessage(exception));
            return RuntimeExitCode.CONFIGURATION.code();
        }
    }

    private static int run(
            final RuntimeConfiguration configuration,
            final PrintStream error) {
        final ReleaseCandidateRuntime runtime = MatchingEngineApplication.createRuntime(
                configuration);
        try {
            runtime.start();
            if (!configuration.managementEnabled()) {
                runtime.publishReady();
            }
        } catch (final RuntimeException failure) {
            final RuntimeExitCode code = startupCode(failure);
            try {
                runtime.shutdown();
            } catch (final RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            error.println("Runtime startup failed: " + safeMessage(failure));
            return code.code();
        }

        final CountDownLatch termination = new CountDownLatch(1);
        final Thread shutdownHook = new Thread(() -> {
            try {
                runtime.shutdown();
            } finally {
                termination.countDown();
            }
        }, "matching-engine-runtime-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        try {
            termination.await();
            return RuntimeExitCode.CLEAN.code();
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            runtime.shutdown();
            return RuntimeExitCode.CLEAN.code();
        }
    }

    private static ParsedArguments parse(final String[] arguments) {
        if (arguments == null) {
            throw new IllegalArgumentException("Arguments must not be null");
        }
        Path configurationFile = null;
        Action action = Action.RUN;
        boolean help = false;
        boolean version = false;
        for (int index = 0; index < arguments.length; index++) {
            final String argument = Objects.requireNonNull(arguments[index], "argument");
            if (argument.equals("--help")) {
                if (help || version || configurationFile != null || action != Action.RUN) {
                    throw new IllegalArgumentException("--help cannot be combined with another action");
                }
                help = true;
            } else if (argument.equals("--version")) {
                if (version || help || configurationFile != null || action != Action.RUN) {
                    throw new IllegalArgumentException(
                            "--version cannot be combined with another action");
                }
                version = true;
            } else if (argument.equals("--validate-config")) {
                action = selectAction(action, Action.VALIDATE);
            } else if (argument.equals("--print-effective-config")) {
                action = selectAction(action, Action.PRINT_EFFECTIVE);
            } else if (argument.equals("--config")) {
                if (configurationFile != null || index + 1 >= arguments.length) {
                    throw new IllegalArgumentException("--config requires exactly one path");
                }
                configurationFile = pathArgument(arguments[++index]);
            } else if (argument.startsWith("--config=")) {
                if (configurationFile != null) {
                    throw new IllegalArgumentException("Duplicate --config option");
                }
                configurationFile = pathArgument(argument.substring("--config=".length()));
            } else {
                throw new IllegalArgumentException("Unknown command-line option: " + argument);
            }
        }
        if ((help || version) && (configurationFile != null || action != Action.RUN)) {
            throw new IllegalArgumentException("Help/version cannot be combined with another option");
        }
        if (help || version) {
            return new ParsedArguments(null, action, help, version);
        }
        if (configurationFile == null) {
            throw new IllegalArgumentException("--config is required");
        }
        return new ParsedArguments(configurationFile, action, false, false);
    }

    private static Action selectAction(final Action current, final Action requested) {
        if (current != Action.RUN) {
            throw new IllegalArgumentException("Only one command action may be selected");
        }
        return requested;
    }

    private static Path pathArgument(final String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Configuration path must not be blank");
        }
        return Path.of(raw.trim());
    }

    private static RuntimeExitCode startupCode(final RuntimeException failure) {
        return hasCause(failure, BindException.class)
                ? RuntimeExitCode.PROTOCOL_BIND
                : RuntimeExitCode.STARTUP_RECOVERY;
    }

    private static boolean hasCause(final Throwable failure, final Class<? extends Throwable> type) {
        Throwable current = failure;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String safeMessage(final Throwable failure) {
        final String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private enum Action {
        RUN,
        VALIDATE,
        PRINT_EFFECTIVE
    }

    private record ParsedArguments(
            Path configurationFile,
            Action action,
            boolean help,
            boolean version) {
    }
}
