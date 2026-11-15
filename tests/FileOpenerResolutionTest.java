package br.redhot.dragonet.tui;

import br.redhot.dragonet.config.Config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileOpenerResolutionTest {

    private static FileEntry textEntry(Path file) throws Exception {
        long size = Files.size(file);
        return new FileEntry(file.getFileName().toString(), file,
                FileEntry.FileType.FILE, size, Instant.now(),
                false, false, false, null, false, false, "", "");
    }

    private static FileEntry binaryEntry(Path file) throws Exception {
        long size = Files.size(file);
        return new FileEntry(file.getFileName().toString(), file,
                FileEntry.FileType.FILE, size, Instant.now(),
                false, false, false, null, false, false, "", "");
    }

    // ---- Imp present ----

    @Test
    void autoImpTextUsesBlock(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("note.txt");
        Files.writeString(file, "hello");
        FileOpener opener = new FileOpener(true, Config.ImpMode.AUTO);
        FileOpener.Resolution r = opener.resolve(textEntry(file));
        assertEquals(FileOpener.Mode.BLOCK, r.mode());
        assertEquals("imp", r.argv().get(0));
        assertEquals(file.toString(), r.argv().get(1));
    }

    @Test
    void autoImpPngUsesDetach(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("photo.png");
        Files.writeString(file, "fake");
        FileOpener opener = new FileOpener(true, Config.ImpMode.AUTO);
        FileOpener.Resolution r = opener.resolve(binaryEntry(file));
        assertEquals(FileOpener.Mode.DETACH, r.mode());
        assertEquals("imp", r.argv().get(0));
    }

    @Test
    void autoImpBinaryUsesDetach(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("data.bin");
        Files.write(file, new byte[]{1, 2, 0, 3});
        FileOpener opener = new FileOpener(true, Config.ImpMode.AUTO);
        FileOpener.Resolution r = opener.resolve(binaryEntry(file));
        assertEquals(FileOpener.Mode.DETACH, r.mode());
    }

    @Test
    void forcedBlockOverrides(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("photo.png");
        Files.writeString(file, "fake");
        FileOpener opener = new FileOpener(true, Config.ImpMode.BLOCK);
        FileOpener.Resolution r = opener.resolve(binaryEntry(file));
        assertEquals(FileOpener.Mode.BLOCK, r.mode());
    }

    @Test
    void forcedDetachOverrides(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("note.txt");
        Files.writeString(file, "hello");
        FileOpener opener = new FileOpener(true, Config.ImpMode.DETACH);
        FileOpener.Resolution r = opener.resolve(textEntry(file));
        assertEquals(FileOpener.Mode.DETACH, r.mode());
    }

    // ---- Imp absent ----

    @Test
    void fallbackToXdgOpenForBinary(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("data.bin");
        Files.write(file, new byte[]{0});
        FileOpener opener = new FileOpener(false, Config.ImpMode.AUTO);
        FileOpener.Resolution r = opener.resolve(binaryEntry(file));
        // On most CI/dev machines xdg-open may or may not be installed.
        // Assert that if present, the argv is xdg-open + path.
        if (r.mode() != FileOpener.Mode.UNAVAILABLE) {
            assertEquals("xdg-open", r.argv().get(0));
        }
    }

    @Test
    void resolveForEditAlwaysUsesEditor(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("note.txt");
        Files.writeString(file, "hi");
        // Not asserting argv contents because $EDITOR is machine-specific;
        // asserting only that the resolution is BLOCK when an editor is set,
        // and UNAVAILABLE otherwise.
        FileOpener opener = new FileOpener(true, Config.ImpMode.AUTO);
        FileOpener.Resolution r = opener.resolveForEdit(textEntry(file));
        if (r.mode() == FileOpener.Mode.BLOCK) {
            assertTrue(r.argv().size() >= 2);
            assertEquals(file.toString(), r.argv().get(r.argv().size() - 1));
        } else {
            assertEquals(FileOpener.Mode.UNAVAILABLE, r.mode());
        }
    }

    @Test
    void shellCommandResolvesToSh(@TempDir Path tmp) {
        FileOpener opener = new FileOpener(false, Config.ImpMode.AUTO);
        FileOpener.Resolution r = opener.resolveForCommand("!echo hi", tmp);
        assertEquals(FileOpener.Mode.BLOCK, r.mode());
        assertEquals("/bin/sh", r.argv().get(0));
        assertEquals("-c", r.argv().get(1));
        assertEquals("echo hi", r.argv().get(2));
    }

    @Test
    void userCommandResolvesToArgv(@TempDir Path tmp) {
        FileOpener opener = new FileOpener(false, Config.ImpMode.AUTO);
        FileOpener.Resolution r = opener.resolveForCommand("ls -la", tmp);
        assertEquals(FileOpener.Mode.BLOCK, r.mode());
        assertEquals("ls", r.argv().get(0));
        assertEquals("-la", r.argv().get(1));
    }

    @Test
    void emptyCommandIsUnavailable(@TempDir Path tmp) {
        FileOpener opener = new FileOpener(false, Config.ImpMode.AUTO);
        FileOpener.Resolution r = opener.resolveForCommand("", tmp);
        assertEquals(FileOpener.Mode.UNAVAILABLE, r.mode());
    }
}
