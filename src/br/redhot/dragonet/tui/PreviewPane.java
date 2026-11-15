package br.redhot.dragonet.tui;

import com.googlecode.lanterna.TextCharacter;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.screen.Screen;

import java.util.List;

/**
 * Renders a {@link PreviewModel.Preview} into a rectangular area.
 *
 * <p>The whole pane rectangle is filled with blanks before anything
 * else is drawn, so in overlay mode the file list underneath never
 * shows through when the preview content is shorter than the viewport.
 *
 * <p>The draw method returns the clamped scroll offset actually used.
 * Callers must store this value; without it, the caller's scroll
 * counter grows unbounded and small content appears stuck.
 */
public final class PreviewPane {

    private PreviewPane() {
        // static utility
    }

    /**
     * Renders the preview, returning the clamped scroll offset.
     *
     * @return the scroll offset actually used; the caller should store
     *         this to keep scrolling bounded
     */
    public static int draw(Screen screen, PreviewModel.Preview preview,
                            int left, int top, int width, int height,
                            int scrollOffset, boolean focused, boolean showBorder) {
        if (screen == null || preview == null || width < 4 || height < 2) {
            return 0;
        }

        // Fill the entire pane rectangle with blank characters so the
        // overlay never bleeds through to whatever was underneath.
        TextCharacter blank = TextCharacter.fromCharacter(
                ' ', Colors.DEFAULT, Colors.DEFAULT)[0];
        for (int row = 0; row < height; row = row + 1) {
            for (int column = 0; column < width; column = column + 1) {
                screen.setCharacter(left + column, top + row, blank);
            }
        }

        int contentLeft = left;
        int contentWidth = width;
        if (showBorder) {
            TextColor borderColor = focused ? Colors.ORANGE : Colors.GRAY_DIM;
            TextCharacter border = TextCharacter.fromCharacter(
                    '|', borderColor, Colors.DEFAULT)[0];
            for (int row = 0; row < height; row = row + 1) {
                screen.setCharacter(left, top + row, border);
            }
            contentLeft = left + 2;
            contentWidth = width - 2;
        }
        if (contentWidth < 2) {
            return 0;
        }

        List<String> lines = preview.lines();
        int lineCount = lines.size();
        int clampedScroll = Math.max(0, Math.min(scrollOffset,
                Math.max(0, lineCount - height)));
        for (int row = 0; row < height; row = row + 1) {
            int lineIndex = clampedScroll + row;
            if (lineIndex >= lineCount) {
                break;
            }
            String line = lines.get(lineIndex);
            for (int column = 0; column < contentWidth && column < line.length();
                 column = column + 1) {
                char character = line.charAt(column);
                screen.setCharacter(contentLeft + column, top + row,
                        TextCharacter.fromCharacter(character,
                                Colors.DEFAULT, Colors.DEFAULT)[0]);
            }
        }
        if (!preview.footer().isEmpty() && height >= 2) {
            String footer = preview.footer();
            int footerRow = top + height - 1;
            for (int column = 0; column < contentWidth && column < footer.length();
                 column = column + 1) {
                screen.setCharacter(contentLeft + column, footerRow,
                        TextCharacter.fromCharacter(footer.charAt(column),
                                Colors.GRAY, Colors.DEFAULT)[0]);
            }
        }
        if (focused && lineCount > height) {
            int indicatorRow = top + (height * clampedScroll)
                    / Math.max(1, lineCount - height);
            if (indicatorRow >= top + height) {
                indicatorRow = top + height - 1;
            }
            screen.setCharacter(left + width - 1, indicatorRow,
                    TextCharacter.fromCharacter('#', Colors.ORANGE, Colors.DEFAULT)[0]);
        }
        return clampedScroll;
    }
}
