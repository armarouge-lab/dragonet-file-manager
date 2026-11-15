package br.redhot.dragonet.tui;

import br.redhot.dragonet.config.Config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves and runs external programs for file dispatch.
 *
 * <p>Resolution has two stages: {@link #resolve(FileEntry)} decides what
 * to run and whether it should block the TUI (TTY programs) or detach
 * (GUI programs). {@link #runBlocking} and {@link #runDetached} execute
 * the plan. The split lets the resolution logic be unit-tested without
 * installing Imp or any other handler.
 *
 * <p>Imp (the Redhot Media Dispatcher) takes precedence when present.
 * In {@code auto} mode, {@code imp <path>} is run blocking for text
 * files (editor/pager need the terminal) and detached for images and
 * binaries (GUI viewers do not).
 */
public final class FileOpener {

    public enum Mode { BLOCK, DETACH, UNAVAILABLE }

    public record Resolution(List<String> argv, Mode mode, String message) {
        public Resolution {
            Objects.requireNonNull(argv, "argv");
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(message, "message");
        }

        static Resolution block(List<String> argv, String message) {
            return new Resolution(argv, Mode.BLOCK, message);
        }

        static Resolution detach(List<String> argv, String message) {
            return new Resolution(argv, Mode.DETACH, message);
        }

        static Resolution unavailable(String message) {
            return new Resolution(List.of(), Mode.UNAVAILABLE, message);
        }
    }

    public record Result(String message) {
    }

    private final boolean impAvailable;
    private final Config.ImpMode impMode;

    public FileOpener(boolean impAvailable, Config.ImpMode impMode) {
        this.impAvailable = impAvailable;
        this.impMode = Objects.requireNonNull(impMode, "impMode");
    }

    public boolean impAvailable() {
        return impAvailable;
    }

    /**
     * Decides what to run and how.
     *
     * @param entry the file entry to open
     * @return a resolution with argv, execution mode, and a short message
     */
    public Resolution resolve(FileEntry entry) {
        Objects.requireNonNull(entry, "entry");
        if (impAvailable) {
            return resolveImp(entry);
        }
        return resolveFallback(entry);
    }

    /**
     * Decides what to run for an explicit "edit" request (Alt+E).
     * Always uses {@code $VISUAL}/{@code $EDITOR}, bypassing Imp.
     */
    public Resolution resolveForEdit(FileEntry entry) {
        Optional<List<String>> editorArgv = resolveEditorArgv();
        if (editorArgv.isEmpty()) {
            return Resolution.unavailable("Set $VISUAL or $EDITOR to edit text files");
        }
        List<String> argv = new ArrayList<>(editorArgv.get());
        argv.add(entry.path().toString());
        return Resolution.block(argv, "Editing with " + argv.get(0));
    }

    /**
     * Decides what to run for an "open with" prompt (Alt+W). The
     * command line is parsed quote-aware; a leading {@code !} forces
     * shell execution.
     */
    public Resolution resolveForCommand(String userInput, Path ignoredWorkingDirectory) {
        if (isShellCommand(userInput)) {
            return Resolution.block(parseShellCommand(userInput), "Running shell");
        }
        List<String> argv = parseUserCommand(userInput);
        if (argv.isEmpty()) {
            return Resolution.unavailable("empty command");
        }
        return Resolution.block(argv, "Running " + argv.get(0));
    }

    /**
     * Runs a resolution that needs the terminal. The caller is
     * responsible for suspending the TUI before calling this.
     */
    public Result runBlocking(Resolution resolution, Path workingDirectory) {
        if (resolution.mode() != Mode.BLOCK) {
            return new Result("Not a blocking resolution");
        }
        try {
            ProcessBuilder builder = new ProcessBuilder(resolution.argv());
            if (workingDirectory != null) {
                builder.directory(workingDirectory.toFile());
            }
            builder.inheritIO();
            int exitCode = builder.start().waitFor();
            if (exitCode == 0) {
                return new Result(resolution.message());
            }
            return new Result(resolution.argv().get(0) + " exited with code " + exitCode);
        } catch (IOException exception) {
            return new Result("Cannot run '" + resolution.argv().get(0) + "': "
                    + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new Result("interrupted");
        }
    }

    /**
     * Runs a resolution detached (start-and-forget). Stdout and stderr
     * are redirected to {@code DISCARD} so a GUI program cannot pollute
     * the terminal that the TUI is using.
     */
    public Result runDetached(Resolution resolution, Path workingDirectory) {
        if (resolution.mode() != Mode.DETACH) {
            return new Result("Not a detach resolution");
        }
        try {
            ProcessBuilder builder = new ProcessBuilder(resolution.argv());
            if (workingDirectory != null) {
                builder.directory(workingDirectory.toFile());
            }
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);
            builder.redirectInput(ProcessBuilder.Redirect.INHERIT);
            builder.start();
            return new Result(resolution.message());
        } catch (IOException exception) {
            return new Result("Cannot launch '" + resolution.argv().get(0) + "': "
                    + exception.getMessage());
        }
    }

    // ---- Resolution helpers ----

    private Resolution resolveImp(FileEntry entry) {
        FileTypeDetector.Kind kind = FileTypeDetector.detect(entry.path());
        boolean needsTerminal = kind == FileTypeDetector.Kind.TEXT;
        Mode execMode = switch (impMode) {
            case AUTO -> needsTerminal ? Mode.BLOCK : Mode.DETACH;
            case BLOCK -> Mode.BLOCK;
            case DETACH -> Mode.DETACH;
        };
        List<String> argv = List.of("imp", entry.path().toString());
        String message = execMode == Mode.BLOCK
                ? "Dispatching with imp (blocking)"
                : "Dispatching with imp";
        return new Resolution(argv, execMode, message);
    }

    private Resolution resolveFallback(FileEntry entry) {
        FileTypeDetector.Kind kind = FileTypeDetector.detect(entry.path());
        switch (kind) {
            case TEXT -> {
                Optional<List<String>> editorArgv = resolveEditorArgv();
                if (editorArgv.isEmpty()) {
                    return Resolution.unavailable(
                            "Set $VISUAL or $EDITOR to open text files");
                }
                List<String> argv = new ArrayList<>(editorArgv.get());
                argv.add(entry.path().toString());
                return Resolution.block(argv, "Opening with " + argv.get(0));
            }
            case IMAGE, BINARY -> {
                if (!isExecutableOnPath("xdg-open")) {
                    return Resolution.unavailable(
                            "xdg-open not found. Install: pkg install xdg-utils");
                }
                List<String> argv = List.of("xdg-open", entry.path().toString());
                return Resolution.detach(argv, "Opening with xdg-open");
            }
            default -> {
                return Resolution.unavailable("Unknown file type");
            }
        }
    }

    private Optional<List<String>> resolveEditorArgv() {
        String editor = firstNonNull(System.getenv("VISUAL"), System.getenv("EDITOR"));
        if (editor == null) {
            return Optional.empty();
        }
        try {
            List<String> argv = CommandSplitter.split(editor);
            if (argv.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(argv);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static String firstNonNull(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static boolean isExecutableOnPath(String command) {
        String pathValue = System.getenv("PATH");
        if (pathValue == null || pathValue.isEmpty()) {
            return false;
        }
        for (String directory : pathValue.split(":")) {
            if (directory.isEmpty()) {
                continue;
            }
            Path candidate = Path.of(directory).resolve(command);
            if (Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)
                    && Files.isExecutable(candidate)) {
                return true;
            }
        }
        return false;
    }

    // ---- Command parsing helpers ----

    public boolean isShellCommand(String userInput) {
        return CommandSplitter.isShellCommand(userInput);
    }

    public List<String> parseUserCommand(String userInput) {
        return CommandSplitter.split(userInput);
    }

    public List<String> parseShellCommand(String userInput) {
        String shellCommand = userInput.startsWith("!")
                ? userInput.substring(1) : userInput;
        return List.of("/bin/sh", "-c", shellCommand);
    }
}
