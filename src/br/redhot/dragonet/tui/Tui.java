package br.redhot.dragonet.tui;

import br.redhot.dragonet.ExitCode;
import br.redhot.dragonet.Logger;
import br.redhot.dragonet.config.Config;
import br.redhot.dragonet.config.Session;
import br.redhot.dragonet.env.Environment;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * The TUI loop.
 *
 * <p>Lanterna 3.1.2 exposes only blocking {@code readInput()} and
 * immediate-return {@code pollInput()} on {@link Screen}. The loop
 * uses {@code pollInput()} plus a short {@code Thread.sleep} so that a
 * future directory watcher can piggyback on the same loop without a
 * rewrite. Idle latency is the sleep duration (50 ms here).
 *
 * <p>Navigates on arrow keys, {@code Enter} and {@code Backspace};
 * exits on {@code ^X} or terminal EOF. Everything else is deferred to
 * later commits.
 */
public final class Tui {

    private static final long IDLE_SLEEP_MILLISECONDS = 50L;

    private final Config config;
    private final Session session;
    private final Environment environment;
    private final Logger logger;

    public Tui(Config config, Session session, Environment environment, Logger logger) {
        this.config = Objects.requireNonNull(config, "config");
        this.session = Objects.requireNonNull(session, "session");
        this.environment = Objects.requireNonNull(environment, "environment");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Opens the TUI and blocks until the user exits.
     *
     * @param initialDirectory the directory to open
     * @return {@link ExitCode#EX_OK} on clean exit,
     *         {@link ExitCode#EX_PRECOND} if the directory cannot be read,
     *         {@link ExitCode#EX_OP_FAILED} on terminal failure
     */
    public ExitCode run(Path initialDirectory) {
        Path startDirectory = initialDirectory.toAbsolutePath().normalize();

        FileList fileList;
        try {
            fileList = FileList.load(startDirectory, config);
        } catch (IOException exception) {
            logger.fail("could not read directory: " + startDirectory);
            logger.fail(exception.getMessage());
            return ExitCode.EX_PRECOND;
        }

        try (Terminal terminal = new DefaultTerminalFactory().createTerminal();
             Screen screen = new TerminalScreen(terminal)) {

            screen.startScreen();
            screen.setCursorPosition(null);

            Layout layout = new Layout(config, environment);
            String statusMessage = null;
            boolean running = true;
            boolean needsRedraw = true;

            while (running) {
                if (needsRedraw) {
                    layout.draw(screen, fileList, statusMessage);
                    statusMessage = null;
                    needsRedraw = false;
                }

                KeyStroke keyStroke;
                try {
                    keyStroke = screen.pollInput();
                } catch (IOException exception) {
                    logger.warn("input read failed: " + exception.getMessage());
                    break;
                }

                if (keyStroke == null) {
                    try {
                        Thread.sleep(IDLE_SLEEP_MILLISECONDS);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }

                KeyType keyType = keyStroke.getKeyType();
                if (keyType == KeyType.EOF) {
                    running = false;
                    continue;
                }
                if (keyType == KeyType.Character && keyStroke.isCtrlDown()) {
                    if (Character.toLowerCase(keyStroke.getCharacter()) == 'x') {
                        running = false;
                    }
                    continue;
                }
                if (keyType == KeyType.ArrowDown) {
                    fileList.moveDown();
                    needsRedraw = true;
                    continue;
                }
                if (keyType == KeyType.ArrowUp) {
                    fileList.moveUp();
                    needsRedraw = true;
                    continue;
                }
                if (keyType == KeyType.Enter) {
                    EnterResult result = handleEnter(fileList);
                    fileList = result.fileList();
                    if (result.statusMessage() != null) {
                        statusMessage = result.statusMessage();
                    }
                    needsRedraw = true;
                    continue;
                }
                if (keyType == KeyType.Backspace) {
                    EnterResult result = navigateToParent(fileList);
                    fileList = result.fileList();
                    if (result.statusMessage() != null) {
                        statusMessage = result.statusMessage();
                    }
                    needsRedraw = true;
                    continue;
                }
            }

            try {
                session.save(environment.statePath("session.conf"));
            } catch (IOException exception) {
                logger.warn("could not save session: " + exception.getMessage());
            }

            return ExitCode.EX_OK;
        } catch (IOException exception) {
            logger.fail("terminal error: " + exception.getMessage());
            return ExitCode.EX_OP_FAILED;
        }
    }

    private EnterResult handleEnter(FileList fileList) {
        FileEntry current = fileList.current();
        if (current == null) {
            return new EnterResult(fileList, null);
        }
        if (current.isParent()) {
            return navigateToParent(fileList);
        }
        if (current.isNavigable()) {
            try {
                return new EnterResult(FileList.load(current.path(), config), null);
            } catch (IOException exception) {
                return new EnterResult(fileList,
                        "Cannot enter " + current.name() + ": " + exception.getMessage());
            }
        }
        if (current.isBrokenSymlink()) {
            return new EnterResult(fileList,
                    "Broken link: " + current.name() + " -> " + current.symlinkTarget());
        }
        return new EnterResult(fileList,
                "Open not implemented yet: " + current.name());
    }

    private EnterResult navigateToParent(FileList fileList) {
        Path currentDirectory = fileList.directory();
        Path parent = currentDirectory.getParent();
        if (parent == null) {
            return new EnterResult(fileList, null);
        }
        Path currentName = currentDirectory.getFileName();
        try {
            FileList parentList = FileList.load(parent, config);
            if (currentName != null) {
                parentList.moveToEntryNamed(currentName.toString());
            }
            return new EnterResult(parentList, null);
        } catch (IOException exception) {
            return new EnterResult(fileList,
                    "Cannot go up: " + exception.getMessage());
        }
    }

    private record EnterResult(FileList fileList, String statusMessage) {}
}