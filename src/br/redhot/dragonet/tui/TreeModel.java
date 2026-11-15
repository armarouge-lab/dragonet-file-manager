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
 * flattens the whole hierarchy into a {@link VisibleEntry} list on
 * demand, and stores the viewport scroll offset so the cursor can move
 * inside the visible area without dragging the whole list.
 *
 * <p>Expansion is asynchronous: {@link #expandCurrent()} returns
 * immediately, the children are loaded on a background thread, and
 * {@link #version()} bumps when they arrive so the render loop knows to
 * redraw.
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
    private int scrollOffset;
    private String searchQuery = "";
    private List<VisibleEntry> cachedFiltered = null;

    public TreeModel(FileList baseFileList, Config config) {
        this.baseFileList = Objects.requireNonNull(baseFileList, "baseFileList");
        this.config = Objects.requireNonNull(config, "config");
        this.cursorIndex = baseFileList.cursorIndex();
        this.scrollOffset = 0;
    }

    /**
     * Sets the search query. Empty clears the filter.
     *
     * <p>The filter applies on top of the visible tree: matching entries
     * and their ancestors are shown. Does not auto-expand collapsed
     * directories (deferred to V0.5c).
     */
    public void setSearchQuery(String query) {
        String safe = query == null ? "" : query.trim();
        if (safe.equals(this.searchQuery)) {
            return;
        }
        this.searchQuery = safe;
        this.cachedFiltered = null;
        clampCursorIndex();
        version.incrementAndGet();
    }

    private void clampCursorIndex() {
        int size = visibleEntries().size();
        if (size == 0) {
            cursorIndex = 0;
            scrollOffset = 0;
            return;
        }
        if (cursorIndex >= size) {
            cursorIndex = size - 1;
        }
        if (cursorIndex < 0) {
            cursorIndex = 0;
        }
    }

    public String searchQuery() {
        return searchQuery;
    }

    public boolean isFiltering() {
        return !searchQuery.isEmpty();
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

    public int scrollOffset() {
        return scrollOffset;
    }

    public void setScrollOffset(int newOffset) {
        this.scrollOffset = Math.max(0, newOffset);
    }

    /**
     * Updates the scroll offset so the cursor is visible in a viewport
     * of the given height, without unnecessarily dragging the list.
     *
     * <p>The offset only changes when the cursor is outside the current
     * window. This is what lets the user move the cursor up and down
     * within the viewport without scrolling the whole screen.
     *
     * @param viewportHeight number of list rows visible on screen
     * @return the adjusted scroll offset
     */
    public int adjustScrollOffset(int viewportHeight) {
        if (viewportHeight <= 0) {
            scrollOffset = 0;
            return scrollOffset;
        }
        int totalVisible = visibleEntries().size();
        if (totalVisible <= 0) {
            scrollOffset = 0;
            return scrollOffset;
        }
        if (cursorIndex < scrollOffset) {
            scrollOffset = cursorIndex;
        } else if (cursorIndex >= scrollOffset + viewportHeight) {
            scrollOffset = cursorIndex - viewportHeight + 1;
        }
        int maxScroll = Math.max(0, totalVisible - viewportHeight);
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }
        if (scrollOffset < 0) {
            scrollOffset = 0;
        }
        return scrollOffset;
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

    /**
     * Moves the cursor to the given visible index, clamped to the
     * current visible range.
     *
     * @param index the target visible index
     */
    public void moveToIndex(int index) {
        int count = visibleEntries().size();
        int clamped = count == 0 ? 0 : Math.max(0, Math.min(index, count - 1));
        if (clamped != cursorIndex) {
            cursorIndex = clamped;
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

    public List<VisibleEntry> visibleEntries() {
        if (!searchQuery.isEmpty()) {
            if (cachedFiltered == null) {
                cachedFiltered = computeFiltered();
            }
            return cachedFiltered;
        }
        List<VisibleEntry> result = new ArrayList<>();
        List<FileEntry> topLevel = baseFileList.entries();
        for (int index = 0; index < topLevel.size(); index = index + 1) {
            FileEntry entry = topLevel.get(index);
            boolean isLast = index == topLevel.size() - 1;
            visit(entry, 0, List.of(), isLast, result);
        }
        return result;
    }

    private List<VisibleEntry> computeFiltered() {
        List<VisibleEntry> result = new ArrayList<>();
        List<FileEntry> topLevel = baseFileList.entries();
        String lower = searchQuery.toLowerCase();
        for (int index = 0; index < topLevel.size(); index = index + 1) {
            FileEntry entry = topLevel.get(index);
            boolean isLast = index == topLevel.size() - 1;
            collectFiltered(entry, 0, List.of(), isLast, lower, result);
        }
        return result;
    }

    private boolean collectFiltered(FileEntry entry, int depth, List<Boolean> ancestorIsLast,
                                     boolean isLastSibling, String lower,
                                     List<VisibleEntry> out) {
        boolean selfMatches = entry.name().toLowerCase().contains(lower);
        List<VisibleEntry> childMatches = new ArrayList<>();
        if (entry.isDirectory()) {
            List<FileEntry> children = childrenCache.get(entry.path());
            if (children != null) {
                List<Boolean> childAncestor = append(ancestorIsLast, isLastSibling);
                for (int index = 0; index < children.size(); index = index + 1) {
                    FileEntry child = children.get(index);
                    boolean childIsLast = index == children.size() - 1;
                    collectFiltered(child, depth + 1, childAncestor, childIsLast, lower,
                            childMatches);
                }
            }
        }
        if (selfMatches || !childMatches.isEmpty()) {
            out.add(new VisibleEntry(entry, null, depth, isLastSibling, ancestorIsLast));
            out.addAll(childMatches);
            return true;
        }
        return false;
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
