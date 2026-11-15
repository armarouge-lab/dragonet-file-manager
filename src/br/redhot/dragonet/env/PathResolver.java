package br.redhot.dragonet.env;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

/**
 * Pure path utilities used by the rest of Dragonet.
 *
 * <p>Contains no I/O beyond {@code toRealPath()} resolution and no
 * state. The home directory is passed explicitly (not read from
 * {@code user.home}) so tests can use a temp dir without touching the
 * real home.
 */
public final class PathResolver {

    private static final Set<Path> PROTECTED_EXACT = Set.of(
            Path.of("/"),
            Path.of("/home")
    );

    private static final Set<Path> PROTECTED_EXACT_AND_DESCENDANTS = Set.of(
            Path.of("/usr"),
            Path.of("/var"),
            Path.of("/etc"),
            Path.of("/boot"),
            Path.of("/dev"),
            Path.of("/proc"),
            Path.of("/sys"),
            Path.of("/opt"),
            Path.of("/srv"),
            Path.of("/root")
    );

    private PathResolver() {
        // static utility class; not instantiable
    }

    /**
     * Canonicalizes a path with a defined fallback.
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

    public static boolean isWithinHome(Path path, Path homeDirectory) {
        Path absolutePath = path.toAbsolutePath().normalize();
        Path absoluteHome = homeDirectory.toAbsolutePath().normalize();
        return absolutePath.startsWith(absoluteHome);
    }

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

    /**
     * Returns whether a path is protected from destructive operations.
     *
     * <p>Checks the design §12.1 list. The system directories are
     * blocked both as exact matches and as ancestors. The user's home
     * directory and {@code /home} are blocked only as exact matches, so
     * files inside the home directory can be created, renamed and
     * deleted normally.
     *
     * <p>Navigation (read-only) is unaffected; the check is enforced by
     * mutating operations only.
     *
     * @param path          the path to test (may be relative)
     * @param homeDirectory the current user's home directory
     * @return {@code true} if the path is protected
     */
    public static boolean isProhibitedForDestructive(Path path, Path homeDirectory) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(homeDirectory, "homeDirectory");
        Path absolute = path.toAbsolutePath().normalize();
        Path absoluteHome = homeDirectory.toAbsolutePath().normalize();

        if (PROTECTED_EXACT.contains(absolute)) {
            return true;
        }
        if (absolute.equals(absoluteHome)) {
            return true;
        }
        for (Path protectedPath : PROTECTED_EXACT_AND_DESCENDANTS) {
            if (absolute.startsWith(protectedPath)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether {@code possibleAncestor} contains {@code descendant}
     * and is not equal to it.
     *
     * @param possibleAncestor the candidate ancestor
     * @param descendant       the candidate descendant
     * @return {@code true} if {@code possibleAncestor} strictly contains {@code descendant}
     */
    public static boolean isAncestorOf(Path possibleAncestor, Path descendant) {
        Objects.requireNonNull(possibleAncestor, "possibleAncestor");
        Objects.requireNonNull(descendant, "descendant");
        Path ancestorAbs = possibleAncestor.toAbsolutePath().normalize();
        Path descendantAbs = descendant.toAbsolutePath().normalize();
        return descendantAbs.startsWith(ancestorAbs) && !descendantAbs.equals(ancestorAbs);
    }
}