package br.redhot.dragonet.tui;

import br.redhot.dragonet.config.Config;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The inline-expansion model on top of one {@link FileList}.
 *
 * <p>Tracks which directories are expanded, caches their children,
 * and flattens the whole hierarchy into a {@link VisibleEntry} list on
 * demand. Expansion is asynchronous: {@link #expandCurrent()} returns
 * immediately, the children are loaded on a background thread, and
 * {@link #version()} bumps when they arrive so the render loop knows to
 * redraw.
 *
 * <p>Cross-checks against the design: expansion state lives in memory
 * for the session and is dropped when the user enters a different
 * directory (a new {@code TreeModel} is created by {@code Tui}).
 */
public final class TreeModel {

    private final Config config;
    private final FileList baseFileList;
    private final Set<Path> expandedPaths = ConcurrentHashMap.newKeySet();
    private final Map<Path, List<FileEntry>> childrenCache = new ConcurrentHashMap<>();
    private final Set<Path> loadingPaths = ConcurrentHashMap.newKeySet();
    private final Set<Path> failedPaths = ConcurrentHashMap.newKeySet();
    private final AtomicLong version = new AtomicLong(0L);
    private int cursorIndex;

    public TreeModel(FileList baseFileList, Config config) {
        this.baseFileList = Objects.requireNonNull(baseFileList, "baseFileList");
        this.config = Objects.requireNonNull(config, "config");
        this.cursorIndex = baseFileList.cursorIndex();
    }

    public Path directory() {
        return baseFileList.directory();
    }

    public List<FileEntry> baseEntries() {
        return baseFileList.entries();
    }

    public long version() {
        return version.get();
    }

    public int cursorIndex() {
        return cursorIndex;
    }

    public VisibleEntry current() {
        List<VisibleEntry> entries = visibleEntries();
        if (entries.isEmpty()) {
            return null;
        }
        if (cursorIndex >= entries.size()) {
            cursorIndex = entries.size() - 1;
        }
        return entries.get(cursorIndex);
    }

    public void moveUp() {
        if (cursorIndex > 0) {
            cursorIndex = cursorIndex - 1;
            version.incrementAndGet();
        }
    }

    public void moveDown() {
        int count = visibleEntries().size();
        if (cursorIndex < count - 1) {
            cursorIndex = cursorIndex + 1;
            version.incrementAndGet();
        }
    }

    public void moveToFirst() {
        if (!visibleEntries().isEmpty()) {
            cursorIndex = 0;
            version.incrementAndGet();
        }
    }

    public void moveToLast() {
        int count = visibleEntries().size();
        if (count > 0) {
            cursorIndex = count - 1;
            version.incrementAndGet();
        }
    }

    public void moveToEntryNamed(String name) {
        List<VisibleEntry> entries = visibleEntries();
        for (int index = 0; index < entries.size(); index = index + 1) {
            VisibleEntry entry = entries.get(index);
            if (entry.entry() != null && entry.entry().name().equals(name)) {
                cursorIndex = index;
                version.incrementAndGet();
                return;
            }
        }
    }

    /**
     * Moves the cursor to the visible ancestor at {@code targetDepth}.
     *
     * <p>Scans upward from the current cursor. No-op if no such
     * ancestor exists.
     *
     * @param targetDepth the depth of the ancestor (0 = cwd-level)
     */
    public void moveCursorToDepth(int targetDepth) {
        List<VisibleEntry> entries = visibleEntries();
        for (int index = cursorIndex - 1; index >= 0; index = index - 1) {
            if (entries.get(index).depth() <= targetDepth) {
                cursorIndex = index;
                version.incrementAndGet();
                return;
            }
        }
    }

    public boolean isExpanded(Path path) {
        return expandedPaths.contains(path);
    }

    /**
     * Expands the current entry if it is a directory and not already
     * expanded. Loads children asynchronously the first time.
     */
    public void expandCurrent() {
        VisibleEntry current = current();
        if (current == null || current.entry() == null) {
            return;
        }
        FileEntry entry = current.entry();
        if (!entry.isDirectory() || expandedPaths.contains(entry.path())) {
            return;
        }
        expandedPaths.add(entry.path());
        version.incrementAndGet();

        if (childrenCache.containsKey(entry.path())) {
            return;
        }
        if (!loadingPaths.add(entry.path())) {
            return;
        }
        CompletableFuture.runAsync(() -> loadChildrenAsync(entry));
    }

    /**
     * Collapses the current entry if it is an expanded directory.
     */
    public void collapseCurrent() {
        VisibleEntry current = current();
        if (current == null || current.entry() == null) {
            return;
        }
        FileEntry entry = current.entry();
        if (!entry.isDirectory() || !expandedPaths.contains(entry.path())) {
            return;
        }
        expandedPaths.remove(entry.path());
        version.incrementAndGet();
    }

    private void loadChildrenAsync(FileEntry entry) {
        try {
            List<FileEntry> children = FileList.loadChildren(entry.path(), config);
            childrenCache.put(entry.path(), children);
        } catch (IOException exception) {
            failedPaths.add(entry.path());
        } finally {
            loadingPaths.remove(entry.path());
            version.incrementAndGet();
        }
    }

    /**
     * Flattens the hierarchy into a list of visible rows.
     *
     * <p>Each row carries its depth, whether it is the last sibling,
     * and the trailing {@code isLastSibling} flags of its ancestors.
     * That is enough for {@link TreeRenderer} to draw the prefix.
     *
     * @return the visible rows in render order
     */
    public List<VisibleEntry> visibleEntries() {
        List<VisibleEntry> result = new ArrayList<>();
        List<FileEntry> topLevel = baseFileList.entries();
        for (int index = 0; index < topLevel.size(); index = index + 1) {
            FileEntry entry = topLevel.get(index);
            boolean isLast = index == topLevel.size() - 1;
            visit(entry, 0, List.of(), isLast, result);
        }
        return result;
    }

    private void visit(FileEntry entry, int depth, List<Boolean> ancestorIsLast,
                       boolean isLastSibling, List<VisibleEntry> out) {
        out.add(new VisibleEntry(entry, null, depth, isLastSibling, ancestorIsLast));
        if (!entry.isDirectory() || !expandedPaths.contains(entry.path())) {
            return;
        }
        List<Boolean> childAncestor = append(ancestorIsLast, isLastSibling);
        if (loadingPaths.contains(entry.path())) {
            out.add(new VisibleEntry(null, "Loading...", depth + 1, true, childAncestor));
            return;
        }
        if (failedPaths.contains(entry.path())) {
            out.add(new VisibleEntry(null, "Error reading directory", depth + 1, true, childAncestor));
            return;
        }
        List<FileEntry> children = childrenCache.get(entry.path());
        if (children == null) {
            return;
        }
        for (int index = 0; index < children.size(); index = index + 1) {
            boolean childIsLast = index == children.size() - 1;
            visit(children.get(index), depth + 1, childAncestor, childIsLast, out);
        }
    }

    private static List<Boolean> append(List<Boolean> list, boolean value) {
        List<Boolean> result = new ArrayList<>(list.size() + 1);
        result.addAll(list);
        result.add(value);
        return result;
    }
}