package br.redhot.dragonet.tui;

import br.redhot.dragonet.config.Config;
import br.redhot.dragonet.env.Environment;
import br.redhot.dragonet.env.PathResolver;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextCharacter;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.screen.Screen;

import java.io.IOException;
import java.util.Objects;

/**
 * The three-part layout: title bar, file list, footer.
 *
 * <p>Draws to a Lanterna {@link Screen} and never reads from the
 * filesystem; all data comes from {@link FileList}. The name
 * {@code Layout} avoids the collision between the spec's
 * {@code tui.Screen} and Lanterna's {@code Screen}.
 */
public final class Layout {

    private static final int SIZE_COLUMN_WIDTH = 7;

    private final Config config;
    private final Environment environment;

    public Layout(Config config, Environment environment) {
        this.config = Objects.requireNonNull(config, "config");
        this.environment = Objects.requireNonNull(environment, "environment");
    }

    /**
     * Renders a full frame and calls {@link Screen#refresh()}.
     *
     * @param screen        the Lanterna screen
     * @param fileList      the list to render
     * @param statusMessage a transient message to show above the
     *                      shortcut bar, or {@code null}
     * @throws IOException if the screen refuses the update
     */
    public void draw(Screen screen, FileList fileList, String statusMessage) throws IOException {
        TerminalSize size = screen.getTerminalSize();
        int columns = size.getColumns();
        int rows = size.getRows();

        fillBackground(screen, columns, rows);
        drawTitle(screen, columns, fileList);
        drawList(screen, columns, rows, fileList);
        drawFooter(screen, columns, rows, statusMessage);

        screen.refresh();
    }

    private void fillBackground(Screen screen, int columns, int rows) {
        TextCharacter blank = TextCharacter.DEFAULT_CHARACTER;
        for (int column = 0; column < columns; column = column + 1) {
            for (int row = 0; row < rows; row = row + 1) {
                screen.setCharacter(column, row, blank);
            }
        }
    }

    private void drawTitle(Screen screen, int columns, FileList fileList) {
        String directoryDisplay = PathResolver.formatForDisplay(
                fileList.directory(), environment.homeDirectory());
        int childCount = fileList.entries().size();
        if (!fileList.entries().isEmpty() && fileList.entries().get(0).isParent()) {
            childCount = childCount - 1;
        }
        String title = " Dragonet — " + directoryDisplay + " — " + childCount + " items";
        writeString(screen, 0, 0, title, columns);
    }

    private void drawList(Screen screen, int columns, int rows, FileList fileList) {
        int listTop = 1;
        int listHeight = rows - 3;
        if (listHeight < 1) {
            return;
        }
        int cursorIndex = fileList.cursorIndex();
        int scrollOffset = Math.max(0, cursorIndex - listHeight + 1);
        if (cursorIndex < scrollOffset) {
            scrollOffset = cursorIndex;
        }
        for (int offset = 0; offset < listHeight; offset = offset + 1) {
            int entryIndex = scrollOffset + offset;
            if (entryIndex >= fileList.entries().size()) {
                break;
            }
            FileEntry entry = fileList.entries().get(entryIndex);
            drawEntry(screen, columns, listTop + offset, entry, entryIndex == cursorIndex);
        }
    }

    private void drawEntry(Screen screen, int columns, int row, FileEntry entry, boolean isCursor) {
        String marker = isCursor ? "> " : "  ";
        String name = displayName(entry);
        String size = formatSize(entry);

        int nameWidth = columns - marker.length() - SIZE_COLUMN_WIDTH - 1;
        if (nameWidth < 1) {
            nameWidth = 1;
        }
        if (name.length() > nameWidth) {
            if (nameWidth > 1) {
                name = name.substring(0, nameWidth - 1) + "…";
            } else {
                name = name.substring(0, 1);
            }
        }

        StringBuilder lineBuilder = new StringBuilder();
        lineBuilder.append(marker);
        lineBuilder.append(name);
        for (int padding = 0; padding < nameWidth - name.length() + 1; padding = padding + 1) {
            lineBuilder.append(' ');
        }
        lineBuilder.append(String.format("%" + SIZE_COLUMN_WIDTH + "s", size));
        String line = lineBuilder.toString();

        TextColor foreground = pickForeground(entry);
        TextColor background = TextColor.ANSI.DEFAULT;
        if (isCursor) {
            TextColor temporary = foreground;
            foreground = background;
            background = temporary;
        }

        for (int column = 0; column < columns; column = column + 1) {
            char character = column < line.length() ? line.charAt(column) : ' ';
            screen.setCharacter(column, row,
                    TextCharacter.fromCharacter(character, foreground, background)[0]);
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
            return TextColor.ANSI.RED;
        }
        return switch (entry.type()) {
            case DIRECTORY -> TextColor.ANSI.BLUE;
            case SYMLINK -> TextColor.ANSI.CYAN;
            case FILE -> entry.isExecutable() ? TextColor.ANSI.GREEN : TextColor.ANSI.DEFAULT;
            case OTHER -> TextColor.ANSI.YELLOW;
        };
    }

    private void drawFooter(Screen screen, int columns, int rows, String statusMessage) {
        if (rows < 2) {
            return;
        }
        int statusRow = rows - 2;
        int shortcutRow = rows - 1;
        if (statusMessage != null) {
            writeString(screen, 0, statusRow, " " + statusMessage, columns);
        }
        writeString(screen, 0, shortcutRow, shortcutsForWidth(columns), columns);
    }

    private String shortcutsForWidth(int columns) {
        if (columns >= 80) {
            return " ^X Exit   ↑↓ Navigate   Enter Open   Backspace Parent";
        }
        if (columns >= 40) {
            return " ^X Exit  ↑↓ Nav  Enter  Bksp";
        }
        return " ^X  ↑↓  Enter";
    }

    private void writeString(Screen screen, int column, int row, String text, int columns) {
        for (int index = 0; index < text.length() && column + index < columns; index = index + 1) {
            screen.setCharacter(column + index, row,
                    TextCharacter.fromCharacter(text.charAt(index))[0]);
        }
    }
}