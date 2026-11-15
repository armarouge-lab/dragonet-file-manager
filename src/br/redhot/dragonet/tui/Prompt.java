package br.redhot.dragonet.tui;

import com.googlecode.lanterna.TextCharacter;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;

import java.util.Objects;

/**
 * Single-line text prompt drawn in the footer.
 *
 * <p>Nano-style editing: arrows, Home/End via ^A/^E, Delete, Backspace,
 * ^D (delete char), ^K (cut to end), ^U (clear). Enter submits, Esc/^C
 * cancels, ^X requests app close.
 *
 * <p>The character logic lives in {@link #handleCharacter(char, boolean, boolean)}
 * so it can be exercised without constructing a Lanterna
 * {@code KeyStroke} (whose char constructors were removed in 3.1.2).
 * {@link #handleKey(KeyStroke)} dispatches real input to it.
 */
public final class Prompt {

    public record Result(Status status, String text) {
        public enum Status { IN_PROGRESS, SUBMITTED, CANCELLED, CLOSE_APP }

        static Result inProgress() {
            return new Result(Status.IN_PROGRESS, "");
        }

        static Result submitted(String text) {
            return new Result(Status.SUBMITTED, text);
        }

        static Result cancelled() {
            return new Result(Status.CANCELLED, "");
        }

        static Result closeApp() {
            return new Result(Status.CLOSE_APP, "");
        }
    }

    private final String question;
    private final StringBuilder buffer;
    private int cursor;

    public Prompt(String question) {
        this(question, "");
    }

    public Prompt(String question, String initialText) {
        this.question = Objects.requireNonNull(question, "question");
        this.buffer = new StringBuilder(Objects.requireNonNull(initialText, "initialText"));
        this.cursor = initialText.length();
    }

    public String question() {
        return question;
    }

    public String text() {
        return buffer.toString();
    }

    public int cursorPosition() {
        return cursor;
    }

    /**
     * Applies a Lanterna keystroke and tells the caller what happened.
     *
     * <p>Character keystrokes are forwarded to
     * {@link #handleCharacter(char, boolean, boolean)}.
     *
     * @param keyStroke the keystroke
     * @return the result (in-progress, submitted, cancelled, close-app)
     */
    public Result handleKey(KeyStroke keyStroke) {
        if (keyStroke == null) {
            return Result.inProgress();
        }
        KeyType keyType = keyStroke.getKeyType();
        if (keyType == KeyType.EOF) {
            return Result.closeApp();
        }
        if (keyType == KeyType.Escape) {
            return Result.cancelled();
        }
        if (keyType == KeyType.Enter) {
            return Result.submitted(buffer.toString());
        }
        if (keyType == KeyType.ArrowLeft) {
            if (cursor > 0) {
                cursor = cursor - 1;
            }
            return Result.inProgress();
        }
        if (keyType == KeyType.ArrowRight) {
            if (cursor < buffer.length()) {
                cursor = cursor + 1;
            }
            return Result.inProgress();
        }
        if (keyType == KeyType.Home) {
            cursor = 0;
            return Result.inProgress();
        }
        if (keyType == KeyType.End) {
            cursor = buffer.length();
            return Result.inProgress();
        }
        if (keyType == KeyType.Delete) {
            if (cursor < buffer.length()) {
                buffer.deleteCharAt(cursor);
            }
            return Result.inProgress();
        }
        if (keyType == KeyType.Backspace) {
            if (cursor > 0) {
                buffer.deleteCharAt(cursor - 1);
                cursor = cursor - 1;
            }
            return Result.inProgress();
        }
        if (keyType == KeyType.Character) {
            return handleCharacter(keyStroke.getCharacter(),
                    keyStroke.isCtrlDown(), keyStroke.isAltDown());
        }
        return Result.inProgress();
    }

    /**
     * Applies a character (with optional Ctrl/Alt) as if it had been
     * typed. This is what real input reaches after
     * {@link #handleKey(KeyStroke)} classifies the keystroke; it is also
     * the entry point used by tests.
     *
     * @param character the character
     * @param ctrlDown  whether Ctrl was held
     * @param altDown   whether Alt was held
     * @return the result
     */
    public Result handleCharacter(char character, boolean ctrlDown, boolean altDown) {
        if (ctrlDown) {
            switch (Character.toLowerCase(character)) {
                case 'a' -> cursor = 0;
                case 'e' -> cursor = buffer.length();
                case 'c' -> {
                    return Result.cancelled();
                }
                case 'd' -> {
                    if (cursor < buffer.length()) {
                        buffer.deleteCharAt(cursor);
                    }
                }
                case 'h' -> {
                    if (cursor > 0) {
                        buffer.deleteCharAt(cursor - 1);
                        cursor = cursor - 1;
                    }
                }
                case 'k' -> buffer.delete(cursor, buffer.length());
                case 'u' -> {
                    buffer.setLength(0);
                    cursor = 0;
                }
                case 'x' -> {
                    return Result.closeApp();
                }
                default -> {
                    // Ignored modifier combo.
                }
            }
            return Result.inProgress();
        }
        if (altDown) {
            return Result.inProgress();
        }
        buffer.insert(cursor, character);
        cursor = cursor + 1;
        return Result.inProgress();
    }

    public void draw(Screen screen, int leftColumn, int width, int inputRow, int hintRow) {
        int maxCol = leftColumn + width;
        TextColor background = Colors.DEFAULT;
        TextColor questionColor = Colors.ORANGE;
        TextColor textColor = Colors.DEFAULT;
        TextColor hintKeyColor = Colors.WHITE;
        TextColor hintBackgroundColor = Colors.ORANGE;

        int col = leftColumn + 1;
        for (int index = 0; index < question.length() && col < maxCol; index = index + 1) {
            screen.setCharacter(col, inputRow,
                    TextCharacter.fromCharacter(question.charAt(index),
                            questionColor, background)[0]);
            col = col + 1;
        }
        for (int index = 0; index < buffer.length() && col < maxCol; index = index + 1) {
            screen.setCharacter(col, inputRow,
                    TextCharacter.fromCharacter(buffer.charAt(index),
                            textColor, background)[0]);
            col = col + 1;
        }

        int hintCol = leftColumn + 1;
        hintCol = drawHint(screen, hintCol, hintRow, "Enter", "Confirm",
                hintKeyColor, hintBackgroundColor, maxCol);
        hintCol = drawHint(screen, hintCol, hintRow, "Esc", "Cancel",
                hintKeyColor, hintBackgroundColor, maxCol);
        if (!buffer.isEmpty()) {
            drawHint(screen, hintCol, hintRow, "^U", "Clear",
                    hintKeyColor, hintBackgroundColor, maxCol);
        }
    }

    private int drawHint(Screen screen, int col, int row, String key, String label,
                          TextColor keyColor, TextColor keyBackground, int maxCol) {
        for (int index = 0; index < key.length() && col < maxCol; index = index + 1) {
            screen.setCharacter(col, row,
                    TextCharacter.fromCharacter(key.charAt(index), keyColor, keyBackground)[0]);
            col = col + 1;
        }
        for (int index = 0; index < label.length() && col < maxCol; index = index + 1) {
            screen.setCharacter(col, row,
                    TextCharacter.fromCharacter(label.charAt(index),
                            Colors.DEFAULT, Colors.DEFAULT)[0]);
            col = col + 1;
        }
        for (int index = 0; index < 2 && col < maxCol; index = index + 1) {
            screen.setCharacter(col, row,
                    TextCharacter.fromCharacter(' ', Colors.DEFAULT, Colors.DEFAULT)[0]);
            col = col + 1;
        }
        return col;
    }

    public int cursorColumnIn(int leftColumn) {
        return leftColumn + 1 + question.length() + cursor;
    }
}