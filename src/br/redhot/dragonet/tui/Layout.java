package br.redhot.dragonet.tui;

import br.redhot.dragonet.config.Config;
import br.redhot.dragonet.env.Environment;
import br.redhot.dragonet.env.PathResolver;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextCharacter;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.screen.Screen;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class Layout {

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

    public void draw(Screen screen, TreeModel treeModel, int infoLevel,
                     String statusMessage, Prompt activePrompt, Modal activeModal)
            throws IOException {
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

        TreeRenderer.Segments segments = treeRenderer.chooseSegments(terminalColumns);
        MetadataFormatter.DisplayPlan plan =
                MetadataFormatter.planFor(infoLevel, terminalColumns);
        int nextLevel = MetadataFormatter.nextLevel(
                MetadataFormatter.effectiveLevel(infoLevel, terminalColumns),
                terminalColumns);
        String nextLevelLabel = MetadataFormatter.levelLabel(nextLevel);

        if (terminalRows >= ROWS_FULL_CHROME) {
            drawFull(screen, leftPadding, contentWidth, terminalRows, treeModel, statusMessage,
                    segments, plan, nextLevelLabel, activePrompt, activeModal);
        } else if (terminalRows >= ROWS_COMPACT_CHROME) {
            drawCompact(screen, leftPadding, contentWidth, terminalRows, treeModel, statusMessage,
                    segments, plan, nextLevelLabel, activePrompt, activeModal);
        } else {
            drawMinimal(screen, leftPadding, contentWidth, terminalRows, treeModel, statusMessage,
                    segments, plan, nextLevelLabel, activePrompt, activeModal);
        }

        if (activeModal != null) {
            activeModal.draw(screen);
        } else if (activePrompt != null) {
            int inputRow = terminalRows - 2;
            if (inputRow < 0) {
                inputRow = 0;
            }
            int hintRow = terminalRows - 1;
            if (hintRow < 0) {
                hintRow = 0;
            }
            activePrompt.draw(screen, leftPadding, contentWidth, inputRow, hintRow);
            int cursorColumn = activePrompt.cursorColumnIn(leftPadding);
            int maxColumn = leftPadding + contentWidth - 1;
            if (cursorColumn > maxColumn) {
                cursorColumn = maxColumn;
            }
            screen.setCursorPosition(new TerminalPosition(cursorColumn, inputRow));
        } else {
            screen.setCursorPosition(null);
        }

        screen.refresh();
    }

    private void drawFull(Screen screen, int left, int width, int rows,
                           TreeModel treeModel, String statusMessage,
                           TreeRenderer.Segments segments,
                           MetadataFormatter.DisplayPlan plan,
                           String nextLevelLabel,
                           Prompt activePrompt, Modal activeModal) {
        drawTitle(screen, left, width, 0, treeModel);
        drawSeparator(screen, left, width, 1);

        int listTop = 3;
        int listBottom = rows - (activePrompt != null ? 2 : 4);
        drawList(screen, left, width, listTop, listBottom, treeModel, segments, plan);

        if (activePrompt == null) {
            drawSeparator(screen, left, width, rows - 3);
        }
        if (activeModal != null) {
            drawModalHint(screen, left, width, rows - 2);
        } else if (activePrompt == null) {
            if (statusMessage != null) {
                drawStatusLine(screen, left, width, rows - 2, statusMessage);
            } else {
                drawShortcuts(screen, left, width, rows - 2, nextLevelLabel);
            }
        }
    }

    private void drawCompact(Screen screen, int left, int width, int rows,
                              TreeModel treeModel, String statusMessage,
                              TreeRenderer.Segments segments,
                              MetadataFormatter.DisplayPlan plan,
                              String nextLevelLabel,
                              Prompt activePrompt, Modal activeModal) {
        drawTitle(screen, left, width, 0, treeModel);
        drawSeparator(screen, left, width, 1);

        int listTop = 2;
        int listBottom = rows - (activePrompt != null ? 2 : 1);
        drawList(screen, left, width, listTop, listBottom, treeModel, segments, plan);

        if (activePrompt == null && activeModal == null) {
            if (statusMessage != null) {
                drawStatusLine(screen, left, width, rows - 1, statusMessage);
            } else {
                drawShortcuts(screen, left, width, rows - 1, nextLevelLabel);
            }
        } else if (activeModal != null) {
            drawModalHint(screen, left, width, rows - 1);
        }
    }

    private void drawMinimal(Screen screen, int left, int width, int rows,
                              TreeModel treeModel, String statusMessage,
                              TreeRenderer.Segments segments,
                              MetadataFormatter.DisplayPlan plan,
                              String nextLevelLabel,
                              Prompt activePrompt, Modal activeModal) {
        drawTitle(screen, left, width, 0, treeModel);

        int listTop = 1;
        int listBottom = rows - (activePrompt != null ? 2 : 1);
        drawList(screen, left, width, listTop, listBottom, treeModel, segments, plan);

        if (activePrompt == null && activeModal == null) {
            if (statusMessage != null) {
                drawStatusLine(screen, left, width, rows - 1, statusMessage);
            } else {
                drawShortcuts(screen, left, width, rows - 1, nextLevelLabel);
            }
        } else if (activeModal != null) {
            drawModalHint(screen, left, width, rows - 1);
        }
    }

    private void drawModalHint(Screen screen, int left, int width, int row) {
        int maxCol = left + width;
        int col = left + 2;
        col = drawRaw(screen, col, row, "y", Colors.WHITE, Colors.ORANGE, maxCol);
        col = drawChunk(screen, col, row, " Yes  ", Colors.DEFAULT, maxCol);
        col = drawRaw(screen, col, row, "n/Esc", Colors.WHITE, Colors.ORANGE, maxCol);
        drawChunk(screen, col, row, " No", Colors.DEFAULT, maxCol);
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
        TextCharacter character = TextCharacter.fromCharacter(
                '-', Colors.GRAY_DIM, Colors.DEFAULT)[0];
        for (int column = 0; column < width; column = column + 1) {
            screen.setCharacter(left + column, row, character);
        }
    }

    private void drawList(Screen screen, int left, int width,
                           int listTop, int listBottom, TreeModel treeModel,
                           TreeRenderer.Segments segments,
                           MetadataFormatter.DisplayPlan plan) {
        int listHeight = listBottom - listTop;
        if (listHeight < 1) {
            return;
        }
        List<VisibleEntry> entries = treeModel.visibleEntries();
        int cursorIndex = treeModel.cursorIndex();
        int scrollOffset = Math.max(0, cursorIndex - listHeight + 1);
        if (cursorIndex < scrollOffset) {
            scrollOffset = cursorIndex;
        }
        Instant now = Instant.now();
        for (int offset = 0; offset < listHeight; offset = offset + 1) {
            int entryIndex = scrollOffset + offset;
            if (entryIndex >= entries.size()) {
                break;
            }
            drawEntry(screen, left, width, listTop + offset, entries.get(entryIndex),
                    entryIndex == cursorIndex, segments, plan, now);
        }
    }

    private void drawEntry(Screen screen, int left, int width, int row, VisibleEntry entry,
                            boolean isCursor, TreeRenderer.Segments segments,
                            MetadataFormatter.DisplayPlan plan, Instant now) {
        TextColor background = isCursor ? Colors.ORANGE : Colors.DEFAULT;
        TextColor markerColor = isCursor ? Colors.WHITE : Colors.GRAY_DIM;
        TextColor treeColor = isCursor ? Colors.WHITE : Colors.GRAY;
        TextColor metadataColor = isCursor ? Colors.WHITE : Colors.GRAY;

        if (isCursor) {
            fillRow(screen, left, width, row, Colors.ORANGE);
        }

        int maxCol = left + width;
        int col = left;

        String marker = isCursor ? "> " : "  ";
        col = drawRaw(screen, col, row, marker, markerColor, background, maxCol);

        String prefix = treeRenderer.renderPrefix(entry, segments);
        col = drawRaw(screen, col, row, prefix, treeColor, background, maxCol);

        String metadata = entry.isPlaceholder() ? "" : buildMetadata(entry.entry(), plan, now);
        int metadataWidth = metadata.length();
        int metadataStart = maxCol - metadataWidth;
        if (metadataStart < col) {
            metadataStart = col;
            metadataWidth = 0;
        }

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

        int gap = metadataWidth > 0 ? 1 : 0;
        int nameArea = metadataStart - col - gap;
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

        while (col < metadataStart && col < maxCol) {
            screen.setCharacter(col, row,
                    TextCharacter.fromCharacter(' ', background, background)[0]);
            col = col + 1;
        }

        if (metadataWidth > 0) {
            for (int index = 0; index < metadata.length() && metadataStart + index < maxCol;
                 index = index + 1) {
                screen.setCharacter(metadataStart + index, row,
                        TextCharacter.fromCharacter(metadata.charAt(index),
                                metadataColor, background)[0]);
            }
        }
    }

    private String buildMetadata(FileEntry entry, MetadataFormatter.DisplayPlan plan,
                                  Instant now) {
        StringBuilder builder = new StringBuilder();
        if (plan.showMode) {
            String mode = entry.modeString();
            if (mode.isEmpty()) {
                mode = "-".repeat(MetadataFormatter.MODE_WIDTH);
            }
            builder.append(padRight(mode, MetadataFormatter.MODE_WIDTH));
        }
        if (plan.showOwner) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            String owner = entry.ownerName();
            if (owner.isEmpty()) {
                owner = "-".repeat(MetadataFormatter.OWNER_WIDTH);
            }
            builder.append(padRight(owner, MetadataFormatter.OWNER_WIDTH));
        }
        if (plan.showSize) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            String size = entry.isDirectory()
                    ? "dir"
                    : MetadataFormatter.formatSize(
                            entry.sizeBytes(), config.humanize(), plan.compactSize);
            int width = plan.compactSize
                    ? MetadataFormatter.SIZE_WIDTH_COMPACT
                    : MetadataFormatter.SIZE_WIDTH_FULL;
            builder.append(padLeft(size, width));
        }
        if (plan.showDate) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            String date = MetadataFormatter.formatDate(
                    entry.lastModified(), now, config.dateRelative(), plan.compactDate);
            int width = plan.compactDate
                    ? MetadataFormatter.DATE_WIDTH_COMPACT
                    : MetadataFormatter.DATE_WIDTH_FULL;
            builder.append(padRight(date, width));
        }
        return builder.toString();
    }

    private static String padRight(String text, int width) {
        if (text.length() >= width) {
            return text.substring(0, width);
        }
        return text + " ".repeat(width - text.length());
    }

    private static String padLeft(String text, int width) {
        if (text.length() >= width) {
            return text.substring(0, width);
        }
        return " ".repeat(width - text.length()) + text;
    }

    private void drawShortcuts(Screen screen, int left, int width, int row,
                                String nextLevelLabel) {
        int maxCol = left + width;
        int col = left + 2;
        col = drawShortcut(screen, col, row, "^X", " Exit", maxCol);
        if (width >= 40) {
            col = drawShortcut(screen, col, row, "\u2191\u2193", " Nav", maxCol);
        }
        if (width >= 50) {
            col = drawShortcut(screen, col, row, "^D", " Del", maxCol);
        }
        if (width >= 60) {
            col = drawShortcut(screen, col, row, "M-I", " \u203a" + nextLevelLabel, maxCol);
        }
        if (width >= 72) {
            col = drawShortcut(screen, col, row, "M-D", " Purge", maxCol);
            col = drawShortcut(screen, col, row, "^R", " Rename", maxCol);
        }
        if (width >= 86) {
            col = drawShortcut(screen, col, row, "M-S", " Mkdir", maxCol);
            col = drawShortcut(screen, col, row, "M-N", " Touch", maxCol);
        }
        if (width >= 100) {
            drawShortcut(screen, col, row, "M-Y", " Symlink", maxCol);
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