package br.redhot.dragonet.tui;

import br.redhot.dragonet.config.Config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeModelScrollTest {

    private static void createFiles(Path dir, int count) throws Exception {
        for (int i = 0; i < count; i++) {
            Files.createFile(dir.resolve(String.format("file%02d.txt", i)));
        }
    }

    @Test
    void initialScrollIsZero(@TempDir Path tempDirectory) throws Exception {
        createFiles(tempDirectory, 5);
        TreeModel model = new TreeModel(
                FileList.load(tempDirectory, Config.defaults()), Config.defaults());
        assertEquals(0, model.adjustScrollOffset(10));
    }

    @Test
    void scrollFollowsCursorDown(@TempDir Path tempDirectory) throws Exception {
        createFiles(tempDirectory, 20);
        TreeModel model = new TreeModel(
                FileList.load(tempDirectory, Config.defaults()), Config.defaults());

        model.moveToIndex(10);
        assertEquals(1, model.adjustScrollOffset(10));

        model.moveToIndex(20);
        assertEquals(11, model.adjustScrollOffset(10));
    }

    @Test
    void scrollDoesNotDragWhenCursorMovesUpWithinViewport(@TempDir Path tempDirectory)
            throws Exception {
        createFiles(tempDirectory, 20);
        TreeModel model = new TreeModel(
                FileList.load(tempDirectory, Config.defaults()), Config.defaults());

        model.moveToIndex(20);
        assertEquals(11, model.adjustScrollOffset(10));

        model.moveUp();
        assertEquals(19, model.cursorIndex());
        assertEquals(11, model.adjustScrollOffset(10));

        for (int i = 0; i < 8; i++) {
            model.moveUp();
            model.adjustScrollOffset(10);
        }
        assertEquals(11, model.cursorIndex());
        assertEquals(11, model.adjustScrollOffset(10));

        model.moveUp();
        assertEquals(10, model.cursorIndex());
        assertEquals(10, model.adjustScrollOffset(10));
    }

    @Test
    void moveToIndexClampsAboveRange(@TempDir Path tempDirectory) throws Exception {
        createFiles(tempDirectory, 3);
        TreeModel model = new TreeModel(
                FileList.load(tempDirectory, Config.defaults()), Config.defaults());
        model.moveToIndex(100);
        assertEquals(3, model.cursorIndex());
    }

    @Test
    void moveToIndexClampsBelowRange(@TempDir Path tempDirectory) throws Exception {
        createFiles(tempDirectory, 3);
        TreeModel model = new TreeModel(
                FileList.load(tempDirectory, Config.defaults()), Config.defaults());
        model.moveToIndex(-5);
        assertEquals(0, model.cursorIndex());
    }

    @Test
    void setScrollOffsetClampsNegative(@TempDir Path tempDirectory) throws Exception {
        createFiles(tempDirectory, 3);
        TreeModel model = new TreeModel(
                FileList.load(tempDirectory, Config.defaults()), Config.defaults());
        model.setScrollOffset(-10);
        assertEquals(0, model.scrollOffset());
    }

    @Test
    void adjustScrollOffsetRecoversFromInvalidSavedScroll(@TempDir Path tempDirectory)
            throws Exception {
        createFiles(tempDirectory, 10);
        TreeModel model = new TreeModel(
                FileList.load(tempDirectory, Config.defaults()), Config.defaults());

        model.moveToIndex(10);
        model.setScrollOffset(100);
        int offset = model.adjustScrollOffset(10);
        assertEquals(1, offset);
        assertTrue(offset >= 0);
    }
}
