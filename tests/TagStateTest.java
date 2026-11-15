package br.redhot.dragonet.tui;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TagStateTest {

    private static FileEntry entry(String name, long size) {
        return new FileEntry(name, Path.of("/fake/" + name), FileEntry.FileType.FILE,
                size, Instant.EPOCH, false, false, false, null, false, false, "", "");
    }

    private static FileEntry parent() {
        return new FileEntry("..", Path.of("/fake"), FileEntry.FileType.DIRECTORY,
                0L, Instant.EPOCH, false, false, false, null, false, false, "", "");
    }

    @Test
    void toggleAddsAndRemoves() {
        TagState tagState = new TagState();
        Path path = Path.of("/fake/a.txt");
        assertFalse(tagState.isTagged(path));
        tagState.toggle(path, 100L);
        assertTrue(tagState.isTagged(path));
        assertEquals(1, tagState.count());
        assertEquals(100L, tagState.totalSizeBytes());
        tagState.toggle(path, 100L);
        assertFalse(tagState.isTagged(path));
        assertEquals(0, tagState.count());
    }

    @Test
    void untagRemovesOnlyOne() {
        TagState tagState = new TagState();
        Path a = Path.of("/fake/a.txt");
        Path b = Path.of("/fake/b.txt");
        tagState.toggle(a, 10L);
        tagState.toggle(b, 20L);
        assertEquals(2, tagState.count());

        tagState.untag(a);
        assertEquals(1, tagState.count());
        assertFalse(tagState.isTagged(a));
        assertTrue(tagState.isTagged(b));
    }

    @Test
    void tagAllSkipsParent() {
        TagState tagState = new TagState();
        List<FileEntry> entries = List.of(parent(), entry("a.txt", 10L), entry("b.txt", 20L));
        tagState.tagAll(entries);
        assertEquals(2, tagState.count());
        assertEquals(30L, tagState.totalSizeBytes());
    }

    @Test
    void untagAllSkipsParentAndRemovesAllInList() {
        TagState tagState = new TagState();
        List<FileEntry> entries = List.of(parent(), entry("a.txt", 10L), entry("b.txt", 20L));
        tagState.tagAll(entries);
        assertEquals(2, tagState.count());

        tagState.untagAll(entries);
        assertEquals(0, tagState.count());
    }

    @Test
    void untagAllDoesNotTouchOtherDirectories() {
        TagState tagState = new TagState();
        tagState.toggle(Path.of("/fake/a.txt"), 10L);
        tagState.toggle(Path.of("/other/b.txt"), 20L);
        assertEquals(2, tagState.count());

        tagState.untagAll(List.of(entry("a.txt", 10L)));
        assertEquals(1, tagState.count());
        assertFalse(tagState.isTagged(Path.of("/fake/a.txt")));
        assertTrue(tagState.isTagged(Path.of("/other/b.txt")));
    }

    @Test
    void invertAllTogglesEachEntry() {
        TagState tagState = new TagState();
        List<FileEntry> entries = List.of(parent(), entry("a.txt", 10L), entry("b.txt", 20L));
        tagState.toggle(Path.of("/fake/a.txt"), 10L);
        assertEquals(1, tagState.count());

        tagState.invertAll(entries);
        assertEquals(1, tagState.count());
        assertFalse(tagState.isTagged(Path.of("/fake/a.txt")));
        assertTrue(tagState.isTagged(Path.of("/fake/b.txt")));
    }

    @Test
    void clearRemovesEverything() {
        TagState tagState = new TagState();
        tagState.toggle(Path.of("/fake/a.txt"), 10L);
        tagState.toggle(Path.of("/fake/b.txt"), 20L);
        assertEquals(2, tagState.count());
        tagState.clear();
        assertEquals(0, tagState.count());
        assertEquals(0L, tagState.totalSizeBytes());
    }

    @Test
    void isTaggedWithNullReturnsFalse() {
        TagState tagState = new TagState();
        assertFalse(tagState.isTagged(null));
    }

    @Test
    void pathsReturnsTaggedEntries() {
        TagState tagState = new TagState();
        tagState.toggle(Path.of("/fake/a.txt"), 10L);
        tagState.toggle(Path.of("/fake/b.txt"), 20L);
        assertEquals(2, tagState.paths().size());
        assertTrue(tagState.paths().contains(Path.of("/fake/a.txt")));
        assertTrue(tagState.paths().contains(Path.of("/fake/b.txt")));
    }

    @Test
    void totalSizeAccumulates() {
        TagState tagState = new TagState();
        tagState.toggle(Path.of("/fake/a.txt"), 100L);
        tagState.toggle(Path.of("/fake/b.txt"), 200L);
        tagState.toggle(Path.of("/fake/c.txt"), 300L);
        assertEquals(600L, tagState.totalSizeBytes());
    }
}
