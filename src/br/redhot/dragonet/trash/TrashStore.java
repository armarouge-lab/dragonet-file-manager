package br.redhot.dragonet.trash;

import br.redhot.dragonet.engine.OperationResult;
import br.redhot.dragonet.env.PathResolver;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * XDG-compliant trash.
 *
 * <p>Layout follows the freedesktop spec: {@code <trash>/files/<uuid>}
 * holds the payload and {@code <trash>/info/<uuid>.trashinfo} holds the
 * metadata ({@code Path=}, URL-encoded, and {@code DeletionDate=},
 * local-time ISO 8601).
 *
 * <p>V0.2b always uses the home trash directory. Files on other
 * filesystems are moved with copy+delete. Per-mount
 * {@code .Trash-<uid>} directories are deferred to a later version
 * together with trash enumeration across mounts.
 */
public final class TrashStore {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final Path homeTrashDirectory;
    private final Path homeDirectory;

    public TrashStore(Path homeTrashDirectory, Path homeDirectory) {
        this.homeTrashDirectory = Objects.requireNonNull(homeTrashDirectory, "homeTrashDirectory");
        this.homeDirectory = Objects.requireNonNull(homeDirectory, "homeDirectory");
    }

    /**
     * Moves {@code source} into the trash.
     *
     * @param source the file or directory to trash
     * @return the outcome; {@code success} implies the source is gone
     */
    public OperationResult moveToTrash(Path source) {
        Path absoluteSource;
        try {
            absoluteSource = source.toAbsolutePath().normalize();
            if (!Files.exists(absoluteSource, LinkOption.NOFOLLOW_LINKS)) {
                return OperationResult.failure("not found: " + absoluteSource);
            }
        } catch (RuntimeException exception) {
            return OperationResult.failure("cannot resolve path: " + exception.getMessage());
        }

        if (PathResolver.isProhibitedForDestructive(absoluteSource, homeDirectory)) {
            return OperationResult.failure("refusing to trash protected path: " + absoluteSource);
        }

        Path filesDirectory = homeTrashDirectory.resolve("files");
        Path infoDirectory = homeTrashDirectory.resolve("info");
        try {
            Files.createDirectories(filesDirectory);
            Files.createDirectories(infoDirectory);
        } catch (IOException exception) {
            return OperationResult.failure("cannot create trash directories: "
                    + exception.getMessage());
        }

        String uuid = newUuid();
        Path trashedFile = filesDirectory.resolve(uuid);
        try {
            moveOrCopy(absoluteSource, trashedFile);
        } catch (IOException exception) {
            return OperationResult.failure("trash move failed: " + exception.getMessage());
        }

        String info = "[Trash Info]\n"
                + "Path=" + encodePath(absoluteSource.toString()) + "\n"
                + "DeletionDate=" + LocalDateTime.now().format(DATE_FORMAT) + "\n";
        Path infoFile = infoDirectory.resolve(uuid + ".trashinfo");
        try {
            Files.writeString(infoFile, info, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            try {
                moveOrCopy(trashedFile, absoluteSource);
            } catch (IOException ignored) {
                // Could not undo; the file is in the trash but metadata is missing.
            }
            return OperationResult.failure("cannot write trashinfo: " + exception.getMessage());
        }

        return OperationResult.success("Trashed: " + absoluteSource.getFileName());
    }

    /**
     * Enumerates every item in the home trash.
     *
     * @return the trashed items, newest first
     */
    public List<TrashedItem> listItems() {
        List<TrashedItem> items = new ArrayList<>();
        Path infoDirectory = homeTrashDirectory.resolve("info");
        if (!Files.isDirectory(infoDirectory)) {
            return items;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(infoDirectory, "*.trashinfo")) {
            for (Path infoFile : stream) {
                TrashedItem item = parseTrashInfo(homeTrashDirectory, infoFile);
                if (item != null) {
                    items.add(item);
                }
            }
        } catch (IOException ignored) {
            // Unreadable trash: return what we have.
        }
        items.sort((a, b) -> b.deletionDate().compareTo(a.deletionDate()));
        return items;
    }

    /**
     * Restores an item by uuid.
     *
     * @param uuid the trash UUID
     * @return the outcome; {@code success} implies the item is back at
     *         {@code originalPath} and no longer in the trash
     */
    /**
     * Finds the newest trash item whose original path matches.
     *
     * <p>Used immediately after a successful {@link #moveToTrash(Path)}
     * call to obtain the UUID for the undo journal.
     *
     * @param originalPath the original path of the trashed item
     * @return the UUID, or empty when no matching item exists
     */
    public java.util.Optional<String> findLatestUuidFor(Path originalPath) {
        Objects.requireNonNull(originalPath, "originalPath");
        java.nio.file.Path absolute = originalPath.toAbsolutePath().normalize();
        for (TrashedItem item : listItems()) {
            if (item.originalPath().equals(absolute)) {
                return java.util.Optional.of(item.uuid());
            }
        }
        return java.util.Optional.empty();
    }

    public OperationResult restore(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return OperationResult.failure("uuid is required");
        }
        for (TrashedItem item : listItems()) {
            if (item.uuid().equals(uuid)) {
                return restore(item);
            }
        }
        return OperationResult.failure("no such trash item: " + uuid);
    }

    /**
     * Restores a specific item.
     *
     * @param item the item to restore
     * @return the outcome
     */
    public OperationResult restore(TrashedItem item) {
        Path original = item.originalPath();
        Path parent = original.getParent();
        if (parent != null && !Files.isDirectory(parent)) {
            return OperationResult.failure(
                    "parent directory does not exist: " + parent + " (create it first)");
        }
        if (Files.exists(original, LinkOption.NOFOLLOW_LINKS)) {
            return OperationResult.targetExists("target already exists: " + original);
        }
        try {
            moveOrCopy(item.trashFilePath(), original);
            Files.deleteIfExists(item.trashInfoPath());
            return OperationResult.success("Restored: " + original.getFileName());
        } catch (IOException exception) {
            return OperationResult.failure("restore failed: " + exception.getMessage());
        }
    }

    /**
     * Moves {@code source} to {@code target}, falling back to
     * copy+delete for cross-filesystem moves.
     *
     * <p>Uses {@code ATOMIC_MOVE} first (best-effort), then plain move,
     * then copy+delete. The source is only removed after a successful
     * copy, so a failed copy leaves the original intact.
     */
    private static void moveOrCopy(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            return;
        } catch (AtomicMoveNotSupportedException ignored) {
            // Fall through to non-atomic move.
        } catch (IOException ignored) {
            // Some other error (e.g., cross-FS); fall through.
        }
        try {
            Files.move(source, target);
            return;
        } catch (IOException ignored) {
            // Cross-FS or other; fall through to copy+delete.
        }
        copyRecursively(source, target);
        deleteRecursively(source);
    }

    private static void copyRecursively(Path source, Path target) throws IOException {
        if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectories(target);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(source)) {
                for (Path child : stream) {
                    copyRecursively(child, target.resolve(child.getFileName()));
                }
            }
        } else {
            Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path child : stream) {
                    deleteRecursively(child);
                }
            }
        }
        Files.delete(path);
    }

    private static TrashedItem parseTrashInfo(Path trashRoot, Path infoFile) {
        try {
            List<String> lines = Files.readAllLines(infoFile, StandardCharsets.UTF_8);
            String pathValue = null;
            String dateValue = null;
            for (String line : lines) {
                if (line.startsWith("Path=")) {
                    pathValue = line.substring(5);
                } else if (line.startsWith("DeletionDate=")) {
                    dateValue = line.substring(13);
                }
            }
            if (pathValue == null) {
                return null;
            }
            String filename = infoFile.getFileName().toString();
            String uuid = filename.substring(0, filename.length() - ".trashinfo".length());
            Path originalPath = Path.of(decodePath(pathValue));
            Instant deletionDate = Instant.now();
            if (dateValue != null) {
                try {
                    LocalDateTime local = LocalDateTime.parse(dateValue, DATE_FORMAT);
                    deletionDate = local.atZone(ZoneId.systemDefault()).toInstant();
                } catch (Exception ignored) {
                    // Keep the fallback date.
                }
            }
            return new TrashedItem(uuid, originalPath, deletionDate,
                    trashRoot.resolve("files").resolve(uuid), infoFile);
        } catch (IOException exception) {
            return null;
        }
    }

    private static String encodePath(String path) {
        return URLEncoder.encode(path, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("%2F", "/")
                .replace("%2f", "/");
    }

    private static String decodePath(String path) {
        return URLDecoder.decode(path, StandardCharsets.UTF_8);
    }

    private static String newUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}