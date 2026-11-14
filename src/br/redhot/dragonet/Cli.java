package br.redhot.dragonet;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Argument parser and dispatcher for the {@code dragon} CLI.
 *
 * <p>V0.1.0a supports only {@code --version} and {@code --help}. Every
 * other path is a placeholder that fails with a clear message. The full
 * dispatcher (subcommands {@code --list}, {@code --cp}, {@code --rm}, ...)
 * arrives in later commits, one subcommand at a time.
 *
 * <p>{@link #run(String[])} is the testable entry point: it returns an
 * {@link ExitCode} and never calls {@link System#exit}. {@link Main}
 * is the only class that converts the returned code into a process exit.
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

    private Cli(List<String> arguments, Logger logger) {
        this.arguments = Objects.requireNonNull(arguments, "arguments");
        this.logger = Objects.requireNonNull(logger, "logger");
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
        List<String> remaining = new ArrayList<>();

        for (String argument : rawArguments) {
            if (argument.equals("--debug")) {
                debugEnabled = true;
            } else {
                remaining.add(argument);
            }
        }

        Logger logger = Logger.forCli(debugEnabled);
        return new Cli(remaining, logger).dispatch();
    }

    private ExitCode dispatch() {
        if (arguments.isEmpty()) {
            return openTui();
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
                return openTui();
        }
    }

    private ExitCode openTui() {
        logger.fail("interactive TUI is not yet implemented in this build");
        logger.info("this is V0.1.0a (foundation); the TUI arrives in a later commit");
        return ExitCode.EX_OP_FAILED;
    }

    private ExitCode printVersion() {
        System.out.println(APPLICATION_NAME + " " + APPLICATION_VERSION);
        return ExitCode.EX_OK;
    }

    private ExitCode printHelp() {
        System.out.println("Usage: " + PROGRAM_NAME + " [--debug] [options] [path]");
        System.out.println();
        System.out.println(APPLICATION_NAME
                + " — TUI file manager for the Redhot suite.");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --version    print version and exit");
        System.out.println("  --help, -h   print this help and exit");
        System.out.println("  --debug      enable debug logging to stderr");
        System.out.println();
        System.out.println("Run " + PROGRAM_NAME
                + " with no arguments to open the TUI in the current directory.");
        return ExitCode.EX_OK;
    }
}