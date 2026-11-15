package br.redhot.dragonet;

import br.redhot.dragonet.config.Config;
import br.redhot.dragonet.config.ConfigException;
import br.redhot.dragonet.config.ConfigReader;
import br.redhot.dragonet.config.Session;
import br.redhot.dragonet.env.Environment;
import br.redhot.dragonet.tui.Tui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Argument parser and dispatcher for the {@code dragon} CLI.
 *
 * <p>V0.1.0b supports {@code --version}, {@code --help}, {@code --debug}
 * and {@code --config <path>}, plus an optional path argument that
 * selects the initial directory. Subcommands ({@code --list},
 * {@code --cp}, {@code --rm}, ...) arrive in later commits.
 *
 * <p>{@link #run(String[])} is the testable entry point: it returns an
 * {@link ExitCode} and never calls {@link System#exit}. {@link Main}
 * is the only class that converts the returned code into a process
 * exit.
 */
public final class Cli {

    /** CLI command name, as typed by the user. */
    public static final String PROGRAM_NAME = "dragon";

    /** Application name, as printed by {@code --version}. */
    public static final String APPLICATION_NAME = "dragonet";

    /** Application version, per the spec test for V0.1.0a. */
    public static final String APPLICATION_VERSION = "0.1.0a";

    private final List<String> arguments;
    private final Logger logger;
    private final Path explicitConfigFile;

    private Cli(List<String> arguments, Logger logger, Path explicitConfigFile) {
        this.arguments = Objects.requireNonNull(arguments, "arguments");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.explicitConfigFile = explicitConfigFile;
    }

    /**
     * Parses arguments and runs the matching command.
     *
     * @param rawArguments arguments as received by {@code main}
     * @return the process exit code
     */
    public static ExitCode run(String[] rawArguments) {
        Objects.requireNonNull(rawArguments, "rawArguments");

        boolean debugEnabled = false;
        Path explicitConfigFile = null;
        List<String> remaining = new ArrayList<>();

        for (int index = 0; index < rawArguments.length; index = index + 1) {
            String argument = rawArguments[index];
            if (argument.equals("--debug")) {
                debugEnabled = true;
            } else if (argument.equals("--config")) {
                if (index + 1 >= rawArguments.length) {
                    Logger.forCli(debugEnabled).fail("--config requires a path argument");
                    return ExitCode.EX_USAGE;
                }
                index = index + 1;
                explicitConfigFile = Path.of(rawArguments[index]);
            } else {
                remaining.add(argument);
            }
        }

        Logger logger = Logger.forCli(debugEnabled);
        return new Cli(remaining, logger, explicitConfigFile).dispatch();
    }

    private ExitCode dispatch() {
        if (arguments.isEmpty()) {
            return openTui(Path.of(System.getProperty("user.dir")));
        }

        String first = arguments.get(0);
        switch (first) {
            case "--version":
                return printVersion();
            case "--help":
            case "-h":
                return printHelp();
            default:
                if (first.startsWith("-")) {
                    logger.fail("unknown option: " + first);
                    logger.fail("run '" + PROGRAM_NAME + " --help' for usage");
                    return ExitCode.EX_USAGE;
                }
                Path path = Path.of(first);
                if (!Files.isDirectory(path)) {
                    logger.fail("not a directory: " + path);
                    return ExitCode.EX_PRECOND;
                }
                return openTui(path);
        }
    }

    private ExitCode openTui(Path initialDirectory) {
        Environment environment;
        try {
            environment = Environment.detect();
            environment.init();
        } catch (IllegalStateException | IOException exception) {
            logger.fail("could not initialize environment: " + exception.getMessage());
            return ExitCode.EX_OP_FAILED;
        }

        Config config;
        try {
            config = new ConfigReader(environment, logger).read(explicitConfigFile);
        } catch (ConfigException exception) {
            logger.fail(exception.getMessage());
            return ExitCode.EX_CONFIG_ERROR;
        }

        Session session = Session.load(environment.statePath("session.conf"));

        return new Tui(config, session, environment, logger).run(initialDirectory);
    }

    private ExitCode printVersion() {
        System.out.println(APPLICATION_NAME + " " + APPLICATION_VERSION);
        return ExitCode.EX_OK;
    }

    private ExitCode printHelp() {
        System.out.println("Usage: " + PROGRAM_NAME
                + " [--debug] [--config <path>] [options] [path]");
        System.out.println();
        System.out.println(APPLICATION_NAME + " — TUI file manager for the Redhot suite.");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --version           print version and exit");
        System.out.println("  --help, -h          print this help and exit");
        System.out.println("  --debug             enable debug logging to stderr");
        System.out.println("  --config <path>     use the given config file (overrides ~/.config)");
        System.out.println();
        System.out.println("Run " + PROGRAM_NAME
                + " with no arguments to open the TUI in the current directory.");
        return ExitCode.EX_OK;
    }
}