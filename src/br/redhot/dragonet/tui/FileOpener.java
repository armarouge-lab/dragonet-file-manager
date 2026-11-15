package br.redhot.dragonet.tui;

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
 * <p>Two distinct execution modes:
 * <ul>
 *   <li>{@link #runBlocking}: {@code inheritIO} + {@code waitFor}. The
 *       caller must suspend the TUI before calling (see
 *       {@code Tui.runWithSuspendedScreen}). Interactive commands
 *       ({@code less}, {@code vim}, {@code sh -c ...}) require this.</li>
 *   <li>{@link #runDetached}: {@code start} and forget. Used for
 *       {@code xdg-open} and other fork-and-return launchers.</li>
 * </ul>
 *
 * <p>All invocations use {@link ProcessBuilder} with explicit argv; no
 * shell is ever invoked except when the caller explicitly requests
 * {@code /bin/sh -c} via {@link #resolveShellCommand}.
 */
public final class FileOpener {

    public enum Outcome { OK, NO_HANDLER, FAILED }

    public record Result(Outcome outcome, String message) {
        public Result {
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(message, "message");
        }

        public static Result ok(String message) {
            return new Result(Outcome.OK, message);
        }

        public static Result noHandler(String hint) {
            return new Result(Outcome.NO_HANDLER, hint);
        }

        public static Result failed(String message) {
            return new Result(Outcome.FAILED, message);
        }
    }

    private static final List<String> NONE = List.of();

    public Optional<List<String>> resolveEditor() {
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

    public Optional<List<String>> resolveXdgOpen(Path path) {
        if (!isExecutableOnPath("xdg-open")) {
            return Optional.empty();
        }
        return Optional.of(List.of("xdg-open", path.toString()));
    }

    public Optional<List<String>> resolveExecutable(Path path) {
        return Optional.of(List.of(path.toString()));
    }

    public List<String> parseUserCommand(String userInput) {
        return CommandSplitter.split(userInput);
    }

    public List<String> parseShellCommand(String userInput) {
        String shellCommand = userInput.startsWith("!")
                ? userInput.substring(1) : userInput;
        return List.of("/bin/sh", "-c", shellCommand);
    }

    public boolean isShellCommand(String userInput) {
        return CommandSplitter.isShellCommand(userInput);
    }

    public Result runBlocking(List<String> argv, Path workingDirectory) {
        if (argv == null || argv.isEmpty()) {
            return Result.failed("empty command");
        }
        try {
            ProcessBuilder builder = new ProcessBuilder(argv);
            if (workingDirectory != null) {
                builder.directory(workingDirectory.toFile());
            }
            builder.inheritIO();
            int exitCode = builder.start().waitFor();
            if (exitCode == 0) {
                return Result.ok(argv.get(0) + " exited cleanly");
            }
            return Result.failed(argv.get(0) + " exited with code " + exitCode);
        } catch (IOException exception) {
            return Result.failed("Cannot run '" + argv.get(0) + "': "
                    + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Result.failed("interrupted");
        }
    }

    public Result runDetached(List<String> argv, Path workingDirectory) {
        if (argv == null || argv.isEmpty()) {
            return Result.failed("empty command");
        }
        try {
            ProcessBuilder builder = new ProcessBuilder(argv);
            if (workingDirectory != null) {
                builder.directory(workingDirectory.toFile());
            }
            builder.start();
            return Result.ok("Launched " + argv.get(0));
        } catch (IOException exception) {
            return Result.failed("Cannot launch '" + argv.get(0) + "': "
                    + exception.getMessage());
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
}
