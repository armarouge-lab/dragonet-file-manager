package br.redhot.dragonet.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateOperationsTest {

    private static final Path FAKE_HOME = Path.of("/home/testuser");

    @Test
    void createDirectory(@TempDir Path tempDirectory) {
        Path target = tempDirectory.resolve("newdir");
        OperationResult result = new CreateDirectoryOperation(target, FAKE_HOME).execute();
        assertTrue(result.success());
        assertTrue(Files.isDirectory(target));
    }

    @Test
    void createDirectoryFailsIfExists(@TempDir Path tempDirectory) throws Exception {
        Path target = tempDirectory.resolve("existing");
        Files.createDirectory(target);
        OperationResult result = new CreateDirectoryOperation(target, FAKE_HOME).execute();
        assertFalse(result.success());
        assertTrue(result.targetExists());
    }

    @Test
    void createFile(@TempDir Path tempDirectory) {
        Path target = tempDirectory.resolve("newfile.txt");
        OperationResult result = new CreateFileOperation(target, FAKE_HOME).execute();
        assertTrue(result.success());
        assertTrue(Files.isRegularFile(target));
    }

    @Test
    void createFileFailsIfExists(@TempDir Path tempDirectory) throws Exception {
        Path target = tempDirectory.resolve("existing.txt");
        Files.createFile(target);
        OperationResult result = new CreateFileOperation(target, FAKE_HOME).execute();
        assertFalse(result.success());
        assertTrue(result.targetExists());
    }

    @Test
    void createSymlink(@TempDir Path tempDirectory) throws Exception {
        Path target = tempDirectory.resolve("target.txt");
        Files.createFile(target);
        Path link = tempDirectory.resolve("link.txt");
        OperationResult result = new CreateSymlinkOperation(link, target, FAKE_HOME).execute();
        assertTrue(result.success());
        assertTrue(Files.isSymbolicLink(link));
    }

    @Test
    void createSymlinkBrokenAllowed(@TempDir Path tempDirectory) {
        Path link = tempDirectory.resolve("broken");
        OperationResult result = new CreateSymlinkOperation(
                link, Path.of("/nonexistent/target"), FAKE_HOME).execute();
        assertTrue(result.success());
        assertTrue(Files.isSymbolicLink(link));
    }

    @Test
    void rename(@TempDir Path tempDirectory) throws Exception {
        Path source = tempDirectory.resolve("old.txt");
        Files.createFile(source);
        OperationResult result = new RenameOperation(
                source, "new.txt", FAKE_HOME, false).execute();
        assertTrue(result.success());
        assertTrue(Files.exists(tempDirectory.resolve("new.txt")));
        assertFalse(Files.exists(source));
    }

    @Test
    void renameDetectsExistingTarget(@TempDir Path tempDirectory) throws Exception {
        Path source = tempDirectory.resolve("source.txt");
        Path target = tempDirectory.resolve("target.txt");
        Files.createFile(source);
        Files.createFile(target);
        OperationResult result = new RenameOperation(
                source, "target.txt", FAKE_HOME, false).execute();
        assertFalse(result.success());
        assertTrue(result.targetExists());
    }

    @Test
    void renameWithOverwriteSucceeds(@TempDir Path tempDirectory) throws Exception {
        Path source = tempDirectory.resolve("source.txt");
        Path target = tempDirectory.resolve("target.txt");
        Files.writeString(source, "source content");
        Files.writeString(target, "target content");
        OperationResult result = new RenameOperation(
                source, "target.txt", FAKE_HOME, true).execute();
        assertTrue(result.success());
        assertEquals("source content", Files.readString(target));
    }

    @Test
    void renameSameNameIsNoOp(@TempDir Path tempDirectory) throws Exception {
        Path source = tempDirectory.resolve("a.txt");
        Files.createFile(source);
        OperationResult result = new RenameOperation(
                source, "a.txt", FAKE_HOME, false).execute();
        assertTrue(result.success());
        assertTrue(Files.exists(source));
    }

    @Test
    void renameWithSlashFails(@TempDir Path tempDirectory) throws Exception {
        Path source = tempDirectory.resolve("a.txt");
        Files.createFile(source);
        OperationResult result = new RenameOperation(
                source, "sub/a.txt", FAKE_HOME, false).execute();
        assertFalse(result.success());
    }

    @Test
    void createDirectoryInProtectedPathFails() {
        Path target = Path.of("/etc/forbidden");
        OperationResult result = new CreateDirectoryOperation(target, FAKE_HOME).execute();
        assertFalse(result.success());
    }
}