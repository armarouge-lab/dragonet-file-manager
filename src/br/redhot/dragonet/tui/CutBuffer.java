package br.redhot.dragonet.tui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Session-scoped cut/copy buffer.
 *
 * <p>Holds the paths marked by {@code ^K} (move) or {@code M-6} (copy)
 * until {@code ^U} consumes them. There is at most one pending
 * operation at a time; a new cut or copy replaces the previous one.
 */
public final class CutBuffer {

    public enum Mode { MOVE, COPY }

    private Mode mode;
    private final List<Path> paths = new ArrayList<>();

    public boolean isEmpty() {
        return paths.isEmpty();
    }

    public Mode mode() {
        return mode;
    }

    public List<Path> paths() {
        return List.copyOf(paths);
    }

    public int count() {
        return paths.size();
    }

    public void set(Mode mode, List<Path> newPaths) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(newPaths, "newPaths");
        this.mode = mode;
        this.paths.clear();
        this.paths.addAll(newPaths);
    }

    public void clear() {
        this.mode = null;
        this.paths.clear();
    }
}
