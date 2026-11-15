package br.redhot.dragonet.trash;

import br.redhot.dragonet.engine.OperationResult;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrashStoreTest {

    private static TrashStore create(@TempDir Path tempDirectory) throws Exception {
        Path trashRoot = tempDirectory.resolve("Trash");
        Path home = tempDirectory.resolve("home");
        Files.createDirectories(home);
        return new TrashStore(trashRoot, home);
    }

    @Test
    void trashAndRestoreFile(@TempDir Path tempDirectory) throws Exception {
        TrashStore store = create(tempDirectory);
        Path home = tempDirectory.resolve("home");
        Path source = home.resolve("note.txt");
        Files.writeString(source, "hello");

        OperationResult trashed = store.moveToTrash(source);
        assertTrue(trashed.success());
        assertFalse(Files.exists(source));

        List<TrashedItem> items = store.listItems();
        assertEquals(1, items.size());
        assertEquals(source, items.get(0).originalPath());

        OperationResult restored = store.restore(items.get(0).uuid());
        assertTrue(restored.success());
        assertTrue(Files.exists(source));
        assertEquals("hello", Files.readString(source));
        assertTrue(store.listItems().isEmpty());
    }

    @Test
    void trashAndRestoreDirectory(@TempDir Path tempDirectory) throws Exception {
        TrashStore store = create(tempDirectory);
        Path home = tempDirectory.resolve("home");
        Path directory = home.resolve("folder");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("inside.txt"), "data");

        OperationResult trashed = store.moveToTrash(directory);
        assertTrue(trashed.success());
        assertFalse(Files.exists(directory));

        OperationResult restored = store.restore(store.listItems().get(0).uuid());
        assertTrue(restored.success());
        assertTrue(Files.isDirectory(directory));
        assertEquals("data", Files.readString(directory.resolve("inside.txt")));
    }

    @Test
    void trashWritesTrashinfo(@TempDir Path tempDirectory) throws Exception {
        TrashStore store = create(tempDirectory);
        Path home = tempDirectory.resolve("home");
        Path source = home.resolve("file with space.txt");
        Files.writeString(source, "x");

        assertTrue(store.moveToTrash(source).success());

        Path infoDir = tempDirectory.resolve("Trash/info");
        try (var stream = Files.newDirectoryStream(infoDir, "*.trashinfo")) {
            Path infoFile = stream.iterator().next();
            String content = Files.readString(infoFile);
            assertTrue(content.startsWith("[Trash Info]"));
            assertTrue(content.contains("DeletionDate="));
            assertTrue(content.contains("file%20with%20space.txt"));
        }
    }

    @Test
    void trashProhibitedPathFails(@TempDir Path tempDirectory) throws Exception {
        TrashStore store = create(tempDirectory);
        OperationResult result = store.moveToTrash(Path.of("/etc/hosts"));
        assertFalse(result.success());
        assertTrue(result.message().contains("protected"));
    }

    @Test
    void trashMissingSourceFails(@TempDir Path tempDirectory) throws Exception {
        TrashStore store = create(tempDirectory);
        Path missing = tempDirectory.resolve("home/missing.txt");
        OperationResult result = store.moveToTrash(missing);
        assertFalse(result.success());
        assertTrue(result.message().contains("not found"));
    }

    @Test
    void restoreMissingParentFails(@TempDir Path tempDirectory) throws Exception {
        TrashStore store = create(tempDirectory);
        Path home = tempDirectory.resolve("home");
        Path parent = Files.createDirectory(home.resolve("nested"));
        Path source = parent.resolve("orphan.txt");
        Files.writeString(source, "x");
        assertTrue(store.moveToTrash(source).success());
        Files.delete(parent);

        OperationResult result = store.restore(store.listItems().get(0).uuid());
        assertFalse(result.success());
        assertTrue(result.message().contains("parent directory does not exist"));
    }

    @Test
    void restoreRejectsExistingTarget(@TempDir Path tempDirectory) throws Exception {
        TrashStore store = create(tempDirectory);
        Path home = tempDirectory.resolve("home");
        Path source = home.resolve("collision.txt");
        Files.writeString(source, "original");
        String uuid = null;
        assertTrue(store.moveToTrash(source).success());
        uuid = store.listItems().get(0).uuid();
        Files.writeString(source, "new");

        OperationResult result = store.restore(uuid);
        assertFalse(result.success());
        assertTrue(result.targetExists());
    }

    @Test
    void restoreUnknownUuidFails(@TempDir Path tempDirectory) throws Exception {
        TrashStore store = create(tempDirectory);
        OperationResult result = store.restore("deadbeef");
        assertFalse(result.success());
        assertTrue(result.message().contains("no such trash item"));
    }

    @Test
    void listIsEmptyWhenTrashMissing(@TempDir Path tempDirectory) throws Exception {
        TrashStore store = create(tempDirectory);
        assertTrue(store.listItems().isEmpty());
    }

    @Test
    void listIsNewestFirst(@TempDir Path tempDirectory) throws Exception {
        TrashStore store = create(tempDirectory);
        Path home = tempDirectory.resolve("home");
        Path first = home.resolve("first.txt");
        Path second = home.resolve("second.txt");
        Files.writeString(first, "1");
        Files.writeString(second, "2");
        assertTrue(store.moveToTrash(first).success());
        Thread.sleep(1100);
        assertTrue(store.moveToTrash(second).success());

        List<TrashedItem> items = store.listItems();
        assertEquals(2, items.size());
        assertTrue(items.get(0).deletionDate().isAfter(items.get(1).deletionDate())
                || items.get(0).deletionDate().equals(items.get(1).deletionDate()));
        assertNotNull(items.get(0).uuid());
    }
}