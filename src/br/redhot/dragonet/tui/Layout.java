package br.redhot.dragonet.tui;

import br.redhot.dragonet.config.Config;
import br.redhot.dragonet.env.Environment;
import br.redhot.dragonet.env.PathResolver;

import com.googlecode.lanterna.SGR;
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

    private static final int MAX_CONTENT_WIDTH = 140;
    private static final int MIN_HORIZONTAL_PADDING = 2;
    private static final int ROWS_FULL_CHROME = 10;
    private static final int ROWS_COMPACT_CHROME = 5;
    private static final int PREVIEW_MIN_TOTAL_WIDTH = 100;
    private static final int PREVIEW_COLUMN_WIDTH = 40;
    private static final int PREVIEW_MIN_COLUMNS = 50;

    private final Config config;
    private final Environment environment;
    private final TreeRenderer treeRenderer;

    public Layout(Config config, Environment environment, TreeRenderer treeRenderer) {
        this.config = Objects.requireNonNull(config, "config");
        this.environment = Objects.requireNonNull(environment, "environment");
        this.treeRenderer = Objects.requireNonNull(treeRenderer, "treeRenderer");
    }

    /**
     * Renders a full frame.
     *
     * @return the clamped preview scroll offset; caller must store it
     *         so scrolling stays bounded
     */
    public int draw(Screen screen, TreeModel treeModel, TagState tagState,
                    CutBuffer cutBuffer, int infoLevel,
                    String statusMessage, Prompt activePrompt, Modal activeModal,
                    MenuModal activeMenuModal, ConflictModal activeConflictModal,
                    PreviewModel previewModel, boolean previewVisible,
                    boolean previewFocused, int previewScroll)
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

        PreviewModel.Preview preview = PreviewModel.Preview.empty();
        boolean showSidebarPreview = previewVisible
                && terminalColumns >= PREVIEW_MIN_TOTAL_WIDTH
                && terminalRows >= ROWS_FULL_CHROME;
        boolean showOverlayPreview = previewVisible
                && previewFocused
                && !showSidebarPreview
                && terminalRows >= ROWS_COMPACT_CHROME;
        if (previewVisible && previewModel != null) {
            VisibleEntry currentEntry = treeModel.current();
            if (currentEntry != null && currentEntry.entry() != null) {
                preview = previewModel.preview(currentEntry.entry());
            }
        }

        int effectiveScroll = 0;
        if (terminalRows >= ROWS_FULL_CHROME) {
            effectiveScroll = drawFull(screen, leftPadding, contentWidth, terminalRows,
                    treeModel, tagState, cutBuffer, statusMessage, preview,
                    showSidebarPreview, previewFocused, previewScroll,
                    activePrompt, activeModal);
        } else if (terminalRows >= ROWS_COMPACT_CHROME) {
            drawCompact(screen, leftPadding, contentWidth, terminalRows, treeModel, tagState,
                    cutBuffer, statusMessage, activePrompt, activeModal);
        } else {
            drawMinimal(screen, leftPadding, contentWidth, terminalRows, treeModel, tagState,
                    cutBuffer, statusMessage, activePrompt, activeModal);
        }

        if (showOverlayPreview) {
            effectiveScroll = drawPreviewOverlay(screen, terminalColumns, terminalRows,
                    preview, previewScroll);
        }

        if (activeConflictModal != null) {
            activeConflictModal.draw(screen);
        } else if (activeMenuModal != null) {
            activeMenuModal.draw(screen);
        } else if (activeModal != null) {
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
        return effectiveScroll;
    }

    private int drawFull(Screen screen, int left, int width, int rows,
                          TreeModel treeModel, TagState tagState, CutBuffer cutBuffer,
                          String statusMessage,
                          PreviewModel.Preview preview, boolean showPreview,
                          boolean previewFocused, int previewScroll,
                          Prompt activePrompt, Modal activeModal) {
        drawTitle(screen, left, width, 0, treeModel, tagState, cutBuffer, previewFocused);
        drawSeparator(screen, left, width, 1);

        int listTop = 3;
        int listBottom = rows - (activePrompt != null ? 2 : 4);
        int listWidth = width;
        int effectiveScroll = 0;
        if (showPreview) {
            int previewWidth = Math.min(PREVIEW_COLUMN_WIDTH, width - PREVIEW_MIN_COLUMNS);
            if (previewWidth >= 20) {
                listWidth = width - previewWidth - 1;
                int previewLeft = left + listWidth + 1;
                effectiveScroll = PreviewPane.draw(screen, preview, previewLeft, listTop,
                        previewWidth, listBottom - listTop, previewScroll,
                        previewFocused, true);
            }
        }
        drawList(screen, left, listWidth, listTop, listBottom, treeModel, tagState);

        if (activePrompt == null) {
            drawSeparator(screen, left, width, rows - 3);
        }
        if (activeModal != null) {
            drawModalHint(screen, left, width, rows - 2);
        } else if (activePrompt == null) {
            if (statusMessage != null) {
                drawStatusLine(screen, left, width, rows - 2, statusMessage);
            } else {
                drawShortcuts(screen, left, width, rows - 2, cutBuffer, previewFocused);
            }
        }
        return effectiveScroll;
    }

    private void drawCompact(Screen screen, int left, int width, int rows,
                              TreeModel treeModel, TagState tagState, CutBuffer cutBuffer,
                              String statusMessage,
                              Prompt activePrompt, Modal activeModal) {
        drawTitle(screen, left, width, 0, treeModel, tagState, cutBuffer, false);
        drawSeparator(screen, left, width, 1);
        int listTop = 2;
        int listBottom = rows - (activePrompt != null ? 2 : 1);
        drawList(screen, left, width, listTop, listBottom, treeModel, tagState);
        if (activePrompt == null && activeModal == null) {
            if (statusMessage != null) {
                drawStatusLine(screen, left, width, rows - 1, statusMessage);
            } else {
                drawShortcuts(screen, left, width, rows - 1, cutBuffer, false);
            }
        } else if (activeModal != null) {
            drawModalHint(screen, left, width, rows - 1);
        }
    }

    private void drawMinimal(Screen screen, int left, int width, int rows,
                              TreeModel treeModel, TagState tagState, CutBuffer cutBuffer,
                              String statusMessage,
                              Prompt activePrompt, Modal activeModal) {
        drawTitle(screen, left, width, 0, treeModel, tagState, cutBuffer, false);
        int listTop = 1;
        int listBottom = rows - (activePrompt != null ? 2 : 1);
        drawList(screen, left, width, listTop, listBottom, treeModel, tagState);
        if (activePrompt == null && activeModal == null) {
            if (statusMessage != null) {
                drawStatusLine(screen, left, width, rows - 1, statusMessage);
            } else {
                drawShortcuts(screen, left, width, rows - 1, cutBuffer, false);
            }
        } else if (activeModal != null) {
            drawModalHint(screen, left, width, rows - 1);
        }
    }

    private int drawPreviewOverlay(Screen screen, int columns, int rows,
                                    PreviewModel.Preview preview, int scroll) {
        int overlayLeft = Math.max(2, columns / 8);
        int overlayTop = Math.max(2, rows / 8);
        int overlayWidth = columns - 2 * overlayLeft;
        int overlayHeight = rows - 2 * overlayTop;
        int effectiveScroll = PreviewPane.draw(screen, preview, overlayLeft, overlayTop,
                overlayWidth, overlayHeight, scroll, true, true);
        // Footer hint for closing the overlay.
        int hintRow = Math.min(rows - 1, overlayTop + overlayHeight);
        if (hintRow > overlayTop + overlayHeight - 1) {
            hintRow = overlayTop + overlayHeight - 1;
        }
        String hint = " Esc/Tab/^L: close ";
        int hintCol = overlayLeft + 1;
        for (int index = 0; index < hint.length() && hintCol + index < columns;
             index = index + 1) {
            screen.setCharacter(hintCol + index, hintRow,
                    TextCharacter.fromCharacter(hint.charAt(index),
                            Colors.ORANGE, Colors.DEFAULT)[0]);
        }
        return effectiveScroll;
    }

    private void drawModalHint(Screen screen, int left, int width, int row) {
        int maxCol = left + width;
        int col = left + 2;
        col = drawRaw(screen, col, row, "y", Colors.WHITE, Colors.ORANGE, maxCol);
        col = drawChunk(screen, col, row, " Yes  ", Colors.DEFAULT, maxCol);
        col = drawRaw(screen, col, row, "n/Esc", Colors.WHITE, Colors.ORANGE, maxCol);
        drawChunk(screen, col, row, " No", Colors.DEFAULT, maxCol);
    }

    private void drawTitle(Screen screen, int left, int width, int row,
                            TreeModel treeModel, TagState tagState, CutBuffer cutBuffer,
                            boolean previewFocused) {
        int childCount = countChildren(treeModel);
        String path = PathResolver.formatForDisplay(
                treeModel.directory(), environment.homeDirectory());
        String countText = childCount + (childCount == 1 ? " item" : " items");
        StringBuilder extras = new StringBuilder();
        if (tagState.count() > 0) {
            extras.append(" \u00b7 ").append(tagState.count()).append(" tagged");
        }
        if (!cutBuffer.isEmpty()) {
            String label = cutBuffer.mode() == CutBuffer.Mode.MOVE ? "move" : "copy";
            extras.append(" \u00b7 ").append(label).append(" ").append(cutBuffer.count());
        }
        int maxCol = left + width;
        int statsWidth = countText.length() + extras.length();
        int statsStart = maxCol - statsWidth;

        int col = left;
        col = drawChunk(screen, col, row, "  ", Colors.DEFAULT, maxCol);
        col = drawChunk(screen, col, row, "dragonet", Colors.ORANGE, maxCol);
        col = drawChunk(screen, col, row, " \u00b7 ", Colors.GRAY_DIM, maxCol);
        int availableForPath = statsStart - col - 1;
        if (availableForPath >= 1) {
            if (path.length() > availableForPath) {
                path = availableForPath > 1
                        ? path.substring(0, availableForPath - 1) + "\u2026"
                        : path.substring(0, availableForPath);
            }
            drawChunk(screen, col, row, path,
                    previewFocused ? Colors.GRAY : Colors.DEFAULT, maxCol);
        }
        if (statsStart >= col) {
            int statsCol = drawChunk(screen, statsStart, row, countText, Colors.GRAY, maxCol);
            if (extras.length() > 0) {
                drawChunk(screen, statsCol, row, extras.toString(), Colors.ORANGE, maxCol);
            }
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
                           TagState tagState) {
        int listHeight = listBottom - listTop;
        if (listHeight < 1) {
            return;
        }
        TreeRenderer.Segments segments = treeRenderer.chooseSegments(width);
        MetadataFormatter.DisplayPlan plan = MetadataFormatter.planFor(0, width);
        List<VisibleEntry> entries = treeModel.visibleEntries();
        int cursorIndex = treeModel.cursorIndex();
        int scrollOffset = treeModel.adjustScrollOffset(listHeight);
        Instant now = Instant.now();
        for (int offset = 0; offset < listHeight; offset = offset + 1) {
            int entryIndex = scrollOffset + offset;
            if (entryIndex >= entries.size()) {
                break;
            }
            VisibleEntry entry = entries.get(entryIndex);
            boolean tagged = entry.entry() != null
                    && tagState.isTagged(entry.entry().path());
            drawEntry(screen, left, width, listTop + offset, entry,
                    entryIndex == cursorIndex, tagged, segments, plan, now);
        }
    }

    private void drawEntry(Screen screen, int left, int width, int row, VisibleEntry entry,
                            boolean isCursor, boolean isTagged,
                            TreeRenderer.Segments segments,
                            MetadataFormatter.DisplayPlan plan, Instant now) {
        String marker;
        if (isCursor && isTagged) {
            marker = ">*";
        } else if (isCursor) {
            marker = "> ";
        } else if (isTagged) {
            marker = "* ";
        } else {
            marker = "  ";
        }
        TextColor background;
        TextColor markerColor;
        TextColor treeColor;
        TextColor metadataColor;
        boolean bold = isTagged && !isCursor;
        if (isCursor && isTagged) {
            background = Colors.WHITE;
            markerColor = Colors.ORANGE;
            treeColor = Colors.ORANGE;
            metadataColor = Colors.ORANGE;
        } else if (isCursor) {
            background = Colors.ORANGE;
            markerColor = Colors.WHITE;
            treeColor = Colors.WHITE;
            metadataColor = Colors.WHITE;
        } else {
            background = Colors.DEFAULT;
            markerColor = isTagged ? Colors.ORANGE : Colors.GRAY_DIM;
            treeColor = Colors.GRAY;
            metadataColor = Colors.GRAY;
        }
        if (isCursor) {
            fillRow(screen, left, width, row, background);
        }
        int maxCol = left + width;
        int col = left;
        for (int index = 0; index < marker.length() && col < maxCol; index = index + 1) {
            screen.setCharacter(col, row,
                    buildChar(marker.charAt(index), markerColor, background, bold));
            col = col + 1;
        }
        String prefix = treeRenderer.renderPrefix(entry, segments);
        for (int index = 0; index < prefix.length() && col < maxCol; index = index + 1) {
            screen.setCharacter(col, row,
                    buildChar(prefix.charAt(index), treeColor, background, bold));
            col = col + 1;
        }
        String metadata = entry.isPlaceholder() ? "" : buildMetadata(entry.entry(), plan, now);
        int metadataStart = maxCol - metadata.length();
        if (metadataStart < col) {
            metadataStart = col;
            metadata = "";
        }
        String name = entry.isPlaceholder()
                ? entry.placeholderText()
                : displayName(entry.entry());
        TextColor nameColor;
        if (isCursor) {
            nameColor = isTagged ? Colors.ORANGE : Colors.WHITE;
        } else if (entry.isPlaceholder()) {
            nameColor = Colors.GRAY;
        } else {
            nameColor = pickForeground(entry.entry());
        }
        int gap = metadata.isEmpty() ? 0 : 1;
        int nameArea = metadataStart - col - gap;
        if (nameArea > 0) {
            if (name.length() > nameArea) {
                name = nameArea > 1
                        ? name.substring(0, nameArea - 1) + "\u2026"
                        : name.substring(0, Math.min(1, name.length()));
            }
            for (int index = 0; index < name.length() && col < maxCol; index = index + 1) {
                screen.setCharacter(col, row,
                        buildChar(name.charAt(index), nameColor, background, bold));
                col = col + 1;
            }
        }
        while (col < metadataStart && col < maxCol) {
            screen.setCharacter(col, row, buildChar(' ', background, background, false));
            col = col + 1;
        }
        if (!metadata.isEmpty()) {
            for (int index = 0; index < metadata.length() && metadataStart + index < maxCol;
                 index = index + 1) {
                screen.setCharacter(metadataStart + index, row,
                        buildChar(metadata.charAt(index), metadataColor, background, bold));
            }
        }
    }

    private static TextCharacter buildChar(char character, TextColor foreground,
                                            TextColor background, boolean bold) {
        if (bold) {
            return TextCharacter.fromCharacter(character, foreground, background, SGR.BOLD)[0];
        }
        return TextCharacter.fromCharacter(character, foreground, background)[0];
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
                                CutBuffer cutBuffer, boolean previewFocused) {
        int maxCol = left + width;
        int col = left + 2;
        col = drawShortcut(screen, col, row, "^X", " Exit", maxCol);
        if (width >= 40) {
            col = drawShortcut(screen, col, row, "^T", " Tag", maxCol);
        }
        if (width >= 52) {
            col = drawShortcut(screen, col, row, "^L", " Preview", maxCol);
        }
        if (!cutBuffer.isEmpty() && width >= 64) {
            String label = cutBuffer.mode() == CutBuffer.Mode.MOVE ? "move" : "copy";
            col = drawShortcut(screen, col, row, "^U",
                    " Paste (" + label + " " + cutBuffer.count() + ")", maxCol);
        }
        if (width >= 76) {
            col = drawShortcut(screen, col, row, "^D", " Del", maxCol);
        }
        if (width >= 88) {
            col = drawShortcut(screen, col, row, "^R", " Rename", maxCol);
        }
        if (width >= 100) {
            col = drawShortcut(screen, col, row, "^G", " Help", maxCol);
        }
        if (width >= 112) {
            col = drawShortcut(screen, col, row, "^W", " Find", maxCol);
        }
        if (width >= 124) {
            col = drawShortcut(screen, col, row, "^B", " Back", maxCol);
            drawShortcut(screen, col, row, "^F", " Fwd", maxCol);
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
