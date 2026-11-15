package br.redhot.dragonet.config;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Browser-style navigation history.
 *
 * <p>Holds an ordered list of visited directories plus a pointer to the
 * current position. Visiting a new directory truncates any forward
 * entries and appends. Back and forward move the pointer. The file is
 * capped at {@code limit} entries (FIFO for the oldest).
 *
 * <p>Format: one absolute path per line.
 */
public final class History {

    private final Path filePath;
    private final int limit;
    private final List<Path> entries = new ArrayList<>();
    private int position = -1;

    public History(Path filePath, int limit) {
        this.filePath = Objects.requireNonNull(filePath, "filePath");
        this.limit = Math.max(1, limit);
    }

    public static History load(Path filePath, int limit) {
        History history = new History(filePath, limit);
        if (!Files.isReadable(filePath)) {
            return history;
        }
        try {
            for (String line : Files.readAllLines(filePath, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                history.entries.add(Path.of(trimmed));
            }
            if (!history.entries.isEmpty()) {
                history.position = history.entries.size() - 1;
            }
            history.trim();
        } catch (IOException exception) {
            // Corrupt file: leave history empty.
        }
        return history;
    }

    public void visit(Path path) {
        Objects.requireNonNull(path, "path");
        Path absolute = path.toAbsolutePath().normalize();
        if (!entries.isEmpty() && entries.get(position).equals(absolute)) {
            return;
        }
        if (position < entries.size() - 1) {
            while (entries.size() > position + 1) {
                entries.remove(entries.size() - 1);
            }
        }
        entries.add(absolute);
        position = entries.size() - 1;
        trim();
    }

    public Optional<Path> back() {
        if (position <= 0) {
            return Optional.empty();
        }
        position = position - 1;
        return Optional.of(entries.get(position));
    }

    public Optional<Path> forward() {
        if (position >= entries.size() - 1) {
            return Optional.empty();
        }
        position = position + 1;
        return Optional.of(entries.get(position));
    }

    public void save() throws IOException {
        Files.createDirectories(filePath.getParent());
        Path temporary = filePath.resolveSibling(
                filePath.getFileName() + ".tmp-" + UUID.randomUUID());
        StringBuilder builder = new StringBuilder();
        for (Path entry : entries) {
            builder.append(entry).append('\n');
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

    public int size() {
        return entries.size();
    }

    private void trim() {
        while (entries.size() > limit) {
            entries.remove(0);
            position = Math.max(0, position - 1);
        }
    }
}
