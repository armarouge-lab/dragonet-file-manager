package br.redhot.dragonet.tui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewModelTest {

    private static final Path FAKE_ROOT = Path.of("/fake");

    private static FileEntry entry(Path path, long size, Instant mtime, FileEntry.FileType type) {
        return new FileEntry(path.getFileName() == null ? "x" : path.getFileName().toString(),
                path, type, size, mtime, false, false, false, null, false, false, "", "");
    }

    @Test
    void parentEntryReturnsEmpty() {
        PreviewModel model = new PreviewModel(1024);
        FileEntry parent = new FileEntry("..", FAKE_ROOT, FileEntry.FileType.DIRECTORY,
                0L, Instant.EPOCH, false, false, false, null, false, false, "", "");
        PreviewModel.Preview preview = model.preview(parent);
        assertEquals(PreviewModel.PreviewKind.EMPTY, preview.kind());
    }

    @Test
    void textFilePreview(@TempDir Path tempDirectory) throws Exception {
        Path file = tempDirectory.resolve("hello.txt");
        Files.writeString(file, "line one\nline two\nline three");
        FileEntry fe = entry(file, Files.size(file), Instant.now(), FileEntry.FileType.FILE);
        PreviewModel model = new PreviewModel(1024);

        PreviewModel.Preview preview = model.preview(fe);
        assertEquals(PreviewModel.PreviewKind.TEXT, preview.kind());
        assertTrue(preview.lines().get(0).contains("line one"));
    }

    @Test
    void textFileTruncatedWhenOverMaxBytes(@TempDir Path tempDirectory) throws Exception {
        Path file = tempDirectory.resolve("big.txt");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 2000; i++) {
            builder.append("0123456789");
        }
        Files.writeString(file, builder.toString());
        FileEntry fe = entry(file, Files.size(file), Instant.now(), FileEntry.FileType.FILE);
        PreviewModel model = new PreviewModel(500);

        PreviewModel.Preview preview = model.preview(fe);
        assertEquals(PreviewModel.PreviewKind.TEXT, preview.kind());
        boolean hasTruncationMarker = preview.lines().stream()
                .anyMatch(line -> line.contains("truncated"));
        assertTrue(hasTruncationMarker);
    }

    @Test
    void directoryPreviewShowsCount(@TempDir Path tempDirectory) throws Exception {
        Path subdir = Files.createDirectory(tempDirectory.resolve("sub"));
        Files.writeString(subdir.resolve("a.txt"), "hello");
        Files.writeString(subdir.resolve("b.txt"), "world");
        FileEntry fe = entry(subdir, 0L, Instant.now(), FileEntry.FileType.DIRECTORY);
        PreviewModel model = new PreviewModel(1024);

        PreviewModel.Preview preview = model.preview(fe);
        assertEquals(PreviewModel.PreviewKind.DIRECTORY, preview.kind());
        assertTrue(preview.lines().get(0).contains("2 items"));
    }

    @Test
    void binaryFilePreview(@TempDir Path tempDirectory) throws Exception {
        Path file = tempDirectory.resolve("bin.dat");
        Files.write(file, new byte[]{1, 2, 3, 0, 4, 5, (byte) 0xFF});
        FileEntry fe = entry(file, Files.size(file), Instant.now(), FileEntry.FileType.FILE);
        PreviewModel model = new PreviewModel(1024);

        PreviewModel.Preview preview = model.preview(fe);
        assertEquals(PreviewModel.PreviewKind.BINARY, preview.kind());
        assertTrue(preview.lines().get(0).contains("01"));
    }

    @Test
    void cachingWorks(@TempDir Path tempDirectory) throws Exception {
        Path file = tempDirectory.resolve("cached.txt");
        Files.writeString(file, "hello");
        FileEntry fe = entry(file, Files.size(file), Instant.now(), FileEntry.FileType.FILE);
        PreviewModel model = new PreviewModel(1024);

        PreviewModel.Preview first = model.preview(fe);
        PreviewModel.Preview second = model.preview(fe);
        assertEquals(first, second);
    }

    @Test
    void emptyFilePreview(@TempDir Path tempDirectory) throws Exception {
        Path file = tempDirectory.resolve("empty.txt");
        Files.createFile(file);
        FileEntry fe = entry(file, 0L, Instant.now(), FileEntry.FileType.FILE);
        PreviewModel model = new PreviewModel(1024);

        PreviewModel.Preview preview = model.preview(fe);
        assertEquals(PreviewModel.PreviewKind.TEXT, preview.kind());
    }
}
