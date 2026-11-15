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
 * <p>Global modifiers ({@code --debug}, {@code --dry-run},
 * {@code --json}, {@code --yes}, {@code --config}) are parsed first;
 * the first non-modifier argument selects the subcommand. Subcommand
 * logic lives in {@link HeadlessCommands}; the TUI is dispatched to
 * {@link Tui}.
 *
 * <p>{@link #run(String[])} is the testable entry point: it returns an
 * {@link ExitCode} and never calls {@link System#exit}.
 */
public final class Cli {

    /** CLI command name, as typed by the user. */
    public static final String PROGRAM_NAME = "dragon";

    /** Application name, as printed by {@code --version}. */
    public static final String APPLICATION_NAME = "dragonet";

    /** Application version. */
    public static final String APPLICATION_VERSION = "1.0a";

    private final List<String> arguments;
    private final Logger logger;
    private final Path explicitConfigFile;
    private final boolean dryRun;
    private final boolean json;
    private final boolean yes;

    private Cli(List<String> arguments, Logger logger, Path explicitConfigFile,
                 boolean dryRun, boolean json, boolean yes) {
        this.arguments = Objects.requireNonNull(arguments, "arguments");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.explicitConfigFile = explicitConfigFile;
        this.dryRun = dryRun;
        this.json = json;
        this.yes = yes;
    }

    public static ExitCode run(String[] rawArguments) {
        Objects.requireNonNull(rawArguments, "rawArguments");

        boolean debugEnabled = false;
        boolean dryRun = false;
        boolean json = false;
        boolean yes = false;
        Path explicitConfigFile = null;
        List<String> remaining = new ArrayList<>();

        for (int index = 0; index < rawArguments.length; index = index + 1) {
            String argument = rawArguments[index];
            switch (argument) {
                case "--debug" -> debugEnabled = true;
                case "--dry-run" -> dryRun = true;
                case "--json" -> json = true;
                case "--yes" -> yes = true;
                case "--config" -> {
                    if (index + 1 >= rawArguments.length) {
                        Logger.forCli(debugEnabled).fail("--config requires a path argument");
                        return ExitCode.EX_USAGE;
                    }
                    index = index + 1;
                    explicitConfigFile = Path.of(rawArguments[index]);
                }
                default -> remaining.add(argument);
            }
        }

        Logger logger = Logger.forCli(debugEnabled);
        return new Cli(remaining, logger, explicitConfigFile, dryRun, json, yes).dispatch();
    }

    private ExitCode dispatch() {
        if (arguments.isEmpty()) {
            return openTui(Path.of(System.getProperty("user.dir")));
        }

        String first = arguments.get(0);
        List<String> rest = arguments.subList(1, arguments.size());

        switch (first) {
            case "--version":
                return printVersion();
            case "--help":
            case "-h":
                return printHelp();
            case "--dump-config":
                return HeadlessCommands.dumpConfig();
            case "--list":
                return runList(rest);
            case "--ls-json":
                return runLsJson(rest);
            case "--cp":
                return runCopy(rest);
            case "--mv":
                return runMove(rest);
            case "--rm-permanent":
                return runRmPermanent(rest);
            case "--mkdir":
                return runCreate(rest, true);
            case "--touch":
                return runCreate(rest, false);
            case "--rm":
                return runTrash(rest);
            case "--trash-list":
                return runTrashList();
            case "--restore":
                return runRestore(rest);
            case "--selftest":
                return runSelftest();
            default:
                break;
        }

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

    // ---- subcommand helpers ----

    private ExitCode runList(List<String> rest) {
        Path path = rest.isEmpty()
                ? Path.of(System.getProperty("user.dir"))
                : Path.of(rest.get(0));
        return HeadlessCommands.list(path, json, logger);
    }

    private ExitCode runLsJson(List<String> rest) {
        Path path = rest.isEmpty()
                ? Path.of(System.getProperty("user.dir"))
                : Path.of(rest.get(0));
        return HeadlessCommands.lsJson(path, logger);
    }

    private ExitCode runCopy(List<String> rest) {
        return runCopyOrMove("--cp", false, rest);
    }

    private ExitCode runMove(List<String> rest) {
        return runCopyOrMove("--mv", true, rest);
    }

    private ExitCode runCopyOrMove(String flag, boolean isMove, List<String> rest) {
        if (rest.size() < 2) {
            logger.fail(flag + " requires <source>... <target>");
            return ExitCode.EX_USAGE;
        }
        Path target = Path.of(rest.get(rest.size() - 1));
        List<String> sourceStrings = rest.subList(0, rest.size() - 1);
        if (sourceStrings.size() > 1 && !Files.isDirectory(target)) {
            logger.fail(flag + ": multiple sources require a directory target: " + target);
            return ExitCode.EX_USAGE;
        }
        Environment environment;
        try {
            environment = Environment.detect();
            environment.init();
        } catch (IllegalStateException | IOException exception) {
            logger.fail("environment error: " + exception.getMessage());
            return ExitCode.EX_OP_FAILED;
        }
        int failures = 0;
        for (String sourceString : sourceStrings) {
            ExitCode code = HeadlessCommands.copyOrMoveOne(flag.substring(2),
                    isMove, Path.of(sourceString), target, environment, dryRun, logger);
            if (code != ExitCode.EX_OK) {
                failures = failures + 1;
            }
        }
        if (failures == 0) {
            return ExitCode.EX_OK;
        }
        if (failures == sourceStrings.size()) {
            return ExitCode.EX_OP_FAILED;
        }
        return ExitCode.EX_PARTIAL;
    }

    private ExitCode runRmPermanent(List<String> rest) {
        List<Path> paths = new ArrayList<>();
        for (String raw : rest) {
            paths.add(Path.of(raw));
        }
        Environment environment;
        try {
            environment = Environment.detect();
            environment.init();
        } catch (IllegalStateException | IOException exception) {
            logger.fail("environment error: " + exception.getMessage());
            return ExitCode.EX_OP_FAILED;
        }
        return HeadlessCommands.rmPermanent(paths, yes, dryRun, environment, logger);
    }

    private ExitCode runCreate(List<String> rest, boolean mkdir) {
        List<Path> paths = new ArrayList<>();
        for (String raw : rest) {
            paths.add(Path.of(raw));
        }
        Environment environment;
        try {
            environment = Environment.detect();
        } catch (IllegalStateException exception) {
            logger.fail("environment error: " + exception.getMessage());
            return ExitCode.EX_OP_FAILED;
        }
        return mkdir
                ? HeadlessCommands.mkdir(paths, environment, logger)
                : HeadlessCommands.touch(paths, environment, logger);
    }

    private ExitCode runTrash(List<String> rest) {
        List<Path> paths = new ArrayList<>();
        for (String raw : rest) {
            paths.add(Path.of(raw));
        }
        Environment environment;
        try {
            environment = Environment.detect();
            environment.init();
        } catch (IllegalStateException | IOException exception) {
            logger.fail("environment error: " + exception.getMessage());
            return ExitCode.EX_OP_FAILED;
        }
        return HeadlessCommands.trash(paths, environment, logger);
    }

    private ExitCode runTrashList() {
        Environment environment;
        try {
            environment = Environment.detect();
            environment.init();
        } catch (IllegalStateException | IOException exception) {
            logger.fail("environment error: " + exception.getMessage());
            return ExitCode.EX_OP_FAILED;
        }
        return HeadlessCommands.trashList(environment);
    }

    private ExitCode runRestore(List<String> rest) {
        if (rest.isEmpty()) {
            logger.fail("--restore requires a uuid");
            return ExitCode.EX_USAGE;
        }
        Environment environment;
        try {
            environment = Environment.detect();
            environment.init();
        } catch (IllegalStateException | IOException exception) {
            logger.fail("environment error: " + exception.getMessage());
            return ExitCode.EX_OP_FAILED;
        }
        return HeadlessCommands.restore(rest.get(0), environment, logger);
    }

    private ExitCode runSelftest() {
        Environment environment;
        try {
            environment = Environment.detect();
        } catch (IllegalStateException exception) {
            logger.fail("environment error: " + exception.getMessage());
            return ExitCode.EX_OP_FAILED;
        }
        return HeadlessCommands.selftest(environment, logger);
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
                + " [--debug] [--dry-run] [--json] [--yes] [--config <path>] <command>");
        System.out.println();
        System.out.println(APPLICATION_NAME + " — TUI file manager for the Redhot suite.");
        System.out.println();
        System.out.println("Interactive:");
        System.out.println("  " + PROGRAM_NAME + " [<path>]          open the TUI");
        System.out.println();
        System.out.println("Headless:");
        System.out.println("  --list [<path>]           list directory (table)");
        System.out.println("  --ls-json [<path>]        list directory (JSON)");
        System.out.println("  --cp <src>... <dst>       copy");
        System.out.println("  --mv <src>... <dst>       move");
        System.out.println("  --rm <path>...            move to trash");
        System.out.println("  --rm-permanent <path>...  delete permanently (needs --yes)");
        System.out.println("  --restore <uuid>          restore from trash");
        System.out.println("  --trash-list              list trashed items");
        System.out.println("  --mkdir <path>...         create directories");
        System.out.println("  --touch <path>...         create empty files");
        System.out.println();
        System.out.println("Meta:");
        System.out.println("  --version                 print version and exit");
        System.out.println("  --help, -h                print this help and exit");
        System.out.println("  --dump-config             print default configuration");
        System.out.println("  --selftest                check runtime environment");
        System.out.println();
        System.out.println("Global modifiers:");
        System.out.println("  --debug                   enable debug logging");
        System.out.println("  --dry-run                 plan without executing");
        System.out.println("  --json                    JSON output where applicable");
        System.out.println("  --yes                     bypass confirmation for --rm-permanent");
        System.out.println("  --config <path>           use the given config file");
        return ExitCode.EX_OK;
    }
}
