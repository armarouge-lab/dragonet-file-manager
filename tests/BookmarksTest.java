package br.redhot.dragonet.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookmarksTest {

    @Test
    void emptyWhenFileMissing(@TempDir Path tempDirectory) {
        Bookmarks bookmarks = Bookmarks.load(tempDirectory.resolve("bookmarks.conf"));
        assertEquals(0, bookmarks.count());
        assertTrue(bookmarks.names().isEmpty());
    }

    @Test
    void addPersistsAndReloads(@TempDir Path tempDirectory) throws Exception {
        Path file = tempDirectory.resolve("bookmarks.conf");
        Bookmarks bookmarks = Bookmarks.load(file);
        bookmarks.add("home", Path.of("/home/user"));
        bookmarks.add("tmp", Path.of("/tmp"));
        bookmarks.save();

        Bookmarks reloaded = Bookmarks.load(file);
        assertEquals(2, reloaded.count());
        assertEquals(List.of("home", "tmp"), reloaded.names());
        assertTrue(reloaded.get("home").isPresent());
        assertEquals(Path.of("/home/user"), reloaded.get("home").orElseThrow());
    }

    @Test
    void removePersists(@TempDir Path tempDirectory) throws Exception {
        Path file = tempDirectory.resolve("bookmarks.conf");
        Bookmarks bookmarks = Bookmarks.load(file);
        bookmarks.add("a", Path.of("/a"));
        bookmarks.add("b", Path.of("/b"));
        bookmarks.save();

        bookmarks.remove("a");
        bookmarks.save();

        Bookmarks reloaded = Bookmarks.load(file);
        assertEquals(1, reloaded.count());
        assertEquals(List.of("b"), reloaded.names());
    }

    @Test
    void invalidNameThrows(@TempDir Path tempDirectory) {
        Bookmarks bookmarks = Bookmarks.load(tempDirectory.resolve("b.conf"));
        try {
            bookmarks.add("has\ttab", Path.of("/x"));
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // OK
        }
    }
}
