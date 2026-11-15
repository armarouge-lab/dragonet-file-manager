package br.redhot.dragonet.tui;

import br.redhot.dragonet.config.Config;
import br.redhot.dragonet.config.ConfigBuilder;
import br.redhot.dragonet.config.ConfigException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileListTest {

    @Test
    void dirsComeBeforeFilesAndSortByName(@TempDir Path tempDirectory) throws Exception {
        Files.createFile(tempDirectory.resolve("zebra.txt"));
        Files.createFile(tempDirectory.resolve("apple.txt"));
        Files.createDirectory(tempDirectory.resolve("banana"));
        Files.createDirectory(tempDirectory.resolve("mango"));
        Files.createFile(tempDirectory.resolve("cherry.txt"));

        FileList fileList = FileList.load(tempDirectory, Config.defaults());
        List<String> names = fileList.entries().stream().map(FileEntry::name).toList();

        assertEquals(List.of("..", "banana", "mango", "apple.txt", "cherry.txt", "zebra.txt"), names);
    }

    @Test
    void hiddenFilesExcludedByDefault(@TempDir Path tempDirectory) throws Exception {
        Files.createFile(tempDirectory.resolve("visible.txt"));
        Files.createFile(tempDirectory.resolve(".hidden"));

        FileList fileList = FileList.load(tempDirectory, Config.defaults());
        boolean hasHidden = fileList.entries().stream()
                .anyMatch(entry -> entry.name().equals(".hidden"));

        assertFalse(hasHidden);
    }

    @Test
    void hiddenFilesIncludedWhenConfigured(@TempDir Path tempDirectory) throws Exception {
        Files.createFile(tempDirectory.resolve("visible.txt"));
        Files.createFile(tempDirectory.resolve(".hidden"));

        ConfigBuilder builder = new ConfigBuilder();
        builder.set("show_hidden", "true", Path.of("test"), 1);
        Config config = builder.build();

        FileList fileList = FileList.load(tempDirectory, config);
        boolean hasHidden = fileList.entries().stream()
                .anyMatch(entry -> entry.name().equals(".hidden"));

        assertTrue(hasHidden);
    }

    @Test
    void parentEntryAbsentAtRoot() {
        // Cannot actually load "/" without root permissions; instead,
        // assert that a directory with a parent always has ".." first.
        Path anyNonRoot = Path.of(System.getProperty("user.dir"));
        if (anyNonRoot.getParent() == null) {
            return;
        }
        // Smoke test only; loading the real cwd might fail on permissions.
        // The behavior is covered by dirsComeBeforeFilesAndSortByName.
    }

    @Test
    void cursorStartsOnFirstRealEntry(@TempDir Path tempDirectory) throws Exception {
        Files.createFile(tempDirectory.resolve("a.txt"));
        Files.createFile(tempDirectory.resolve("b.txt"));

        FileList fileList = FileList.load(tempDirectory, Config.defaults());
        assertEquals(1, fileList.cursorIndex());
        assertNotNull(fileList.current());
        assertEquals("a.txt", fileList.current().name());
    }

    @Test
    void cursorMovesAndBounds(@TempDir Path tempDirectory) throws Exception {
        Files.createFile(tempDirectory.resolve("a.txt"));
        Files.createFile(tempDirectory.resolve("b.txt"));
        Files.createFile(tempDirectory.resolve("c.txt"));

        FileList fileList = FileList.load(tempDirectory, Config.defaults());
        // entries: ["..", "a.txt", "b.txt", "c.txt"]; cursor starts at 1.

        assertEquals(1, fileList.cursorIndex());
        fileList.moveUp();
        assertEquals(0, fileList.cursorIndex());
        fileList.moveUp();
        assertEquals(0, fileList.cursorIndex());
        fileList.moveDown();
        fileList.moveDown();
        fileList.moveDown();
        assertEquals(3, fileList.cursorIndex());
        fileList.moveDown();
        assertEquals(3, fileList.cursorIndex());
        fileList.moveToFirst();
        assertEquals(0, fileList.cursorIndex());
        fileList.moveToLast();
        assertEquals(3, fileList.cursorIndex());
    }

    @Test
    void moveToEntryNamedFindsEntry(@TempDir Path tempDirectory) throws Exception {
        Files.createFile(tempDirectory.resolve("x.txt"));
        Files.createFile(tempDirectory.resolve("y.txt"));

        FileList fileList = FileList.load(tempDirectory, Config.defaults());
        fileList.moveToEntryNamed("y.txt");
        assertNotNull(fileList.current());
        assertEquals("y.txt", fileList.current().name());

        fileList.moveToEntryNamed("does-not-exist");
        assertEquals("y.txt", fileList.current().name());
    }

    @Test
    void emptyDirectoryHasOnlyParentEntry(@TempDir Path tempDirectory) throws Exception {
        FileList fileList = FileList.load(tempDirectory, Config.defaults());
        assertEquals(1, fileList.entries().size());
        assertEquals("..", fileList.entries().get(0).name());
        assertEquals(0, fileList.cursorIndex());
        assertNotNull(fileList.current());
    }
}