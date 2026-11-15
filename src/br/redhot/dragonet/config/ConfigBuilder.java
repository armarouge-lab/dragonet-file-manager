package br.redhot.dragonet.config;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Mutable builder for {@link Config}.
 *
 * <p>Starts with the documented defaults and applies every key set by
 * {@link ConfigReader}. Each setter validates its input and throws
 * {@link ConfigException} on bad values; the reader propagates that to
 * the CLI (exit {@code EX_CONFIG_ERROR}) or to the TUI (config error
 * modal).
 *
 * <p>Unknown keys are silently ignored here — the reader is responsible
 * for warning about them (it knows the source file and line).
 */
public final class ConfigBuilder {

    private int version = 1;
    private String theme = "redhot";
    private boolean showHidden = false;
    private Config.SortOrder sort = Config.SortOrder.NAME;
    private boolean sortReverse = false;
    private boolean preview = false;
    private long previewMaxBytes = 1_048_576L;
    private boolean confirmDelete = true;
    private boolean trashEnabled = true;
    private boolean trashAutoTrim = false;
    private int trashMaxAgeDays = 30;
    private long trashMaxSizeGigabytes = 10L;
    private boolean followSymlinks = false;
    private int historyLimit = 500;
    private int undoLimit = 100;
    private Config.Humanize humanize = Config.Humanize.BINARY;
    private boolean dateRelative = true;
    private boolean sortLocale = true;
    private boolean watchDirectory = true;
    private Config.TreeStyle treeStyle = Config.TreeStyle.AUTO;
    private int infoLevel = 0;
    private String chafaCommand = "chafa";
    private String fileCommand = "file";
    private String openTextEditor = "";
    private String openMediaPlayer = "";

    /**
     * Applies a single {@code key=value} pair from a config file.
     *
     * @param key    the config key (already trimmed)
     * @param value  the config value (already trimmed)
     * @param source the file being parsed (for error messages)
     * @param line   the 1-based line number (for error messages)
     * @throws ConfigException if the value is invalid for the key
     */
    public void set(String key, String value, Path source, int line) throws ConfigException {
        switch (key) {
            case "version" -> this.version = parseNonNegativeInt(key, value, source, line, 1);
            case "theme" -> this.theme = requireNonEmpty(key, value, source, line);
            case "show_hidden" -> this.showHidden = parseBoolean(key, value, source, line);
            case "sort" -> this.sort = parseSortOrder(key, value, source, line);
            case "sort_reverse" -> this.sortReverse = parseBoolean(key, value, source, line);
            case "preview" -> this.preview = parsePreview(key, value, source, line);
            case "preview_max_bytes" -> this.previewMaxBytes = parseNonNegativeLong(key, value, source, line, 0);
            case "confirm_delete" -> this.confirmDelete = parseBoolean(key, value, source, line);
            case "trash_enabled" -> this.trashEnabled = parseBoolean(key, value, source, line);
            case "trash_auto_trim" -> this.trashAutoTrim = parseBoolean(key, value, source, line);
            case "trash_max_age_days" -> this.trashMaxAgeDays = parseNonNegativeInt(key, value, source, line, 0);
            case "trash_max_size_gb" -> this.trashMaxSizeGigabytes = parseNonNegativeLong(key, value, source, line, 0);
            case "follow_symlinks" -> this.followSymlinks = parseBoolean(key, value, source, line);
            case "history_limit" -> this.historyLimit = parseNonNegativeInt(key, value, source, line, 0);
            case "undo_limit" -> this.undoLimit = parseNonNegativeInt(key, value, source, line, 0);
            case "humanize" -> this.humanize = parseHumanize(key, value, source, line);
            case "date_relative" -> this.dateRelative = parseBoolean(key, value, source, line);
            case "sort_locale" -> this.sortLocale = parseBoolean(key, value, source, line);
            case "watch_directory" -> this.watchDirectory = parseBoolean(key, value, source, line);
            case "tree_style" -> this.treeStyle = parseTreeStyle(key, value, source, line);
            case "info_level" -> this.infoLevel = parseRangeInt(key, value, source, line, 0, 3);
            case "chafa_command" -> this.chafaCommand = value;
            case "file_command" -> this.fileCommand = value;
            case "open_text_editor" -> this.openTextEditor = value;
            case "open_media_player" -> this.openMediaPlayer = value;
            default -> {
                // Unknown key. The reader warns; nothing to do here.
            }
        }
    }

    /**
     * Returns whether {@code key} is part of the documented schema.
     *
     * @param key the config key to test
     * @return {@code true} if the key is known
     */
    public boolean isKnownKey(String key) {
        return switch (key) {
            case "version", "theme", "show_hidden", "sort", "sort_reverse",
                 "preview", "preview_max_bytes", "confirm_delete", "trash_enabled",
                 "trash_auto_trim", "trash_max_age_days", "trash_max_size_gb",
                 "follow_symlinks", "history_limit", "undo_limit", "humanize",
                 "date_relative", "sort_locale", "watch_directory", "tree_style",
                 "info_level", "chafa_command", "file_command",
                 "open_text_editor", "open_media_player" -> true;
            default -> false;
        };
    }

    /**
     * Produces an immutable {@link Config}.
     *
     * @return a {@code Config} with the values accumulated so far
     */
    public Config build() {
        return new Config(
                version, theme, showHidden, sort, sortReverse, preview,
                previewMaxBytes, confirmDelete, trashEnabled, trashAutoTrim,
                trashMaxAgeDays, trashMaxSizeGigabytes, followSymlinks,
                historyLimit, undoLimit, humanize, dateRelative, sortLocale,
                watchDirectory, treeStyle, infoLevel, chafaCommand, fileCommand,
                openTextEditor, openMediaPlayer
        );
    }

    private static String requireNonEmpty(String key, String value, Path source, int line)
            throws ConfigException {
        if (value.isEmpty()) {
            throw new ConfigException(source, line, key, value, "expected a non-empty string");
        }
        return value;
    }

    private static int parseInt(String key, String value, Path source, int line)
            throws ConfigException {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw new ConfigException(source, line, key, value, "expected an integer");
        }
    }

    private static long parseLong(String key, String value, Path source, int line)
            throws ConfigException {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            throw new ConfigException(source, line, key, value, "expected a long integer");
        }
    }

    private static int parseNonNegativeInt(String key, String value, Path source, int line, int minimum)
            throws ConfigException {
        int parsed = parseInt(key, value, source, line);
        if (parsed < minimum) {
            throw new ConfigException(source, line, key, value,
                    "expected an integer >= " + minimum);
        }
        return parsed;
    }

    private static long parseNonNegativeLong(String key, String value, Path source, int line, long minimum)
            throws ConfigException {
        long parsed = parseLong(key, value, source, line);
        if (parsed < minimum) {
            throw new ConfigException(source, line, key, value,
                    "expected an integer >= " + minimum);
        }
        return parsed;
    }

    private static int parseRangeInt(String key, String value, Path source, int line, int minimum, int maximum)
            throws ConfigException {
        int parsed = parseInt(key, value, source, line);
        if (parsed < minimum || parsed > maximum) {
            throw new ConfigException(source, line, key, value,
                    "expected an integer between " + minimum + " and " + maximum);
        }
        return parsed;
    }

    private static boolean parseBoolean(String key, String value, Path source, int line)
            throws ConfigException {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true", "yes", "1", "on" -> true;
            case "false", "no", "0", "off" -> false;
            default -> throw new ConfigException(source, line, key, value,
                    "expected a boolean (true/false)");
        };
    }

    private static boolean parsePreview(String key, String value, Path source, int line)
            throws ConfigException {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes", "1" -> true;
            case "off", "false", "no", "0" -> false;
            default -> throw new ConfigException(source, line, key, value,
                    "expected 'on' or 'off'");
        };
    }

    private static Config.SortOrder parseSortOrder(String key, String value, Path source, int line)
            throws ConfigException {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "name" -> Config.SortOrder.NAME;
            case "size" -> Config.SortOrder.SIZE;
            case "modified" -> Config.SortOrder.MODIFIED;
            case "type" -> Config.SortOrder.TYPE;
            default -> throw new ConfigException(source, line, key, value,
                    "expected one of: name, size, modified, type");
        };
    }

    private static Config.Humanize parseHumanize(String key, String value, Path source, int line)
            throws ConfigException {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "binary" -> Config.Humanize.BINARY;
            case "decimal" -> Config.Humanize.DECIMAL;
            default -> throw new ConfigException(source, line, key, value,
                    "expected 'binary' or 'decimal'");
        };
    }

    private static Config.TreeStyle parseTreeStyle(String key, String value, Path source, int line)
            throws ConfigException {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "auto" -> Config.TreeStyle.AUTO;
            case "utf8" -> Config.TreeStyle.UTF8;
            case "ascii" -> Config.TreeStyle.ASCII;
            default -> throw new ConfigException(source, line, key, value,
                    "expected one of: auto, utf8, ascii");
        };
    }
}