package br.redhot.dragonet.config;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Raised when a configuration file contains an invalid value.
 *
 * <p>Carries enough context (file, line, key, value) for the CLI to print
 * an actionable message and for the TUI to point the user at the exact
 * line that needs fixing.
 */
public final class ConfigException extends Exception {

    private static final long serialVersionUID = 1L;

    private final Path filePath;
    private final int lineNumber;
    private final String key;
    private final String value;

    public ConfigException(Path filePath, int lineNumber, String key, String value, String reason) {
        super(buildMessage(filePath, lineNumber, key, value, reason));
        this.filePath = Objects.requireNonNull(filePath, "filePath");
        this.lineNumber = lineNumber;
        this.key = Objects.requireNonNull(key, "key");
        this.value = Objects.requireNonNull(value, "value");
    }

    private static String buildMessage(Path filePath, int lineNumber, String key, String value, String reason) {
        if (lineNumber <= 0) {
            return String.format("%s: %s", filePath, reason);
        }
        return String.format("%s:%d: invalid value for '%s': %s (got '%s')",
                filePath, lineNumber, key, reason, value);
    }

    public Path filePath() {
        return filePath;
    }

    public int lineNumber() {
        return lineNumber;
    }

    public String key() {
        return key;
    }

    public String value() {
        return value;
    }
}