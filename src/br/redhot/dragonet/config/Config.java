package br.redhot.dragonet.config;

import java.util.Objects;

/**
 * Immutable Dragonet configuration.
 *
 * <p>Mirrors the schema documented in the design document (§10.3). All
 * fields are non-null (records do not enforce it, so the compact
 * constructor validates). Instances are built by {@link ConfigBuilder}
 * and never mutated.
 *
 * <p>Field names are {@code camelCase}; the corresponding keys in
 * {@code dragonet.conf} are {@code snake_case}. The translation is done
 * once, in {@link ConfigBuilder#set}.
 */
public record Config(
        int version,
        String theme,
        boolean showHidden,
        SortOrder sort,
        boolean sortReverse,
        boolean preview,
        long previewMaxBytes,
        boolean confirmDelete,
        boolean trashEnabled,
        boolean trashAutoTrim,
        int trashMaxAgeDays,
        long trashMaxSizeGigabytes,
        boolean followSymlinks,
        int historyLimit,
        int undoLimit,
        Humanize humanize,
        boolean dateRelative,
        boolean sortLocale,
        boolean watchDirectory,
        TreeStyle treeStyle,
        ImpMode impMode,
        int infoLevel,
        String chafaCommand,
        String fileCommand,
        String openTextEditor,
        String openMediaPlayer
) {

    public Config {
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(sort, "sort");
        Objects.requireNonNull(humanize, "humanize");
        Objects.requireNonNull(treeStyle, "treeStyle");
        Objects.requireNonNull(impMode, "impMode");
        Objects.requireNonNull(chafaCommand, "chafaCommand");
        Objects.requireNonNull(fileCommand, "fileCommand");
        Objects.requireNonNull(openTextEditor, "openTextEditor");
        Objects.requireNonNull(openMediaPlayer, "openMediaPlayer");
        if (version < 1) {
            throw new IllegalArgumentException("version must be >= 1");
        }
        if (previewMaxBytes < 0) {
            throw new IllegalArgumentException("previewMaxBytes must be >= 0");
        }
        if (trashMaxAgeDays < 0) {
            throw new IllegalArgumentException("trashMaxAgeDays must be >= 0");
        }
        if (trashMaxSizeGigabytes < 0) {
            throw new IllegalArgumentException("trashMaxSizeGigabytes must be >= 0");
        }
        if (historyLimit < 0) {
            throw new IllegalArgumentException("historyLimit must be >= 0");
        }
        if (undoLimit < 0) {
            throw new IllegalArgumentException("undoLimit must be >= 0");
        }
        if (infoLevel < 0 || infoLevel > 3) {
            throw new IllegalArgumentException("infoLevel must be 0..3");
        }
    }

    public enum SortOrder {
        NAME, SIZE, MODIFIED, TYPE
    }

    public enum Humanize {
        BINARY, DECIMAL
    }

    public enum TreeStyle {
        AUTO, UTF8, ASCII
    }

    public enum ImpMode {
        AUTO, BLOCK, DETACH
    }

    /**
     * Returns a copy of this configuration with {@code showHidden}
     * replaced. Used by the {@code Alt+H} toggle to switch the working
     * view without mutating the original config.
     *
     * @param newShowHidden the desired value
     * @return a new {@code Config}
     */
    public Config withShowHidden(boolean newShowHidden) {
        return new Config(
                version, theme, newShowHidden, sort, sortReverse, preview,
                previewMaxBytes, confirmDelete, trashEnabled, trashAutoTrim,
                trashMaxAgeDays, trashMaxSizeGigabytes, followSymlinks,
                historyLimit, undoLimit, humanize, dateRelative, sortLocale,
                watchDirectory, treeStyle, impMode, infoLevel, chafaCommand, fileCommand,
                openTextEditor, openMediaPlayer
        );
    }

    /**
     * Returns the documented default configuration (§10.3).
     *
     * @return a {@code Config} with every value set to its default
     */
    public static Config defaults() {
        return new Config(
                1,
                "redhot",
                false,
                SortOrder.NAME,
                false,
                false,
                1_048_576L,
                true,
                true,
                false,
                30,
                10L,
                false,
                500,
                100,
                Humanize.BINARY,
                true,
                true,
                true,
                TreeStyle.AUTO,
                ImpMode.AUTO,
                0,
                "chafa",
                "file",
                "",
                ""
        );
    }
}