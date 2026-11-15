package br.redhot.dragonet.config;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record Session(
        Config.SortOrder sort,
        boolean sortReverse,
        boolean showHidden,
        boolean preview,
        int infoLevel
) {

    public Session {
        Objects.requireNonNull(sort, "sort");
        if (infoLevel < 0 || infoLevel > 3) {
            throw new IllegalArgumentException("infoLevel must be 0..3");
        }
    }

    public static Session defaults() {
        return new Session(Config.SortOrder.NAME, false, false, true, 0);
    }

    public Session withInfoLevel(int newInfoLevel) {
        return new Session(sort, sortReverse, showHidden, preview, newInfoLevel);
    }

    public static Session load(Path sessionFile) {
        Objects.requireNonNull(sessionFile, "sessionFile");
        if (!Files.isReadable(sessionFile)) {
            return defaults();
        }
        try {
            SessionBuilder builder = new SessionBuilder();
            for (String line : Files.readAllLines(sessionFile, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int equalsIndex = trimmed.indexOf('=');
                if (equalsIndex < 0) {
                    continue;
                }
                String key = trimmed.substring(0, equalsIndex).trim();
                String value = trimmed.substring(equalsIndex + 1).trim();
                builder.set(key, value);
            }
            return builder.build();
        } catch (IOException exception) {
            return defaults();
        }
    }

    public void save(Path sessionFile) throws IOException {
        Objects.requireNonNull(sessionFile, "sessionFile");
        Path parent = sessionFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = sessionFile.resolveSibling(
                sessionFile.getFileName() + ".tmp-" + UUID.randomUUID());
        String content = "sort=" + sort.name().toLowerCase(Locale.ROOT) + "\n"
                + "sort_reverse=" + sortReverse + "\n"
                + "show_hidden=" + showHidden + "\n"
                + "preview=" + preview + "\n"
                + "info_level=" + infoLevel + "\n";
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            Files.move(temporary, sessionFile,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static final class SessionBuilder {

        private Config.SortOrder sort = Config.SortOrder.NAME;
        private boolean sortReverse = false;
        private boolean showHidden = false;
        private boolean preview = true;
        private int infoLevel = 0;

        void set(String key, String value) {
            switch (key) {
                case "sort" -> sort = parseSortOrder(value);
                case "sort_reverse" -> sortReverse = Boolean.parseBoolean(value);
                case "show_hidden" -> showHidden = Boolean.parseBoolean(value);
                case "preview" -> preview = Boolean.parseBoolean(value);
                case "info_level" -> {
                    try {
                        infoLevel = Integer.parseInt(value);
                    } catch (NumberFormatException ignored) {
                        infoLevel = 0;
                    }
                }
                default -> {
                    // Unknown key; ignore silently (session.conf is app-managed).
                }
            }
        }

        private static Config.SortOrder parseSortOrder(String value) {
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "size" -> Config.SortOrder.SIZE;
                case "modified" -> Config.SortOrder.MODIFIED;
                case "type" -> Config.SortOrder.TYPE;
                default -> Config.SortOrder.NAME;
            };
        }

        Session build() {
            if (infoLevel < 0 || infoLevel > 3) {
                infoLevel = 0;
            }
            return new Session(sort, sortReverse, showHidden, preview, infoLevel);
        }
    }
}