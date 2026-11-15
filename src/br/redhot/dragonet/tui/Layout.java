package br.redhot.dragonet.tui;

import br.redhot.dragonet.config.Config;
import br.redhot.dragonet.env.Environment;
import br.redhot.dragonet.env.PathResolver;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextCharacter;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.screen.Screen;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * The three-part layout: title bar, file list, footer.
 *
 * <p>Content is centered on wide terminals: a maximum content width of
 * {@value #MAX_CONTENT_WIDTH} columns and symmetric horizontal padding
 * keep the list from touching the screen edges. On narrow terminals
 * the full width is used.
 *
 * <p>Chrome is deliberately minimal: a title line, two thin horizontal
 * separators, and a footer with nano-style shortcut hints. No outer
 * frame. Colors come exclusively from {@link Colors}.
 */
public final class Layout {

    private static final int SIZE_COLUMN_WIDTH = 7;
    private static final int MAX_CONTENT_WIDTH = 100;
    private static final int MIN_HORIZONTAL_PADDING = 2;
    private static final int ROWS_FULL_CHROME = 10;
    private static final int ROWS_COMPACT_CHROME = 5;

    private final Config config;
    private final Environment environment;
    private final TreeRenderer treeRenderer;

    public Layout(Config config, Environment environment, TreeRenderer treeRenderer) {
        this.config = Objects.requireNonNull(config, "config");
        this.environment = Objects.requireNonNull(environment, "environment");
        this.treeRenderer = Objects.requireNonNull(treeRenderer, "treeRenderer");
    }

    public void draw(Screen screen, TreeModel treeModel, String statusMessage) throws IOException {
        TerminalSize size = screen.getTerminalSize();
        int terminalColumns = size.getColumns();
        int terminalRows = size.getRows();

        fillBackground(screen, terminalColumns, terminalRows);

        int contentWidth = Math.min(terminalColumns - 2 * MIN_HORIZONTAL_PADDING,
                MAX_CONTENT_WIDTH);
        if (contentWidth < 10) {
            contentWidth = terminalColumns;
        }
        int leftPadding = (terminalColumns - contentWidth) / 2;

        if (terminalRows >= ROWS_FULL_CHROME) {
            drawFullLayout(screen, leftPadding, contentWidth, terminalRows,
                    treeModel, statusMessage);
        } else if (terminalRows >= ROWS_COMPACT_CHROME) {
            drawCompactLayout(screen, leftPadding, contentWidth, terminalRows,
                    treeModel, statusMessage);
        } else {
            drawMinimalLayout(screen, leftPadding, contentWidth, terminalRows,
                    treeModel, statusMessage);
        }

        screen.refresh();
    }

    private void drawFullLayout(Screen screen, int left, int width, int rows,
                                 TreeModel treeModel, String statusMessage) {
        drawTitle(screen, left, width, 0, treeModel);
        drawSeparator(screen, left, width, 1);

        int listTop = 3;
        int listBottom = rows - 4;
        drawList(screen, left, width, listTop, listBottom, treeModel);

        drawSeparator(screen, left, width, rows - 3);
        if (statusMessage != null) {
            drawStatusLine(screen, left, width, rows - 2, statusMessage);
        } else {
            drawShortcuts(screen, left, width, rows - 2);
        }
    }

    private void drawCompactLayout(Screen screen, int left, int width, int rows,
                                    TreeModel treeModel, String statusMessage) {
        drawTitle(screen, left, width, 0, treeModel);
        drawSeparator(screen, left, width, 1);

        int listTop = 2;
        int listBottom = rows - 1;
        drawList(screen, left, width, listTop, listBottom, treeModel);

        if (statusMessage != null) {
            drawStatusLine(screen, left, width, rows - 1, statusMessage);
        } else {
            drawShortcuts(screen, left, width, rows - 1);
        }
    }

    private void drawMinimalLayout(Screen screen, int left, int width, int rows,
                                    TreeModel treeModel, String statusMessage) {
        drawTitle(screen, left, width, 0, treeModel);

        int listTop = 1;
        int listBottom = rows - 1;
        drawList(screen, left, width, listTop, listBottom, treeModel);

        if (statusMessage != null) {
            drawStatusLine(screen, left, width, rows - 1, statusMessage);
        } else {
            drawShortcuts(screen, left, width, rows - 1);
        }
    }

    private void drawTitle(Screen screen, int left, int width, int row, TreeModel treeModel) {
        int childCount = countChildren(treeModel);
        String path = PathResolver.formatForDisplay(
                treeModel.directory(), environment.homeDirectory());
        String stats = childCount + (childCount == 1 ? " item" : " items");
        int maxCol = left + width;
        int statsStart = maxCol - stats.length();

        int col = left;
        col = drawChunk(screen, col, row, "  ", Colors.DEFAULT, maxCol);
        col = drawChunk(screen, col, row, "dragonet", Colors.ORANGE, maxCol);
        col = drawChunk(screen, col, row, " \u00b7 ", Colors.GRAY_DIM, maxCol);

        int availableForPath = statsStart - col - 1;
        if (availableForPath >= 1) {
            if (path.length() > availableForPath) {
                if (availableForPath > 1) {
                    path = path.substring(0, availableForPath - 1) + "\u2026";
                } else {
                    path = path.substring(0, availableForPath);
                }
            }
            drawChunk(screen, col, row, path, Colors.DEFAULT, maxCol);
        }

        if (statsStart >= col) {
            drawChunk(screen, statsStart, row, stats, Colors.GRAY, maxCol);
        }
    }

    private int countChildren(TreeModel treeModel) {
        int count = treeModel.baseEntries().size();
        if (count > 0 && treeModel.baseEntries().get(0).isParent()) {
            count = count - 1;
        }
        return count;
    }

    private void drawSeparator(Screen screen, int left, int width, int row) {
        char separator = '-';
        TextCharacter character = TextCharacter.fromCharacter(
                separator, Colors.GRAY_DIM, Colors.DEFAULT)[0];
        for (int column = 0; column < width; column = column + 1) {
            screen.setCharacter(left + column, row, character);
        }
    }

    private void drawList(Screen screen, int left, int width,
                           int listTop, int listBottom, TreeModel treeModel) {
        int listHeight = listBottom - listTop;
        if (listHeight < 1) {
            return;
        }
        TreeRenderer.Segments segments = treeRenderer.chooseSegments(width);
        List<VisibleEntry> entries = treeModel.visibleEntries();
        int cursorIndex = treeModel.cursorIndex();
        int scrollOffset = Math.max(0, cursorIndex - listHeight + 1);
        if (cursorIndex < scrollOffset) {
            scrollOffset = cursorIndex;
        }
        for (int offset = 0; offset < listHeight; offset = offset + 1) {
            int entryIndex = scrollOffset + offset;
            if (entryIndex >= entries.size()) {
                break;
            }
            drawEntry(screen, left, width, listTop + offset, entries.get(entryIndex),
                    entryIndex == cursorIndex, segments);
        }
    }

    private void drawEntry(Screen screen, int left, int width, int row, VisibleEntry entry,
                            boolean isCursor, TreeRenderer.Segments segments) {
        TextColor background = isCursor ? Colors.ORANGE : Colors.DEFAULT;
        TextColor markerColor = isCursor ? Colors.WHITE : Colors.GRAY_DIM;
        TextColor treeColor = isCursor ? Colors.WHITE : Colors.GRAY;
        TextColor sizeColor = isCursor ? Colors.WHITE : Colors.GRAY;

        if (isCursor) {
            fillRow(screen, left, width, row, Colors.ORANGE);
        }

        int maxCol = left + width;
        int col = left;

        String marker = isCursor ? "> " : "  ";
        col = drawRaw(screen, col, row, marker, markerColor, background, maxCol);

        String prefix = treeRenderer.renderPrefix(entry, segments);
        col = drawRaw(screen, col, row, prefix, treeColor, background, maxCol);

        String name = entry.isPlaceholder()
                ? entry.placeholderText()
                : displayName(entry.entry());
        TextColor nameColor;
        if (isCursor) {
            nameColor = Colors.WHITE;
        } else if (entry.isPlaceholder()) {
            nameColor = Colors.GRAY;
        } else {
            nameColor = pickForeground(entry.entry());
        }

        int sizeStartColumn = left + width - SIZE_COLUMN_WIDTH;
        int nameArea = sizeStartColumn - col - 1;
        if (nameArea > 0) {
            if (name.length() > nameArea) {
                if (nameArea > 1) {
                    name = name.substring(0, nameArea - 1) + "\u2026";
                } else {
                    name = name.substring(0, Math.min(1, name.length()));
                }
            }
            col = drawRaw(screen, col, row, name, nameColor, background, maxCol);
        }

        while (col < sizeStartColumn && col < maxCol) {
            screen.setCharacter(col, row,
                    TextCharacter.fromCharacter(' ', background, background)[0]);
            col = col + 1;
        }

        String size = entry.isPlaceholder() ? "" : formatSize(entry.entry());
        String rightAligned = String.format("%" + SIZE_COLUMN_WIDTH + "s", size);
        for (int index = 0; index < rightAligned.length() && col < maxCol; index = index + 1) {
            screen.setCharacter(col, row,
                    TextCharacter.fromCharacter(rightAligned.charAt(index),
                            sizeColor, background)[0]);
            col = col + 1;
        }
    }

    private void drawShortcuts(Screen screen, int left, int width, int row) {
        int maxCol = left + width;
        int col = left + 2;
        col = drawShortcut(screen, col, row, "^X", " Exit", maxCol);
        if (width >= 40) {
            col = drawShortcut(screen, col, row, "\u2191\u2193", " Nav", maxCol);
        }
        if (width >= 50) {
            col = drawShortcut(screen, col, row, "\u2192", " Exp", maxCol);
        }
        if (width >= 60) {
            col = drawShortcut(screen, col, row, "\u2190", " Col", maxCol);
        }
        if (width >= 72) {
            col = drawShortcut(screen, col, row, "Enter", " Open", maxCol);
        }
        if (width >= 86) {
            drawShortcut(screen, col, row, "Bksp", " Up", maxCol);
        }
    }

    private int drawShortcut(Screen screen, int col, int row, String key,
                              String label, int maxCol) {
        col = drawRaw(screen, col, row, key, Colors.WHITE, Colors.ORANGE, maxCol);
        col = drawChunk(screen, col, row, label, Colors.DEFAULT, maxCol);
        col = drawChunk(screen, col, row, "  ", Colors.DEFAULT, maxCol);
        return col;
    }

    private void drawStatusLine(Screen screen, int left, int width, int row,
                                 String statusMessage) {
        int col = left + 2;
        drawChunk(screen, col, row, statusMessage, Colors.ORANGE, left + width);
    }

    private int drawChunk(Screen screen, int col, int row, String text,
                           TextColor foreground, int maxCol) {
        for (int index = 0; index < text.length() && col < maxCol; index = index + 1) {
            screen.setCharacter(col, row,
                    TextCharacter.fromCharacter(text.charAt(index),
                            foreground, Colors.DEFAULT)[0]);
            col = col + 1;
        }
        return col;
    }

    private int drawRaw(Screen screen, int col, int row, String text,
                         TextColor foreground, TextColor background, int maxCol) {
        for (int index = 0; index < text.length() && col < maxCol; index = index + 1) {
            screen.setCharacter(col, row,
                    TextCharacter.fromCharacter(text.charAt(index),
                            foreground, background)[0]);
            col = col + 1;
        }
        return col;
    }

    private void fillBackground(Screen screen, int columns, int rows) {
        TextCharacter blank = TextCharacter.DEFAULT_CHARACTER;
        for (int column = 0; column < columns; column = column + 1) {
            for (int row = 0; row < rows; row = row + 1) {
                screen.setCharacter(column, row, blank);
            }
        }
    }

    private void fillRow(Screen screen, int left, int width, int row, TextColor background) {
        TextCharacter blank = TextCharacter.fromCharacter(' ', background, background)[0];
        for (int column = left; column < left + width; column = column + 1) {
            screen.setCharacter(column, row, blank);
        }
    }

    private String displayName(FileEntry entry) {
        if (entry.isSymlink() && entry.symlinkTarget() != null) {
            return entry.name() + " -> " + entry.symlinkTarget();
        }
        return entry.name();
    }

    private String formatSize(FileEntry entry) {
        if (entry.isDirectory()) {
            return "dir";
        }
        long bytes = entry.sizeBytes();
        long divisor = config.humanize() == Config.Humanize.BINARY ? 1024L : 1000L;
        String[] units = {"B", "K", "M", "G", "T"};
        if (bytes < divisor) {
            return bytes + "B";
        }
        double value = bytes;
        int unitIndex = 0;
        while (value >= divisor && unitIndex < units.length - 1) {
            value = value / divisor;
            unitIndex = unitIndex + 1;
        }
        if (value < 10.0) {
            return String.format("%.1f%s", value, units[unitIndex]);
        }
        return String.format("%.0f%s", value, units[unitIndex]);
    }

    private TextColor pickForeground(FileEntry entry) {
        if (entry.isSymlink() && entry.isBrokenSymlink()) {
            return Colors.RED;
        }
        return switch (entry.type()) {
            case DIRECTORY -> Colors.ORANGE;
            case SYMLINK -> Colors.GRAY;
            case FILE -> entry.isExecutable() ? Colors.ORANGE_BRIGHT : Colors.DEFAULT;
            case OTHER -> Colors.ORANGE_BRIGHT;
        };
    }
}