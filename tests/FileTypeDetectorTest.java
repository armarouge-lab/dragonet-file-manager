package br.redhot.dragonet.tui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileTypeDetectorTest {

    @Test
    void textFileIsText(@TempDir Path tempDirectory) throws Exception {
        Path file = tempDirectory.resolve("hello.txt");
        Files.writeString(file, "hello world\nline two\n");
        assertEquals(FileTypeDetector.Kind.TEXT, FileTypeDetector.detect(file));
    }

    @Test
    void jsonIsText(@TempDir Path tempDirectory) throws Exception {
        Path file = tempDirectory.resolve("data.json");
        Files.writeString(file, "{\"key\": \"value\"}");
        assertEquals(FileTypeDetector.Kind.TEXT, FileTypeDetector.detect(file));
    }

    @Test
    void binaryWithNulIsBinary(@TempDir Path tempDirectory) throws Exception {
        Path file = tempDirectory.resolve("data.bin");
        Files.write(file, new byte[]{1, 2, 3, 0, 4, 5});
        assertEquals(FileTypeDetector.Kind.BINARY, FileTypeDetector.detect(file));
    }

    @Test
    void pngExtensionIsImage(@TempDir Path tempDirectory) throws Exception {
        Path file = tempDirectory.resolve("photo.png");
        Files.writeString(file, "fake content, extension only matters");
        assertEquals(FileTypeDetector.Kind.IMAGE, FileTypeDetector.detect(file));
    }

    @Test
    void jpegUppercaseIsImage(@TempDir Path tempDirectory) throws Exception {
        Path file = tempDirectory.resolve("PHOTO.JPG");
        Files.writeString(file, "fake");
        assertTrue(FileTypeDetector.isImageExtension(file));
    }

    @Test
    void extensionCheckCaseInsensitive(@TempDir Path tempDirectory) {
        assertTrue(FileTypeDetector.isImageExtension(Path.of("/tmp/x.Gif")));
        assertFalse(FileTypeDetector.isImageExtension(Path.of("/tmp/x.txt")));
        assertFalse(FileTypeDetector.isImageExtension(Path.of("/tmp/noext")));
    }

    @Test
    void emptyFileIsText(@TempDir Path tempDirectory) throws Exception {
        Path file = tempDirectory.resolve("empty.txt");
        Files.createFile(file);
        assertEquals(FileTypeDetector.Kind.TEXT, FileTypeDetector.detect(file));
    }

    @Test
    void highBitCharsStillText(@TempDir Path tempDirectory) throws Exception {
        Path file = tempDirectory.resolve("utf8.txt");
        Files.writeString(file, "ação, ñ, 日本語");
        assertEquals(FileTypeDetector.Kind.TEXT, FileTypeDetector.detect(file));
    }
}
