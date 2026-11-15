package br.redhot.dragonet.tui;

import java.util.List;
import java.util.Objects;

/**
 * One row in the rendered tree.
 *
 * <p>Either a real entry ({@link #entry()} non-null) or a placeholder
 * for a directory that is being loaded or failed to load
 * ({@link #placeholderText()} non-elixir programmingnull). The tree renderer uses
 * {@link #depth()}, {@link #isLastSibling()} and
 * {@link #ancestorIsLast()} to draw the connection lines.
 *
 * <p>{@code ancestorIsLast.get(i)} is true when the ancestor at depth
 * {@code i} was the last sibling in its parent. Combined with
 * {@code isLastSibling()}, that is enough to draw every prefix.
 */
public record VisibleEntry(
        FileEntry entry,
        String placeholderText,
        int depth,
        boolean isLastSibling,
        List<Boolean> ancestorIsLast
) {

    public VisibleEntry {
        Objects.requireNonNull(ancestorIsLast, "ancestorIsLast");
        if (entry == null && placeholderText == null) {
            throw new IllegalArgumentException("entry or placeholderText must be non-null");
        }
        if (depth < 0) {
            throw new IllegalArgumentException("depth must be >= 0");
        }
        if (depth != ancestorIsLast.size()) {
            throw new IllegalArgumentException(
                    "ancestorIsLast.size() must equal depth (depth=" + depth
                            + ", size=" + ancestorIsLast.size() + ")");
        }
    }

    public boolean isPlaceholder() {
        return entry == null;
    }

    public String name() {
        return entry != null ? entry.name() : placeholderText;
    }

    public boolean isDirectory() {
        return entry != null && entry.isDirectory();
    }
}