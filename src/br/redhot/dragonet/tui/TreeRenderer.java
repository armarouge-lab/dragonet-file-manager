package br.redhot.dragonet.tui;

import br.redhot.dragonet.config.Config;

import java.util.Objects;

/**
 * Draws the tree prefix for a {@link VisibleEntry}.
 *
 * <p>Uses ASCII tree characters exclusively: {@code +--} (branch),
 * {@code `--} (leaf), {@code |} (continuation). This is the classic
 * Unix {@code tree(1)} style, guaranteed to render on every terminal
 * regardless of locale or font.
 *
 * <p>The class keeps the {@code config} and {@code utf8Supported}
 * arguments for forward compatibility: V1.1 will re-add a UTF-8
 * variant selectable via {@code tree_style=utf8}.
 */
public final class TreeRenderer {

    public record Segments(String branch, String leaf, String continuation, String empty) {
        public Segments {
            Objects.requireNonNull(branch, "branch");
            Objects.requireNonNull(leaf, "leaf");
            Objects.requireNonNull(continuation, "continuation");
            Objects.requireNonNull(empty, "empty");
        }
    }

    public static final Segments ASCII_SEGMENTS =
            new Segments("+-- ", "`-- ", "|   ", "    ");
    public static final Segments ASCII_COMPACT =
            new Segments("+- ", "`- ", "| ", "  ");

    private final Config config;
    private final boolean utf8Supported;

    public TreeRenderer(Config config, boolean utf8Supported) {
        this.config = Objects.requireNonNull(config, "config");
        this.utf8Supported = utf8Supported;
    }

    /**
     * Chooses the segment set for the given terminal width.
     *
     * <p>Compact (2 columns per depth level) below 80 columns, full
     * (4 columns) above. ASCII only.
     *
     * @param columns the terminal width
     * @return the chosen segment set
     */
    public Segments chooseSegments(int columns) {
        return columns < 80 ? ASCII_COMPACT : ASCII_SEGMENTS;
    }

    /**
     * Builds the tree prefix (connection lines) for one entry.
     *
     * @param entry    the entry to render
     * @param segments the segment set chosen for this frame
     * @return the prefix string
     */
    public String renderPrefix(VisibleEntry entry, Segments segments) {
        StringBuilder builder = new StringBuilder();
        for (boolean ancestorWasLast : entry.ancestorIsLast()) {
            builder.append(ancestorWasLast ? segments.empty() : segments.continuation());
        }
        builder.append(entry.isLastSibling() ? segments.leaf() : segments.branch());
        return builder.toString();
    }
}