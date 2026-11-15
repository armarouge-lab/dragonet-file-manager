package br.redhot.dragonet.tui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * In-memory tag selection.
 *
 * <p>Tags are keyed by canonical path so they survive navigation
 * between directories and across expansions. The stored size is the
 * size observed at tag time; it is used only for the informational
 * total in the title bar, not for any filesystem decision.
 *
 * <p>Not persisted to disk in V0.3a (design §4.3: tags cleared on
 * exit).
 */
public final class TagState {

    private final Map<Path, Long> taggedPaths = new LinkedHashMap<>();

    public void toggle(Path path, long sizeBytes) {
        Objects.requireNonNull(path, "path");
        Path canonical = canonicalize(path);
        if (taggedPaths.containsKey(canonical)) {
            taggedPaths.remove(canonical);
        } else {
            taggedPaths.put(canonical, sizeBytes);
        }
    }

    public boolean isTagged(Path path) {
        if (path == null) {
            return false;
        }
        return taggedPaths.containsKey(canonicalize(path));
    }

    public void untag(Path path) {
        Objects.requireNonNull(path, "path");
        taggedPaths.remove(canonicalize(path));
    }

    public void tagAll(List<FileEntry> entries) {
        Objects.requireNonNull(entries, "entries");
        for (FileEntry entry : entries) {
            if (entry.isParent()) {
                continue;
            }
            taggedPaths.put(canonicalize(entry.path()), entry.sizeBytes());
        }
    }

    public void invertAll(List<FileEntry> entries) {
        Objects.requireNonNull(entries, "entries");
        for (FileEntry entry : entries) {
            if (entry.isParent()) {
                continue;
            }
            Path canonical = canonicalize(entry.path());
            if (taggedPaths.containsKey(canonical)) {
                taggedPaths.remove(canonical);
            } else {
                taggedPaths.put(canonical, entry.sizeBytes());
            }
        }
    }

    public void clear() {
        taggedPaths.clear();
    }

    public int count() {
        return taggedPaths.size();
    }

    public long totalSizeBytes() {
        long total = 0L;
        for (Long size : taggedPaths.values()) {
            total = total + size;
        }
        return total;
    }

    public List<Path> paths() {
        return new ArrayList<>(taggedPaths.keySet());
    }

    private static Path canonicalize(Path path) {
        try {
            return path.toRealPath();
        } catch (Exception exception) {
            return path.toAbsolutePath().normalize();
        }
    }
}
