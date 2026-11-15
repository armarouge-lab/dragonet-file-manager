package br.redhot.dragonet.tui;

import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextCharacter;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

import java.util.Objects;

/**
 * A centered menu modal that returns the first printable character
 * the user presses. The caller decides which characters are valid.
 *
 * <p>Used by V0.3a for the tag menu ({@code u}, {@code i}, {@code c}).
 * Esc, EOF, or Enter close the modal with {@code '\0'}; any other key
 * that is not a plain character is ignored.
 */
public final class MenuModal {

    public record Result(boolean active, char choice) {
        static Result open() {
            return new Result(true, '\0');
        }

        static Result chosen(char choice) {
            return new Result(false, choice);
        }

        static Result cancelled() {
            return new Result(false, '\0');
        }
    }

    private final String title;
    private final String options;

    public MenuModal(String title, String options) {
        this.title = Objects.requireNonNull(title, "title");
        this.options = Objects.requireNonNull(options, "options");
    }

    public Result handleKey(KeyStroke keyStroke) {
        if (keyStroke == null) {
            return Result.open();
        }
        KeyType keyType = keyStroke.getKeyType();
        if (keyType == KeyType.Escape || keyType == KeyType.EOF
                || keyType == KeyType.Enter) {
            return Result.cancelled();
        }
        if (keyType == KeyType.Character && !keyStroke.isCtrlDown()) {
            return Result.chosen(keyStroke.getCharacter());
        }
        return Result.open();
    }

    public void draw(Screen screen) {
        TerminalSize size = screen.getTerminalSize();
        int columns = size.getColumns();
        int rows = size.getRows();

        int widestLine = Math.max(title.length(), options.length());
        int innerWidth = widestLine + 2;
        int boxWidth = Math.min(innerWidth + 2, columns - 4);
        if (boxWidth < 10) {
            boxWidth = 10;
        }
        int boxHeight = 4;
        int boxLeft = (columns - boxWidth) / 2;
        int boxTop = (rows - boxHeight) / 2;
        if (boxTop < 0) {
            boxTop = 0;
        }

        TextColor borderColor = Colors.ORANGE;
        TextColor titleColor = Colors.DEFAULT;
        TextColor optionsColor = Colors.GRAY;

        // Top and bottom borders
        for (int x = 0; x < boxWidth; x = x + 1) {
            char glyph = (x == 0 || x == boxWidth - 1) ? '+' : '-';
            screen.setCharacter(boxLeft + x, boxTop,
                    TextCharacter.fromCharacter(glyph, borderColor, Colors.DEFAULT)[0]);
            screen.setCharacter(boxLeft + x, boxTop + boxHeight - 1,
                    TextCharacter.fromCharacter(glyph, borderColor, Colors.DEFAULT)[0]);
        }
        // Left and right borders
        for (int y = 1; y < boxHeight - 1; y = y + 1) {
            screen.setCharacter(boxLeft, boxTop + y,
                    TextCharacter.fromCharacter('|', borderColor, Colors.DEFAULT)[0]);
            screen.setCharacter(boxLeft + boxWidth - 1, boxTop + y,
                    TextCharacter.fromCharacter('|', borderColor, Colors.DEFAULT)[0]);
            for (int x = 1; x < boxWidth - 1; x = x + 1) {
                screen.setCharacter(boxLeft + x, boxTop + y,
                        TextCharacter.fromCharacter(' ', Colors.DEFAULT, Colors.DEFAULT)[0]);
            }
        }

        drawCentered(screen, boxLeft, boxWidth, boxTop + 1, title, titleColor);
        drawCentered(screen, boxLeft, boxWidth, boxTop + 2, options, optionsColor);

        screen.setCursorPosition(null);
    }

    private static void drawCentered(Screen screen, int boxLeft, int boxWidth, int row,
                                     String text, TextColor color) {
        int startColumn = boxLeft + (boxWidth - text.length()) / 2;
        if (startColumn < boxLeft + 1) {
            startColumn = boxLeft + 1;
        }
        for (int index = 0; index < text.length()
                && startColumn + index < boxLeft + boxWidth - 1; index = index + 1) {
            screen.setCharacter(startColumn + index, row,
                    TextCharacter.fromCharacter(text.charAt(index),
                            color, Colors.DEFAULT)[0]);
        }
    }
}
