package br.redhot.dragonet.env;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Pure path utilities used by the rest of Dragonet.
 *
 * <p>Contains no I/O beyond {@code toRealPath()} resolution and no
 * state. The home directory is passed explicitly (not read from
 * {@code user.home}) so tests can use a temp dir without touching the
 * real home.
 */
public final class PathResolver {

    private PathResolver() {
        // static utility class; not instantiable
    }

    /**
     * Canonicalizes a path with a defined fallback.
     *
     * <p>Calls {@link Path#toRealPath()} first. If the path does not
     * exist, returns {@link Path#toAbsolutePath()} normalized instead
     * of throwing, so callers can build plans for files that will be
     * created.
     *
     * @param path the path to canonicalize
     * @return the canonical form, or the absolute normalized form on failure
     * @throws IOException if both forms fail
     */
    public static Path canonicalize(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        try {
            return path.toRealPath();
        } catch (IOException exception) {
            return path.toAbsolutePath().normalize();
        }
    }

    /**
     * Expands a leading {@code ~} to the supplied home directory.
     *
     * <p>Only {@code ~} and {@code ~/...} are expanded. {@code ~user}
     * is not supported (would require passwd lookup and complicates
     * testing).
     *
     * @param rawPath       the path as typed by the user
     * @param homeDirectory the home directory to substitute
     * @return the expanded path
     */
    public static Path expandHome(String rawPath, Path homeDirectory) {
        Objects.requireNonNull(rawPath, "rawPath");
        Objects.requireNonNull(homeDirectory, "homeDirectory");
        if (rawPath.equals("~")) {
            return homeDirectory;
        }
        if (rawPath.startsWith("~/")) {
            return homeDirectory.resolve(rawPath.substring(2));
        }
        return Path.of(rawPath);
    }

    /**
     * Returns whether {@code path} is inside {@code homeDirectory}.
     *
     * @param path          the path to test
     * @param homeDirectory the home directory
     * @return {@code true} if the path is inside the home directory
     */
    public static boolean isWithinHome(Path path, Path homeDirectory) {
        Path absolutePath = path.toAbsolutePath().normalize();
        Path absoluteHome = homeDirectory.toAbsolutePath().normalize();
        return absolutePath.startsWith(absoluteHome);
    }

    /**
     * Formats a path for display, substituting {@code ~} for the home
     * prefix when applicable.
     *
     * <p>Used by the title bar (§5.1). The output is for human eyes
     * only; never use it in filesystem calls.
     *
     * @param path          the path to format
     * @param homeDirectory the home directory
     * @return a display string
     */
    public static String formatForDisplay(Path path, Path homeDirectory) {
        Path absolutePath = path.toAbsolutePath().normalize();
        Path absoluteHome = homeDirectory.toAbsolutePath().normalize();
        if (absolutePath.equals(absoluteHome)) {
            return "~";
        }
        if (absolutePath.startsWith(absoluteHome)) {
            return "~" + absolutePath.toString().substring(absoluteHome.toString().length());
        }
        return absolutePath.toString();
    }
}