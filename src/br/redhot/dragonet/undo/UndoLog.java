package br.redhot.dragonet.undo;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Append-only undo journal.
 *
 * <p>One line per entry, {@code fsync} after every write. Entries are
 * never modified in place; an undo is recorded by appending a new
 * {@link UndoEntry.OperationKind#UNDO UNDO} entry that points at the
 * epoch of the entry being undone. When reading, entries whose epoch
 * has been undone are filtered out.
 *
 * <p>The journal is trimmed lazily: when it grows beyond twice the
 * configured limit, the oldest half is dropped with an atomic rewrite.
 * This bounds the file size while keeping the common case append-only.
 */
public final class UndoLog {

    private final Path logPath;
    private final int limit;

    public UndoLog(Path logPath, int limit) {
        this.logPath = Objects.requireNonNull(logPath, "logPath");
        this.limit = Math.max(1, limit);
    }

    public Path logPath() {
        return logPath;
    }

    public void append(UndoEntry entry) throws IOException {
        Objects.requireNonNull(entry, "entry");
        Files.createDirectories(logPath.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(logPath, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.write(entry.format());
            writer.newLine();
        }
        try (FileChannel channel = FileChannel.open(logPath, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
        trimIfNeeded();
    }

    public List<UndoEntry> readAll() throws IOException {
        if (!Files.isRegularFile(logPath)) {
            return List.of();
        }
        List<UndoEntry> entries = new ArrayList<>();
        for (String line : Files.readAllLines(logPath, StandardCharsets.UTF_8)) {
            UndoEntry parsed = UndoEntry.parse(line);
            if (parsed != null) {
                entries.add(parsed);
            }
        }
        return entries;
    }

    public Optional<UndoEntry> lastUndoable() throws IOException {
        List<UndoEntry> all = readAll();
        Set<Long> undoneEpochs = new HashSet<>();
        for (UndoEntry entry : all) {
            if (entry.kind() == UndoEntry.OperationKind.UNDO) {
                String target = entry.arguments().get("target_epoch");
                if (target != null) {
                    try {
                        undoneEpochs.add(Long.parseLong(target));
                    } catch (NumberFormatException ignored) {
                        // Malformed entry; ignore.
                    }
                }
            }
        }
        UndoEntry latest = null;
        for (UndoEntry entry : all) {
            if (entry.kind() == UndoEntry.OperationKind.UNDO) {
                continue;
            }
            if (undoneEpochs.contains(entry.epochSeconds())) {
                continue;
            }
            if (latest == null || entry.epochSeconds() > latest.epochSeconds()) {
                latest = entry;
            }
        }
        return Optional.ofNullable(latest);
    }

    private void trimIfNeeded() throws IOException {
        List<String> lines = Files.readAllLines(logPath, StandardCharsets.UTF_8);
        int threshold = limit * 2;
        if (lines.size() <= threshold) {
            return;
        }
        List<String> kept = new ArrayList<>(lines.subList(lines.size() - limit, lines.size()));
        Path temporary = logPath.resolveSibling(logPath.getFileName() + ".tmp");
        Files.write(temporary, kept, StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
        Files.move(temporary, logPath,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }
}
