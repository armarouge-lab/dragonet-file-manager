package br.redhot.dragonet.config;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistent named bookmarks.
 *
 * <p>Format: one line per bookmark, {@code <name>\t<path>}, UTF-8. Names
 * must not contain tabs or newlines. Writes are atomic (temp file plus
 * rename plus {@code fsync}).
 */
public final class Bookmarks {

    private final Path filePath;
    private final Map<String, Path> entries = new LinkedHashMap<>();

    public Bookmarks(Path filePath) {
        this.filePath = Objects.requireNonNull(filePath, "filePath");
    }

    public static Bookmarks load(Path filePath) {
        Bookmarks bookmarks = new Bookmarks(filePath);
        if (!Files.isReadable(filePath)) {
            return bookmarks;
        }
        try {
            for (String line : Files.readAllLines(filePath, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int tab = trimmed.indexOf('\t');
                if (tab <= 0) {
                    continue;
                }
                String name = trimmed.substring(0, tab);
                String path = trimmed.substring(tab + 1);
                bookmarks.entries.put(name, Path.of(path));
            }
        } catch (IOException exception) {
            // Corrupt file: return empty bookmarks silently.
        }
        return bookmarks;
    }

    public void save() throws IOException {
        Files.createDirectories(filePath.getParent());
        Path temporary = filePath.resolveSibling(
                filePath.getFileName() + ".tmp-" + UUID.randomUUID());
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Path> entry : entries.entrySet()) {
            builder.append(entry.getKey()).append('\t').append(entry.getValue()).append('\n');
        }
        try {
            Files.writeString(temporary, builder.toString(), StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            Files.move(temporary, filePath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public void add(String name, Path path) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(path, "path");
        if (name.isBlank() || name.contains("\t") || name.contains("\n")) {
            throw new IllegalArgumentException("invalid bookmark name");
        }
        entries.put(name, path);
    }

    public void remove(String name) {
        entries.remove(name);
    }

    public Optional<Path> get(String name) {
        return Optional.ofNullable(entries.get(name));
    }

    public List<String> names() {
        return new ArrayList<>(entries.keySet());
    }

    public Map<String, Path> all() {
        return Map.copyOf(entries);
    }

    public int count() {
        return entries.size();
    }
}
