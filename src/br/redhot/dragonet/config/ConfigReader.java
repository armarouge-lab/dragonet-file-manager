package br.redhot.dragonet.config;

import br.redhot.dragonet.Logger;
import br.redhot.dragonet.env.Environment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reads Dragonet configuration from the documented chain of files.
 *
 * <p>Order of precedence (later overrides earlier, key by key):
 * <ol>
 *   <li>System-wide config ({@code /etc/redhot/dragonet/dragonet.conf})</li>
 *   <li>User config ({@code ~/.config/redhot/dragonet/dragonet.conf})</li>
 *   <li>{@code $DRAGONET_CONFIG}, if set</li>
 *   <li>{@code --config <path>}, if provided by the caller</li>
 * </ol>
 *
 * <p>Files that do not exist are skipped. Files that exist but cannot
 * be parsed raise {@link ConfigException}, which the CLI turns into
 * {@code EX_CONFIG_ERROR} and the TUI into a config-error modal.
 */
public final class ConfigReader {

    private final Environment environment;
    private final Logger logger;

    public ConfigReader(Environment environment, Logger logger) {
        this.environment = Objects.requireNonNull(environment, "environment");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Reads the full configuration chain.
     *
     * @param explicitConfigFile the {@code --config} argument, or {@code null}
     * @return the merged {@link Config}
     * @throws ConfigException if any file contains an invalid value
     */
    public Config read(Path explicitConfigFile) throws ConfigException {
        List<Path> sources = new ArrayList<>();

        Path systemConfig = environment.systemConfigPath("dragonet.conf");
        Path userConfig = environment.userConfigPath("dragonet.conf");
        if (Files.isReadable(systemConfig)) {
            sources.add(systemConfig);
        }
        if (Files.isReadable(userConfig)) {
            sources.add(userConfig);
        }

        String environmentConfig = System.getenv("DRAGONET_CONFIG");
        if (environmentConfig != null && !environmentConfig.isEmpty()) {
            Path environmentPath = Path.of(environmentConfig);
            if (Files.isReadable(environmentPath)) {
                sources.add(environmentPath);
            } else {
                logger.warn("DRAGONET_CONFIG points to an unreadable file: " + environmentPath);
            }
        }

        if (explicitConfigFile != null) {
            if (!Files.isReadable(explicitConfigFile)) {
                throw new ConfigException(explicitConfigFile, 0, "", "",
                        "config file not readable");
            }
            sources.add(explicitConfigFile);
        }

        ConfigBuilder builder = new ConfigBuilder();
        for (Path source : sources) {
            readFile(source, builder);
        }
        return builder.build();
    }

    private void readFile(Path source, ConfigBuilder builder) throws ConfigException {
        List<String> lines;
        try {
            lines = Files.readAllLines(source, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new ConfigException(source, 0, "", "",
                    "could not read file: " + exception.getMessage());
        }

        int lineNumber = 0;
        for (String rawLine : lines) {
            lineNumber = lineNumber + 1;

            String line = rawLine;
            int commentIndex = line.indexOf('#');
            if (commentIndex >= 0) {
                line = line.substring(0, commentIndex);
            }
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            int equalsIndex = line.indexOf('=');
            if (equalsIndex < 0) {
                throw new ConfigException(source, lineNumber, line, "",
                        "expected key=value");
            }

            String key = line.substring(0, equalsIndex).trim();
            String value = line.substring(equalsIndex + 1).trim();

            if (key.isEmpty()) {
                throw new ConfigException(source, lineNumber, "", value, "empty key");
            }

            if (!builder.isKnownKey(key)) {
                logger.warn(String.format("%s:%d: unknown key '%s' (ignored)",
                        source, lineNumber, key));
                continue;
            }

            builder.set(key, value, source, lineNumber);
        }
    }
}