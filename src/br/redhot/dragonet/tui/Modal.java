package br.redhot.dragonet.tui;

import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextCharacter;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

import java.util.Objects;

/**
 * Simple {@code [y/N]} confirmation modal.
 *
 * <p>Centered box drawn over the current frame. Only responds to
 * {@code y}/{@code Y} (confirm), {@code n}/{@code N}/{@code Esc}/Enter
 * (cancel). No focus, no tab order, no multi-button.
 */
public final class Modal {

    public record Result(boolean active, boolean confirmed) {
        static Result inProgress() {
            return new Result(true, false);
        }

        static Result resolved(boolean confirmed) {
            return new Result(false, confirmed);
        }
    }

    private final String message;

    public Modal(String message) {
        this.message = Objects.requireNonNull(message, "message");
    }

    public String message() {
        return message;
    }

    public Result handleKey(KeyStroke keyStroke) {
        if (keyStroke == null) {
            return Result.inProgress();
        }
        KeyType keyType = keyStroke.getKeyType();
        if (keyType == KeyType.Escape) {
            return Result.resolved(false);
        }
        if (keyType == KeyType.Enter) {
            return Result.resolved(false);
        }
        if (keyType == KeyType.EOF) {
            return Result.resolved(false);
        }
        if (keyType == KeyType.Character) {
            char character = Character.toLowerCase(keyStroke.getCharacter());
            if (character == 'y') {
                return Result.resolved(true);
            }
            if (character == 'n' || character == 'c') {
                return Result.resolved(false);
            }
        }
        return Result.inProgress();
    }

    public void draw(Screen screen) {
        TerminalSize size = screen.getTerminalSize();
        int columns = size.getColumns();
        int rows = size.getRows();

        int innerWidth = message.length() + 4;
        int boxWidth = Math.min(innerWidth + 2, columns - 4);
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
        TextColor textColor = Colors.DEFAULT;
        TextColor hintColor = Colors.GRAY;

        // Borders (ASCII box)
        for (int x = 0; x < boxWidth; x = x + 1) {
            boolean isEdge = x == 0 || x == boxWidth - 1;
            char glyph = isEdge ? '+' : '-';
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
        }
        // Interior blank
        for (int y = 1; y < boxHeight - 1; y = y + 1) {
            for (int x = 1; x < boxWidth - 1; x = x + 1) {
                screen.setCharacter(boxLeft + x, boxTop + y,
                        TextCharacter.fromCharacter(' ', textColor, Colors.DEFAULT)[0]);
            }
        }

        // Message line (row 1 of interior)
        int messageCol = boxLeft + (boxWidth - message.length()) / 2;
        if (messageCol < boxLeft + 1) {
            messageCol = boxLeft + 1;
        }
        for (int i = 0; i < message.length()
                && messageCol + i < boxLeft + boxWidth - 1; i = i + 1) {
            screen.setCharacter(messageCol + i, boxTop + 1,
                    TextCharacter.fromCharacter(message.charAt(i),
                            textColor, Colors.DEFAULT)[0]);
        }

        // Hint line (row 2 of interior)
        String hint = "[y] yes  [n] no";
        int hintCol = boxLeft + (boxWidth - hint.length()) / 2;
        if (hintCol < boxLeft + 1) {
            hintCol = boxLeft + 1;
        }
        for (int i = 0; i < hint.length()
                && hintCol + i < boxLeft + boxWidth - 1; i = i + 1) {
            screen.setCharacter(hintCol + i, boxTop + 2,
                    TextCharacter.fromCharacter(hint.charAt(i),
                            hintColor, Colors.DEFAULT)[0]);
        }

        // Cursor: hidden during modal
        screen.setCursorPosition((TerminalPosition) null);
    }
}