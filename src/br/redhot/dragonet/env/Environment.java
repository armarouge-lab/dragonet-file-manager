package br.redhot.dragonet.env;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Locale;
import java.util.Set;

/**
 * Runtime environment detection.
 *
 * <p>Knows where config, state and trash live, whether external tools
 * are available, and whether the terminal supports UTF-8. No business
 * logic lives here; every other class asks {@code Environment} instead
 * of inspecting {@code os.name} or {@code $LANG} directly.
 *
 * <p>Instance creation is cheap; call {@link #detect()} once per
 * process and pass the result around.
 */
public final class Environment {

    private static final Set<PosixFilePermission> DIRECTORY_MODE =
            PosixFilePermissions.fromString("rwx------");

    private final Path homeDirectory;
    private final Path systemConfigDirectory;
    private final Path userConfigDirectory;
    private final Path stateDirectory;
    private final Path dataDirectory;
    private final Path trashDirectory;
    private final String operatingSystem;

    private Environment(Path homeDirectory,
                        Path systemConfigDirectory,
                        Path userConfigDirectory,
                        Path stateDirectory,
                        Path dataDirectory,
                        Path trashDirectory,
                        String operatingSystem) {
        this.homeDirectory = homeDirectory;
        this.systemConfigDirectory = systemConfigDirectory;
        this.userConfigDirectory = userConfigDirectory;
        this.stateDirectory = stateDirectory;
        this.dataDirectory = dataDirectory;
        this.trashDirectory = trashDirectory;
        this.operatingSystem = operatingSystem;
    }

    /**
     * Detects the current environment.
     *
     * @return a populated {@code Environment}
     * @throws IllegalStateException if {@code user.home} is not set
     */
    public static Environment detect() {
        String home = System.getProperty("user.home");
        if (home == null || home.isEmpty()) {
            throw new IllegalStateException("user.home is not set");
        }
        Path homeDirectory = Path.of(home);

        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String operatingSystem;
        Path systemConfigDirectory;
        if (osName.contains("freebsd")) {
            operatingSystem = "freebsd";
            systemConfigDirectory = Path.of("/usr/local/etc/redhot/dragonet");
        } else if (osName.contains("openbsd")) {
            operatingSystem = "openbsd";
            systemConfigDirectory = Path.of("/etc/redhot/dragonet");
        } else {
            operatingSystem = "linux";
            systemConfigDirectory = Path.of("/etc/redhot/dragonet");
        }

        Path userConfigDirectory = homeDirectory.resolve(".config/redhot/dragonet");
        Path stateDirectory = homeDirectory.resolve(".local/state/redhot/dragonet");
        Path dataDirectory = homeDirectory.resolve(".local/share");
        Path trashDirectory = dataDirectory.resolve("Trash");

        return new Environment(homeDirectory, systemConfigDirectory,
                userConfigDirectory, stateDirectory, dataDirectory, trashDirectory,
                operatingSystem);
    }

    /**
     * Creates the directories Dragonet owns. Idempotent.
     *
     * @throws IOException if a directory cannot be created
     */
    public void init() throws IOException {
        createDirectory(userConfigDirectory);
        createDirectory(stateDirectory);
        createDirectory(trashDirectory);
        createDirectory(trashDirectory.resolve("files"));
        createDirectory(trashDirectory.resolve("info"));
    }

    private static void createDirectory(Path directory) throws IOException {
        if (Files.isDirectory(directory)) {
            return;
        }
        Files.createDirectories(directory);
        try {
            Files.setPosixFilePermissions(directory, DIRECTORY_MODE);
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX filesystem. Nothing to do.
        }
    }

    public Path homeDirectory() {
        return homeDirectory;
    }

    public Path systemConfigDirectory() {
        return systemConfigDirectory;
    }

    public Path userConfigDirectory() {
        return userConfigDirectory;
    }

    public Path stateDirectory() {
        return stateDirectory;
    }

    public Path dataDirectory() {
        return dataDirectory;
    }

    public Path trashDirectory() {
        return trashDirectory;
    }

    public Path systemConfigPath(String fileName) {
        return systemConfigDirectory.resolve(fileName);
    }

    public Path userConfigPath(String fileName) {
        return userConfigDirectory.resolve(fileName);
    }

    public Path statePath(String fileName) {
        return stateDirectory.resolve(fileName);
    }

    /**
     * Returns the on-disk path of a theme preset.
     *
     * <p>{@code redhot} maps to {@code /etc/redhot/theme.conf}. Other
     * names map to {@code /etc/redhot/themes/<name>.conf}. On FreeBSD
     * the {@code /usr/local} prefix is used.
     *
     * @param themeName the preset name ({@code redhot}, {@code mono}, {@code light})
     * @return the theme file path (may not exist)
     */
    public Path themePath(String themeName) {
        Path redhotDirectory = systemConfigDirectory.getParent();
        if ("redhot".equals(themeName)) {
            return redhotDirectory.resolve("theme.conf");
        }
        return redhotDirectory.resolve("themes").resolve(themeName + ".conf");
    }

    public Path trashPath() {
        return trashDirectory;
    }

    public boolean hasChafa() {
        return isExecutableOnPath("chafa");
    }

    public boolean hasFile() {
        return isExecutableOnPath("file");
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
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether the current user can write to {@code path}.
     *
     * <p>If {@code path} does not exist, tests the parent directory
     * instead, so "can I create this file?" answers correctly.
     *
     * @param path the path to test
     * @return {@code true} if the path is writable
     */
    public boolean isWritable(Path path) {
        if (Files.exists(path)) {
            return Files.isWritable(path);
        }
        Path parent = path.getParent();
        return parent != null && Files.isWritable(parent);
    }

    /**
     * Returns whether the current locale indicates UTF-8 support.
     *
     * <p>Checks {@code LC_ALL}, {@code LC_CTYPE} and {@code LANG}.
     * Explicit UTF-8 wins. If any variable is explicitly {@code C} or
     * {@code POSIX}, ASCII is assumed. If no variable is set at all,
     * UTF-8 is assumed (modern terminals are UTF-8 by default; the
     * user can force ASCII via {@code tree_style=ascii}).
     *
     * @return {@code true} if UTF-8 is assumed or indicated
     */
    public boolean supportsUtf8() {
        boolean explicitNonUtf8 = false;
        for (String variable : new String[]{"LC_ALL", "LC_CTYPE", "LANG"}) {
            String value = System.getenv(variable);
            if (value == null || value.isEmpty()) {
                continue;
            }
            String upper = value.toUpperCase(Locale.ROOT);
            if (upper.contains("UTF-8") || upper.contains("UTF8")) {
                return true;
            }
            if (upper.equals("C") || upper.equals("POSIX")) {
                explicitNonUtf8 = true;
            }
        }
        return !explicitNonUtf8;
    }

    public String operatingSystem() {
        return operatingSystem;
    }

    public boolean isFreeBSD() {
        return "freebsd".equals(operatingSystem);
    }

    public boolean isOpenBSD() {
        return "openbsd".equals(operatingSystem);
    }

    public boolean isLinux() {
        return "linux".equals(operatingSystem);
    }
}