package br.redhot.dragonet.undo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UndoLogTest {

    @Test
    void emptyWhenFileMissing(@TempDir Path tempDirectory) throws Exception {
        UndoLog log = new UndoLog(tempDirectory.resolve("undo.log"), 100);
        assertTrue(log.readAll().isEmpty());
        assertTrue(log.lastUndoable().isEmpty());
    }

    @Test
    void appendAndRead(@TempDir Path tempDirectory) throws Exception {
        UndoLog log = new UndoLog(tempDirectory.resolve("undo.log"), 100);
        log.append(UndoEntry.mkdir(Path.of("/tmp/newdir"), 1000L));
        log.append(UndoEntry.move(Path.of("/tmp/a"), Path.of("/tmp/b"), 1001L));

        List<UndoEntry> entries = log.readAll();
        assertEquals(2, entries.size());
        assertEquals(UndoEntry.OperationKind.MKDIR, entries.get(0).kind());
        assertEquals(UndoEntry.OperationKind.MOVE, entries.get(1).kind());
        assertEquals("/tmp/newdir", entries.get(0).arguments().get("path"));
        assertEquals("/tmp/a", entries.get(1).arguments().get("source"));
        assertEquals("/tmp/b", entries.get(1).arguments().get("target"));
    }

    @Test
    void lastUndoableReturnsLatest(@TempDir Path tempDirectory) throws Exception {
        UndoLog log = new UndoLog(tempDirectory.resolve("undo.log"), 100);
        log.append(UndoEntry.mkdir(Path.of("/tmp/a"), 1000L));
        log.append(UndoEntry.mkdir(Path.of("/tmp/b"), 1001L));
        log.append(UndoEntry.mkdir(Path.of("/tmp/c"), 1002L));

        Optional<UndoEntry> last = log.lastUndoable();
        assertTrue(last.isPresent());
        assertEquals(1002L, last.get().epochSeconds());
    }

    @Test
    void lastUndoableSkipsUndoneEntries(@TempDir Path tempDirectory) throws Exception {
        UndoLog log = new UndoLog(tempDirectory.resolve("undo.log"), 100);
        log.append(UndoEntry.mkdir(Path.of("/tmp/a"), 1000L));
        log.append(UndoEntry.mkdir(Path.of("/tmp/b"), 1001L));
        log.append(UndoEntry.undo(1001L, 1002L));

        Optional<UndoEntry> last = log.lastUndoable();
        assertTrue(last.isPresent());
        assertEquals(1000L, last.get().epochSeconds());
    }

    @Test
    void allUndoneReturnsEmpty(@TempDir Path tempDirectory) throws Exception {
        UndoLog log = new UndoLog(tempDirectory.resolve("undo.log"), 100);
        log.append(UndoEntry.mkdir(Path.of("/tmp/a"), 1000L));
        log.append(UndoEntry.undo(1000L, 1001L));

        assertTrue(log.lastUndoable().isEmpty());
    }

    @Test
    void undoUndoNotPossible(@TempDir Path tempDirectory) throws Exception {
        UndoLog log = new UndoLog(tempDirectory.resolve("undo.log"), 100);
        log.append(UndoEntry.mkdir(Path.of("/tmp/a"), 1000L));
        log.append(UndoEntry.undo(1000L, 1001L));

        assertTrue(log.lastUndoable().isEmpty());
    }

    @Test
    void pathsWithSpacesRoundTrip(@TempDir Path tempDirectory) throws Exception {
        UndoLog log = new UndoLog(tempDirectory.resolve("undo.log"), 100);
        Path weird = Path.of("/tmp/a file with spaces.txt");
        log.append(UndoEntry.mkdir(weird, 1000L));

        Optional<UndoEntry> entry = log.lastUndoable();
        assertTrue(entry.isPresent());
        assertEquals(weird.toString(), entry.get().arguments().get("path"));
    }

    @Test
    void malformedLinesAreSkipped(@TempDir Path tempDirectory) throws Exception {
        Path file = tempDirectory.resolve("undo.log");
        Files.writeString(file, "garbage\n1000 mkdir path=/tmp/a\n1001 nonsense\n");
        UndoLog log = new UndoLog(file, 100);

        List<UndoEntry> entries = log.readAll();
        assertEquals(1, entries.size());
        assertEquals(UndoEntry.OperationKind.MKDIR, entries.get(0).kind());
    }

    @Test
    void trimsWhenLimitDoubled(@TempDir Path tempDirectory) throws Exception {
        UndoLog log = new UndoLog(tempDirectory.resolve("undo.log"), 3);
        for (int index = 0; index < 20; index = index + 1) {
            log.append(UndoEntry.mkdir(Path.of("/tmp/d" + index), 1000L + index));
        }
        List<UndoEntry> entries = log.readAll();
        assertTrue(entries.size() <= 6,
                "expected file trimmed to <=6 entries, got " + entries.size());
        assertTrue(entries.size() >= 3,
                "expected at least the recent limit kept, got " + entries.size());
    }

    @Test
    void formatAndParseRoundTrip() {
        UndoEntry original = UndoEntry.move(
                Path.of("/tmp/foo bar.txt"), Path.of("/tmp/baz/qux.txt"), 1234567L);
        String formatted = original.format();
        UndoEntry parsed = UndoEntry.parse(formatted);
        assertEquals(original.epochSeconds(), parsed.epochSeconds());
        assertEquals(original.kind(), parsed.kind());
        assertEquals(original.arguments(), parsed.arguments());
    }

    @Test
    void parseNullReturnsNull() {
        assertNull(UndoEntry.parse(null));
        assertNull(UndoEntry.parse(""));
        assertNull(UndoEntry.parse("   "));
        assertNull(UndoEntry.parse("# comment"));
    }

    @Test
    void lastUndoableReflectsSequenceOfUndos(@TempDir Path tempDirectory) throws Exception {
        UndoLog log = new UndoLog(tempDirectory.resolve("undo.log"), 100);
        log.append(UndoEntry.mkdir(Path.of("/tmp/a"), 1000L));
        log.append(UndoEntry.mkdir(Path.of("/tmp/b"), 1001L));
        log.append(UndoEntry.mkdir(Path.of("/tmp/c"), 1002L));

        assertEquals(1002L, log.lastUndoable().orElseThrow().epochSeconds());
        log.append(UndoEntry.undo(1002L, 1003L));
        assertEquals(1001L, log.lastUndoable().orElseThrow().epochSeconds());
        log.append(UndoEntry.undo(1001L, 1004L));
        assertEquals(1000L, log.lastUndoable().orElseThrow().epochSeconds());
        log.append(UndoEntry.undo(1000L, 1005L));
        assertFalse(log.lastUndoable().isPresent());
    }
}
