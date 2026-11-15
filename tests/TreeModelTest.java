package br.redhot.dragonet.tui;

import br.redhot.dragonet.config.Config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeModelTest {

    private static final long WAIT_TIMEOUT_MILLISECONDS = 3000L;

    @Test
    void initialVisibleEntriesMatchFlatList(@TempDir Path tempDirectory) throws Exception {
        Files.createFile(tempDirectory.resolve("a.txt"));
        Files.createFile(tempDirectory.resolve("b.txt"));
        FileList list = FileList.load(tempDirectory, Config.defaults());
        TreeModel model = new TreeModel(list, Config.defaults());

        List<VisibleEntry> visible = model.visibleEntries();
        assertEquals(3, visible.size());
        assertEquals(0, visible.get(0).depth());
        assertEquals(0, visible.get(1).depth());
        assertEquals(0, visible.get(2).depth());
    }

    @Test
    void expandShowsChildrenWithDepthOne(@TempDir Path tempDirectory) throws Exception {
        Path child = Files.createDirectory(tempDirectory.resolve("child"));
        Files.createFile(child.resolve("inside.txt"));

        Config config = Config.defaults();
        FileList list = FileList.load(tempDirectory, config);
        TreeModel model = new TreeModel(list, config);
        model.moveToEntryNamed("child");
        model.expandCurrent();

        VisibleEntry inside = waitForEntry(model, "inside.txt");
        assertNotNull(inside);
        assertEquals(1, inside.depth());
    }

    @Test
    void collapseRemovesChildren(@TempDir Path tempDirectory) throws Exception {
        Path child = Files.createDirectory(tempDirectory.resolve("child"));
        Files.createFile(child.resolve("inside.txt"));

        Config config = Config.defaults();
        FileList list = FileList.load(tempDirectory, config);
        TreeModel model = new TreeModel(list, config);
        model.moveToEntryNamed("child");
        model.expandCurrent();

        assertNotNull(waitForEntry(model, "inside.txt"));

        model.moveToEntryNamed("child");
        model.collapseCurrent();
        assertEquals(2, model.visibleEntries().size());
    }

    @Test
    void lastSiblingFlagIsSetOnLastTopLevelEntry(@TempDir Path tempDirectory) throws Exception {
        Files.createFile(tempDirectory.resolve("a.txt"));
        Files.createFile(tempDirectory.resolve("b.txt"));
        FileList list = FileList.load(tempDirectory, Config.defaults());
        TreeModel model = new TreeModel(list, Config.defaults());

        List<VisibleEntry> visible = model.visibleEntries();
        assertTrue(visible.get(visible.size() - 1).isLastSibling());
    }

    @Test
    void nestedExpansionAncestorIsLastPropagates(@TempDir Path tempDirectory) throws Exception {
        Path a = Files.createDirectory(tempDirectory.resolve("a"));
        Path aChild = Files.createDirectory(a.resolve("aChild"));
        Files.createFile(aChild.resolve("leaf.txt"));

        Config config = Config.defaults();
        FileList list = FileList.load(tempDirectory, config);
        TreeModel model = new TreeModel(list, config);

        model.moveToEntryNamed("a");
        model.expandCurrent();
        assertNotNull(waitForEntry(model, "aChild"));

        model.moveToEntryNamed("aChild");
        model.expandCurrent();
        VisibleEntry leaf = waitForEntry(model, "leaf.txt");

        assertNotNull(leaf);
        assertEquals(2, leaf.depth());
        assertEquals(2, leaf.ancestorIsLast().size());
    }

    private static VisibleEntry waitForEntry(TreeModel model, String name) throws InterruptedException {
        long deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MILLISECONDS;
        while (System.currentTimeMillis() < deadline) {
            VisibleEntry found = model.visibleEntries().stream()
                    .filter(entry -> entry.entry() != null && entry.entry().name().equals(name))
                    .findFirst()
                    .orElse(null);
            if (found != null) {
                return found;
            }
            Thread.sleep(10L);
        }
        return null;
    }
}