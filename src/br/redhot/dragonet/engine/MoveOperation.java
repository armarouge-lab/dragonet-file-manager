package br.redhot.dragonet.engine;

import br.redhot.dragonet.env.PathResolver;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Move from a single source to a destination path.
 *
 * <p>Tries {@code ATOMIC_MOVE} first (same filesystem, instant), then
 * plain move, then a copy+delete fallback for cross-filesystem moves.
 * The source is only removed after a successful copy.
 */
public final class MoveOperation implements Operation {

    private final Path source;
    private final Path target;
    private final Path homeDirectory;
    private final boolean overwrite;

    public MoveOperation(Path source, Path target, Path homeDirectory, boolean overwrite) {
        this.source = Objects.requireNonNull(source, "source");
        this.target = Objects.requireNonNull(target, "target");
        this.homeDirectory = Objects.requireNonNull(homeDirectory, "homeDirectory");
        this.overwrite = overwrite;
    }

    @Override
    public OperationResult execute() {
        if (PathResolver.isProhibitedForDestructive(source, homeDirectory)) {
            return OperationResult.failure("refusing to move protected path: " + source);
        }
        if (PathResolver.isProhibitedForDestructive(target, homeDirectory)) {
            return OperationResult.failure("refusing to move into protected path: " + target);
        }
        if (PathResolver.isAncestorOf(source, target)) {
            return OperationResult.failure("refusing to move into itself: " + target);
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !overwrite) {
            return OperationResult.targetExists("exists: " + target.getFileName());
        }
        try {
            moveAtomic(source, target, overwrite);
            return OperationResult.success("Moved: " + source.getFileName());
        } catch (FileAlreadyExistsException exception) {
            return OperationResult.targetExists("exists: " + target.getFileName());
        } catch (IOException exception) {
            return OperationResult.failure("move failed: " + exception.getMessage());
        }
    }

    private static void moveAtomic(Path source, Path target, boolean overwrite) throws IOException {
        StandardCopyOption[] atomic = overwrite
                ? new StandardCopyOption[]{StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING}
                : new StandardCopyOption[]{StandardCopyOption.ATOMIC_MOVE};
        try {
            Files.move(source, target, atomic);
            return;
        } catch (IOException ignored) {
            // ATOMIC_MOVE unsupported or cross-FS.
        }
        StandardCopyOption[] plain = overwrite
                ? new StandardCopyOption[]{StandardCopyOption.REPLACE_EXISTING}
                : new StandardCopyOption[0];
        try {
            Files.move(source, target, plain);
            return;
        } catch (IOException ignored) {
            // Cross-FS: fall through to copy+delete.
        }
        OperationResult copyResult = new CopyOperation(
                source, target, target.getParent(), overwrite).execute();
        if (!copyResult.success()) {
            throw new IOException("cross-FS move failed: " + copyResult.message());
        }
        deleteRecursively(source);
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            try (var stream = Files.newDirectoryStream(path)) {
                for (Path child : stream) {
                    deleteRecursively(child);
                }
            }
        }
        Files.delete(path);
    }
}
