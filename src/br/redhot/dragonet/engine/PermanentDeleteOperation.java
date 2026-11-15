package br.redhot.dragonet.engine;

import br.redhot.dragonet.env.PathResolver;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

/**
 * Permanently deletes a file or directory (post-order for directories).
 *
 * <p>The caller is responsible for the double confirmation (typing
 * {@code YES}); this class does not prompt.
 */
public final class PermanentDeleteOperation implements Operation {

    private final Path target;
    private final Path homeDirectory;

    public PermanentDeleteOperation(Path target, Path homeDirectory) {
        this.target = Objects.requireNonNull(target, "target");
        this.homeDirectory = Objects.requireNonNull(homeDirectory, "homeDirectory");
    }

    @Override
    public OperationResult execute() {
        if (PathResolver.isProhibitedForDestructive(target, homeDirectory)) {
            return OperationResult.failure("refusing to delete protected path: " + target);
        }
        try {
            if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
                Files.walkFileTree(target, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                            throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path directory, IOException exception)
                            throws IOException {
                        if (exception != null) {
                            throw exception;
                        }
                        Files.delete(directory);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } else {
                Files.delete(target);
            }
            return OperationResult.success("Permanently deleted: " + target.getFileName());
        } catch (IOException exception) {
            return OperationResult.failure("delete failed: " + exception.getMessage());
        }
    }
}