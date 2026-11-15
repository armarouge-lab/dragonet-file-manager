package br.redhot.dragonet.engine;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopyMoveOperationTest {

    private static final Path FAKE_HOME = Path.of("/home/testuser");

    @Test
    void copyFile(@TempDir Path tempDirectory) throws Exception {
        Path source = tempDirectory.resolve("a.txt");
        Path target = tempDirectory.resolve("b.txt");
        Files.writeString(source, "hello");

        OperationResult result = new CopyOperation(
                source, target, FAKE_HOME, false).execute();
        assertTrue(result.success());
        assertEquals("hello", Files.readString(target));
        assertTrue(Files.exists(source));
    }

    @Test
    void copyDirectoryRecursive(@TempDir Path tempDirectory) throws Exception {
        Path source = Files.createDirectory(tempDirectory.resolve("src"));
        Files.writeString(source.resolve("a.txt"), "a");
        Path sub = Files.createDirectory(source.resolve("sub"));
        Files.writeString(sub.resolve("b.txt"), "b");

        Path target = tempDirectory.resolve("dst");
        OperationResult result = new CopyOperation(
                source, target, FAKE_HOME, false).execute();
        assertTrue(result.success());
        assertEquals("a", Files.readString(target.resolve("a.txt")));
        assertEquals("b", Files.readString(target.resolve("sub").resolve("b.txt")));
    }

    @Test
    void copyRejectsExistingTarget(@TempDir Path tempDirectory) throws Exception {
        Path source = tempDirectory.resolve("a.txt");
        Path target = tempDirectory.resolve("b.txt");
        Files.writeString(source, "a");
        Files.writeString(target, "b");

        OperationResult result = new CopyOperation(
                source, target, FAKE_HOME, false).execute();
        assertFalse(result.success());
        assertTrue(result.targetExists());
        assertEquals("b", Files.readString(target));
    }

    @Test
    void copyOverwriteReplaces(@TempDir Path tempDirectory) throws Exception {
        Path source = tempDirectory.resolve("a.txt");
        Path target = tempDirectory.resolve("b.txt");
        Files.writeString(source, "new");
        Files.writeString(target, "old");

        OperationResult result = new CopyOperation(
                source, target, FAKE_HOME, true).execute();
        assertTrue(result.success());
        assertEquals("new", Files.readString(target));
    }

    @Test
    void copyIntoItselfFails(@TempDir Path tempDirectory) throws Exception {
        Path source = Files.createDirectory(tempDirectory.resolve("src"));
        Path target = source.resolve("nested");
        OperationResult result = new CopyOperation(
                source, target, FAKE_HOME, false).execute();
        assertFalse(result.success());
    }

    @Test
    void moveRenamesFile(@TempDir Path tempDirectory) throws Exception {
        Path source = tempDirectory.resolve("a.txt");
        Path target = tempDirectory.resolve("b.txt");
        Files.writeString(source, "hello");

        OperationResult result = new MoveOperation(
                source, target, FAKE_HOME, false).execute();
        assertTrue(result.success());
        assertFalse(Files.exists(source));
        assertEquals("hello", Files.readString(target));
    }

    @Test
    void moveDetectsConflict(@TempDir Path tempDirectory) throws Exception {
        Path source = tempDirectory.resolve("a.txt");
        Path target = tempDirectory.resolve("b.txt");
        Files.writeString(source, "a");
        Files.writeString(target, "b");

        OperationResult result = new MoveOperation(
                source, target, FAKE_HOME, false).execute();
        assertFalse(result.success());
        assertTrue(result.targetExists());
    }

    @Test
    void moveOverwriteReplaces(@TempDir Path tempDirectory) throws Exception {
        Path source = tempDirectory.resolve("a.txt");
        Path target = tempDirectory.resolve("b.txt");
        Files.writeString(source, "new");
        Files.writeString(target, "old");

        OperationResult result = new MoveOperation(
                source, target, FAKE_HOME, true).execute();
        assertTrue(result.success());
        assertFalse(Files.exists(source));
        assertEquals("new", Files.readString(target));
    }

    @Test
    void copyPreservesSymlink(@TempDir Path tempDirectory) throws Exception {
        Path linkTarget = tempDirectory.resolve("target.txt");
        Files.writeString(linkTarget, "x");
        Path link = tempDirectory.resolve("link.txt");
        Files.createSymbolicLink(link, linkTarget);

        Path targetDir = Files.createDirectory(tempDirectory.resolve("dst"));
        Path target = targetDir.resolve("link.txt");
        OperationResult result = new CopyOperation(
                link, target, FAKE_HOME, false).execute();
        assertTrue(result.success());
        assertTrue(Files.isSymbolicLink(target));
    }

    @Test
    void sparseDetection(@TempDir Path tempDirectory) throws Exception {
        Path sparse = tempDirectory.resolve("sparse.img");
        try (RandomAccessFile file = new RandomAccessFile(sparse.toFile(), "rw")) {
            file.seek(1_000_000L);
            file.write(1);
        }
        long allocatedBytes;
        try {
            allocatedBytes = ((Number) Files.getAttribute(sparse, "unix:blocks"))
                    .longValue() * 512L;
        } catch (Exception exception) {
            Assumptions.assumeTrue(false,
                    "unix:blocks not exposed: " + exception.getMessage());
            return;
        }
        long logicalSize = Files.size(sparse);
        Assumptions.assumeTrue(allocatedBytes < logicalSize,
                "filesystem does not create sparse files (allocated="
                        + allocatedBytes + ", logical=" + logicalSize + ")");
        assertTrue(CopyOperation.isSparse(sparse));
    }

    @Test
    void regularFileIsNotSparse(@TempDir Path tempDirectory) throws Exception {
        Path regular = tempDirectory.resolve("plain.txt");
        Files.writeString(regular, "hello world");
        assertFalse(CopyOperation.isSparse(regular));
    }
}
