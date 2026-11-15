package br.redhot.dragonet.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryTest {

    @Test
    void visitAccumulates(@TempDir Path tempDirectory) {
        History history = new History(tempDirectory.resolve("history"), 100);
        history.visit(Path.of("/a"));
        history.visit(Path.of("/b"));
        history.visit(Path.of("/c"));
        assertEquals(3, history.size());
    }

    @Test
    void backForward(@TempDir Path tempDirectory) {
        History history = new History(tempDirectory.resolve("history"), 100);
        history.visit(Path.of("/a"));
        history.visit(Path.of("/b"));
        history.visit(Path.of("/c"));

        Optional<Path> back = history.back();
        assertTrue(back.isPresent());
        assertEquals(Path.of("/b"), back.get());

        back = history.back();
        assertEquals(Path.of("/a"), back.get());

        back = history.back();
        assertFalse(back.isPresent());

        Optional<Path> forward = history.forward();
        assertEquals(Path.of("/b"), forward.get());
    }

    @Test
    void visitAfterBackTruncatesForward(@TempDir Path tempDirectory) {
        History history = new History(tempDirectory.resolve("history"), 100);
        history.visit(Path.of("/a"));
        history.visit(Path.of("/b"));
        history.visit(Path.of("/c"));
        history.back();
        history.back();

        history.visit(Path.of("/d"));
        assertEquals(2, history.size());
        assertFalse(history.forward().isPresent());
    }

    @Test
    void trimmingKeepsRecent(@TempDir Path tempDirectory) {
        History history = new History(tempDirectory.resolve("history"), 3);
        for (int index = 0; index < 10; index = index + 1) {
            history.visit(Path.of("/dir" + index));
        }
        assertEquals(3, history.size());
    }

    @Test
    void saveAndReload(@TempDir Path tempDirectory) throws Exception {
        Path file = tempDirectory.resolve("history");
        History history = new History(file, 100);
        history.visit(Path.of("/a"));
        history.visit(Path.of("/b"));
        history.save();

        History reloaded = History.load(file, 100);
        assertEquals(2, reloaded.size());
        Optional<Path> back = reloaded.back();
        assertTrue(back.isPresent());
        assertEquals(Path.of("/a"), back.get());
    }

    @Test
    void duplicateVisitIsNoOp(@TempDir Path tempDirectory) {
        History history = new History(tempDirectory.resolve("history"), 100);
        history.visit(Path.of("/a"));
        history.visit(Path.of("/a"));
        assertEquals(1, history.size());
    }
}
