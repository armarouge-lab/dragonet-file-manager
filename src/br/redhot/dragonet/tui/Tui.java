package br.redhot.dragonet.tui;

import br.redhot.dragonet.ExitCode;
import br.redhot.dragonet.Logger;
import br.redhot.dragonet.config.Bookmarks;
import br.redhot.dragonet.config.Config;
import br.redhot.dragonet.config.History;
import br.redhot.dragonet.config.Session;
import br.redhot.dragonet.engine.ConflictResolution;
import br.redhot.dragonet.engine.CopyOperation;
import br.redhot.dragonet.engine.CreateDirectoryOperation;
import br.redhot.dragonet.engine.CreateFileOperation;
import br.redhot.dragonet.engine.CreateSymlinkOperation;
import br.redhot.dragonet.engine.MoveOperation;
import br.redhot.dragonet.engine.Operation;
import br.redhot.dragonet.engine.OperationResult;
import br.redhot.dragonet.engine.PermanentDeleteOperation;
import br.redhot.dragonet.engine.RenameOperation;
import br.redhot.dragonet.env.Environment;
import br.redhot.dragonet.trash.TrashStore;
import br.redhot.dragonet.undo.UndoEntry;
import br.redhot.dragonet.undo.UndoLog;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public final class Tui {

    private static final long IDLE_SLEEP_MILLISECONDS = 50L;
    private static final int PREVIEW_MIN_TOTAL_WIDTH = 100;
    private static final int PREVIEW_MIN_ROWS = 10;

    private final Config config;
    private final Environment environment;
    private final Logger logger;
    private final TrashStore trashStore;
    private final UndoLog undoLog;
    private final PreviewModel previewModel;
    private final FileOpener fileOpener;
    private final Bookmarks bookmarks;
    private final History history;
    private final TagState tagState = new TagState();
    private final CutBuffer cutBuffer = new CutBuffer();
    private Session session;

    private Screen screen;
    private TreeModel treeModel;
    private long lastRenderedVersion = -1L;
    private String statusMessage;
    private Prompt activePrompt;
    private Consumer<String> pendingPromptAction;
    private Modal activeModal;
    private Consumer<Boolean> pendingModalAction;
    private MenuModal activeMenuModal;
    private Consumer<Character> pendingMenuAction;
    private ConflictModal activeConflictModal;
    private Consumer<ConflictResolution> pendingConflictAction;
    private String pendingSymlinkName;
    private PasteSession pendingPaste;
    private boolean shouldExit;
    private boolean previewVisible;
    private boolean previewFocused;
    private int previewScroll;
    private boolean searchPromptActive;
    private boolean navigatingHistory;

    public Tui(Config config, Session session, Environment environment, Logger logger) {
        this.config = Objects.requireNonNull(config, "config");
        this.session = Objects.requireNonNull(session, "session");
        this.environment = Objects.requireNonNull(environment, "environment");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.trashStore = new TrashStore(environment.trashPath(), environment.homeDirectory());
        this.undoLog = new UndoLog(environment.statePath("undo.log"), config.undoLimit());
        this.previewModel = new PreviewModel(config.previewMaxBytes());
        this.fileOpener = new FileOpener(environment.hasImp(), config.impMode());
        this.bookmarks = Bookmarks.load(environment.userConfigPath("bookmarks.conf"));
        this.history = History.load(environment.statePath("history"), config.historyLimit());
        this.previewVisible = session.preview();
        this.previewFocused = false;
        this.previewScroll = 0;
        this.searchPromptActive = false;
        this.navigatingHistory = false;
    }

    public ExitCode run(Path initialDirectory) {
        Path startDirectory = initialDirectory.toAbsolutePath().normalize();

        try {
            FileList baseList = FileList.load(startDirectory, config);
            treeModel = new TreeModel(baseList, config);
        } catch (IOException exception) {
            logger.fail("could not read directory: " + startDirectory);
            logger.fail(exception.getMessage());
            return ExitCode.EX_PRECOND;
        }
        recordVisit(startDirectory);

        try (Terminal terminal = new DefaultTerminalFactory().createTerminal()) {
            this.screen = new TerminalScreen(terminal);
            screen.startScreen();
            disableSignalChars();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    session.save(environment.statePath("session.conf"));
                } catch (IOException ignored) {
                    // best-effort on shutdown
                }
                try {
                    history.save();
                } catch (IOException ignored) {
                    // best-effort on shutdown
                }
            }));

            TreeRenderer treeRenderer = new TreeRenderer(config, environment.supportsUtf8());
            Layout layout = new Layout(config, environment, treeRenderer);
            boolean running = true;

            while (running) {
                TerminalSize resized = screen.doResizeIfNecessary();
                if (resized != null) {
                    lastRenderedVersion = -1L;
                }

                if (treeModel.version() != lastRenderedVersion) {
                    previewScroll = layout.draw(screen, treeModel, tagState, cutBuffer,
                            session.infoLevel(), statusMessage, activePrompt, activeModal,
                            activeMenuModal, activeConflictModal, previewModel,
                            previewVisible, previewFocused, previewScroll);
                    statusMessage = null;
                    lastRenderedVersion = treeModel.version();
                }

                if (shouldExit) {
                    running = false;
                    continue;
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

                if (activeConflictModal != null) {
                    ConflictModal.Result result = activeConflictModal.handleKey(keyStroke);
                    if (!result.active()) {
                        Consumer<ConflictResolution> action = pendingConflictAction;
                        activeConflictModal = null;
                        pendingConflictAction = null;
                        lastRenderedVersion = -1L;
                        if (action != null) {
                            action.accept(result.resolution());
                        }
                    }
                    continue;
                }

                if (activeMenuModal != null) {
                    MenuModal.Result result = activeMenuModal.handleKey(keyStroke);
                    if (!result.active()) {
                        Consumer<Character> action = pendingMenuAction;
                        activeMenuModal = null;
                        pendingMenuAction = null;
                        lastRenderedVersion = -1L;
                        if (action != null) {
                            action.accept(result.choice());
                        }
                    }
                    continue;
                }

                if (activeModal != null) {
                    Modal.Result result = activeModal.handleKey(keyStroke);
                    if (!result.active()) {
                        Consumer<Boolean> action = pendingModalAction;
                        activeModal = null;
                        pendingModalAction = null;
                        lastRenderedVersion = -1L;
                        if (action != null) {
                            action.accept(result.confirmed());
                        }
                    }
                    continue;
                }

                if (activePrompt != null) {
                    Prompt.Result result = activePrompt.handleKey(keyStroke);
                    Prompt.Result.Status status = result.status();
                    if (status == Prompt.Result.Status.CLOSE_APP) {
                        running = false;
                    } else if (status == Prompt.Result.Status.CANCELLED) {
                        if (searchPromptActive) {
                            treeModel.setSearchQuery("");
                            searchPromptActive = false;
                        }
                        activePrompt = null;
                        pendingPromptAction = null;
                        pendingSymlinkName = null;
                        lastRenderedVersion = -1L;
                    } else if (status == Prompt.Result.Status.SUBMITTED) {
                        String text = result.text();
                        if (searchPromptActive) {
                            searchPromptActive = false;
                            activePrompt = null;
                            pendingPromptAction = null;
                            treeModel.setSearchQuery(text);
                            lastRenderedVersion = -1L;
                        } else {
                            Consumer<String> action = pendingPromptAction;
                            activePrompt = null;
                            pendingPromptAction = null;
                            lastRenderedVersion = -1L;
                            if (action != null) {
                                action.accept(text);
                            }
                        }
                    } else {
                        if (searchPromptActive) {
                            treeModel.setSearchQuery(activePrompt.text());
                        }
                        lastRenderedVersion = -1L;
                    }
                    continue;
                }

                running = handleIdleKey(keyStroke);
            }

            try {
                session.save(environment.statePath("session.conf"));
            } catch (IOException exception) {
                logger.warn("could not save session: " + exception.getMessage());
            }
            try {
                history.save();
            } catch (IOException exception) {
                logger.warn("could not save history: " + exception.getMessage());
            }

            return ExitCode.EX_OK;
        } catch (IOException exception) {
            logger.fail("terminal error: " + exception.getMessage());
            return ExitCode.EX_OP_FAILED;
        } finally {
            restoreSignalChars();
            this.screen = null;
        }
    }

    private boolean handleIdleKey(KeyStroke keyStroke) {
        KeyType keyType = keyStroke.getKeyType();

        if (previewFocused) {
            if (keyType == KeyType.Escape || keyType == KeyType.Tab) {
                previewFocused = false;
                if (isNarrowTerminal()) {
                    previewVisible = false;
                }
                lastRenderedVersion = -1L;
                return true;
            }
            if (keyType == KeyType.ArrowDown) {
                previewScroll = previewScroll + 1;
                lastRenderedVersion = -1L;
                return true;
            }
            if (keyType == KeyType.ArrowUp) {
                previewScroll = Math.max(0, previewScroll - 1);
                lastRenderedVersion = -1L;
                return true;
            }
            if (keyType == KeyType.PageDown) {
                previewScroll = previewScroll + 20;
                lastRenderedVersion = -1L;
                return true;
            }
            if (keyType == KeyType.PageUp) {
                previewScroll = Math.max(0, previewScroll - 20);
                lastRenderedVersion = -1L;
                return true;
            }
            boolean passThrough = keyType == KeyType.Character && keyStroke.isCtrlDown()
                    && (Character.toLowerCase(keyStroke.getCharacter()) == 'x'
                        || Character.toLowerCase(keyStroke.getCharacter()) == 'l');
            if (!passThrough) {
                return true;
            }
        }

        if (keyType == KeyType.EOF) {
            return false;
        }
        if (keyType == KeyType.Escape) {
            handleCancel();
            return true;
        }
        if (keyType == KeyType.Tab) {
            if (previewVisible) {
                previewFocused = !previewFocused;
                lastRenderedVersion = -1L;
            }
            return true;
        }
        if (keyType == KeyType.Character && keyStroke.isCtrlDown()) {
            char character = Character.toLowerCase(keyStroke.getCharacter());
            switch (character) {
                case 'x' -> { return handleExitRequest(); }
                case 'g' -> { showHelp(); return true; }
                case 'c' -> { handleCancel(); return true; }
                case 'w' -> { startSearch(); return true; }
                case 'l' -> { togglePreview(); return true; }
                case 'b' -> { navigateBack(); return true; }
                case 'f' -> { navigateForward(); return true; }
                case 'd' -> { startTrash(); return true; }
                case 'p' -> { startPermanentDelete(); return true; }
                case 'r' -> { startRename(); return true; }
                case 't' -> { handleTagToggle(); return true; }
                case 'a' -> { handleTagAllShortcut(); return true; }
                case 'n' -> { startTouch(); return true; }
                case 'o' -> { startOpenWith(); return true; }
                default -> { return true; }
            }
        }
        if (keyType == KeyType.Character && keyStroke.isAltDown()) {
            char character = Character.toLowerCase(keyStroke.getCharacter());
            switch (character) {
                case 'c' -> { startCut(CutBuffer.Mode.COPY); return true; }
                case 'v' -> { startPaste(); return true; }
                case 'x' -> { startCut(CutBuffer.Mode.MOVE); return true; }
                case 'e' -> { startEdit(); return true; }
                case 'm' -> { startMkdir(); return true; }
                case 's' -> { startSymlink(); return true; }
                case 'h' -> { startHidden(); return true; }
                case 'i' -> { toggleInfoLevel(); return true; }
                case 'b' -> { handleBookmarkMenu(); return true; }
                case 'z' -> { runShell(); return true; }
                case 'u' -> { handleUndo(); return true; }
                default -> {
                    // Ignored.
                }
            }
            return true;
        }
        if (keyType == KeyType.Character && keyStroke.getCharacter() == ' ') {
            handleTagToggle();
            return true;
        }
        if (keyType == KeyType.Character) {
            char character = keyStroke.getCharacter();
            if (treeModel.isFiltering()) {
                if (character == 'n') {
                    moveToNextMatch(true);
                    return true;
                }
                if (character == 'N') {
                    moveToNextMatch(false);
                    return true;
                }
            }
            return true;
        }
        if (keyType == KeyType.ArrowDown) {
            treeModel.moveDown();
            previewScroll = 0;
            return true;
        }
        if (keyType == KeyType.ArrowUp) {
            treeModel.moveUp();
            previewScroll = 0;
            return true;
        }
        if (keyType == KeyType.ArrowRight) {
            treeModel.expandCurrent();
            return true;
        }
        if (keyType == KeyType.ArrowLeft) {
            applyNavigateResult(handleArrowLeft());
            previewScroll = 0;
            return true;
        }
        if (keyType == KeyType.Enter) {
            applyNavigateResult(handleEnter());
            previewScroll = 0;
            return true;
        }
        if (keyType == KeyType.Backspace) {
            applyNavigateResult(navigateToParent());
            previewScroll = 0;
            return true;
        }
        return true;
    }

    // ---- Search ----

    private void startSearch() {
        searchPromptActive = true;
        activePrompt = new Prompt("Find: ", treeModel.searchQuery());
        lastRenderedVersion = -1L;
    }

    private void moveToNextMatch(boolean forward) {
        List<VisibleEntry> entries = treeModel.visibleEntries();
        if (entries.isEmpty()) {
            return;
        }
        int currentIndex = treeModel.cursorIndex();
        int count = entries.size();
        int step = forward ? 1 : -1;
        int nextIndex = ((currentIndex + step) % count + count) % count;
        treeModel.moveToIndex(nextIndex);
        lastRenderedVersion = -1L;
    }

    // ---- Open with / dispatch ----

    private void startOpenWith() {
        VisibleEntry current = treeModel.current();
        if (current == null || current.entry() == null || current.entry().isParent()) {
            return;
        }
        activePrompt = new Prompt("Open with: ");
        pendingPromptAction = (text) -> {
            if (text.isBlank()) {
                statusMessage = "Cancelled";
                lastRenderedVersion = -1L;
                return;
            }
            runUserCommand(text);
            lastRenderedVersion = -1L;
        };
        lastRenderedVersion = -1L;
    }

    private void startEdit() {
        VisibleEntry current = treeModel.current();
        if (current == null || current.entry() == null || current.entry().isParent()) {
            return;
        }
        FileOpener.Resolution resolution = fileOpener.resolveForEdit(current.entry());
        executeResolution(resolution);
    }

    private void executeResolution(FileOpener.Resolution resolution) {
        if (resolution.mode() == FileOpener.Mode.UNAVAILABLE) {
            statusMessage = resolution.message();
            lastRenderedVersion = -1L;
            return;
        }
        FileOpener.Result result;
        if (resolution.mode() == FileOpener.Mode.BLOCK) {
            result = runWithSuspendedScreen(
                    () -> fileOpener.runBlocking(resolution, treeModel.directory()));
        } else {
            result = fileOpener.runDetached(resolution, treeModel.directory());
        }
        statusMessage = result.message();
        lastRenderedVersion = -1L;
    }

    private void runUserCommand(String userInput) {
        FileOpener.Resolution resolution;
        try {
            resolution = fileOpener.resolveForCommand(userInput, treeModel.directory());
        } catch (IllegalArgumentException exception) {
            statusMessage = exception.getMessage();
            lastRenderedVersion = -1L;
            return;
        }
        executeResolution(resolution);
    }

    private void handleEnterOpen(FileEntry entry) {
        if (entry.isExecutable()) {
            activeModal = new Modal("Run '" + entry.name() + "'?");
            pendingModalAction = (confirmed) -> {
                if (confirmed) {
                    FileOpener.Result result = runWithSuspendedScreen(
                            () -> fileOpener.runBlocking(
                                    new FileOpener.Resolution(
                                            List.of(entry.path().toString()),
                                            FileOpener.Mode.BLOCK,
                                            "Running " + entry.name()),
                                    treeModel.directory()));
                    statusMessage = result.message();
                } else {
                    statusMessage = "Cancelled";
                }
                lastRenderedVersion = -1L;
            };
            lastRenderedVersion = -1L;
            return;
        }
        FileOpener.Resolution resolution = fileOpener.resolve(entry);
        executeResolution(resolution);
    }

    // ---- Bookmarks ----

    private void handleBookmarkMenu() {
        activeMenuModal = new MenuModal("Bookmarks", "a:add  p:pick  d:delete");
        pendingMenuAction = (choice) -> {
            switch (choice) {
                case 'a' -> startAddBookmark();
                case 'p' -> startPickBookmark();
                case 'd' -> startDeleteBookmark();
                default -> {
                    statusMessage = "Cancelled";
                    lastRenderedVersion = -1L;
                }
            }
        };
        lastRenderedVersion = -1L;
    }

    private void startAddBookmark() {
        Path currentDirectory = treeModel.directory();
        String defaultName = currentDirectory.getFileName() == null
                ? "bookmark" : currentDirectory.getFileName().toString();
        activePrompt = new Prompt("Bookmark name: ", defaultName);
        pendingPromptAction = (name) -> {
            if (name.isBlank()) {
                statusMessage = "Cancelled";
                lastRenderedVersion = -1L;
                return;
            }
            bookmarks.add(name, currentDirectory);
            try {
                bookmarks.save();
                statusMessage = "Bookmarked: " + name;
            } catch (IOException exception) {
                statusMessage = "Save failed: " + exception.getMessage();
            }
            lastRenderedVersion = -1L;
        };
        lastRenderedVersion = -1L;
    }

    private void startPickBookmark() {
        List<String> names = bookmarks.names();
        if (names.isEmpty()) {
            statusMessage = "No bookmarks";
            lastRenderedVersion = -1L;
            return;
        }
        StringBuilder options = new StringBuilder();
        for (int index = 0; index < names.size() && index < 26; index = index + 1) {
            char letter = (char) ('a' + index);
            if (options.length() > 0) {
                options.append("  ");
            }
            options.append(letter).append(':').append(names.get(index));
        }
        activeMenuModal = new MenuModal("Pick bookmark", options.toString());
        pendingMenuAction = (choice) -> {
            int index = choice - 'a';
            if (index < 0 || index >= names.size()) {
                statusMessage = "Cancelled";
                lastRenderedVersion = -1L;
                return;
            }
            String name = names.get(index);
            Optional<Path> target = bookmarks.get(name);
            if (target.isEmpty()) {
                statusMessage = "Missing bookmark: " + name;
                lastRenderedVersion = -1L;
                return;
            }
            navigateTo(target.get(), false);
        };
        lastRenderedVersion = -1L;
    }

    private void startDeleteBookmark() {
        List<String> names = bookmarks.names();
        if (names.isEmpty()) {
            statusMessage = "No bookmarks";
            lastRenderedVersion = -1L;
            return;
        }
        StringBuilder options = new StringBuilder();
        for (int index = 0; index < names.size() && index < 26; index = index + 1) {
            char letter = (char) ('a' + index);
            if (options.length() > 0) {
                options.append("  ");
            }
            options.append(letter).append(':').append(names.get(index));
        }
        activeMenuModal = new MenuModal("Delete bookmark", options.toString());
        pendingMenuAction = (choice) -> {
            int index = choice - 'a';
            if (index < 0 || index >= names.size()) {
                statusMessage = "Cancelled";
                lastRenderedVersion = -1L;
                return;
            }
            String name = names.get(index);
            bookmarks.remove(name);
            try {
                bookmarks.save();
                statusMessage = "Removed: " + name;
            } catch (IOException exception) {
                statusMessage = "Save failed: " + exception.getMessage();
            }
            lastRenderedVersion = -1L;
        };
        lastRenderedVersion = -1L;
    }

    // ---- History ----

    private void navigateBack() {
        Optional<Path> target = history.back();
        if (target.isEmpty()) {
            statusMessage = "History: no back";
            lastRenderedVersion = -1L;
            return;
        }
        navigatingHistory = true;
        navigateTo(target.get(), true);
    }

    private void navigateForward() {
        Optional<Path> target = history.forward();
        if (target.isEmpty()) {
            statusMessage = "History: no forward";
            lastRenderedVersion = -1L;
            return;
        }
        navigatingHistory = true;
        navigateTo(target.get(), true);
    }

    private void navigateTo(Path directory, boolean fromHistory) {
        if (!Files.isDirectory(directory)) {
            statusMessage = "Not a directory: " + directory;
            lastRenderedVersion = -1L;
            return;
        }
        try {
            FileList baseList = FileList.load(directory, config);
            treeModel = new TreeModel(baseList, config);
            previewScroll = 0;
            if (!fromHistory) {
                recordVisit(directory);
            }
            navigatingHistory = false;
            lastRenderedVersion = -1L;
        } catch (IOException exception) {
            statusMessage = "Cannot open: " + exception.getMessage();
            lastRenderedVersion = -1L;
        }
    }

    private void recordVisit(Path directory) {
        history.visit(directory);
    }

    // ---- Shell suspend ----

    private void runShell() {
        String shell = System.getenv("SHELL");
        if (shell == null || shell.isBlank()) {
            shell = "/bin/sh";
        }
        FileOpener.Resolution resolution = new FileOpener.Resolution(
                List.of(shell), FileOpener.Mode.BLOCK, "Shell: " + shell);
        FileOpener.Result result = runWithSuspendedScreen(
                () -> fileOpener.runBlocking(resolution, treeModel.directory()));
        statusMessage = result.message();
        lastRenderedVersion = -1L;
    }

    @FunctionalInterface
    private interface ScreenSuspendedCall {
        FileOpener.Result run();
    }

    private FileOpener.Result runWithSuspendedScreen(ScreenSuspendedCall call) {
        if (screen == null) {
            return new FileOpener.Result("no active screen");
        }
        try {
            restoreSignalChars();
            screen.stopScreen();
        } catch (IOException exception) {
            return new FileOpener.Result("Cannot suspend TUI: " + exception.getMessage());
        }
        FileOpener.Result result = new FileOpener.Result("unknown");
        try {
            result = call.run();
        } finally {
            try {
                screen.startScreen();
                disableSignalChars();
            } catch (IOException exception) {
                logger.warn("could not resume screen: " + exception.getMessage());
            }
            lastRenderedVersion = -1L;
        }
        return result;
    }

    // ---- Preview ----

    private boolean isNarrowTerminal() {
        TerminalSize size = screen.getTerminalSize();
        return size.getColumns() < PREVIEW_MIN_TOTAL_WIDTH || size.getRows() < PREVIEW_MIN_ROWS;
    }

    private void togglePreview() {
        if (isNarrowTerminal()) {
            if (previewVisible && previewFocused) {
                previewVisible = false;
                previewFocused = false;
                previewScroll = 0;
                statusMessage = "Preview: off";
            } else {
                previewVisible = true;
                previewFocused = true;
                previewScroll = 0;
                statusMessage = "Preview: on (Esc to close)";
            }
        } else {
            previewVisible = !previewVisible;
            if (!previewVisible) {
                previewFocused = false;
                previewScroll = 0;
            }
            statusMessage = previewVisible ? "Preview: on" : "Preview: off";
        }
        session = session.withPreview(previewVisible);
        lastRenderedVersion = -1L;
    }

    private void disableSignalChars() {
        boolean ok = sttyOn("/dev/tty", "-isig") || sttyInherited("-isig");
        if (!ok) {
            logger.warn("stty -isig failed on both /dev/tty and INHERIT. "
                    + "Ctrl+C will send SIGINT and close the app. "
                    + "Workaround: run 'stty -isig' in another terminal while the app is open.");
        }
    }

    private void restoreSignalChars() {
        sttyOn("/dev/tty", "isig");
        sttyInherited("isig");
    }

    private boolean sttyOn(String ttyPath, String argument) {
        java.io.File tty = new java.io.File(ttyPath);
        if (!tty.exists() || !tty.canRead()) {
            return false;
        }
        try {
            ProcessBuilder builder = new ProcessBuilder("stty", argument);
            builder.redirectInput(tty);
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);
            int exitCode = builder.start().waitFor();
            return exitCode == 0;
        } catch (IOException exception) {
            logger.debug("stty " + argument + " via " + ttyPath + " failed: "
                    + exception.getMessage());
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean sttyInherited(String argument) {
        try {
            ProcessBuilder builder = new ProcessBuilder("stty", argument);
            builder.redirectInput(ProcessBuilder.Redirect.INHERIT);
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);
            return builder.start().waitFor() == 0;
        } catch (IOException exception) {
            logger.debug("stty " + argument + " via INHERIT failed: "
                    + exception.getMessage());
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void showHelp() {
        activeModal = new Modal("Redhot Help not installed. Install: pkg install redhot-help");
        pendingModalAction = (ignored) -> {
            lastRenderedVersion = -1L;
        };
        lastRenderedVersion = -1L;
    }

    private boolean handleExitRequest() {
        if (cutBuffer.isEmpty() && tagState.count() == 0) {
            return false;
        }
        StringBuilder description = new StringBuilder();
        if (!cutBuffer.isEmpty()) {
            String label = cutBuffer.mode() == CutBuffer.Mode.MOVE ? "cut" : "copy";
            description.append(cutBuffer.count()).append(" in ").append(label).append("buffer");
        }
        if (tagState.count() > 0) {
            if (description.length() > 0) {
                description.append(" and ");
            }
            description.append(tagState.count()).append(" tagged");
        }
        description.append(". Exit anyway?");
        activeModal = new Modal(description.toString());
        pendingModalAction = (confirmed) -> {
            if (confirmed) {
                shouldExit = true;
            }
            lastRenderedVersion = -1L;
        };
        lastRenderedVersion = -1L;
        return true;
    }

    private void applyNavigateResult(NavigateResult result) {
        if (result.treeModel != treeModel) {
            treeModel = result.treeModel;
            lastRenderedVersion = -1L;
        }
        if (result.statusMessage != null) {
            statusMessage = result.statusMessage;
            lastRenderedVersion = -1L;
        }
    }

    private void toggleInfoLevel() {
        int columns = screen.getTerminalSize().getColumns();
        if (columns < 40) {
            statusMessage = "terminal too narrow for metadata";
            lastRenderedVersion = -1L;
            return;
        }
        int currentLevel = MetadataFormatter.effectiveLevel(session.infoLevel(), columns);
        int next = MetadataFormatter.nextLevel(currentLevel, columns);
        session = session.withInfoLevel(next);
        lastRenderedVersion = -1L;
    }

    private void handleUndo() {
        Optional<UndoEntry> maybeEntry;
        try {
            maybeEntry = undoLog.lastUndoable();
        } catch (IOException exception) {
            statusMessage = "Undo failed: " + exception.getMessage();
            lastRenderedVersion = -1L;
            return;
        }
        if (maybeEntry.isEmpty()) {
            statusMessage = "Nothing to undo";
            lastRenderedVersion = -1L;
            return;
        }
        UndoEntry entry = maybeEntry.get();
        OperationResult result = performUndo(entry);
        statusMessage = result.message();
        if (result.success()) {
            try {
                undoLog.append(UndoEntry.undo(entry.epochSeconds(), Instant.now().getEpochSecond()));
            } catch (IOException exception) {
                logger.warn("could not write undo marker: " + exception.getMessage());
            }
            reloadPreservingCursor();
        }
        lastRenderedVersion = -1L;
    }

    private OperationResult performUndo(UndoEntry entry) {
        switch (entry.kind()) {
            case MOVE, RENAME -> {
                String sourceString = entry.arguments().get("source");
                String targetString = entry.arguments().get("target");
                if (sourceString == null || targetString == null) {
                    return OperationResult.failure("undo entry is missing source/target");
                }
                Path source = Path.of(sourceString);
                Path target = Path.of(targetString);
                if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    return OperationResult.failure("file no longer exists: " + target);
                }
                return new MoveOperation(target, source, environment.homeDirectory(), false).execute();
            }
            case TRASH -> {
                String uuid = entry.arguments().get("uuid");
                if (uuid == null) {
                    return OperationResult.failure("undo entry is missing uuid");
                }
                return trashStore.restore(uuid);
            }
            case MKDIR -> {
                String pathString = entry.arguments().get("path");
                if (pathString == null) {
                    return OperationResult.failure("undo entry is missing path");
                }
                Path path = Path.of(pathString);
                if (!Files.isDirectory(path)) {
                    return OperationResult.failure("directory no longer exists: " + path);
                }
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                    if (stream.iterator().hasNext()) {
                        return OperationResult.failure("directory not empty: " + path);
                    }
                } catch (IOException exception) {
                    return OperationResult.failure("cannot inspect directory: " + exception.getMessage());
                }
                try {
                    Files.delete(path);
                    return OperationResult.success("Removed: " + path.getFileName());
                } catch (IOException exception) {
                    return OperationResult.failure("delete failed: " + exception.getMessage());
                }
            }
            case UNDO -> {
                return OperationResult.failure("cannot undo an undo");
            }
            default -> {
                return OperationResult.failure("unknown undo entry kind");
            }
        }
    }

    private void startCut(CutBuffer.Mode mode) {
        List<Path> selected = collectSelectedPaths();
        if (selected.isEmpty()) {
            statusMessage = "Nothing to " + (mode == CutBuffer.Mode.MOVE ? "cut" : "copy");
            lastRenderedVersion = -1L;
            return;
        }
        cutBuffer.set(mode, selected);
        String verb = mode == CutBuffer.Mode.MOVE ? "Cut" : "Copy";
        statusMessage = verb + ": " + selected.size()
                + (selected.size() == 1 ? " item" : " items")
                + " — navigate to target, then ^U";
        lastRenderedVersion = -1L;
    }

    private List<Path> collectSelectedPaths() {
        Set<Path> paths = new LinkedHashSet<>(tagState.paths());
        if (paths.isEmpty()) {
            VisibleEntry current = treeModel.current();
            if (current != null && current.entry() != null && !current.entry().isParent()) {
                paths.add(current.entry().path());
            }
        }
        return new ArrayList<>(paths);
    }

    private void startPaste() {
        if (cutBuffer.isEmpty()) {
            statusMessage = "Nothing to paste";
            lastRenderedVersion = -1L;
            return;
        }
        pendingPaste = new PasteSession(cutBuffer.mode(),
                new ArrayList<>(cutBuffer.paths()));
        processNextPasteItem();
    }

    private void processNextPasteItem() {
        if (pendingPaste == null) {
            return;
        }
        if (pendingPaste.remaining.isEmpty()) {
            finishPaste();
            return;
        }
        Path source = pendingPaste.remaining.get(0);
        String name = source.getFileName() == null ? "" : source.getFileName().toString();
        Path target = treeModel.directory().resolve(name);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            if (pendingPaste.overwriteAll) {
                executePasteItem(source, target, true);
                return;
            }
            if (pendingPaste.skipAll) {
                pendingPaste.remaining.remove(0);
                processNextPasteItem();
                return;
            }
            activeConflictModal = new ConflictModal(name);
            pendingConflictAction = (resolution) -> {
                if (resolution == null) {
                    finishPasteAborted();
                    return;
                }
                switch (resolution) {
                    case OVERWRITE -> executePasteItem(source, target, true);
                    case SKIP -> {
                        pendingPaste.remaining.remove(0);
                        processNextPasteItem();
                    }
                    case RENAME -> promptRenameForConflict(source);
                    case ABORT -> finishPasteAborted();
                    case OVERWRITE_ALL -> {
                        pendingPaste.overwriteAll = true;
                        executePasteItem(source, target, true);
                    }
                    case SKIP_ALL -> {
                        pendingPaste.skipAll = true;
                        pendingPaste.remaining.remove(0);
                        processNextPasteItem();
                    }
                    default -> finishPasteAborted();
                }
            };
            lastRenderedVersion = -1L;
            return;
        }
        executePasteItem(source, target, false);
    }

    private void promptRenameForConflict(Path source) {
        String current = source.getFileName() == null ? "" : source.getFileName().toString();
        activePrompt = new Prompt("New name: ", current);
        pendingPromptAction = (newName) -> {
            if (newName.isBlank()) {
                finishPasteAborted();
                return;
            }
            Path target = treeModel.directory().resolve(newName);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                statusMessage = "Exists: " + newName;
                pendingPaste.remaining.remove(0);
                processNextPasteItem();
                return;
            }
            executePasteItem(source, target, false);
        };
        lastRenderedVersion = -1L;
    }

    private void executePasteItem(Path source, Path target, boolean overwrite) {
        if (CopyOperation.isSparse(source) && !pendingPaste.sparseConfirmed) {
            activeModal = new Modal("Sparse file. Copying may use full logical size. Continue?");
            pendingModalAction = (confirmed) -> {
                if (confirmed) {
                    pendingPaste.sparseConfirmed = true;
                    runPasteOperation(source, target, overwrite);
                } else {
                    finishPasteAborted();
                }
            };
            lastRenderedVersion = -1L;
            return;
        }
        runPasteOperation(source, target, overwrite);
    }

    private void runPasteOperation(Path source, Path target, boolean overwrite) {
        boolean isMove = pendingPaste.mode == CutBuffer.Mode.MOVE;
        OperationResult result = isMove
                ? new MoveOperation(source, target, environment.homeDirectory(), overwrite).execute()
                : new CopyOperation(source, target, environment.homeDirectory(), overwrite).execute();
        if (result.success()) {
            pendingPaste.successCount = pendingPaste.successCount + 1;
            if (isMove) {
                try {
                    undoLog.append(UndoEntry.move(source, target, Instant.now().getEpochSecond()));
                } catch (IOException exception) {
                    logger.warn("could not log move to undo: " + exception.getMessage());
                }
            }
        } else {
            pendingPaste.failureCount = pendingPaste.failureCount + 1;
        }
        pendingPaste.remaining.remove(0);
        processNextPasteItem();
    }

    private void finishPaste() {
        int success = pendingPaste.successCount;
        int failure = pendingPaste.failureCount;
        String verb = pendingPaste.mode == CutBuffer.Mode.MOVE ? "Moved" : "Copied";
        statusMessage = failure == 0
                ? verb + " " + success
                : verb + " " + success + ", " + failure + " failed";
        cutBuffer.clear();
        pendingPaste = null;
        reloadPreservingCursor();
    }

    private void finishPasteAborted() {
        statusMessage = "Paste aborted";
        pendingPaste = null;
        lastRenderedVersion = -1L;
    }

    private void handleTagToggle() {
        VisibleEntry current = treeModel.current();
        if (current == null || current.entry() == null) {
            return;
        }
        FileEntry entry = current.entry();
        if (entry.isParent()) {
            statusMessage = "Cannot tag ..";
            lastRenderedVersion = -1L;
            return;
        }
        if (tagState.isTagged(entry.path())) {
            tagState.untag(entry.path());
            statusMessage = "Untagged: " + entry.name();
        } else {
            tagState.toggle(entry.path(), entry.sizeBytes());
            statusMessage = "Tagged: " + entry.name();
        }
        treeModel.moveDown();
        lastRenderedVersion = -1L;
    }

    private void handleTagMenu() {
        activeMenuModal = new MenuModal("Tag menu", "a:all  i:invert  u:untag  c:clear");
        pendingMenuAction = (choice) -> {
            switch (choice) {
                case 'a' -> {
                    List<FileEntry> entries = getCurrentLevelEntries();
                    tagState.tagAll(entries);
                    statusMessage = "Tagged " + entries.size() + " entries";
                }
                case 'i' -> {
                    tagState.invertAll(getCurrentLevelEntries());
                    statusMessage = "Inverted tags at current level";
                }
                case 'u' -> {
                    tagState.untagAll(getCurrentLevelEntries());
                    statusMessage = "Untagged all at current level";
                }
                case 'c' -> {
                    tagState.clear();
                    statusMessage = "Cleared all tags";
                }
                default -> statusMessage = "Cancelled";
            }
            lastRenderedVersion = -1L;
        };
        lastRenderedVersion = -1L;
    }

    private void handleTagAllShortcut() {
        List<FileEntry> entries = getCurrentLevelEntries();
        tagState.tagAll(entries);
        statusMessage = "Tagged " + entries.size() + " entries";
        lastRenderedVersion = -1L;
    }

    private void handleCancel() {
        if (treeModel.isFiltering()) {
            treeModel.setSearchQuery("");
            statusMessage = "Search cleared";
            lastRenderedVersion = -1L;
            return;
        }
        if (tagState.count() > 0) {
            activeModal = new Modal("Clear " + tagState.count() + " tagged items?");
            pendingModalAction = (confirmed) -> {
                if (confirmed) {
                    tagState.clear();
                    statusMessage = "Cleared all tags";
                } else {
                    statusMessage = "Cancelled";
                }
                lastRenderedVersion = -1L;
            };
            lastRenderedVersion = -1L;
            return;
        }
        statusMessage = "Nothing to cancel";
        lastRenderedVersion = -1L;
    }

    private void startHidden() {
        // Hidden toggle: reload the list with show_hidden flipped in session.
        // Session doesn't carry show_hidden in V0.7; use config.showHidden()
        // flipped temporarily would require a rebuild. For V0.7 we just
        // report that the toggle is not yet wired (dívida para V0.8).
        statusMessage = "Hidden toggle: not yet implemented (V0.8)";
        lastRenderedVersion = -1L;
    }

    private void handleClearTags() {
        if (tagState.count() == 0) {
            return;
        }
        activeModal = new Modal("Clear " + tagState.count() + " tagged items?");
        pendingModalAction = (confirmed) -> {
            if (confirmed) {
                tagState.clear();
                statusMessage = "Cleared all tags";
            } else {
                statusMessage = "Cancelled";
            }
            lastRenderedVersion = -1L;
        };
        lastRenderedVersion = -1L;
    }

    private List<FileEntry> getCurrentLevelEntries() {
        List<FileEntry> result = new ArrayList<>();
        for (FileEntry entry : treeModel.baseEntries()) {
            if (!entry.isParent()) {
                result.add(entry);
            }
        }
        return result;
    }

    private void startMkdir() {
        Path currentDirectory = treeModel.directory();
        activePrompt = new Prompt("New directory name: ");
        pendingPromptAction = (text) -> {
            if (text.isBlank()) {
                statusMessage = "Cancelled";
                lastRenderedVersion = -1L;
                return;
            }
            Path target = currentDirectory.resolve(text);
            Operation operation = new CreateDirectoryOperation(target, environment.homeDirectory());
            OperationResult result = operation.execute();
            statusMessage = result.message();
            if (result.success()) {
                try {
                    undoLog.append(UndoEntry.mkdir(target, Instant.now().getEpochSecond()));
                } catch (IOException exception) {
                    logger.warn("could not log mkdir to undo: " + exception.getMessage());
                }
                reloadSelectingName(text);
            }
            lastRenderedVersion = -1L;
        };
        lastRenderedVersion = -1L;
    }

    private void startTouch() {
        Path currentDirectory = treeModel.directory();
        activePrompt = new Prompt("New file name: ");
        pendingPromptAction = (text) -> {
            if (text.isBlank()) {
                statusMessage = "Cancelled";
                lastRenderedVersion = -1L;
                return;
            }
            Operation operation = new CreateFileOperation(
                    currentDirectory.resolve(text), environment.homeDirectory());
            OperationResult result = operation.execute();
            statusMessage = result.message();
            if (result.success()) {
                reloadSelectingName(text);
            }
            lastRenderedVersion = -1L;
        };
        lastRenderedVersion = -1L;
    }

    private void startSymlink() {
        Path currentDirectory = treeModel.directory();
        activePrompt = new Prompt("Symlink name: ");
        pendingPromptAction = (linkName) -> {
            if (linkName.isBlank()) {
                statusMessage = "Cancelled";
                lastRenderedVersion = -1L;
                return;
            }
            pendingSymlinkName = linkName;
            activePrompt = new Prompt("Symlink target: ");
            pendingPromptAction = (targetText) -> {
                String captured = pendingSymlinkName;
                pendingSymlinkName = null;
                if (targetText.isBlank() || captured == null) {
                    statusMessage = "Cancelled";
                    lastRenderedVersion = -1L;
                    return;
                }
                Operation operation = new CreateSymlinkOperation(
                        currentDirectory.resolve(captured),
                        Path.of(targetText),
                        environment.homeDirectory());
                OperationResult result = operation.execute();
                statusMessage = result.message();
                if (result.success()) {
                    reloadSelectingName(captured);
                }
                lastRenderedVersion = -1L;
            };
            lastRenderedVersion = -1L;
        };
        lastRenderedVersion = -1L;
    }

    private void startRename() {
        VisibleEntry currentEntry = treeModel.current();
        if (currentEntry == null || currentEntry.entry() == null) {
            return;
        }
        FileEntry target = currentEntry.entry();
        if (target.isParent()) {
            statusMessage = "Cannot rename ..";
            lastRenderedVersion = -1L;
            return;
        }
        activePrompt = new Prompt("Rename '" + target.name() + "': ", target.name());
        pendingPromptAction = (newName) -> {
            if (newName.isBlank() || newName.equals(target.name())) {
                statusMessage = "No change";
                lastRenderedVersion = -1L;
                return;
            }
            Path newPath = target.path().resolveSibling(newName);
            Operation operation = new RenameOperation(
                    target.path(), newName, environment.homeDirectory(), false);
            OperationResult result = operation.execute();
            if (result.targetExists()) {
                activeModal = new Modal("'" + newName + "' exists. Overwrite?");
                pendingModalAction = (confirmed) -> {
                    if (confirmed) {
                        Operation overwrite = new RenameOperation(
                                target.path(), newName,
                                environment.homeDirectory(), true);
                        OperationResult overwriteResult = overwrite.execute();
                        statusMessage = overwriteResult.message();
                        if (overwriteResult.success()) {
                            logRenameUndo(target.path(), newPath);
                            reloadSelectingName(newName);
                        }
                    } else {
                        statusMessage = "Cancelled";
                    }
                    lastRenderedVersion = -1L;
                };
            } else {
                statusMessage = result.message();
                if (result.success()) {
                    logRenameUndo(target.path(), newPath);
                    reloadSelectingName(newName);
                }
            }
            lastRenderedVersion = -1L;
        };
        lastRenderedVersion = -1L;
    }

    private void logRenameUndo(Path oldPath, Path newPath) {
        try {
            undoLog.append(UndoEntry.rename(oldPath, newPath, Instant.now().getEpochSecond()));
        } catch (IOException exception) {
            logger.warn("could not log rename to undo: " + exception.getMessage());
        }
    }

    private void startTrash() {
        VisibleEntry currentEntry = treeModel.current();
        if (currentEntry == null || currentEntry.entry() == null) {
            return;
        }
        FileEntry target = currentEntry.entry();
        if (target.isParent()) {
            statusMessage = "Cannot delete ..";
            lastRenderedVersion = -1L;
            return;
        }
        activeModal = new Modal("Trash '" + target.name() + "'?");
        pendingModalAction = (confirmed) -> {
            if (!confirmed) {
                statusMessage = "Cancelled";
                lastRenderedVersion = -1L;
                return;
            }
            OperationResult result = trashStore.moveToTrash(target.path());
            statusMessage = result.message();
            if (result.success()) {
                tagState.untag(target.path());
                trashStore.findLatestUuidFor(target.path()).ifPresent(uuid -> {
                    try {
                        undoLog.append(UndoEntry.trash(target.path(), uuid,
                                Instant.now().getEpochSecond()));
                    } catch (IOException exception) {
                        logger.warn("could not log trash to undo: " + exception.getMessage());
                    }
                });
                reloadPreservingCursor();
            }
            lastRenderedVersion = -1L;
        };
        lastRenderedVersion = -1L;
    }

    private void startPermanentDelete() {
        VisibleEntry currentEntry = treeModel.current();
        if (currentEntry == null || currentEntry.entry() == null) {
            return;
        }
        FileEntry target = currentEntry.entry();
        if (target.isParent()) {
            statusMessage = "Cannot delete ..";
            lastRenderedVersion = -1L;
            return;
        }
        activePrompt = new Prompt(
                "Type YES to permanently delete '" + target.name() + "': ");
        pendingPromptAction = (text) -> {
            if (!"YES".equals(text)) {
                statusMessage = "Cancelled";
                lastRenderedVersion = -1L;
                return;
            }
            Operation operation = new PermanentDeleteOperation(
                    target.path(), environment.homeDirectory());
            OperationResult result = operation.execute();
            statusMessage = result.message();
            if (result.success()) {
                tagState.untag(target.path());
                reloadPreservingCursor();
            }
            lastRenderedVersion = -1L;
        };
        lastRenderedVersion = -1L;
    }

    private void reloadPreservingCursor() {
        Path currentDirectory = treeModel.directory();
        int savedCursor = treeModel.cursorIndex();
        int savedScroll = treeModel.scrollOffset();
        try {
            FileList baseList = FileList.load(currentDirectory, config);
            TreeModel newModel = new TreeModel(baseList, config);
            newModel.moveToIndex(savedCursor);
            newModel.setScrollOffset(savedScroll);
            treeModel = newModel;
            lastRenderedVersion = -1L;
        } catch (IOException exception) {
            statusMessage = "Reload failed: " + exception.getMessage();
        }
    }

    private void reloadSelectingName(String name) {
        Path currentDirectory = treeModel.directory();
        try {
            FileList baseList = FileList.load(currentDirectory, config);
            TreeModel newModel = new TreeModel(baseList, config);
            newModel.moveToEntryNamed(name);
            treeModel = newModel;
            lastRenderedVersion = -1L;
        } catch (IOException exception) {
            statusMessage = "Reload failed: " + exception.getMessage();
        }
    }

    private NavigateResult handleArrowLeft() {
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
        return navigateToParent();
    }

    private NavigateResult handleEnter() {
        VisibleEntry current = treeModel.current();
        if (current == null || current.entry() == null) {
            return new NavigateResult(treeModel, null);
        }
        FileEntry entry = current.entry();
        if (entry.isParent()) {
            return navigateToParent();
        }
        if (entry.isNavigable()) {
            try {
                FileList baseList = FileList.load(entry.path(), config);
                TreeModel newModel = new TreeModel(baseList, config);
                recordVisit(entry.path());
                return new NavigateResult(newModel, null);
            } catch (IOException exception) {
                return new NavigateResult(treeModel,
                        "Cannot enter " + entry.name() + ": " + exception.getMessage());
            }
        }
        if (entry.isBrokenSymlink()) {
            return new NavigateResult(treeModel,
                    "Broken link: " + entry.name() + " -> " + entry.symlinkTarget());
        }
        handleEnterOpen(entry);
        return new NavigateResult(treeModel, null);
    }

    private NavigateResult navigateToParent() {
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
            recordVisit(parent);
            return new NavigateResult(newModel, null);
        } catch (IOException exception) {
            return new NavigateResult(treeModel,
                    "Cannot go up: " + exception.getMessage());
        }
    }

    private record NavigateResult(TreeModel treeModel, String statusMessage) {}

    private static final class PasteSession {
        final CutBuffer.Mode mode;
        final List<Path> remaining;
        boolean overwriteAll;
        boolean skipAll;
        boolean sparseConfirmed;
        int successCount;
        int failureCount;

        PasteSession(CutBuffer.Mode mode, List<Path> remaining) {
            this.mode = mode;
            this.remaining = remaining;
        }
    }
}
