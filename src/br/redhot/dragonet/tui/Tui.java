package br.redhot.dragonet.tui;

import br.redhot.dragonet.ExitCode;
import br.redhot.dragonet.Logger;
import br.redhot.dragonet.config.Config;
import br.redhot.dragonet.config.Session;
import br.redhot.dragonet.engine.CreateDirectoryOperation;
import br.redhot.dragonet.engine.CreateFileOperation;
import br.redhot.dragonet.engine.CreateSymlinkOperation;
import br.redhot.dragonet.engine.Operation;
import br.redhot.dragonet.engine.OperationResult;
import br.redhot.dragonet.engine.PermanentDeleteOperation;
import br.redhot.dragonet.engine.RenameOperation;
import br.redhot.dragonet.env.Environment;
import br.redhot.dragonet.trash.TrashStore;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class Tui {

    private static final long IDLE_SLEEP_MILLISECONDS = 50L;

    private final Config config;
    private final Environment environment;
    private final Logger logger;
    private final TrashStore trashStore;
    private final TagState tagState = new TagState();
    private Session session;

    private TreeModel treeModel;
    private long lastRenderedVersion = -1L;
    private String statusMessage;
    private Prompt activePrompt;
    private Consumer<String> pendingPromptAction;
    private Modal activeModal;
    private Consumer<Boolean> pendingModalAction;
    private MenuModal activeMenuModal;
    private Consumer<Character> pendingMenuAction;
    private String pendingSymlinkName;

    public Tui(Config config, Session session, Environment environment, Logger logger) {
        this.config = Objects.requireNonNull(config, "config");
        this.session = Objects.requireNonNull(session, "session");
        this.environment = Objects.requireNonNull(environment, "environment");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.trashStore = new TrashStore(environment.trashPath(), environment.homeDirectory());
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

        try (Terminal terminal = new DefaultTerminalFactory().createTerminal();
             Screen screen = new TerminalScreen(terminal)) {

            screen.startScreen();

            TreeRenderer treeRenderer = new TreeRenderer(config, environment.supportsUtf8());
            Layout layout = new Layout(config, environment, treeRenderer);
            boolean running = true;

            while (running) {
                if (treeModel.version() != lastRenderedVersion) {
                    layout.draw(screen, treeModel, tagState, session.infoLevel(),
                            statusMessage, activePrompt, activeModal, activeMenuModal);
                    statusMessage = null;
                    lastRenderedVersion = treeModel.version();
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
                        activePrompt = null;
                        pendingPromptAction = null;
                        pendingSymlinkName = null;
                        lastRenderedVersion = -1L;
                    } else if (status == Prompt.Result.Status.SUBMITTED) {
                        String text = result.text();
                        Consumer<String> action = pendingPromptAction;
                        activePrompt = null;
                        pendingPromptAction = null;
                        lastRenderedVersion = -1L;
                        if (action != null) {
                            action.accept(text);
                        }
                    } else {
                        lastRenderedVersion = -1L;
                    }
                    continue;
                }

                running = handleIdleKey(keyStroke, screen);
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

    private boolean handleIdleKey(KeyStroke keyStroke, Screen screen) {
        KeyType keyType = keyStroke.getKeyType();
        if (keyType == KeyType.EOF) {
            return false;
        }
        if (keyType == KeyType.Character && keyStroke.isCtrlDown()) {
            char character = Character.toLowerCase(keyStroke.getCharacter());
            if (character == 'x') {
                return false;
            }
            if (character == 'r') {
                startRename();
                return true;
            }
            if (character == 'd') {
                startTrash();
                return true;
            }
            if (character == 't') {
                handleTagToggle();
                return true;
            }
            if (character == 'c') {
                handleClearTags();
                return true;
            }
            return true;
        }
        if (keyType == KeyType.Character && keyStroke.isAltDown()) {
            char character = Character.toLowerCase(keyStroke.getCharacter());
            switch (character) {
                case 'i' -> toggleInfoLevel(screen);
                case 's' -> startMkdir();
                case 'n' -> startTouch();
                case 'y' -> startSymlink();
                case 'd' -> startPermanentDelete();
                case 'a' -> handleTagAll();
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
        if (keyType == KeyType.ArrowDown) {
            treeModel.moveDown();
            return true;
        }
        if (keyType == KeyType.ArrowUp) {
            treeModel.moveUp();
            return true;
        }
        if (keyType == KeyType.ArrowRight) {
            treeModel.expandCurrent();
            return true;
        }
        if (keyType == KeyType.ArrowLeft) {
            applyNavigateResult(handleArrowLeft());
            return true;
        }
        if (keyType == KeyType.Enter) {
            applyNavigateResult(handleEnter());
            return true;
        }
        if (keyType == KeyType.Backspace) {
            applyNavigateResult(navigateToParent());
            return true;
        }
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

    private void toggleInfoLevel(Screen screen) {
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
            activeMenuModal = new MenuModal(
                    "Tag: " + entry.name(),
                    "u:untag  i:invert all  c:clear all");
            pendingMenuAction = (choice) -> {
                switch (choice) {
                    case 'u' -> {
                        tagState.untag(entry.path());
                        statusMessage = "Untagged: " + entry.name();
                    }
                    case 'i' -> {
                        tagState.invertAll(getCurrentLevelEntries());
                        statusMessage = "Inverted tags at current level";
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
            return;
        }
        tagState.toggle(entry.path(), entry.sizeBytes());
        statusMessage = "Tagged: " + entry.name();
        treeModel.moveDown();
        lastRenderedVersion = -1L;
    }

    private void handleTagAll() {
        List<FileEntry> currentEntries = getCurrentLevelEntries();
        tagState.tagAll(currentEntries);
        statusMessage = "Tagged " + currentEntries.size() + " entries";
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
            Operation operation = new CreateDirectoryOperation(
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
                    reloadSelectingName(newName);
                }
            }
            lastRenderedVersion = -1L;
        };
        lastRenderedVersion = -1L;
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
            return new NavigateResult(newModel, null);
        } catch (IOException exception) {
            return new NavigateResult(treeModel,
                    "Cannot go up: " + exception.getMessage());
        }
    }

    private record NavigateResult(TreeModel treeModel, String statusMessage) {}
}
