package br.redhot.dragonet;

import br.redhot.dragonet.config.Config;
import br.redhot.dragonet.config.ConfigException;
import br.redhot.dragonet.config.ConfigReader;
import br.redhot.dragonet.config.Session;
import br.redhot.dragonet.engine.CreateDirectoryOperation;
import br.redhot.dragonet.engine.CreateFileOperation;
import br.redhot.dragonet.engine.Operation;
import br.redhot.dragonet.engine.OperationResult;
import br.redhot.dragonet.env.Environment;
import br.redhot.dragonet.trash.TrashedItem;
import br.redhot.dragonet.trash.TrashStore;
import br.redhot.dragonet.tui.Tui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Cli {

    public static final String PROGRAM_NAME = "dragon";
    public static final String APPLICATION_NAME = "dragonet";
    public static final String APPLICATION_VERSION = "0.5a";

    private static final DateTimeFormatter TRASH_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final List<String> arguments;
    private final Logger logger;
    private final Path explicitConfigFile;

    private Cli(List<String> arguments, Logger logger, Path explicitConfigFile) {
        this.arguments = Objects.requireNonNull(arguments, "arguments");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.explicitConfigFile = explicitConfigFile;
    }

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
        List<String> rest = arguments.subList(1, arguments.size());

        switch (first) {
            case "--version":
                return printVersion();
            case "--help":
            case "-h":
                return printHelp();
            case "--mkdir":
                return runHeadlessCreate("--mkdir", rest);
            case "--touch":
                return runHeadlessCreate("--touch", rest);
            case "--rm":
                return runHeadlessTrash(rest);
            case "--trash-list":
                return runTrashList();
            case "--restore":
                return runRestore(rest);
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

    private ExitCode runHeadlessCreate(String flag, List<String> paths) {
        if (paths.isEmpty()) {
            logger.fail(flag + " requires at least one path");
            return ExitCode.EX_USAGE;
        }
        Environment environment;
        try {
            environment = Environment.detect();
        } catch (IllegalStateException exception) {
            logger.fail("environment error: " + exception.getMessage());
            return ExitCode.EX_OP_FAILED;
        }
        Path homeDirectory = environment.homeDirectory();
        int failureCount = 0;
        for (String rawPath : paths) {
            Path target = Path.of(rawPath);
            Operation operation = flag.equals("--mkdir")
                    ? new CreateDirectoryOperation(target, homeDirectory)
                    : new CreateFileOperation(target, homeDirectory);
            OperationResult result = operation.execute();
            if (result.success()) {
                logger.ok(result.message());
            } else {
                logger.fail(result.message());
                failureCount = failureCount + 1;
            }
        }
        return summarize(failureCount, paths.size());
    }

    private ExitCode runHeadlessTrash(List<String> paths) {
        if (paths.isEmpty()) {
            logger.fail("--rm requires at least one path");
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
        TrashStore trashStore = new TrashStore(
                environment.trashPath(), environment.homeDirectory());
        int failureCount = 0;
        for (String rawPath : paths) {
            OperationResult result = trashStore.moveToTrash(Path.of(rawPath));
            if (result.success()) {
                logger.ok(result.message());
            } else {
                logger.fail(result.message());
                failureCount = failureCount + 1;
            }
        }
        return summarize(failureCount, paths.size());
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
        TrashStore trashStore = new TrashStore(
                environment.trashPath(), environment.homeDirectory());
        List<TrashedItem> items = trashStore.listItems();
        if (items.isEmpty()) {
            System.out.println("(trash is empty)");
            return ExitCode.EX_NOTHING;
        }
        System.out.printf("%-32s  %-19s  %s%n", "UUID", "Deleted", "Original Path");
        for (TrashedItem item : items) {
            String deleted = item.deletionDate()
                    .atZone(ZoneId.systemDefault())
                    .format(TRASH_DATE_FORMAT);
            System.out.printf("%-32s  %-19s  %s%n",
                    item.uuid(), deleted, item.originalPath());
        }
        return ExitCode.EX_OK;
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
        TrashStore trashStore = new TrashStore(
                environment.trashPath(), environment.homeDirectory());
        OperationResult result = trashStore.restore(rest.get(0));
        if (result.success()) {
            logger.ok(result.message());
            return ExitCode.EX_OK;
        }
        logger.fail(result.message());
        if (result.targetExists()) {
            return ExitCode.EX_PRECOND;
        }
        return ExitCode.EX_OP_FAILED;
    }

    private ExitCode summarize(int failureCount, int total) {
        if (failureCount == 0) {
            return ExitCode.EX_OK;
        }
        if (failureCount == total) {
            return ExitCode.EX_OP_FAILED;
        }
        return ExitCode.EX_PARTIAL;
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
        System.out.println("  --mkdir <path>...   create directories (headless)");
        System.out.println("  --touch <path>...   create empty files (headless)");
        System.out.println("  --rm <path>...      move to trash (headless)");
        System.out.println("  --trash-list        list trashed items");
        System.out.println("  --restore <uuid>    restore a trashed item by uuid");
        System.out.println();
        System.out.println("Run " + PROGRAM_NAME
                + " with no arguments to open the TUI in the current directory.");
        return ExitCode.EX_OK;
    }
}