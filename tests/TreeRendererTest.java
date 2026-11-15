package br.redhot.dragonet.tui;

import br.redhot.dragonet.config.Config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeRendererTest {

    private static final FileEntry SOME_FILE = new FileEntry(
            "a.txt", Path.of("/fake/a.txt"), FileEntry.FileType.FILE, 100L,
            Instant.EPOCH, false, false, false, null, false, false);

    @Test
    void fullSegmentsAtOrAbove80Columns() {
        TreeRenderer renderer = new TreeRenderer(Config.defaults(), true);
        assertEquals(TreeRenderer.ASCII_SEGMENTS, renderer.chooseSegments(80));
        assertEquals(TreeRenderer.ASCII_SEGMENTS, renderer.chooseSegments(120));
    }

    @Test
    void compactSegmentsBelow80Columns() {
        TreeRenderer renderer = new TreeRenderer(Config.defaults(), true);
        assertEquals(TreeRenderer.ASCII_COMPACT, renderer.chooseSegments(60));
        assertEquals(TreeRenderer.ASCII_COMPACT, renderer.chooseSegments(40));
    }

    @Test
    void depthZeroUsesBranchOrLeaf() {
        TreeRenderer renderer = new TreeRenderer(Config.defaults(), true);
        VisibleEntry notLast = new VisibleEntry(SOME_FILE, null, 0, false, List.of());
        VisibleEntry last = new VisibleEntry(SOME_FILE, null, 0, true, List.of());

        assertEquals("+-- ", renderer.renderPrefix(notLast, TreeRenderer.ASCII_SEGMENTS));
        assertEquals("`-- ", renderer.renderPrefix(last, TreeRenderer.ASCII_SEGMENTS));
    }

    @Test
    void depthTwoCombinesAncestorsAndLeaf() {
        TreeRenderer renderer = new TreeRenderer(Config.defaults(), true);

        VisibleEntry entry = new VisibleEntry(SOME_FILE, null, 2, false,
                List.of(false, false));
        assertEquals("|   |   +-- ",
                renderer.renderPrefix(entry, TreeRenderer.ASCII_SEGMENTS));

        VisibleEntry leafInLastBranch = new VisibleEntry(SOME_FILE, null, 2, true,
                List.of(false, true));
        assertEquals("|       `-- ",
                renderer.renderPrefix(leafInLastBranch, TreeRenderer.ASCII_SEGMENTS));
    }

    @Test
    void depthThreeNestedBranches() {
        TreeRenderer renderer = new TreeRenderer(Config.defaults(), true);
        VisibleEntry entry = new VisibleEntry(SOME_FILE, null, 3, false,
                List.of(false, false, true));
        assertEquals("|   |       +-- ",
                renderer.renderPrefix(entry, TreeRenderer.ASCII_SEGMENTS));
    }

    @Test
    void compactSegmentsRenderTwoColumnIndent() {
        TreeRenderer renderer = new TreeRenderer(Config.defaults(), true);
        VisibleEntry entry = new VisibleEntry(SOME_FILE, null, 2, false,
                List.of(false, false));
        assertEquals("| | +- ",
                renderer.renderPrefix(entry, TreeRenderer.ASCII_COMPACT));
    }

    @Test
    void placeholderIsAllowed() {
        VisibleEntry placeholder = new VisibleEntry(null, "Loading...", 1, true, List.of(false));
        assertTrue(placeholder.isPlaceholder());
        assertEquals("Loading...", placeholder.name());
    }
}