package br.redhot.dragonet.tui;

import br.redhot.dragonet.config.Config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeModelSearchTest {

    @Test
    void filterMatchesCurrentLevelEntries(@TempDir Path tempDirectory) throws Exception {
        Files.createFile(tempDirectory.resolve("apple.txt"));
        Files.createFile(tempDirectory.resolve("banana.txt"));
        Files.createFile(tempDirectory.resolve("cherry.txt"));

        TreeModel model = new TreeModel(
                FileList.load(tempDirectory, Config.defaults()), Config.defaults());
        model.setSearchQuery("an");

        List<VisibleEntry> visible = model.visibleEntries();
        assertTrue(visible.stream().anyMatch(e -> e.entry() != null
                && e.entry().name().equals("banana.txt")));
        assertFalse(visible.stream().anyMatch(e -> e.entry() != null
                && e.entry().name().equals("apple.txt")));
        assertFalse(visible.stream().anyMatch(e -> e.entry() != null
                && e.entry().name().equals("cherry.txt")));
    }

    @Test
    void searchAutoExpandsCollapsedDirs(@TempDir Path tempDirectory) throws Exception {
        Path proj = Files.createDirectory(tempDirectory.resolve("proj"));
        Files.createFile(proj.resolve("main.c"));
        Files.createFile(tempDirectory.resolve("readme.txt"));

        TreeModel model = new TreeModel(
                FileList.load(tempDirectory, Config.defaults()), Config.defaults());
        assertFalse(model.visibleEntries().stream()
                .anyMatch(e -> e.entry() != null && e.entry().name().equals("main.c")),
                "main.c must not be visible before auto-expand");

        model.setSearchQuery("main");

        assertTrue(model.visibleEntries().stream()
                .anyMatch(e -> e.entry() != null && e.entry().name().equals("main.c")),
                "main.c must be visible after auto-expand");

        model.setSearchQuery("");

        assertFalse(model.visibleEntries().stream()
                .anyMatch(e -> e.entry() != null && e.entry().name().equals("main.c")),
                "main.c must be hidden again after clearing search");
    }

    @Test
    void emptyQueryShowsAll(@TempDir Path tempDirectory) throws Exception {
        Files.createFile(tempDirectory.resolve("a.txt"));
        Files.createFile(tempDirectory.resolve("b.txt"));
        TreeModel model = new TreeModel(
                FileList.load(tempDirectory, Config.defaults()), Config.defaults());

        int unfiltered = model.visibleEntries().size();
        model.setSearchQuery("");
        assertEquals(unfiltered, model.visibleEntries().size());
    }

    @Test
    void caseInsensitiveMatch(@TempDir Path tempDirectory) throws Exception {
        Files.createFile(tempDirectory.resolve("Banana.txt"));
        TreeModel model = new TreeModel(
                FileList.load(tempDirectory, Config.defaults()), Config.defaults());

        model.setSearchQuery("ban");
        assertTrue(model.visibleEntries().stream().anyMatch(e -> e.entry() != null
                && e.entry().name().equals("Banana.txt")));

        model.setSearchQuery("BAN");
        assertTrue(model.visibleEntries().stream().anyMatch(e -> e.entry() != null
                && e.entry().name().equals("Banana.txt")));
    }

    @Test
    void filterClampsCursorWhenOutOfBounds(@TempDir Path tempDirectory) throws Exception {
        for (int index = 0; index < 20; index = index + 1) {
            Files.createFile(tempDirectory.resolve("file" + index + ".txt"));
        }
        TreeModel model = new TreeModel(
                FileList.load(tempDirectory, Config.defaults()), Config.defaults());
        model.moveToIndex(15);
        assertEquals(15, model.cursorIndex());

        model.setSearchQuery("file0");

        int newSize = model.visibleEntries().size();
        assertTrue(newSize >= 1, "expected at least file0.txt");
        assertTrue(model.cursorIndex() < newSize,
                "cursor must be clamped within visible range");
        assertEquals(0, model.cursorIndex());
    }

    @Test
    void isFilteringReflectsState(@TempDir Path tempDirectory) throws Exception {
        Files.createFile(tempDirectory.resolve("a.txt"));
        TreeModel model = new TreeModel(
                FileList.load(tempDirectory, Config.defaults()), Config.defaults());
        assertFalse(model.isFiltering());
        model.setSearchQuery("a");
        assertTrue(model.isFiltering());
        model.setSearchQuery("");
        assertFalse(model.isFiltering());
    }
}
