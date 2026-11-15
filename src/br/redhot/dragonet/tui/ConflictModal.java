package br.redhot.dragonet.tui;

import br.redhot.dragonet.engine.ConflictResolution;

import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextCharacter;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

import java.util.Objects;

/**
 * Modal asking how to resolve a destination conflict during a paste.
 *
 * <p>Lowercase letters apply to the current file; uppercase letters
 * apply to the current file and all remaining files in the batch.
 * Esc/Enter abort.
 */
public final class ConflictModal {

    public record Result(boolean active, ConflictResolution resolution) {
        static Result open() {
            return new Result(true, null);
        }

        static Result resolved(ConflictResolution resolution) {
            return new Result(false, resolution);
        }
    }

    private final String message;

    public ConflictModal(String conflictName) {
        this.message = "Conflict: " + Objects.requireNonNull(conflictName, "conflictName");
    }

    public Result handleKey(KeyStroke keyStroke) {
        if (keyStroke == null) {
            return Result.open();
        }
        KeyType keyType = keyStroke.getKeyType();
        if (keyType == KeyType.Escape || keyType == KeyType.EOF || keyType == KeyType.Enter) {
            return Result.resolved(ConflictResolution.ABORT);
        }
        if (keyType != KeyType.Character) {
            return Result.open();
        }
        return switch (keyStroke.getCharacter()) {
            case 'o' -> Result.resolved(ConflictResolution.OVERWRITE);
            case 's' -> Result.resolved(ConflictResolution.SKIP);
            case 'r' -> Result.resolved(ConflictResolution.RENAME);
            case 'a' -> Result.resolved(ConflictResolution.ABORT);
            case 'O' -> Result.resolved(ConflictResolution.OVERWRITE_ALL);
            case 'S' -> Result.resolved(ConflictResolution.SKIP_ALL);
            default -> Result.open();
        };
    }

    public void draw(Screen screen) {
        TerminalSize size = screen.getTerminalSize();
        int columns = size.getColumns();
        int rows = size.getRows();

        String hint = "[o]verwrite [s]kip [r]ename [a]bort  (cap = all)";
        int widestLine = Math.max(message.length(), hint.length());
        int boxWidth = Math.min(widestLine + 4, columns - 4);
        if (boxWidth < 10) {
            boxWidth = 10;
        }
        int boxHeight = 5;
        int boxLeft = (columns - boxWidth) / 2;
        int boxTop = (rows - boxHeight) / 2;
        if (boxTop < 0) {
            boxTop = 0;
        }

        TextColor borderColor = Colors.ORANGE;
        TextColor messageColor = Colors.DEFAULT;
        TextColor hintColor = Colors.GRAY;

        for (int x = 0; x < boxWidth; x = x + 1) {
            char glyph = (x == 0 || x == boxWidth - 1) ? '+' : '-';
            screen.setCharacter(boxLeft + x, boxTop,
                    TextCharacter.fromCharacter(glyph, borderColor, Colors.DEFAULT)[0]);
            screen.setCharacter(boxLeft + x, boxTop + boxHeight - 1,
                    TextCharacter.fromCharacter(glyph, borderColor, Colors.DEFAULT)[0]);
        }
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
        drawCentered(screen, boxLeft, boxWidth, boxTop + 1, message, messageColor);
        drawCentered(screen, boxLeft, boxWidth, boxTop + 2, hint, hintColor);
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
