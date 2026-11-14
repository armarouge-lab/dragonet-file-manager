package br.redhot.dragonet;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextCharacter;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

import java.io.IOException;
import java.util.Objects;

/**
 * Minimal TUI shell for V0.1.0a.
 *
 * <p>Renders a title bar, an empty file area, and a footer with
 * {@code ^X Exit}. The only interactive behavior is exiting on
 * {@code Ctrl-X}. Everything else (file list, navigation, operations)
 * arrives in later commits, per the roadmap.
 *
 * <p>Uses the Lanterna {@code Screen} layer, which double-buffers the
 * terminal and only applies the diff on {@link Screen#refresh()}.
 */
public final class Tui {

    private final Logger logger;

    public Tui(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Opens the TUI and blocks until the user exits.
     *
     * @return {@link ExitCode#EX_OK} on clean exit, {@link ExitCode#EX_OP_FAILED}
     *         if the terminal cannot be initialized
     */
    public ExitCode run() {
        try (Terminal terminal = new DefaultTerminalFactory().createTerminal();
             Screen screen = new TerminalScreen(terminal)) {

            screen.startScreen();
            screen.setCursorPosition(null);   // no visible cursor in this build

            drawScreen(screen);

            while (true) {
                KeyStroke keyStroke = screen.readInput();
                if (isExitKey(keyStroke)) {
                    return ExitCode.EX_OK;
                }
            }
        } catch (IOException exception) {
            logger.fail("could not initialize the terminal", exception);
            logger.fail("check that stdout is a TTY and TERM is set (e.g. xterm-256color)");
            return ExitCode.EX_OP_FAILED;
        }
    }

    private void drawScreen(Screen screen) throws IOException {
        TerminalSize terminalSize = screen.getTerminalSize();
        int columns = terminalSize.getColumns();
        int rows = terminalSize.getRows();

        // Fill the back buffer with a solid background so the terminal's
        // prior contents do not bleed through the first refresh.
        TextCharacter blank = TextCharacter.DEFAULT_CHARACTER;
        for (int column = 0; column < columns; column = column + 1) {
            for (int row = 0; row < rows; row = row + 1) {
                screen.setCharacter(column, row, blank);
            }
        }

        String title = " Dragonet ";
        String footer = " ^X Exit ";

        writeString(screen, 0, 0, title);
        writeString(screen, 0, rows - 1, footer);

        screen.refresh();
    }

    private void writeString(Screen screen, int column, int row, String text) {
        for (int index = 0; index < text.length(); index = index + 1) {
            // fromCharacter returns char[] because a single char may expand to
            // two TextCharacters (surrogate pair). For BMP input the array has
            // exactly one element; this build only renders ASCII.
            screen.setCharacter(column + index, row,
                    TextCharacter.fromCharacter(text.charAt(index))[0]);
        }
    }

    private boolean isExitKey(KeyStroke keyStroke) {
        return keyStroke.getKeyType() == KeyType.EOF
                || (keyStroke.getKeyType() == KeyType.Character
                    && keyStroke.isCtrlDown()
                    && (keyStroke.getCharacter() == 'x'
                        || keyStroke.getCharacter() == 'X'));
    }
}