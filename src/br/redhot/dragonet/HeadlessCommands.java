package br.redhot.dragonet;

import br.redhot.dragonet.config.Config;
import br.redhot.dragonet.config.Session;
import br.redhot.dragonet.engine.CopyOperation;
import br.redhot.dragonet.engine.CreateDirectoryOperation;
import br.redhot.dragonet.engine.CreateFileOperation;
import br.redhot.dragonet.engine.MoveOperation;
import br.redhot.dragonet.engine.Operation;
import br.redhot.dragonet.engine.OperationResult;
import br.redhot.dragonet.engine.PermanentDeleteOperation;
import br.redhot.dragonet.env.Environment;
import br.redhot.dragonet.tui.FileEntry;
import br.redhot.dragonet.tui.FileList;
import br.redhot.dragonet.tui.MetadataFormatter;
import br.redhot.dragonet.trash.TrashedItem;
import br.redhot.dragonet.trash.TrashStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Implementations of the headless CLI subcommands.
 *
 * <p>Every method returns an {@link ExitCode} and writes to
 * {@code stdout}/{@code stderr} through the supplied {@link Logger}.
 * The {@link Cli} class does not contain any business logic beyond
 * argument parsing and dispatch.
 */
public final class HeadlessCommands {

    private static final DateTimeFormatter TRASH_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private HeadlessCommands() {
        // static utility
    }

    // ---- --list / --ls-json ----

    public static ExitCode list(Path directoryPath, boolean json, Logger logger) {
        List<FileEntry> entries;
        try {
            entries = FileList.loadChildren(directoryPath, Config.defaults());
        } catch (IOException exception) {
            logger.fail("cannot read directory: " + directoryPath);
            logger.fail(exception.getMessage());
            return ExitCode.EX_PRECOND;
        }
        if (json) {
            printEntriesAsJson(entries);
        } else {
            printEntriesAsTable(entries);
        }
        return ExitCode.EX_OK;
    }

    public static ExitCode lsJson(Path directoryPath, Logger logger) {
        return list(directoryPath, true, logger);
    }

    private static void printEntriesAsTable(List<FileEntry> entries) {
        Instant now = Instant.now();
        for (FileEntry entry : entries) {
            String mode = entry.modeString().isEmpty()
                    ? (entry.isDirectory() ? "drwxr-xr-x" : "-rw-r--r--")
                    : entry.modeString();
            String owner = entry.ownerName().isEmpty() ? "-" : entry.ownerName();
            String size = entry.isDirectory() ? "-"
                    : MetadataFormatter.formatSize(entry.sizeBytes(),
                            Config.Humanize.BINARY, false);
            String date = MetadataFormatter.formatDate(
                    entry.lastModified(), now, true, false);
            System.out.printf("%-10s  %-8s  %7s  %-12s  %s%n",
                    mode, truncate(owner, 8), size, date, displayName(entry));
        }
    }

    private static void printEntriesAsJson(List<FileEntry> entries) {
        StringBuilder builder = new StringBuilder();
        builder.append("[\n");
        for (int index = 0; index < entries.size(); index = index + 1) {
            FileEntry entry = entries.get(index);
            builder.append("  {");
            builder.append("\"name\": ").append(jsonString(entry.name())).append(", ");
            builder.append("\"type\": ").append(jsonString(typeOf(entry))).append(", ");
            builder.append("\"size\": ").append(entry.sizeBytes()).append(", ");
            builder.append("\"modified\": ")
                    .append(jsonString(isoInstant(entry.lastModified()))).append(", ");
            builder.append("\"mode\": ").append(jsonString(entry.modeString())).append(", ");
            builder.append("\"owner\": ").append(jsonString(entry.ownerName())).append(", ");
            builder.append("\"executable\": ").append(entry.isExecutable()).append(", ");
            builder.append("\"symlink\": ").append(entry.isSymlink()).append(", ");
            builder.append("\"broken\": ").append(entry.isBrokenSymlink()).append(", ");
            builder.append("\"hidden\": ").append(entry.isHidden()).append(", ");
            String target = entry.symlinkTarget() == null
                    ? "null" : jsonString(entry.symlinkTarget().toString());
            builder.append("\"target\": ").append(target);
            builder.append("}");
            if (index < entries.size() - 1) {
                builder.append(",");
            }
            builder.append("\n");
        }
        builder.append("]\n");
        System.out.print(builder);
    }

    private static String typeOf(FileEntry entry) {
        return switch (entry.type()) {
            case DIRECTORY -> "directory";
            case SYMLINK -> "symlink";
            case FILE -> "file";
            case OTHER -> "other";
        };
    }

    private static String displayName(FileEntry entry) {
        if (entry.isSymlink() && entry.symlinkTarget() != null) {
            return entry.name() + " -> " + entry.symlinkTarget();
        }
        return entry.name();
    }

    private static String isoInstant(Instant instant) {
        return instant.atZone(ZoneId.systemDefault()).toLocalDateTime().toString();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 1) + "…";
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index = index + 1) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 0x20) {
                        builder.append(String.format("\\u%04x", (int) character));
                    } else {
                        builder.append(character);
                    }
                }
            }
        }
        builder.append("\"");
        return builder.toString();
    }

    // ---- --cp / --mv ----

    public static ExitCode copy(Path source, Path target, Environment environment,
                                 boolean dryRun, Logger logger) {
        return executeMultiFile("cp", false, source, target, environment, dryRun, logger);
    }

    public static ExitCode move(Path source, Path target, Environment environment,
                                 boolean dryRun, Logger logger) {
        return executeMultiFile("mv", true, source, target, environment, dryRun, logger);
    }

    /**
     * Runs a copy or move of {@code source} to {@code target}. Handles
     * the "dst is directory" case by resolving to {@code target/src.name}.
     */
    public static ExitCode copyOrMoveOne(String verb, boolean isMove,
                                          Path source, Path target,
                                          Environment environment,
                                          boolean dryRun, Logger logger) {
        if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
            logger.fail(verb + ": source not found: " + source);
            return ExitCode.EX_PRECOND;
        }
        Path effectiveTarget = target;
        if (Files.isDirectory(target)) {
            Path name = source.getFileName();
            if (name != null) {
                effectiveTarget = target.resolve(name);
            }
        }
        if (dryRun) {
            System.out.printf("[info] would %s %s -> %s%n", verb, source, effectiveTarget);
            return ExitCode.EX_OK;
        }
        boolean targetIsFile = Files.isRegularFile(effectiveTarget)
                || Files.isSymbolicLink(effectiveTarget);
        Operation operation = isMove
                ? new MoveOperation(source, effectiveTarget,
                        environment.homeDirectory(), targetIsFile)
                : new CopyOperation(source, effectiveTarget,
                        environment.homeDirectory(), targetIsFile);
        OperationResult result = operation.execute();
        if (result.success()) {
            logger.ok(result.message());
            return ExitCode.EX_OK;
        }
        logger.fail(result.message());
        return result.targetExists() ? ExitCode.EX_PRECOND : ExitCode.EX_OP_FAILED;
    }

    private static ExitCode executeMultiFile(String verb, boolean isMove,
                                              Path source, Path target,
                                              Environment environment,
                                              boolean dryRun, Logger logger) {
        return copyOrMoveOne(verb, isMove, source, target, environment, dryRun, logger);
    }

    // ---- --rm-permanent ----

    public static ExitCode rmPermanent(List<Path> paths, boolean confirmed,
                                        boolean dryRun, Environment environment,
                                        Logger logger) {
        if (paths.isEmpty()) {
            logger.fail("--rm-permanent requires at least one path");
            return ExitCode.EX_USAGE;
        }
        if (!confirmed) {
            logger.fail("--rm-permanent requires --yes to confirm (irreversible)");
            return ExitCode.EX_USAGE;
        }
        int failures = 0;
        for (Path path : paths) {
            if (dryRun) {
                System.out.printf("[info] would delete permanently: %s%n", path);
                continue;
            }
            OperationResult result = new PermanentDeleteOperation(
                    path, environment.homeDirectory()).execute();
            if (result.success()) {
                logger.ok(result.message());
            } else {
                logger.fail(result.message());
                failures = failures + 1;
            }
        }
        return summarize(failures, paths.size());
    }

    // ---- --mkdir / --touch ----

    public static ExitCode mkdir(List<Path> paths, Environment environment, Logger logger) {
        return createMultiple("--mkdir", paths, environment, logger);
    }

    public static ExitCode touch(List<Path> paths, Environment environment, Logger logger) {
        return createMultiple("--touch", paths, environment, logger);
    }

    private static ExitCode createMultiple(String flag, List<Path> paths,
                                            Environment environment, Logger logger) {
        if (paths.isEmpty()) {
            logger.fail(flag + " requires at least one path");
            return ExitCode.EX_USAGE;
        }
        int failures = 0;
        for (Path path : paths) {
            Operation operation = flag.equals("--mkdir")
                    ? new CreateDirectoryOperation(path, environment.homeDirectory())
                    : new CreateFileOperation(path, environment.homeDirectory());
            OperationResult result = operation.execute();
            if (result.success()) {
                logger.ok(result.message());
            } else {
                logger.fail(result.message());
                failures = failures + 1;
            }
        }
        return summarize(failures, paths.size());
    }

    // ---- --rm / --trash-list / --restore ----

    public static ExitCode trash(List<Path> paths, Environment environment, Logger logger) {
        if (paths.isEmpty()) {
            logger.fail("--rm requires at least one path");
            return ExitCode.EX_USAGE;
        }
        TrashStore store = new TrashStore(environment.trashPath(), environment.homeDirectory());
        int failures = 0;
        for (Path path : paths) {
            OperationResult result = store.moveToTrash(path);
            if (result.success()) {
                logger.ok(result.message());
            } else {
                logger.fail(result.message());
                failures = failures + 1;
            }
        }
        return summarize(failures, paths.size());
    }

    public static ExitCode trashList(Environment environment) {
        TrashStore store = new TrashStore(environment.trashPath(), environment.homeDirectory());
        List<TrashedItem> items = store.listItems();
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

    public static ExitCode restore(String uuid, Environment environment, Logger logger) {
        TrashStore store = new TrashStore(environment.trashPath(), environment.homeDirectory());
        OperationResult result = store.restore(uuid);
        if (result.success()) {
            logger.ok(result.message());
            return ExitCode.EX_OK;
        }
        logger.fail(result.message());
        return result.targetExists() ? ExitCode.EX_PRECOND : ExitCode.EX_OP_FAILED;
    }

    // ---- --dump-config ----

    public static ExitCode dumpConfig() {
        Config config = Config.defaults();
        StringBuilder builder = new StringBuilder();
        builder.append("# Dragonet configuration (defaults)\n");
        builder.append("# Copy to ~/.config/redhot/dragonet/dragonet.conf and edit as needed.\n");
        builder.append('\n');
        builder.append("version=").append(config.version()).append('\n');
        builder.append("theme=").append(config.theme()).append('\n');
        builder.append("show_hidden=").append(config.showHidden()).append('\n');
        builder.append("sort=").append(config.sort().name().toLowerCase()).append('\n');
        builder.append("sort_reverse=").append(config.sortReverse()).append('\n');
        builder.append("preview=").append(config.preview() ? "on" : "off").append('\n');
        builder.append("preview_max_bytes=").append(config.previewMaxBytes()).append('\n');
        builder.append("confirm_delete=").append(config.confirmDelete()).append('\n');
        builder.append("trash_enabled=").append(config.trashEnabled()).append('\n');
        builder.append("trash_auto_trim=").append(config.trashAutoTrim()).append('\n');
        builder.append("trash_max_age_days=").append(config.trashMaxAgeDays()).append('\n');
        builder.append("trash_max_size_gb=").append(config.trashMaxSizeGigabytes()).append('\n');
        builder.append("follow_symlinks=").append(config.followSymlinks()).append('\n');
        builder.append("history_limit=").append(config.historyLimit()).append('\n');
        builder.append("undo_limit=").append(config.undoLimit()).append('\n');
        builder.append("humanize=").append(config.humanize().name().toLowerCase()).append('\n');
        builder.append("date_relative=").append(config.dateRelative()).append('\n');
        builder.append("sort_locale=").append(config.sortLocale()).append('\n');
        builder.append("watch_directory=").append(config.watchDirectory()).append('\n');
        builder.append("tree_style=").append(config.treeStyle().name().toLowerCase()).append('\n');
        builder.append("imp_mode=").append(config.impMode().name().toLowerCase()).append('\n');
        builder.append("info_level=").append(config.infoLevel()).append('\n');
        builder.append("chafa_command=").append(config.chafaCommand()).append('\n');
        builder.append("file_command=").append(config.fileCommand()).append('\n');
        builder.append("open_text_editor=").append(config.openTextEditor()).append('\n');
        builder.append("open_media_player=").append(config.openMediaPlayer()).append('\n');
        System.out.print(builder);
        return ExitCode.EX_OK;
    }

    // ---- --selftest ----

    public static ExitCode selftest(Environment environment, Logger logger) {
        int failures = 0;

        String javaVersion = System.getProperty("java.version", "unknown");
        if (majorJavaVersion(javaVersion) >= 17) {
            logger.ok("java " + javaVersion);
        } else {
            logger.fail("java " + javaVersion + " (need >= 17)");
            failures = failures + 1;
        }

        try {
            Class.forName("com.googlecode.lanterna.TerminalSize");
            logger.ok("lanterna 3.1.2");
        } catch (ClassNotFoundException exception) {
            logger.fail("lanterna not found on classpath");
            failures = failures + 1;
        }

        Path trash = environment.trashPath();
        try {
            Files.createDirectories(trash);
            if (Files.isWritable(trash)) {
                logger.ok("trash: " + trash + " (writable)");
            } else {
                logger.fail("trash: " + trash + " (not writable)");
                failures = failures + 1;
            }
        } catch (IOException exception) {
            logger.fail("trash: " + trash + " (cannot create: "
                    + exception.getMessage() + ")");
            failures = failures + 1;
        }

        if (environment.hasChafa()) {
            logger.ok("chafa: present");
        } else {
            logger.warn("chafa: not found (optional; image previews disabled)");
        }

        if (environment.hasFile()) {
            logger.ok("file: present");
        } else {
            logger.warn("file: not found (optional; MIME detection disabled)");
        }

        if (failures == 0) {
            return ExitCode.EX_OK;
        }
        return ExitCode.EX_OP_FAILED;
    }

    private static int majorJavaVersion(String javaVersion) {
        if (javaVersion == null || javaVersion.isEmpty()) {
            return 0;
        }
        String trimmed = javaVersion;
        if (trimmed.startsWith("1.")) {
            trimmed = trimmed.substring(2);
        }
        int dotIndex = trimmed.indexOf('.');
        String majorString = dotIndex > 0 ? trimmed.substring(0, dotIndex) : trimmed;
        try {
            return Integer.parseInt(majorString);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static ExitCode summarize(int failures, int total) {
        if (failures == 0) {
            return ExitCode.EX_OK;
        }
        if (failures == total) {
            return ExitCode.EX_OP_FAILED;
        }
        return ExitCode.EX_PARTIAL;
    }
}
