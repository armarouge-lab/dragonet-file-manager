package br.redhot.dragonet.tui;

import br.redhot.dragonet.config.Config;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;

/**
 * Pure formatting and layout decisions for the metadata column.
 *
 * <p>No I/O, no state, no Lanterna. Every method is deterministic
 * given its arguments, which makes the four-level {@code M-I}
 * behavior testable without a terminal.
 *
 * <p>Levels (design §5.2.1):
 * <ol start="0">
 *   <li>Size</li>
 *   <li>Size + Date</li>
 *   <li>Full (Mode + Owner + Size + Date)</li>
 *   <li>Clean (name only)</li>
 * </ol>
 */
public final class MetadataFormatter {

    public static final int LEVEL_SIZE = 0;
    public static final int LEVEL_SIZE_DATE = 1;
    public static final int LEVEL_FULL = 2;
    public static final int LEVEL_CLEAN = 3;

    public static final int MODE_WIDTH = 10;
    public static final int OWNER_WIDTH = 12;
    public static final int SIZE_WIDTH_FULL = 7;
    public static final int SIZE_WIDTH_COMPACT = 4;
    public static final int DATE_WIDTH_FULL = 12;
    public static final int DATE_WIDTH_COMPACT = 6;

    private static final int[] LEVELS_TINY = {LEVEL_CLEAN};
    private static final int[] LEVELS_NARROW = {LEVEL_SIZE, LEVEL_CLEAN};
    private static final int[] LEVELS_FULL = {
            LEVEL_SIZE, LEVEL_SIZE_DATE, LEVEL_FULL, LEVEL_CLEAN};

    private static final String[] MONTH_NAMES = {
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    private MetadataFormatter() {
        // static utility
    }

    public static final class DisplayPlan {
        public final boolean showSize;
        public final boolean showDate;
        public final boolean showMode;
        public final boolean showOwner;
        public final boolean compactSize;
        public final boolean compactDate;

        DisplayPlan(boolean showSize, boolean showDate, boolean showMode,
                    boolean showOwner, boolean compactSize, boolean compactDate) {
            this.showSize = showSize;
            this.showDate = showDate;
            this.showMode = showMode;
            this.showOwner = showOwner;
            this.compactSize = compactSize;
            this.compactDate = compactDate;
        }
    }

    /**
     * Returns the levels that are available at the given terminal width.
     *
     * @param columns the terminal width
     * @return the available levels, in cycle order
     */
    public static int[] availableLevels(int columns) {
        if (columns < 40) {
            return LEVELS_TINY;
        }
        if (columns < 60) {
            return LEVELS_NARROW;
        }
        return LEVELS_FULL;
    }

    /**
     * Snaps {@code infoLevel} to the closest available level if it is
     * not in {@link #availableLevels(int)}.
     *
     * <p>When snapping is needed, the first available level is used
     * (which is {@link #LEVEL_SIZE} at narrow widths and
     * {@link #LEVEL_CLEAN} at tiny widths). That preserves the
     * invariant that the return value is always in
     * {@code availableLevels(columns)}.
     *
     * @param infoLevel the stored level
     * @param columns   the terminal width
     * @return a level guaranteed to be in {@code availableLevels(columns)}
     */
    public static int effectiveLevel(int infoLevel, int columns) {
        for (int level : availableLevels(columns)) {
            if (level == infoLevel) {
                return infoLevel;
            }
        }
        return availableLevels(columns)[0];
    }

    /**
     * Returns the level that follows {@code currentLevel} at this width.
     *
     * @param currentLevel the current effective level
     * @param columns      the terminal width
     * @return the next available level
     */
    public static int nextLevel(int currentLevel, int columns) {
        int[] available = availableLevels(columns);
        int index = 0;
        for (int i = 0; i < available.length; i = i + 1) {
            if (available[i] == currentLevel) {
                index = i;
                break;
            }
        }
        return available[(index + 1) % available.length];
    }

    /**
     * Returns the display label for a level, as shown in the footer
     * hint ({@code M-I ›<label>}).
     *
     * @param level the level
     * @return a human-readable label
     */
    public static String levelLabel(int level) {
        return switch (level) {
            case LEVEL_SIZE -> "Size";
            case LEVEL_SIZE_DATE -> "Size+Date";
            case LEVEL_FULL -> "Full";
            case LEVEL_CLEAN -> "Clean";
            default -> "Size";
        };
    }

    /**
     * Decides which columns to draw for a given level and width.
     *
     * @param infoLevel the stored level (snapped via {@link #effectiveLevel})
     * @param columns   the terminal width
     * @return a {@link DisplayPlan}
     */
    public static DisplayPlan planFor(int infoLevel, int columns) {
        int effective = effectiveLevel(infoLevel, columns);
        boolean wide = columns >= 80;
        boolean medium = columns >= 60 && columns < 80;
        boolean narrow = columns >= 40 && columns < 60;

        if (effective == LEVEL_CLEAN) {
            return new DisplayPlan(false, false, false, false, false, false);
        }
        if (effective == LEVEL_SIZE) {
            return new DisplayPlan(true, false, false, false, narrow, false);
        }
        if (effective == LEVEL_SIZE_DATE) {
            return new DisplayPlan(true, true, false, false, false, !wide);
        }
        if (medium) {
            return new DisplayPlan(true, true, true, false, false, true);
        }
        return new DisplayPlan(true, true, true, true, false, false);
    }

    /**
     * Formats a byte count for the size column.
     *
     * @param bytes    the size in bytes
     * @param humanize binary (1024) or decimal (1000)
     * @param compact  when {@code true}, drop the decimal part
     * @return a short string such as {@code "4.0K"} or {@code "4K"}
     */
    public static String formatSize(long bytes, Config.Humanize humanize, boolean compact) {
        long divisor = humanize == Config.Humanize.BINARY ? 1024L : 1000L;
        if (bytes < divisor) {
            return bytes + "B";
        }
        String[] units = {"K", "M", "G", "T"};
        double value = bytes;
        int unitIndex = -1;
        while (value >= divisor && unitIndex < units.length - 1) {
            value = value / divisor;
            unitIndex = unitIndex + 1;
        }
        if (compact || value >= 10.0) {
            return String.format(Locale.ROOT, "%.0f%s", value, units[unitIndex]);
        }
        return String.format(Locale.ROOT, "%.1f%s", value, units[unitIndex]);
    }

    /**
     * Formats a timestamp for the date column.
     *
     * <p>If {@code relative} is true and the timestamp is within seven
     * days of {@code now}, produces a short relative string
     * ({@code "now"}, {@code "5m"}, {@code "2h"}, {@code "3d"}).
     * Otherwise produces an absolute string ({@code "Nov 11 03:00"}
     * full, or {@code "Nov 11"} compact).
     *
     * @param timestamp the file's modification time
     * @param now       the reference instant
     * @param relative  whether relative formatting is enabled
     * @param compact   whether to use the short absolute form
     * @return a short string
     */
    public static String formatDate(Instant timestamp, Instant now,
                                    boolean relative, boolean compact) {
        if (relative) {
            long seconds = now.getEpochSecond() - timestamp.getEpochSecond();
            if (seconds >= 0) {
                if (seconds < 60) {
                    return "now";
                }
                if (seconds < 3600) {
                    return (seconds / 60) + "m";
                }
                if (seconds < 86_400) {
                    return (seconds / 3600) + "h";
                }
                if (seconds < 7 * 86_400L) {
                    return (seconds / 86_400) + "d";
                }
            }
        }
        ZonedDateTime zoned = timestamp.atZone(ZoneId.systemDefault());
        String month = MONTH_NAMES[zoned.getMonthValue() - 1];
        if (compact) {
            return String.format(Locale.ROOT, "%s %02d", month, zoned.getDayOfMonth());
        }
        return String.format(Locale.ROOT, "%s %02d %02d:%02d",
                month, zoned.getDayOfMonth(), zoned.getHour(), zoned.getMinute());
    }
}
