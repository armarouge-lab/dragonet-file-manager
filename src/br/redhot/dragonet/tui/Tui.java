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

public final class Tui {

    private static final long IDLE_SLEEP_MILLISECONDS = 50L;

    private final Config config;
    private final Environment environment;
    private final Logger logger;
    private Session session;

    public Tui(Config config, Session session, Environment environment, Logger logger) {
        this.config = Objects.requireNonNull(config, "config");
        this.session = Objects.requireNonNull(session, "session");
        this.environment = Objects.requireNonNull(environment, "environment");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public ExitCode run(Path initialDirectory) {
        Path startDirectory = initialDirectory.toAbsolutePath().normalize();

        TreeModel treeModel;
        try {
            FileList baseList = FileList.load(startDirectory, config);
            treeModel = new TreeModel(baseList, config);
        } catch (IOException exception) {
            logger.fail("could not read directory: " + startDirectory);
            logger.fail(exception.getMessage());
            return ExitCode.EX_PRECOND;
        }

        try (Terminal terminal = new DefaultTerminalFactory().createTerminal();
             Screen screen = new TerminalScreen(terminal)) {

            screen.startScreen();
            screen.setCursorPosition(null);

            TreeRenderer treeRenderer = new TreeRenderer(config, environment.supportsUtf8());
            Layout layout = new Layout(config, environment, treeRenderer);

            long lastRenderedVersion = -1L;
            String statusMessage = null;
            boolean running = true;

            while (running) {
                long currentVersion = treeModel.version();
                if (currentVersion != lastRenderedVersion) {
                    layout.draw(screen, treeModel, session.infoLevel(), statusMessage);
                    statusMessage = null;
                    lastRenderedVersion = currentVersion;
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
                if (keyType == KeyType.Character && keyStroke.isAltDown()) {
                    char character = Character.toLowerCase(keyStroke.getCharacter());
                    if (character == 'i') {
                        int columns = screen.getTerminalSize().getColumns();
                        if (columns < 40) {
                            statusMessage = "terminal too narrow for metadata";
                            lastRenderedVersion = -1L;
                        } else {
                            int currentLevel = MetadataFormatter.effectiveLevel(
                                    session.infoLevel(), columns);
                            int next = MetadataFormatter.nextLevel(currentLevel, columns);
                            session = session.withInfoLevel(next);
                            lastRenderedVersion = -1L;
                        }
                    }
                    continue;
                }
                if (keyType == KeyType.ArrowDown) {
                    treeModel.moveDown();
                    continue;
                }
                if (keyType == KeyType.ArrowUp) {
                    treeModel.moveUp();
                    continue;
                }
                if (keyType == KeyType.ArrowRight) {
                    treeModel.expandCurrent();
                    continue;
                }
                if (keyType == KeyType.ArrowLeft) {
                    NavigateResult result = handleArrowLeft(treeModel);
                    if (result.treeModel != treeModel) {
                        treeModel = result.treeModel;
                        lastRenderedVersion = -1L;
                    }
                    if (result.statusMessage != null) {
                        statusMessage = result.statusMessage;
                        lastRenderedVersion = -1L;
                    }
                    continue;
                }
                if (keyType == KeyType.Enter) {
                    NavigateResult result = handleEnter(treeModel);
                    if (result.treeModel != treeModel) {
                        treeModel = result.treeModel;
                        lastRenderedVersion = -1L;
                    }
                    if (result.statusMessage != null) {
                        statusMessage = result.statusMessage;
                        lastRenderedVersion = -1L;
                    }
                    continue;
                }
                if (keyType == KeyType.Backspace) {
                    NavigateResult result = navigateToParent(treeModel);
                    if (result.treeModel != treeModel) {
                        treeModel = result.treeModel;
                        lastRenderedVersion = -1L;
                    }
                    if (result.statusMessage != null) {
                        statusMessage = result.statusMessage;
                        lastRenderedVersion = -1L;
                    }
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

    private NavigateResult handleArrowLeft(TreeModel treeModel) {
        VisibleEntry current = treeModel.current();
        if (current == null) {
            return new NavigateResult(treeModel, null);
        }
        if (current.depth() > 0) {
            if (current.entry() != null && current.entry().isDirectory()
                    && treeModel.isExpanded(current.entry().path())) {
                treeModel.collapseCurrent();
            } else {
                treeModel.moveCursorToDepth(current.depth() - 1);
            }
            return new NavigateResult(treeModel, null);
        }
        return navigateToParent(treeModel);
    }

    private NavigateResult handleEnter(TreeModel treeModel) {
        VisibleEntry current = treeModel.current();
        if (current == null || current.entry() == null) {
            return new NavigateResult(treeModel, null);
        }
        FileEntry entry = current.entry();
        if (entry.isParent()) {
            return navigateToParent(treeModel);
        }
        if (entry.isNavigable()) {
            try {
                FileList baseList = FileList.load(entry.path(), config);
                return new NavigateResult(new TreeModel(baseList, config), null);
            } catch (IOException exception) {
                return new NavigateResult(treeModel,
                        "Cannot enter " + entry.name() + ": " + exception.getMessage());
            }
        }
        if (entry.isBrokenSymlink()) {
            return new NavigateResult(treeModel,
                    "Broken link: " + entry.name() + " -> " + entry.symlinkTarget());
        }
        return new NavigateResult(treeModel,
                "Open not implemented yet: " + entry.name());
    }

    private NavigateResult navigateToParent(TreeModel treeModel) {
        Path currentDirectory = treeModel.directory();
        Path parent = currentDirectory.getParent();
        if (parent == null) {
            return new NavigateResult(treeModel, null);
        }
        Path currentName = currentDirectory.getFileName();
        try {
            FileList parentList = FileList.load(parent, config);
            TreeModel newModel = new TreeModel(parentList, config);
            if (currentName != null) {
                newModel.moveToEntryNamed(currentName.toString());
            }
            return new NavigateResult(newModel, null);
        } catch (IOException exception) {
            return new NavigateResult(treeModel,
                    "Cannot go up: " + exception.getMessage());
        }
    }

    private record NavigateResult(TreeModel treeModel, String statusMessage) {}
}